package com.tfg.ierning.data;

/*
 * Copyright 2022 David Padilla Montero
 */

import static com.tfg.ierning.data.Constants.APP_TAG;
import static java.lang.Thread.sleep;

import android.content.Context;
import android.util.Log;

/**
 *
 * Class runnable that write in memory the data base, long time runnable
 *
 * @author David Padilla Montero
 */
public class writer implements Runnable {

    private HealthAIDataPackage dataB; //data base
    private Context app; //main context app

    /**
     * Default constructor
     * @param dataB data base
     * @param myApp main context app
     */
    public writer(HealthAIDataPackage dataB, Context myApp) {
        this.dataB = dataB;
        app=myApp;
    }


    @Override
    public void run() {

        while(!dataB.finishReading()){
            //wait the APIs to finish
            try {
                sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        dataB.cleanDays();
        dataB.preProcess();
        dataB.toFile(app);
        Log.e(APP_TAG,"Data base writing finish");
    }
}
