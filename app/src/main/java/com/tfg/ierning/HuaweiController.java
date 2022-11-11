package com.tfg.ierning;

/*
 * Copyright 2022 David Padilla Montero
 */

import static com.tfg.ierning.data.Constants.APP_TAG;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.huawei.hmf.tasks.OnCompleteListener;
import com.huawei.hmf.tasks.OnFailureListener;
import com.huawei.hmf.tasks.OnSuccessListener;
import com.huawei.hmf.tasks.Task;
import com.huawei.hms.common.ApiException;
import com.huawei.hms.hihealth.DataController;
import com.huawei.hms.hihealth.HiHealthStatusCodes;
import com.huawei.hms.hihealth.HuaweiHiHealth;
import com.huawei.hms.hihealth.data.DataType;
import com.huawei.hms.hihealth.data.Field;
import com.huawei.hms.hihealth.data.SamplePoint;
import com.huawei.hms.hihealth.data.SampleSet;
import com.huawei.hms.hihealth.options.ReadOptions;
import com.huawei.hms.hihealth.result.ReadReply;
import com.huawei.hms.support.account.AccountAuthManager;
import com.huawei.hms.support.account.request.AccountAuthParams;
import com.huawei.hms.support.account.request.AccountAuthParamsHelper;
import com.huawei.hms.support.account.service.AccountAuthService;
import com.tfg.ierning.data.HealthAIDataPackage;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Class that control the Huawei API
 *
 * @author David Padilla Montero
 */
public class HuaweiController {

    private Context mainAct;        //Main app context

    private DataController dataController ; //Huawei data controller
    private HealthAIDataPackage dataB;      //Data base

    private Date actualDate;                //Date to use

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  //date format

    /**
     * Default constructor
     * @param mainAct  Main app context
     * @param data  Data base
     */
    public HuaweiController(Context mainAct, HealthAIDataPackage data) {
        this.mainAct = mainAct;
        this.dataController = HuaweiHiHealth.getDataController(mainAct);
        dataB=data;
        actualDate=new Date();
    }

