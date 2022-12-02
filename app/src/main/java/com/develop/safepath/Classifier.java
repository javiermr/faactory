package com.develop.safepath;

import android.graphics.Bitmap;



public interface Classifier {



    float[][] recognizeImg(Bitmap bitmap);


    void close();
}
