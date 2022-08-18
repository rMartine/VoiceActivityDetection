package edu.cicese.clasificadores.microfono;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class ProcesaSVM extends Thread {
	double [] modelo;
	double[] modeloRms;
	double [] normaliza;
	
	short [] muestras = new short[2];
	int nMuestra = 0;
	double ventaneo = 0;
	double entropia = 0;
	double centroide = 0;
	double bw = 0;
	double datoRms = 0;
	double datosZcr = 0;
	double sign0 = 0;
	double sign1 = 0;
	byte[] marco;
	short tamBuffer;
	
	private Handler messageHandler;
	Message msg = new Message();
	Bundle bundle = new Bundle();
	
	public ProcesaSVM(byte[] buff, short tamBuf, double[] elModelo, double[] elModelRms, double[] laNorma, Handler messageHandler){
		marco = buff;
		tamBuffer = tamBuf;
		modelo = elModelo;
		modeloRms = elModelRms;
		normaliza = laNorma;
		this.messageHandler = messageHandler;
	}
	
	public void run(){
			muestras[0] = 0;
			muestras[1] = 0;
			short frameMsb = 0;
			short frameLsb = 0;
			
			nMuestra = 0;
			Complex[] buffer = new Complex[tamBuffer];
			short i = 0;
			for (i = 0; i < (tamBuffer*2); i += 2){
				//Leyendo el dato y convirtiendo de dos bytes a un short
				muestras[0] = muestras[1];
				frameMsb = marco[i+1];
				frameLsb = marco[i];
				if (frameLsb < 0){
					frameLsb = (short) ((frameLsb * (-1)) + 128);
				}
				if (frameMsb < 0){
					muestras[1] = (short) ((-1) * (frameLsb + (frameMsb * (-1) * Math.pow(2,8))));
				} else {
					muestras[1] = (short) (frameLsb + (frameMsb * Math.pow(2,8)));
				}
				//Calculo ZCR
				sign0 = 0;
				sign1 = 0;
				if (muestras[1]>=0){
					sign1 = 1;
				} else {
					sign1 = -1;
				}
				if (muestras[0]>=0){
					sign0 = 1;
				} else {
					sign0 = -1;
				}
				
				ventaneo = muestras[1] * (0.5 - 0.5*Math.cos(2*3.14159265*nMuestra/(tamBuffer-1)));
				buffer[nMuestra] = new Complex (ventaneo, 0);
				//Calculo RMS (Square)
				datoRms = datoRms + Math.pow((muestras[1]), 2);
				//Calculo ZCR
				datosZcr = datosZcr + Math.sqrt(Math.pow((sign1 - sign0),2));
				//datosZcr = datosZcr + (((muestras[1] / (Math.sqrt(Math.pow(muestras[1],2)))) - (muestras[0] / (Math.sqrt(Math.pow(muestras[0],2)))))/2);
				//Termina calculo ZCR
				nMuestra++;
				
				if (nMuestra == tamBuffer){
					calculaDato(buffer, datoRms, datosZcr);
					buffer = null;
					buffer = new Complex[tamBuffer];
					nMuestra = 0;
					datoRms = 0;
					datosZcr = 0;
				}
			}
	}
	
	public void calculaDato (Complex[] elBuffer, double rms, double zcr){
		//Calculo R-M-S
		rms = Math.sqrt((rms / ((double) tamBuffer)));
		//Termina calculo R-M-S
		
		//Calculo ZCR
		zcr = zcr / 2.0;
		//Termina calculo ZCR
		double evaluaRMS = 0.0;
		evaluaRMS = (modeloRms[0]*(rms/normaliza[normaliza.length-1])) + modeloRms[1];
		if (evaluaRMS>0){
			Complex[] bufferFft = new Complex[tamBuffer];
			bufferFft = FFT.fft(elBuffer);
			//Aqui debe realizarse el procesamiento de los datos
			//Comienza normalización
			//Primero se necesita conocer el bin con frecuencia mayor
			//------obtener el valor mas alto en el espectro y poder normalizar---------
			double energiaAcum = 0;
			int i = 0;
			for(i=1; i <= bufferFft.length/2; i++){
				energiaAcum = energiaAcum + bufferFft[i].abs();
			}
			
			double[] fftNorm = new double[tamBuffer/2];
			for (i=1; i <= (tamBuffer/2); i++){
				fftNorm[i-1] = bufferFft[i].abs() / energiaAcum;
			}
			//Termina normalización
			
			//Calculo de entropía, centroide espectral
			double entropiaEspectral = 0;
			double centroideEspectral = 0;
			double centroideSum = 0;
			for (i=0; i<fftNorm.length; i++){
				entropiaEspectral = entropiaEspectral + fftNorm[i] * Math.log(fftNorm[i]);
				centroideEspectral = centroideEspectral + ((i+1)*Math.pow(fftNorm[i], 2));
				centroideSum = centroideSum + Math.pow(fftNorm[i], 2);
			}
			entropiaEspectral = entropiaEspectral * (-1);
			centroideEspectral = centroideEspectral / centroideSum;
			//Termina calculo de entropia y centroide
			
			//Calculo de ancho de banda. Revisar si está bien hecho
			double anchoBanda = 0;
			for (i=0; i<fftNorm.length; i++){
				anchoBanda = anchoBanda + (Math.pow(fftNorm[i], 2) * Math.pow(((i+1) - centroideEspectral), 2));
			}
			anchoBanda = anchoBanda / centroideSum;
			//termina calculo de ancho de banda
			double [] feat = {entropiaEspectral, centroideEspectral, anchoBanda, zcr, rms};
			clasifica(feat, evaluaRMS);
		} else {
			bundle.putString ("text", "NV,Mic: NV\nRMS:" + String.valueOf(evaluaRMS));
			msg.setData(bundle);
			messageHandler.sendMessage(msg);
		}
	}
	
	public void clasifica(double[] features, double Evarms){
		byte i=0;
		double evaluacion=0.0;
		for (i=0; i<features.length; i++){
			evaluacion = evaluacion + (modelo[i]*(features[i]/normaliza[i]));
		}
		evaluacion = evaluacion + modelo[i];
		String res="V";
		if (evaluacion<=0){
			res="NV";
		}
		res = res + ",Mic: " + res + "\nRMS: " + String.valueOf(Evarms) + "\n5F: " + String.valueOf(evaluacion); // System.currentTimeMillis();
		bundle.putString ("text", res);
		msg.setData(bundle);
		messageHandler.sendMessage(msg);
	}
}
