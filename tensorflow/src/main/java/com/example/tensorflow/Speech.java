package com.example.tensorflow;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class Speech
{
    private Context context;

    public Speech(Context context) {
        this.context = context;
    }

    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_DURATION_MS = 1000;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);
    private static final float DETECTION_THRESHOLD = 0.45f;
    private static final int NumThreads = 2;
    private static final int n_MELs = 128;
    private static final String KEYWORD = "мувикс";
    private static final String TRASHWORD = "trash";
    private static final String LABEL_FILENAME = "conv_actions_labels.txt";
    private static final String MODEL_FILENAME = "mels.tflite";
    private static final String TAG_RECOGNIZE_COMMAND="RecognizeCommand";
    private final ReentrantLock tfLiteLock = new ReentrantLock();
    private int [] imageShape;
    private TensorBuffer inBuffer;
    private TensorBuffer outputTensorBuffer;
    private List<String> associatedAxisLabels = null;

    private List<String> labels = new ArrayList<String>();

    float [] tmpFloatInputBuffer = new float[RECORDING_LENGTH];
    short [] RoundRobinBuffer = new short[RECORDING_LENGTH];



    private final Interpreter.Options tfLiteOptions = new Interpreter.Options();
    private MappedByteBuffer tfLiteModel;
    private Interpreter tfLite;

    /** Memory-map the model file in Assets. */
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


    public  void create() {

        try {
            tfLiteModel = loadModelFile(context.getAssets(), MODEL_FILENAME);
            recreateInterpreter();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        model_info();
        String ASSOCIATED_AXIS_LABELS = LABEL_FILENAME;

        try {
            associatedAxisLabels = FileUtil.loadLabels(context, ASSOCIATED_AXIS_LABELS);
        } catch (IOException e) {
            Log.e("tfliteSupport", "Error reading label file", e);
        }
    }
    private void recreateInterpreter() {
        tfLiteLock.lock();
        try {
            if (tfLite != null) {
                tfLite.close();
                tfLite = null;
            }
            tfLiteOptions.setNumThreads(NumThreads);
            tfLite = new Interpreter(tfLiteModel, tfLiteOptions);
            tfLite.resizeInput(0, new int[] {1,n_MELs,32,1});
        } finally {
            tfLiteLock.unlock();
        }
    }
    com.jlibrosa.audio.JLibrosa jLibrosa = new com.jlibrosa.audio.JLibrosa();


    private void model_info()
    {
        int imageTensorIndex = 0;
        imageShape = tfLite.getInputTensor(imageTensorIndex).shape();
        DataType imageDataType= tfLite.getInputTensor(imageTensorIndex).dataType();
        int probabilityTensorIndex = 0;
        int [] probabilityShape = tfLite.getOutputTensor(probabilityTensorIndex).shape();
        DataType probabilityDataType =tfLite.getOutputTensor(probabilityTensorIndex).dataType();

        //create in/out model buffer
        inBuffer = TensorBuffer.createDynamic(imageDataType);
        outputTensorBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);
    }
    public void clearBuffer()
    {
        RoundRobinBuffer=new short[RECORDING_LENGTH];
    }

    public String recognize(short[] audioBuffer) {
        Map<String, Float> floatMap = null;
        long startTime = new Date().getTime();
        float[] inputBuffer = new float[audioBuffer.length];

        //RoundRobin buffer
        System.arraycopy(RoundRobinBuffer, audioBuffer.length, RoundRobinBuffer, 0, 16000 - audioBuffer.length);
        System.arraycopy(audioBuffer, 0, RoundRobinBuffer, 16000 - audioBuffer.length, audioBuffer.length);

        for (int i = 0; i < RoundRobinBuffer.length; i++)
            tmpFloatInputBuffer[i] = RoundRobinBuffer[i] * 1.0f;


        double [][] melsValues = jLibrosa.generateMelSpectroGram(tmpFloatInputBuffer);



        ByteBuffer input = ByteBuffer.allocateDirect(n_MELs * 32 * 4);
        input.order(ByteOrder.nativeOrder());
        for (int i = 0; i < n_MELs; i++) {

            for (int j = 0; j < 32; j++) {
                input.putFloat((float) melsValues[i][j]);
            }
        }
        int[] shape = new int[]{1, n_MELs, 32, 1};

        inBuffer.loadBuffer(input, shape);
        ByteBuffer inpBuffer = inBuffer.getBuffer();
        float[][] output = new float[1][3];
        tfLite.run(inpBuffer, output);

        Log.d(TAG_RECOGNIZE_COMMAND, "Вероятность movix: "+ Arrays.deepToString(output));
        boolean best_word=true;
        for (int i=0; i<3;i++){
            if (output[0][i]>output[0][0]){ //если есть слово повероятней ключевого ; KEYWORD always 2th position
                best_word=false; //то ключевое неактуально
            }
       }

        if ( (output[0][0]>=DETECTION_THRESHOLD)) {  // ключевое лучшее и прошло порог
            clearBuffer();
          //  Log.d(TAG_RECOGNIZE_COMMAND, "Вероятность movix: "+ Arrays.deepToString(output));
            return KEYWORD;
        }
         return TRASHWORD;
    }


}
