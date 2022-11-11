package com.tfg.ierning;

/*
 * Copyright 2022 David Padilla Montero
 */

import static com.tfg.ierning.data.Constants.APP_TAG;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.tfg.ierning.data.HealthAIDataPackage;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 *
 * Class that control the Google API
 *
 * @author David Padilla Montero
 */
public class GFitController {

    private GoogleSignInOptions gso;    //Google options
    private GoogleSignInClient client;  //Google client user
    private GoogleSignInAccount account;//Google account from user
    private Context mainAct;            //Main app context
    private FitnessOptions fo;          //request options

    HealthAIDataPackage dataB;          //data base reference

    /**
     * Default constructor
     * @param gso       //Google options
     * @param client    //Google client user
     * @param mainAct   //Main app context
     * @param data      //data base
     */
    public GFitController(GoogleSignInOptions gso, GoogleSignInClient client, Context mainAct, HealthAIDataPackage data) {
        this.gso = gso;
        this.client = client;
        this.mainAct = mainAct;
        dataB=data;
    }

    /**
     * Start the request options and permission, and get the account
     */
    public void Start(){
        //option/permission builder
        fo = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ) //steps
                .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ) //distance
                .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ) //BPS
                .addDataType(DataType.TYPE_LOCATION_SAMPLE, FitnessOptions.ACCESS_READ) //location-
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ) //calories
                .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ) //sleep
                .addDataType(DataType.TYPE_SPEED, FitnessOptions.ACCESS_READ) //speed
                .build();

        account = GoogleSignIn.getAccountForExtension(mainAct, fo);

    }


    /**
     * Create the request and send it
     * @param askingDate day to ask for data
     */
    public void accessGoogleFit(String askingDate) {

        ZonedDateTime start = null,end = null;

        //create day format
        DateTimeFormatter formatter = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
            start = ZonedDateTime.parse(askingDate+" 00:00:00 CET", formatter);
            end= start;
            end=end.plusDays(1);
        }



        Log.i(APP_TAG, "FIT Range Start:" +start);
        Log.i(APP_TAG, "FIT Range End:"+end);

        DataReadRequest readRequest = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            //request build, check options
            readRequest = new DataReadRequest.Builder().aggregate(DataType.TYPE_STEP_COUNT_DELTA)
                    .setTimeRange(start.toEpochSecond(), end.toEpochSecond(), TimeUnit.SECONDS)
                    .bucketByTime(1, TimeUnit.MINUTES)
                    .build();
        }


        ZonedDateTime finalStart = start;
        ZonedDateTime finalEnd = end;
        Fitness.getHistoryClient(mainAct, account)
                .readData(readRequest)
                .addOnSuccessListener (response -> {
                    // The aggregate query puts datasets into buckets, so convert to a
                    // single list of datasets
                    for (Bucket bucket : response.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            dumpDataSet(dataSet,dataB);

                        }
                    }

                    //start cascade request
                    heartRateRequest(dataB,finalStart, finalEnd);


                })
                .addOnFailureListener(e ->
                        Log.w(APP_TAG, "There was an error reading data from Google Fit", e));

    }

    /**
     * Request for heart rate
     * @param data data base
     * @param finalStart date where the request start
     * @param finalEnd date where the request end
     */
    private void heartRateRequest(HealthAIDataPackage data,ZonedDateTime finalStart,ZonedDateTime finalEnd){
        DataReadRequest readRequest = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            //request build, check options
            readRequest = new DataReadRequest.Builder().aggregate(DataType.TYPE_HEART_RATE_BPM)
                    .setTimeRange(finalStart.toEpochSecond(), finalEnd.toEpochSecond(), TimeUnit.SECONDS)
                    .bucketByTime(1, TimeUnit.MINUTES).build();
        }


        Fitness.getHistoryClient(mainAct, account)
                .readData(readRequest)
                .addOnSuccessListener (response -> {
                    // The aggregate query puts datasets into buckets, so convert to a
                    // single list of datasets
                    for (Bucket bucket : response.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            dumpDataSet(dataSet,data);
                        }
                    }
                    //next cascade request
                    locationRequest(data,finalStart, finalEnd);
                })
                .addOnFailureListener(e ->
                        Log.w(APP_TAG, "There was an error reading data from Google Fit", e));

    }

    /**
     * Request for location
     * @param data data base
     * @param finalStart date where the request start
     * @param finalEnd date where the request end
     */
    private void locationRequest(HealthAIDataPackage data,ZonedDateTime finalStart,ZonedDateTime finalEnd){
        DataReadRequest readRequest = null;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            //request build, check options
            readRequest = new DataReadRequest.Builder().aggregate(DataType.TYPE_LOCATION_SAMPLE)
                    .setTimeRange(finalStart.toEpochSecond(), finalEnd.toEpochSecond(), TimeUnit.SECONDS)
                    .bucketByTime(1, TimeUnit.MINUTES).build();
        }


        Fitness.getHistoryClient(mainAct, account)
                .readData(readRequest)
                .addOnSuccessListener (response -> {
                    // The aggregate query puts datasets into buckets, so convert to a
                    // single list of datasets
                    for (Bucket bucket : response.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            dumpDataSet(dataSet,data);
                        }
                    }
                    //nest cascade request
                    caloriesRequest(data,finalStart, finalEnd);
                })
                .addOnFailureListener(e ->
                        Log.w(APP_TAG, "There was an error reading data from Google Fit", e));

    }

    /**
     * Request for calories
     * @param data data base
     * @param finalStart date where the request start
     * @param finalEnd date where the request end
     */
    private void caloriesRequest(HealthAIDataPackage data,ZonedDateTime finalStart,ZonedDateTime finalEnd){
        DataReadRequest readRequest = null;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            //request build, check options
            readRequest = new DataReadRequest.Builder().aggregate(DataType.TYPE_CALORIES_EXPENDED)
                    .setTimeRange(finalStart.toEpochSecond(), finalEnd.toEpochSecond(), TimeUnit.SECONDS)
                    .bucketByTime(1, TimeUnit.MINUTES).build();
        }


        Fitness.getHistoryClient(mainAct, account)
                .readData(readRequest)
                .addOnSuccessListener (response -> {
                    // The aggregate query puts datasets into buckets, so convert to a
                    // single list of datasets
                    for (Bucket bucket : response.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            dumpDataSet(dataSet,data);
                        }
                    }
                    //next cascade request
                    speedRequest(data,finalStart, finalEnd);
                })
                .addOnFailureListener(e ->
                        Log.w(APP_TAG, "There was an error reading data from Google Fit", e));

    }

    /**
     * Request for speed
     * @param data data base
     * @param finalStart date where the request start
     * @param finalEnd date where the request end
     */
    private void speedRequest(HealthAIDataPackage data,ZonedDateTime finalStart,ZonedDateTime finalEnd){
        DataReadRequest readRequest = null;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            //request build, check options
            readRequest = new DataReadRequest.Builder().aggregate(DataType.TYPE_SPEED)
                    .setTimeRange(finalStart.toEpochSecond(), finalEnd.toEpochSecond(), TimeUnit.SECONDS)
                    .bucketByTime(1, TimeUnit.MINUTES).build();
        }


        Fitness.getHistoryClient(mainAct, account)
                .readData(readRequest)
                .addOnSuccessListener (response -> {
                    // The aggregate query puts datasets into buckets, so convert to a
                    // single list of datasets
                    for (Bucket bucket : response.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            dumpDataSet(dataSet,data);
                        }
                    }
                    distanceRequest(data,finalStart, finalEnd);
                })
                .addOnFailureListener(e ->
                        Log.w(APP_TAG, "There was an error reading data from Google Fit", e));

    }

    /**
     * Request for distance
     * @param data data base
     * @param finalStart date where the request start
     * @param finalEnd date where the request end
     */
    private void distanceRequest(HealthAIDataPackage data,ZonedDateTime finalStart,ZonedDateTime finalEnd){
        DataReadRequest readRequest = null;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            //request build, check options
            readRequest = new DataReadRequest.Builder().aggregate(DataType.TYPE_DISTANCE_DELTA)
                    .setTimeRange(finalStart.toEpochSecond(), finalEnd.toEpochSecond(), TimeUnit.SECONDS)
                    .bucketByTime(1, TimeUnit.MINUTES).build();
        }


        Fitness.getHistoryClient(mainAct, account)
                .readData(readRequest)
                .addOnSuccessListener (response -> {
                    // The aggregate query puts datasets into buckets, so convert to a
                    // single list of datasets
                    for (Bucket bucket : response.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            dumpDataSet(dataSet,data);
                        }
                    }
                    //finish the cascade request
                    data.setGoogleFinish();
                })
                .addOnFailureListener(e ->
                        Log.w(APP_TAG, "There was an error reading data from Google Fit", e));

    }

    /**
     * Send the request data to the data base
     * @param dataSet request data
     * @param data database
     */
    private void dumpDataSet(DataSet dataSet,HealthAIDataPackage data) {
        float askData=0f;
        for (DataPoint dp : dataSet.getDataPoints()) {//for each data set
            for (Field field : dp.getDataType().getFields()) {//for each field
                switch (field.getName()) {
                    case "distance":

                        askData=dp.getValue(field).asFloat();
                        if(askData<=0.3){askData=0f;}
                        data.addDistance(dp.getStartTime(TimeUnit.MILLISECONDS),askData);
                        break;

                    case "calories":
                        data.addCalories(dp.getStartTime(TimeUnit.MILLISECONDS),dp.getValue(field).asFloat());
                        break;

                    case "speed":
                        data.addSpeed(dp.getStartTime(TimeUnit.MILLISECONDS),dp.getValue(field).asFloat());
                        break;

                    case "steps":
                        data.addHSteps(dp.getStartTime(TimeUnit.MILLISECONDS),dp.getValue(field).asInt());
                        break;

                    case "average":
                        askData=dp.getValue(field).asFloat();
                        if(askData<=30.0){askData=0f;}
                        data.addHeartRate(dp.getStartTime(TimeUnit.MILLISECONDS),askData);
                        break;

                    default:
                        break;
                }

            }
        }
    }

    /**
     * @return account
     */
    public GoogleSignInAccount getAccount() {
        return account;
    }

    /**
     * @return fitness options
     */
    public FitnessOptions getFo() {
        return fo;
    }


}
