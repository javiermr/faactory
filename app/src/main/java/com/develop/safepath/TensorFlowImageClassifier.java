package com.develop.safepath;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;


import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.gpu.CompatibilityList;


public class TensorFlowImageClassifier implements Classifier {

    private static final int BATCH_SIZE = 1;
    private static final int PIXEL_SIZE = 3;

    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;

    private Interpreter interpreter;
    private Interpreter interpreter_path;

    private int inputSize;
    private boolean quant;


    private TensorFlowImageClassifier() {

    }

    static Classifier create(AssetManager assetManager,
                             String modelPath,
                             int inputSize,
                             boolean quant) throws IOException {


        Interpreter.Options options = new Interpreter.Options();
        CompatibilityList compatList = new CompatibilityList();
        Interpreter.Options options2 = new Interpreter.Options();

        if(compatList.isDelegateSupportedOnThisDevice()){

            // if the device has a supported GPU, add the GPU delegate


            GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
            GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
            options.addDelegate(gpuDelegate);


           /* if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P){
                NnApiDelegate nnApiDelegate=new NnApiDelegate();
                options.addDelegate(nnApiDelegate);
            }
                       else {



            }*/




        } else {
            // if the GPU is not supported, run on 4 threads
            options.setNumThreads(4);
        }

        //options.setNumThreads(4);


        options2.setNumThreads(4);

        TensorFlowImageClassifier classifier = new TensorFlowImageClassifier();
        //classifier.interpreter = new Interpreter(classifier.loadModelFile(assetManager, "mobile_f16.tflite"), options);
        classifier.interpreter = new Interpreter(classifier.loadModelFile(assetManager, "mobile.tflite"), options);
        classifier.interpreter_path = new Interpreter(classifier.loadModelFile(assetManager, "normal_decoder.tflite"), options2);

        classifier.inputSize = inputSize;
        classifier.quant = quant;

        return classifier;
    }

    


    @Override
    public float[][] recognizeImg(Bitmap bitmap) {
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(bitmap);
        if(quant){
            byte[][] result = new byte[1][4];
            float[][] result2float = new float[1][4];


            interpreter.run(byteBuffer, result);

            //Log.d("resultado"," "+result[0][0]+" "+result[0][1]+" "+result[0][2]+" "+result[0][3]);


            result2float[0][0]=(float)result[0][0];
            result2float[0][1]=(float)result[0][1];
            result2float[0][2]=(float)result[0][2];
            result2float[0][3]=(float)result[0][3];



            return result2float;
        }else {
            float [][] result ;
            float [][] result_path;

            int[] dim=new int[4];
            dim[0]=1;
            dim[1]=256;
            dim[2]=256;
            dim[3]=3;



            interpreter.resizeInput(0, dim);
            result = new float[1][3];
            interpreter.run(byteBuffer, result);
            //return getSortedResultFloat(result);

         /*   Log.d("resultado"," "+result[0][0]+" "+result[0][1]+" "+result[0][2]+" "+result[0][3]+" "+" "+result[0][4]+" "+result[0][5]+" "+result[0][6]+" "+result[0][7]+" "+result[0][8]+" "+result[0][9]);


            int[][] e1 = new int[1][1];
            e1[0][0] = 909;

            float[][] e2 = new float[1][512];
            for(int i=0;i<512;i++) {
                e2[0][i] = 0;
            }
            float[][] out1 = new float[1][980];
            float[][] out2 = new float[1][512];

            float[][] sal = new float[1][512];



            for(int i_local=0;i_local<25;i_local++) {
                Object[] inputs = new Object[3];

                inputs[0] = e1;
                inputs[2] = e2;
                inputs[1] = result;
                Map<Integer, Object> outputs = new HashMap<>();

                outputs.put(0, out1);
                outputs.put(1, out2);


                interpreter_path.runForMultipleInputsOutputs(inputs, outputs);


                int a = findMaxIndex(out1);
                //Log.d("regresa", " " + a);
                e1[0][0] =a;

                sal[0][i_local] =a;
                e2 = out2;

                outputs.clear();
                outputs =null;
                inputs = null;
            }*/

            return result;


        }

    }

    int findMaxIndex(float[][] arr) {
        float max = arr[0][0];
        int maxIdx = 0;
        for(int i = 1; i < 980; i++) {
            if(arr[0][i] > max) {
                max = arr[0][i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    @Override
    public void close() {
        interpreter.close();
        interpreter = null;


    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }



    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer;

        if(quant) {
            byteBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * inputSize * inputSize * PIXEL_SIZE);
        } else {
            byteBuffer = ByteBuffer.allocateDirect(4 * BATCH_SIZE * inputSize * inputSize * PIXEL_SIZE);
        }

        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[inputSize * inputSize];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (quant) {
                    // Quantized model
                    byteBuffer.put((byte) ((pixelValue >> 16) & 0xFF));
                    byteBuffer.put((byte) ((pixelValue >> 8) & 0xFF));
                    byteBuffer.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    byteBuffer.putFloat((((pixelValue >> 16) & 0xFF) ));
                    byteBuffer.putFloat((((pixelValue >> 8) & 0xFF) ));
                    byteBuffer.putFloat(((pixelValue & 0xFF) ) );
                }
            }
        }
        return byteBuffer;
    }

}
