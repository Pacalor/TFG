package com.tfg.ierning.data;
/*
 * Copyright 2022 David Padilla Montero
 */

import static com.tfg.ierning.data.Constants.APP_TAG;
import static com.tfg.ierning.data.Constants.DB_FILE;
import static com.tfg.ierning.data.Constants.IA_FILE;
import static com.tfg.ierning.data.Constants.MAXBPS;
import static com.tfg.ierning.data.Constants.MILISECONDAY;
import static com.tfg.ierning.data.Constants.MINBPS;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.interpolation.UnivariateInterpolator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Data base controller and data base file manager
 *
 * @author David Padilla Montero
 */
public class HealthAIDataPackage {

    private HashMap<Long, List<Double>> data; //hash map with epoch time and the data list for that time
    private List<Long> index;   //index with the epoch time, for fast search and sort
    private List<Double> zero;  //empty data list

    private boolean alreadyPrepos;  //know if the data base is preprocessed
    private DayFileController dfc;  //day file controller reference
    private boolean HuaweiFinish;   //know if huawei finish to write
    private boolean GoogleFinish;   //know if google finish to write
    private Lock lock;              //concurrence lock


    //heartRate;----------0
    //sleep; -------------1
    //steps;--------------2
    //acumulatedSteps;----3
    //distance;-----------4
    //acumulatedDistance;-5
    //location;-----------6
    //location;-----------7
    //speed;--------------8
    //calories;-----------9

