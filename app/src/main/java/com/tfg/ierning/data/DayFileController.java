package com.tfg.ierning.data;

/*
 * Copyright 2022 David Padilla Montero
 */

import static com.tfg.ierning.data.Constants.APP_TAG;

import static com.tfg.ierning.data.Constants.DAY_FILE;
import static com.tfg.ierning.data.Constants.TOTAL_DAYS_SAVED;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Class that control the days in the data and the day to request
 *
 * @author David Padilla Montero
 */
public class DayFileController {

    private ArrayList<String> days;     //Array list with the actual days with data in the data base
    private ArrayList<String> daysMiss; //Array list with the days with data that are not in the data base
    private ArrayList<String> extraDays;//Array list with the days in the data base we dont want
    private Context app;                //Main app context

    /**
     * Default constructor
     *
     * @param application Main app context
     */
    public DayFileController(Context application) {
        this.days = new ArrayList<>();
        this.daysMiss = new ArrayList<>();
        this.extraDays=new ArrayList<>();
        app=application;
    }

    /**
     * Start the day controller reading the file to know the actual data base days, and add the rest of days
     *
     * @return Array list with all miss days we need to request
     */
    public ArrayList<String> start(){

        if(days.size()<TOTAL_DAYS_SAVED) { //if we are not at max days in data base we read the file
            readData();
        }

        if(days.size()<1){ //if the file have not days, we make a initialization
            initialization();
        }else{
            getMissedDays();
        }

        if(days.size()>TOTAL_DAYS_SAVED){ //if we have more than we need we add to the extra days
            deleteExtraDays();
        }

        return daysMiss;
    }

    /**
     * Check the actual day and init all the days we miss
     */
    private void initialization() {

        String newDay="";
        DateTimeFormatter formatter = null;
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
             formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); //days format in data base
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            ZonedDateTime endTime = LocalDateTime.now().atZone(ZoneId.systemDefault()); //get actual day

            for (int i = 0; i < TOTAL_DAYS_SAVED; i++) { //generate days
                endTime=endTime.minusDays(1); //never reach actual day
                newDay=endTime.format(formatter);
                daysMiss.add(newDay);
            }

            Collections.sort(daysMiss);

        }
    }

    /**
     * Check the actual day and init all the days we miss then remove the actual days in the data base
     */
    private void getMissedDays(){
        String newDay="";
        DateTimeFormatter formatter = null;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); //days format in data base
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            ZonedDateTime endTime = LocalDateTime.now().atZone(ZoneId.systemDefault());//get actual day

            for (int i = 0; i < TOTAL_DAYS_SAVED; i++) {//generate days
                endTime=endTime.minusDays(1); //never reach actual day
                newDay=endTime.format(formatter);
                daysMiss.add(newDay);
            }

            daysMiss.removeAll(days);
            Collections.sort(daysMiss);

        }

    }

    /**
     * Read the file day from memory
     */
    private void readData(){

        FileInputStream fis = null;
        try {
            fis = app.openFileInput(DAY_FILE); //android file open
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        InputStreamReader inputStreamReader = new InputStreamReader(fis, StandardCharsets.UTF_8); //open reader

        try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
            String line = reader.readLine();
            while (line != null) { //read the file

                days.add(line);

                line = reader.readLine();
            }
        } catch (IOException e) {
            // Error occurred when opening raw file for reading.
            Log.e(APP_TAG,"reading DAY_FILE error");
        }

    }

    /**
     * Save the actual data base days in the file
     */
    public void save(){

        File file = new File(app.getFilesDir(), DAY_FILE);
        String lineToWrite="";
        String separator="\n";

        Collections.sort(days);

        try (FileOutputStream fos = app.openFileOutput(DAY_FILE, Context.MODE_PRIVATE)) {

            for (String day: days) { //write each day
                lineToWrite=day+separator;
                fos.write(lineToWrite.getBytes());
            }


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Add a new day, remove it from the miss days to request
     * @param day the actual day to be add
     */
    public void addDay(String day){

        days.add(day);
        daysMiss.remove(day);
        save(); //save it in memory
    }

    /**
     * Add a miss day, the last day we use
     * @param day the actual day to be add
     */
    public void addMiss(String day){
        days.remove(days.size()-1); //remove the day form the array
        daysMiss.add(day);
        Collections.sort(days);
        save(); //save it in memory
    }

    /**
     * Add to the array of extra days to be deleted in the data base
     */
    private void deleteExtraDays(){
        Collections.sort(days);
        String fileDay;

        while (days.size()>TOTAL_DAYS_SAVED) {
            fileDay=days.remove(0); //remove it from the day array
            extraDays.add(fileDay);
        }
    }

    /**
     * @return days miss
     */
    public ArrayList<String> getMiss(){
        return daysMiss;
    }

    /**
     * @return days to be deleted
     */
    public ArrayList<String> getDayToDelete(){
        return extraDays;
    }

    /**
     * @return days in data base
     */
    public ArrayList<String> getDays(){
        Collections.sort(days);
        return days;
    }
}
