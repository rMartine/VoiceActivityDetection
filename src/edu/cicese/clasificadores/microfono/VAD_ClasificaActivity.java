package edu.cicese.clasificadores.microfono;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class VAD_ClasificaActivity extends Activity {
	//Variables de hardware de audio y manejador d emensajes
	public AudioRecord audioRecord;
	public int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO; 
	public int audioEncoding = AudioFormat.ENCODING_PCM_16BIT; 
	public static final int SAMPPERSEC = 8000; //22050, 8000, 11025, 22050 44100 or 48000 
	
	//Buffer para guardar los samples
	int blockSize = 4096;
	byte[] buffer = new byte[blockSize*2];
	
	//No tengo idea para que s eusan estas variables.... ??????
	public int mSamplesRead; //La cantidad de samples que fueron leidas
	public int buffersizebytes; //Necesario para el constructor 
	public int buflen; //Necesario para el constructor
	
	//Variables de clasificación
	//Modelo completo de ventanas de 4096 muestras
	/*double [] model = {0.7321, 16.4584, -21.9686, 4.2052, 8.1823, -2.3723};
	double [] normaliza = {7.42606485408159, 1178.80271475241, 611170.419895, 7590.66318492093, 2397.0};*/
	
	//Modelo completo de ventanas de 2048 muestras normalizado a 1.20 5Features para clasificar
	double [] model = {3.3501, 18.6294, -26.1744, 9.0646, 11.2483, -4.3009};
	double [] modelRms = {43.3096, -2.0891};
	double [] normaliza = {8.08836432868427, 752.049542798517, 211182.176627803, 1544.4, 12751.1318344319};
	
	//Modelo completo de ventanas de 20148, normalizado a 1.20 4 features para clasificar
	/*double [] model = {16.342, 17.2421, -26.7856, 5.5616, -13.377};
	double [] modelRms = {43.3096, -2.0891};
	double [] normaliza = {8.08836432868427, 752.049542798517, 211182.176627803, 1544.4, 18014.3501463764};*/
	
	final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg){
			ImageView im = (ImageView) findViewById (R.id.imageView1);
			String[] result = msg.getData().getString("text").split(",");
			if (result[0].compareTo("V")==0){
				im.setImageResource(R.drawable.voz);
			} else{
				im.setImageResource(R.drawable.ambiente);
			}
			TextView tv = (TextView) findViewById (R.id.textView1);
			tv.setText(result[1]);
		}
	};
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final Trigger trigger = new Trigger();
        Button salir = (Button) findViewById(R.id.button1);
        salir.setOnClickListener(new OnClickListener(){
        	public void onClick(View view){
        		try{
        			trigger.interrupt();
    				audioRecord.release();
    				finish();
    			}catch (Exception e){
    				e.printStackTrace();
    			}
        	}
        });
        
		buffersizebytes = AudioRecord.getMinBufferSize(SAMPPERSEC, channelConfiguration, audioEncoding); //4096 on ion 
		audioRecord = new AudioRecord(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPPERSEC, channelConfiguration, audioEncoding, buffersizebytes); //constructor
		audioRecord.startRecording();
		trigger.start();
    }
    
    private class Trigger extends Thread{
		@Override
  		public void run() {
  			ProcesaSVM procesa;
			//ciclo para poner al programa a leer muestras
	    	do{
		    	mSamplesRead = audioRecord.read(buffer, 0, blockSize);
		    	procesa = new ProcesaSVM (buffer, (short) 2048, model, modelRms, normaliza, handler);
				procesa.start();
				try {
					sleep(200);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
	    	} while (true);
			//return null;
		}
  	}
}