    /**
     * Default constructor
     * @param dataDay reference to the day file controller
     */
    public HealthAIDataPackage(DayFileController dataDay) {
        data=new HashMap<>();
        index=new ArrayList<>();
        zero=new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            zero.add(0.0);
        }
        alreadyPrepos=true;
        dfc=dataDay;
        HuaweiFinish=false;
        GoogleFinish=false;
        lock=new ReentrantLock();
    }

    /**
     * data base write to memory file
     * @param app main app context
     */
    public void toFile(Context app){

        File file = new File(app.getFilesDir(), DB_FILE);
        String lineToWrite="";
        HuaweiFinish=false;
        GoogleFinish=false;


        try (FileOutputStream fos = app.openFileOutput(DB_FILE, Context.MODE_PRIVATE)) {

            lineToWrite="day \n";
            fos.write(lineToWrite.getBytes());

            lineToWrite="\n";
            fos.write(lineToWrite.getBytes());

            //each part is in normalized units

            lineToWrite="Hour;Beats per second;1: light sleep, 2: REM sleep,3: deep sleep,4: awake,5: nap;Steps;Steps Until Now" +
                    ";Metres;Metres until now;latitude;Longitude;metres per second;calories;\n";
            fos.write(lineToWrite.getBytes());
            Collections.sort(index);

            for (int i = 0; i < index.size(); i++) {
                Long key = index.get(i);
                List<Double> value = data.get(index.get(i));

                lineToWrite=key+";";
                fos.write(lineToWrite.getBytes());

                for (Double d: value) {
                    lineToWrite=d+";";
                    fos.write(lineToWrite.getBytes());
                }
                lineToWrite="\n";
                fos.write(lineToWrite.getBytes());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            System.out.println(file.toPath().toAbsolutePath().toString());
        }
    }


    /**
     * Read data base from file
     * @param app main app context
     */
    public void readFile(Context app)  {

        List<String> lines;
        List<Double> dataRead;
        FileInputStream fis = null;
        try {
            fis = app.openFileInput(DB_FILE);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        InputStreamReader inputStreamReader =
                new InputStreamReader(fis, StandardCharsets.UTF_8);

        try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
            //read the empty lines
            reader.readLine();//day
            reader.readLine();//day number
            reader.readLine();//data type
            String line = reader.readLine();//data

            while (line != null) {

                lines= Arrays.asList(line.split(";"));


                dataRead=new ArrayList<>();

                for (int i = 1; i < 11; i++) {
                    dataRead.add(Double.valueOf(lines.get(i)));

                }


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    data.putIfAbsent(Long.parseLong(lines.get(0)),dataRead);
                    index.add(Long.parseLong(lines.get(0)));
                }

                line = reader.readLine();
            }


        } catch (IOException e) {
            Log.e(APP_TAG,"reading data base file error");
        }
    }

    /**
     * Add speed to the data base with concurrence
     * @param d epoch time to put the data
     * @param value data value
     */
    public void addSpeed(Long d,double value){
        lock.lock();

        List<Double> dtList = new ArrayList<>(zero);
        alreadyPrepos=false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            if(data.containsKey(d)){ //we need to check if is already created

                dtList=data.get(d);
                dtList.set(8,value);
                data.put(d,dtList);

            }else{
                dtList.set(8,value);
                data.putIfAbsent(d,dtList);
                index.add(d);
            }

        }

        lock.unlock();
    }

    /**
     * Add Sleep to the data base with concurrence
     * @param d epoch time to put the data
     * @param value data value
     */
    public void addSleep(Long d,double value){
        lock.lock();

        List<Double> dtList = new ArrayList<>(zero);
        alreadyPrepos=false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            if(data.containsKey(d)){// we need to check if is already created

                dtList=data.get(d);
                dtList.set(1,value);
                data.put(d,dtList);
            }else{
                dtList.set(1,value);
                data.putIfAbsent(d,dtList);
                index.add(d);
            }

        }

        lock.unlock();
    }

    /**
     * Add Steps to the data base with concurrence
     * @param d epoch time to put the data
     * @param value data value
     */
    public void addHSteps(Long d,double value){
        lock.lock();

        List<Double> dtList = new ArrayList<>(zero);
        alreadyPrepos=false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            if(data.containsKey(d)){//we need to check if is already created

                dtList=data.get(d);
                dtList.set(2,value);
                data.put(d,dtList);
            }else{
                dtList.set(2,value);
                data.putIfAbsent(d,dtList);
                index.add(d);

            }

        }

        lock.unlock();
    }

    /**
     * Add Distance to the data base with concurrence
     * @param d epoch time to put the data
     * @param value data value
     */
    public void addDistance(Long d,double value){
        lock.lock();

        List<Double> dtList = new ArrayList<>(zero);
        alreadyPrepos=false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            if(data.containsKey(d)){ //we need to check if is already created
                dtList=data.get(d);
                dtList.set(4,value);
                data.put(d,dtList);
            }else{
                dtList.set(4,value);
                data.putIfAbsent(d,dtList);
                index.add(d);
            }

        }

        lock.unlock();
    }

    /**
     * Add Location to the data base with concurrence
     * @param d epoch time to put the data
     * @param value data value, pair for 2 different location coordinates
     */
    public void addLocation(Long d,Pair<Double,Double> value){
        lock.lock();

        List<Double> dtList = new ArrayList<>(zero);
        alreadyPrepos=false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            if(data.containsKey(d)){//we need to check if is already created

                dtList=data.get(d);
                dtList.set(6,value.first);
                dtList.set(7,value.second);
                data.put(d,dtList);
            }else{
                dtList.set(6,value.first);
                dtList.set(7,value.second);
                data.putIfAbsent(d,dtList);
                index.add(d);
            }

        }

        lock.unlock();
    }

    /**
     * Add Heart Rate to the data base with concurrence
     * @param d epoch time to put the data
     * @param value data value
     */
    public void addHeartRate(Long d,double value){
        lock.lock();

        List<Double> dtList = new ArrayList<>(zero);
        alreadyPrepos=false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            if(data.containsKey(d)){//we need to check if is already created

                dtList=data.get(d);
                dtList.set(0,value);
                data.put(d,dtList);
            }else{
                dtList.set(0,value);
                data.putIfAbsent(d,dtList);
                index.add(d);
            }

        }

        lock.unlock();
    }

    /**
     * Add Calories to the data base with concurrence
     * @param d epoch time to put the data
     * @param value data value
     */
    public void addCalories(Long d,double value){
        lock.lock();

        List<Double> dtList = new ArrayList<>(zero);
        alreadyPrepos=false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            if(data.containsKey(d)){//we need to check if is already created

                dtList=data.get(d);
                dtList.set(9,value);
                data.put(d,dtList);
            }else{
                dtList.set(9,value);
                data.putIfAbsent(d,dtList);
                index.add(d);
            }

        }

        lock.unlock();
    }


    /**
     * Process the data in memory to clean the empty data values
     */
    public void preProcess(){

        if(!alreadyPrepos) {
            alreadyPrepos=true;
            Collections.sort(index);

            //general data
            List<Double> actualData = new ArrayList<>();


            //heart rate data
            List<Double> XHRinterpolation = new ArrayList<>(); //x heart rate interpolation
            List<Double> YHRinterpolation = new ArrayList<>();// y heart rato interpolation
            int HRstart = 0;
            UnivariateInterpolator interpolator = new SplineInterpolator();
            UnivariateFunction polynomial;

            //metres and steps data
            double lastStep = 0.0;
            double lastMetre = 0.0;
            boolean activeDataPrepost = false;

            //STEPS UNTIL NOW3, METRES UNTIL NOW 5
            for (int i = 0; i < index.size(); i++) {
                actualData = data.get(index.get(i)); //get data

                if (actualData.get(3) == (-1.0)) {//search mark
                    activeDataPrepost = true;
                    lastStep = 0.0;//reset data per day
                    lastMetre = 0.0;

                    if (HRstart == 0) {
                        HRstart = i;
                    }//first time i found the -1 to mark the start

                }

                if (activeDataPrepost) {//active data preprocest

                    if (actualData.get(2) != (0.0)) {//data is not 0
                        lastStep += actualData.get(2);
                        actualData.set(3, lastStep);
                    }
                    if (actualData.get(4) != (0.0)) {//data is not 0
                        lastMetre += actualData.get(4);
                        actualData.set(5, lastMetre);
                    }

                    //early heart rate search
                    if (actualData.get(0) != (0.0)) {
                        XHRinterpolation.add((double) i);//add index position
                        YHRinterpolation.add(actualData.get(0));//add real HR value
                    }


                    actualData.set(5, lastMetre);
                    actualData.set(3, lastStep);
                    data.put(index.get(i), actualData);
                }

            }


            double[] x = ArrayUtils.toPrimitive(XHRinterpolation.toArray(new Double[0]));


            double[] y = ArrayUtils.toPrimitive(YHRinterpolation.toArray(new Double[0]));

            polynomial = interpolator.interpolate(x, y);//generate polynomial

            double correctHR=50.0;

            for (int i = (int) x[0]; i <= XHRinterpolation.get(XHRinterpolation.size() - 1); i++) {

                actualData = data.get(index.get(i)); //get data

                if (actualData.get(0) == (0.0)) {//if need change, update data
                    if(polynomial.value(i)>MINBPS && polynomial.value(i)<MAXBPS){
                        actualData.set(0, polynomial.value(i));
                        data.put(index.get(i), actualData);
                    }else{
                        actualData.set(0, correctHR);
                        data.put(index.get(i), actualData);
                    }
                }else{
                    correctHR=actualData.get(0);
                }

            }
        }

    }

    /**
     * Generate the file ARFF for the algorithm to study with sleep data
     * @param app   Main app context
     * @param startDay epoch time to start search sleep data
     * @param days number of days we are going to test with
     * @return numbers of data lines we have
     */
    public int ARFF_sleep(Context app,long startDay,int days){
        int count=0;
        long sleepDay=startDay;

        int startDayIndex=0;
        List<Long> indexShort=new ArrayList<>();
        Pair<Integer,Integer> sleepToTest; //start and finish of the index where there is sleep data

        String lineToWrite="";

        //ARFF format file
        String head =
                "@relation 'ierningData'\n\n"+
                        "@attribute heartRate numeric\n"+
                        "@attribute sleep numeric\n"+
                        "@attribute class {groupA,groupB}\n\n"+
                        "@data\n";

        while(!index.contains(sleepDay) && sleepDay>index.get(1)){//fix near epoch time in the index
            sleepDay--;
        }


        startDayIndex=index.indexOf(sleepDay);


        sleepToTest=foundSleep(startDayIndex);//find sleep

        try (FileOutputStream fos = app.openFileOutput(IA_FILE, Context.MODE_PRIVATE)) {
            fos.write(head.getBytes());

            //find first the group A to study

            for (int i = sleepToTest.first; i <sleepToTest.second ; i++) {
                lineToWrite=+data.get(index.get(i)).get(0)+","+data.get(index.get(i)).get(1)+",groupA\n";
                fos.write(lineToWrite.getBytes());
                count++;
            }

            //search the rest X days to study
            for (int i = 1; i <= days; i++) {

                sleepDay=startDay;

                while(!index.contains(sleepDay-(MILISECONDAY)*i) && sleepDay>index.get(1)){//find near time epoch
                    sleepDay--;
                }
                if(sleepDay>index.get(i)) {
                    startDayIndex = index.indexOf(sleepDay - (MILISECONDAY) * i);
                    sleepToTest = foundSleep(startDayIndex);

                    //get index values
                    for (int j = sleepToTest.first; j < sleepToTest.second; j++) {
                        indexShort.add(index.get(j));
                    }
                }
            }

            //write the group B to study
            for (int i = 0; i <indexShort.size() ; i++) {
                lineToWrite=data.get(indexShort.get(i)).get(0)+","+data.get(indexShort.get(i)).get(1)+",groupB\n";
                fos.write(lineToWrite.getBytes());
                count++;
            }


        } catch (IOException e) {
            e.printStackTrace();
        }

        return count;
    }


    /**
     * Generate the file ARFF for the algorithm to study with activity data
     * @param app   Main app context
     * @param startDay epoch time to start search sleep data
     * @param days number of days we are going to test with
     * @return numbers of data lines we have
     */
    public int ARFF_activity(Context app,long startDay,int days){

        int count=0;
        ArrayList<Integer> activityToTest; //index list with activity data

        String lineToWrite="";

        //ARFF file format
        String head =
                "@relation 'ierningData'\n\n"+
                        "@attribute heartRate numeric\n"+ //0
                        "@attribute steps numeric\n"+ //2
                        "@attribute acumulatedSteps numeric\n"+ //3
                        "@attribute distance numeric\n"+ //4
                        "@attribute acumulatedDistance numeric\n"+ //5
                        "@attribute class {groupA,groupB}\n\n"+
                        "@data\n";



        activityToTest=foundActivity(startDay,startDay+MILISECONDAY);//find sleep


        try (FileOutputStream fos = app.openFileOutput(IA_FILE, Context.MODE_PRIVATE)) {
            fos.write(head.getBytes());

            //first actual day data write

            for (int i:activityToTest) {
                lineToWrite=+data.get(index.get(i)).get(0)+","+data.get(index.get(i)).get(2)
                        +","+data.get(index.get(i)).get(3)+","+data.get(index.get(i)).get(4)
                        +","+data.get(index.get(i)).get(5)+",groupA\n";
                fos.write(lineToWrite.getBytes());
                count++;
            }


            //find the x others day
            for (int i = 1; i <= days; i++) {

                activityToTest=foundActivity(startDay-((MILISECONDAY)*i),(startDay+MILISECONDAY)-((MILISECONDAY)*i));//find sleep

                for (int j:activityToTest) {
                    lineToWrite=+data.get(index.get(j)).get(0)+","+data.get(index.get(j)).get(2)
                            +","+data.get(index.get(j)).get(3)+","+data.get(index.get(j)).get(4)
                            +","+data.get(index.get(j)).get(5)+",groupB\n";
                    fos.write(lineToWrite.getBytes());
                    count++;
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return count;
    }

    /**
     * Search the sleep time with data
     * @param startDayIndex day to search
     * @return start and end of the sleep time
     */
    private Pair<Integer, Integer> foundSleep(int startDayIndex) {

        Pair<Integer,Integer> sleepToTest= new Pair<>(0,0);
        int start=startDayIndex,end=startDayIndex,count=0;
        List<Double> actualData= new ArrayList<>();

        //find the first sleep data
        while((data.get(index.get(start)).get(1) == 0.0) && (data.get(index.get(end)).get(1) == 0.0)     ){
            if(start>0){
                start--;
            }
            if(end<(index.size()-1)){
                end++;
            }
        }

        //CASE 1: found the start of the data
        if((data.get(index.get(end)).get(1) != 0.0)){
            start=end;
            for (int i = start; i < index.size(); i++) {
                 actualData = data.get(index.get(i));

                if(data.get(index.get(start-1)).get(1) != 0.0){
                    start--;
                }

                if(actualData.get(1) == 0.0){
                    count++;
                }else{
                    count=0;
                    end=i;
                }

                if(count >= 3){ //days we can tolerate with no data
                    while(data.get(index.get(start-1)).get(1) != 0.0){
                        start--;
                    }
                    break;
                }
            }
        }else{
            //CASE 2: found the end of the data
            end=start;
            for (int i = end; i >= 0; i--) {
                actualData = data.get(index.get(i));

                if(data.get(index.get(end+1)).get(1) != 0.0){
                    end++;
                }

                if(actualData.get(1) == 0.0){
                    count++;
                }else{
                    count=0;
                    start=i;
                }

                if(count >= 3){//days we can tolerate with no data
                    while(data.get(index.get(end+1)).get(1) != 0.0){
                        end++;
                    }
                    break;
                }
            }
        }

        sleepToTest=new Pair<>(start,end);

        return sleepToTest;
    }

    /**
     * Search the activity time with data
     * @param starDay epoch time to start the search
     * @param endDay epoch time to stop the search
     * @return list with the index numbers with activity data
     */
    private ArrayList<Integer> foundActivity(long starDay,long endDay){
        ArrayList<Integer> points=new ArrayList<>();
        List<Double> actualData= new ArrayList<>();

        int start;


        while(!index.contains(starDay)&& starDay>index.get(1)){//find closer epoch time in the index
            starDay++;
        }


        if(starDay>index.get(1)){//if there is data
            start=index.indexOf(starDay);//first day epoch time


            for (int i = start; i < index.size() && index.get(i) <= endDay ; i++) {//from now to the end of the day
                actualData=data.get(index.get(i));

                if(actualData.get(2)!=(0.0)){
                    points.add(i);
                }

            }
        }
        return points;
    }

    /**
     * Mark the new day, with a indicator so the Preprocess can start there
     */
    public void newDay(){
        List<Double> lastData = new ArrayList<>();
        Collections.sort(index);

        if(index.size()>0) {//we change the last data if we have data

            lastData = data.get(index.get(index.size() - 1));
            Log.e(APP_TAG,"HORA DE LOS DATOS ULTIMOS:"+index.get(index.size()-1));
            lastData.set(3, -1.0);
            lastData.set(5, -1.0);
            data.put(index.get(index.size()-1),lastData );

        }else{//we create the first data if there is no data
            lastData=new ArrayList<>(zero);
            lastData.set(3, -1.0);
            lastData.set(5, -1.0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                data.putIfAbsent(MILISECONDAY,lastData);
                index.add(MILISECONDAY);
            }
        }
    }

    /**
     * add a error day
     * @param day error day
     */
    public void errorDay(String day){
        dfc.addMiss(day);
    }

    /**
     * set finish huawei
     */
    public void setHuaweiFinish(){
        HuaweiFinish=true;
    }

    /**
     * set finish google
     */
    public void setGoogleFinish(){
        GoogleFinish=true;
    }

    /**
     * @return if all APIs finish
     */
    public boolean finishReading(){
        return (HuaweiFinish && GoogleFinish);
    }

    /**
     * Delete all the extra data in the data base
     */
    public void cleanDays() {

        if(dfc.getDayToDelete().size()>0){//check if there is data to delete

            //get first day time
            long starDay=0;
            Date newDay;
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

                try {
                    newDay = dateFormat.parse(dfc.getDays().get(0) + " 00:00:00");
                    starDay=newDay.getTime();

                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
            Long l;

            //clean all days before first day
            for (int i=0;i<index.size();i++) {
                l=index.get(i);

                if(l<starDay){
                    data.remove(l);
                    index.remove(i);
                    i--;
                }


            }

        }
    }

    /**
     * reset the finish APIs indicator
     */
    public void resetWait() {
        HuaweiFinish=false;
        GoogleFinish=false;
    }
}
