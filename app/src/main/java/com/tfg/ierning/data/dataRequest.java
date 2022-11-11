package com.tfg.ierning.data;

/*
 * Copyright 2022 David Padilla Montero
 */
import static com.tfg.ierning.data.Constants.APP_TAG;
import static java.lang.Thread.sleep;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.tfg.ierning.GFitController;
import com.tfg.ierning.HuaweiController;

import java.util.ArrayList;
import java.util.Collections;

/**
 *
 * Class runnable that make a full data base request, long time runnable
 *
 * @author David Padilla Montero
 */
public class dataRequest implements Runnable  {

    private HealthAIDataPackage dataB;  //data base file
    private Context app;                //main app context
    private DayFileController dayFC;    //day file controller
    private HuaweiController HuaweiC;   //Huawei API controller
    private GFitController GFitC;       //Google API controller
    private ImageView wait;             //image use to tell the user that the runnable is working

    /**
     * Default constructor
     *
     * @param dataB data base file
     * @param app   main app context
     * @param dayFC day file controller
     * @param huaweiC   Huawei API controller
     * @param GFitC Google API controller
     * @param im    image use to tell the user that the runnable is working
     */
    public dataRequest(HealthAIDataPackage dataB, Context app, DayFileController dayFC, HuaweiController huaweiC, GFitController GFitC, ImageView im) {
        this.dataB = dataB;
        this.app = app;
        this.dayFC = dayFC;
        HuaweiC = huaweiC;
        this.GFitC = GFitC;
        wait=im;
    }

    @Override
    public void run() {

        //start variables
        ArrayList<String> dataDays = dayFC.getMiss();
        String dayToAsk;
        Collections.sort(dataDays);

        for (int i = 0;dataDays.size()>0;) { //for all days we miss

            dataB.newDay();
            dayToAsk=dataDays.get(i);
            dayFC.addDay(dataDays.get(i));

            HuaweiC.readData(dayToAsk); //Huawei request

            try { //small catch up between the slo Huawei and the fast google
                sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            GFitC.accessGoogleFit(dayToAsk); //Google request

            while(!dataB.finishReading()){
                //we need a long time, enough to finish the requests of the APIs
                try {
                    sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            try {
                //back up time to don't speed up the the
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            dataB.resetWait(); //reset the waiting dataB finish

            dataDays=dayFC.getMiss(); //get the miss days
            Collections.sort(dataDays);

            dataB.preProcess(); //preprocess data after each request
        }


        //finally write database to file and delete old days

        dataB.cleanDays();
        dataB.toFile(app);
        wait.setVisibility(View.INVISIBLE);
        Log.e(APP_TAG,"Data base writing finish");

    }


}