    /**
     * Cancel de authorization used in the API
     */
    public void cancelAuthorization() {
        AccountAuthParams authParams =
                new AccountAuthParamsHelper().setAccessToken().setScopeList(new ArrayList<>()).createParams();
        final AccountAuthService authService = AccountAuthManager.getService(mainAct, authParams);
        authService.cancelAuthorization().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(Task<Void> task) {
                if (task.isSuccessful()) {
                    // success cancel
                    Log.i(APP_TAG, "cancelAuthorization success");
                } else {
                    // fail cancel
                    Exception exception = task.getException();
                    if (exception instanceof ApiException) {
                        int statusCode = ((ApiException) exception).getStatusCode();
                        Log.e(APP_TAG, "cancelAuthorization fail for errorCode: " + statusCode);
                    }
                }
            }

        });
    }

    /**
     * Request data to the API
     *
     * @param askingDate date to make the request
     */
    public void readData(String askingDate){

        //Prepare the date for the request

        Date startDate = null;
        Date endDate = null;

        try {


            startDate = dateFormat.parse(askingDate+" 00:00:00");

            actualDate=startDate;
            endDate = new Date(startDate.getTime()+86400000);
            Log.e(APP_TAG,startDate.toString());
            Log.e(APP_TAG,endDate.toString());

        } catch (ParseException exception) {
            Log.i(APP_TAG,"Time parsing error");
        }

        //Set the data in the request
        ReadOptions readOptions = new ReadOptions.Builder()
                .read(DataType.DT_INSTANTANEOUS_HEART_RATE)
                .read(DataType.DT_CONTINUOUS_STEPS_DELTA)
                .read(DataType.DT_CONTINUOUS_DISTANCE_DELTA)
                .read(DataType.DT_CONTINUOUS_SLEEP)
                .read(DataType.DT_INSTANTANEOUS_RESTING_HEART_RATE)
                .read(DataType.DT_INSTANTANEOUS_SPEED)
                .read(DataType.DT_INSTANTANEOUS_EXERCISE_HEART_RATE)
                .read(DataType.DT_INSTANTANEOUS_LOCATION_SAMPLE)

                .setTimeRange(startDate.getTime(), endDate.getTime(), TimeUnit.MILLISECONDS)
                .build();

        // Generate the request
        Task<ReadReply> readReplyTask = dataController.read(readOptions);

        //Start the API request and set the listener

        Date finalStartDate = startDate;
        readReplyTask.addOnSuccessListener(new OnSuccessListener<ReadReply>() {
            @Override
            public void onSuccess(ReadReply readReply) {
                logger("Success read a SampleSets from HMS core");
                logger(String.valueOf(readReply.getSampleSets().size()));

                saveSample(readReply, finalStartDate);


            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                String errorCode = e.getMessage();
                String errorMsg = HiHealthStatusCodes.getStatusCodeMessage(Integer.parseInt(errorCode));
                logger(errorCode + ": " + errorMsg);
                dataB.errorDay(askingDate);
            }
        });

    }

    /**
     * Send string to the logcat.
     *
     * @param string Log string.
     */
    private void logger(String string) {
        Log.i(APP_TAG, string);
    }


    /**
     * Save all the reply from the API in the data base
     * @param readReply request reply data
     * @param today day from the reply
     */
    private void saveSample(ReadReply readReply,Date today) {

        double dou=0;
        logger(String.valueOf(readReply.getSampleSets().size()));

        for(SampleSet sampleSet : readReply.getSampleSets()) {//for each sample in my reply
            for (SamplePoint samplePoint : sampleSet.getSamplePoints()) {//for each sample data time


                switch (samplePoint.getDataType().getName()) {

                    case "com.huawei.instantaneous.heart_rate":

                        for (Field field : samplePoint.getDataType().getFields()) {
                            dou=samplePoint.getFieldValue(field).asDoubleValue();

                        }
                        if(dou<=30.0){dou=0.0;}//check wrong data
                        dataB.addHeartRate(samplePoint.getStartTime(TimeUnit.MILLISECONDS),dou);

                        break;

                    case "com.huawei.continuous.steps.delta":

                        for (Field field : samplePoint.getDataType().getFields()) {
                            dou=samplePoint.getFieldValue(field).asIntValue();

                        }
                        dataB.addHSteps(samplePoint.getStartTime(TimeUnit.MILLISECONDS),dou);

                        break;

                    case "com.huawei.continuous.distance.delta":

                        for (Field field : samplePoint.getDataType().getFields()) {
                            dou=samplePoint.getFieldValue(field).asDoubleValue();
                            if(dou<=0.3){dou=0.0;}//check wrong data
                        }
                        dataB.addDistance(samplePoint.getStartTime(TimeUnit.MILLISECONDS),dou);

                        break;

                    case "com.huawei.instantaneous.resting_heart_rate":

                        for (Field field : samplePoint.getDataType().getFields()) {
                            dou=samplePoint.getFieldValue(field).asDoubleValue();
                        }
                        if(dou<=30.0){dou=0.0;}//check wrong data
                        dataB.addHeartRate(samplePoint.getStartTime(TimeUnit.MILLISECONDS),dou);
                        break;

                    case "com.huawei.instantaneous.speed":

                        for (Field field : samplePoint.getDataType().getFields()) {
                            dou=samplePoint.getFieldValue(field).asDoubleValue();
                        }

                        dataB.addSpeed(samplePoint.getStartTime(TimeUnit.MILLISECONDS),dou);

                        break;

                    case "com.huawei.instantaneous.exercise_heart_rate":

                        for (Field field : samplePoint.getDataType().getFields()) {
                            dou=samplePoint.getFieldValue(field).asDoubleValue();
                        }
                        if(dou<=30.0){dou=0.0;}//check wrong data

                        dataB.addHeartRate(samplePoint.getStartTime(TimeUnit.MILLISECONDS),dou);
                        break;

                    case "com.huawei.instantaneous.location.sample":

                        Pair<Double,Double> dou2;
                        dou=samplePoint.getFieldValue(samplePoint.getDataType().getFields().get(0)).asDoubleValue();
                        dou2=new Pair<>(dou,samplePoint.getFieldValue(samplePoint.getDataType().getFields().get(1)).asDoubleValue());

                        dataB.addLocation(samplePoint.getStartTime(TimeUnit.MILLISECONDS),dou2);
                        break;

                    case "com.huawei.continuous.sleep.fragment":

                        for (Field field : samplePoint.getDataType().getFields()) {
                            dou=samplePoint.getFieldValue(field).asIntValue();
                        }

                        dataB.addSleep(samplePoint.getStartTime(TimeUnit.MILLISECONDS),dou);
                        break;

                    default:

                        break;

                }

            }
        }

        dataB.setHuaweiFinish(); //finally we set finish
    }


}
