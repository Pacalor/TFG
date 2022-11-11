package com.tfg.ierning.data;

/*
 * Copyright 2022 David Padilla Montero
 */

import java.text.SimpleDateFormat;

/**
 * Class with the constant used in the program
 *
 * @author David Padilla Montero
 */
public class Constants {

    public static final String APP_TAG = "Ierning";
    public static final String DATA_FILE = "configIE";
    public static final String TEST_FILE = "testFile";
    public static final String DAY_FILE = "IerningDAYS";
    public static final String IA_FILE = "IerningData.arff";
    public static final String DB_FILE = "IerningDB";
    public static final Double MAXBPS= 200.0;
    public static final Double MINBPS=30.0;

    public static final SimpleDateFormat DAYFORMAT= new SimpleDateFormat("yyyy-MM-dd");
    public static final String CREDENTIALGOOGLE = "i'm sorry i can't give you mine =D";
    public static final int REQUEST_AUTH = 1004;
    public static final int REQUEST_AUTH_FIT = 1006;
    public static final int TOTAL_DAYS_SAVED = 22;
    public static final long MILISECONDAY = 86400000;


}
