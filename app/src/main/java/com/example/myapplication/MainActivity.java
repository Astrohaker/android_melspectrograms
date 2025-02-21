package com.example.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.example.tensorflow.Speech;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;


public class MainActivity extends AppCompatActivity {
    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_DURATION_MS = 1000;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);
    private static final long MINIMUM_TIME_BETWEEN_SAMPLES_MS = 50;
    private static final int REQUEST_RECORD_AUDIO = 13;

    private static final int BUFFER_LENGTH = RECORDING_LENGTH / 4;
    private static final String TAG_ASSISTANT_SPOTTER = "Assistant.Spotter";

    short[] recordingBuffer = new short[BUFFER_LENGTH];
    short[] inputBuffer = new short[BUFFER_LENGTH];
    boolean shouldContinue = true;
    private Thread recordingThread;
    boolean shouldContinueRecognition = true;
    private Thread recognitionThread;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();
    private TextView textView;
    private TextView timeView;
    private Context context;
    private Speech speech;


    private void requestMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            requestPermissions(
                    new String[]{
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, 1);

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestMicrophonePermission();
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 1);

        context = getBaseContext();
        loadModel(context);
        startRecording();
        startRecognition();

    }

    public void loadModel(Context context) {
        speech = new Speech(context);
        speech.create();
    }

    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        shouldContinue = true;
        recordingThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                record();
                            }
                        });
        recordingThread.start();
    }

    public synchronized void stopRecording() {
        if (recordingThread == null) {
            return;
        }
        shouldContinue = false;
        recordingThread = null;
    }

    private void record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
       /* int bufferSize =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }*/
        int bufferSize = BUFFER_LENGTH;

        short[] audioBuffer = new short[BUFFER_LENGTH];

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {return;}
        AudioRecord record =
                new AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {

            return;
        }
        record.startRecording();
        while (shouldContinue) {
            int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
            recordingBufferLock.lock();
            try {
                System.arraycopy(audioBuffer, 0, recordingBuffer, 0, audioBuffer.length);
            } finally {
                recordingBufferLock.unlock();
            }
        }
        record.stop();
        record.release();
    }
    public synchronized void startRecognition() {
        if (recognitionThread != null) {
            return;
        }
        shouldContinueRecognition = true;
        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                recognize();
                            }
                        });
        recognitionThread.start();
    }

    public synchronized void stopRecognition() {
        if (recognitionThread == null) {
            return;
        }
        shouldContinueRecognition = false;
        recognitionThread = null;
    }
    private boolean equalsArrays(short[] recordingBuffer,short[] inputBuffer)
    {
        boolean equal=false;
        for (int i=0; i<recordingBuffer.length;i++)
        {
            if(recordingBuffer[i]!=inputBuffer[i])
            {
                equal=true;break;
            }
        }
        return equal;
    }

    private void recognize() {

        long lastProcessingTimeMs=0;
        long startTime =0;
        while (shouldContinueRecognition) {


            if(equalsArrays(recordingBuffer,inputBuffer)) {
                recordingBufferLock.lock();
                try {
                    System.arraycopy(recordingBuffer, 0, inputBuffer, 0, recordingBuffer.length);
                } finally {
                    recordingBufferLock.unlock();
                }

                //тут загрузка в модель и получение результата
                startTime = new Date().getTime();
                String result = speech.recognize(inputBuffer);
                lastProcessingTimeMs = new Date().getTime() - startTime;
                if (result == "мувикс")
                {
                    Log.d(TAG_ASSISTANT_SPOTTER, String.format("Сработала активационная фраза. Время распознавания: %d мс", lastProcessingTimeMs));
                }

                //view result
                timeView = findViewById(R.id.timeview);
                timeView.setText(lastProcessingTimeMs + " ms");
                textView = findViewById(R.id.result);
                textView.setText(result);
                try {
                    Thread.sleep(MINIMUM_TIME_BETWEEN_SAMPLES_MS);
                } catch (InterruptedException e) {

                }
            }
        }
    }


}