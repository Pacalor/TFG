package com.tfg.ierning;

/*
 * Copyright 2022 David Padilla Montero
 */

import static com.tfg.ierning.data.Constants.APP_TAG;
import static com.tfg.ierning.data.Constants.CREDENTIALGOOGLE;
import static com.tfg.ierning.data.Constants.DAY_FILE;
import static com.tfg.ierning.data.Constants.DB_FILE;
import static com.tfg.ierning.data.Constants.REQUEST_AUTH;
import static com.tfg.ierning.data.Constants.REQUEST_AUTH_FIT;
import static com.tfg.ierning.data.Constants.TOTAL_DAYS_SAVED;

import static java.lang.Thread.sleep;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.firebase.auth.FirebaseAuth;
import com.huawei.hms.hihealth.HuaweiHiHealth;
import com.huawei.hms.hihealth.SettingController;
import com.huawei.hms.hihealth.data.Scopes;
import com.huawei.hms.hihealth.result.HealthKitAuthResult;
import com.tfg.ierning.AI.IAController;
import com.tfg.ierning.data.DayFileController;
import com.tfg.ierning.data.HealthAIDataPackage;
import com.tfg.ierning.data.dataRequest;
import com.tfg.ierning.data.writer;
import com.tfg.ierning.databinding.ActivityMainBinding;

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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main android class, control view and the app work
 * @author David Padilla Montero
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;    //main app activity

    private SettingController HuaweiSettingController; //huawei setting

    private HuaweiController HWcontroller;  //huawei API controller

    private FirebaseAuth mAuth; //fire base authentication

    private GoogleSignInOptions gso; //google options

    private GoogleSignInClient mGoogleSignInClient; //google client

    private GFitController gfcontroller; //Google API controller

    private DayFileController dfc; //day controller

    private ArrayList<String> dataDays; //days to ask data

    private HealthAIDataPackage dataBase; //data base controller

    private IAController IA; //IA controller class

    private ExecutorService executor; //threadPool executor

    private writer dataWriter; //runnable writer

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor= Executors.newFixedThreadPool(4);

        //Android default onCreate

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);


        findViewById(R.id.wait).setVisibility(View.INVISIBLE);

        //FILES INIT

        //test file existing

        Log.i(APP_TAG,""+this.fileList().length);

        if(this.fileList().length<3){

            //INIT
            try {
                initFiles();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        //Data file controller on create


        dfc=new DayFileController(this);
        dataDays=dfc.start();

        dataBase=new HealthAIDataPackage(dfc);
        dataBase.readFile(this);

        dataWriter=new writer(dataBase,this);

        //Huawei on create

        HWcontroller= new HuaweiController(getApplicationContext(),dataBase);

        initService();

        //Authorization process, which is called each time the process is started.
        requestAuth();

        //Google fit on create

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
         gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(CREDENTIALGOOGLE)
                .requestEmail()
                .build();


        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        //google fit controller
        gfcontroller=new GFitController(gso,mGoogleSignInClient,this,dataBase);
        gfcontroller.Start();

        //account check
        signIn();

        //IA start
        IA =new IAController(this);

    }

    @Override
    protected void onStart() {
        super.onStart();

        //DATA COLLECTION
        startDataCollection();
    }

    @Override
    public void finish() {
        super.finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    /**
     * write data in external data
     * @param view main view
     */
    public void saveData(View view){

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                23);

        String text= ruleRead();

        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(folder, "taxis_rules.txt");

        try (FileOutputStream fos = new FileOutputStream(file)) {

            fos.write(text.getBytes());

            Toast.makeText(this, "Archivo guardado en:" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
        }

        file = new File(folder, "taxis_quaSumm.txt");
        text = readResume();
        try (FileOutputStream fos = new FileOutputStream(file)) {

            fos.write(text.getBytes());
            Toast.makeText(this, "Archivo guardado en:" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Show data to the user
     * @param view main app view
     */
    public void showData(View view){

        String rules= ruleRead();

        AlertDialog.Builder builder= new AlertDialog.Builder(this);
        builder.setMessage(rules).setTitle("Reglas de la última ejecución del algoritmo");
        builder.setPositiveButton("Cerrar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }


    /**
     * start the full data collection
     * @param view main app view
     */
    public void forceDownload(View view){

        startFullDataCollection();
    }

    /**
     * Google sing it
     */
    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, REQUEST_AUTH_FIT);
    }

    /**
     * Initialize SettingController.
     */
    private void initService() {
        HuaweiSettingController = HuaweiHiHealth.getSettingController(this);
    }


    /**
     * Scope declaration to use in the Huawei data service
     */
    private void requestAuth() {

        // Add scopes to ask for authorization, all will be read data
        String[] scopes = new String[] {
                Scopes.HEALTHKIT_HEARTRATE_READ,
                Scopes.HEALTHKIT_SLEEP_READ,
                Scopes.HEALTHKIT_STRESS_READ,
                Scopes.HEALTHKIT_STEP_READ,
                Scopes.HEALTHKIT_DISTANCE_READ,
                Scopes.HEALTHKIT_LOCATION_READ,
                Scopes.HEALTHKIT_SPEED_READ,
                Scopes.HEALTHKIT_CALORIES_READ,
                Scopes.HEALTHKIT_STRENGTH_READ,
                Scopes.HEALTHKIT_ACTIVITY_READ,
                Scopes.HEALTHKIT_ACTIVITY_RECORD_READ
        };

        // Obtain the intent of the authorization process. The value true indicates that the authorization process of the Health app is enabled, and false indicates that the authorization process is disabled.
        Intent intent = HuaweiSettingController.requestAuthorizationIntent(scopes, true);

        // Open the authorization process screen.
        Log.i(APP_TAG, "start authorization activity");
        startActivityForResult(intent, REQUEST_AUTH);
    }


    /**
     * Huawei authorization process
     * @param requestCode code request
     * @param resultCode code result
     * @param data  intent
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Process only the response result of the authorization process.
        if (requestCode == REQUEST_AUTH) {
            // Obtain the authorization response result from the intent.
            HealthKitAuthResult result = HuaweiSettingController.parseHealthKitAuthResultFromIntent(data);
            if (result == null) {
                Log.w(APP_TAG, "authorization fail");
                return;
            }

            if (result.isSuccess()) {
                Log.i(APP_TAG, "authorization success");

            } else {
                Log.w(APP_TAG, "authorization fail, errorCode:" + result.getErrorCode());
            }
        }else if(requestCode == REQUEST_AUTH_FIT){

            // The Task returned from this call is always completed, no need to attach
            // a listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    /**
     * Google authorization process
     * @param completedTask task to be completed
     */
    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.i(APP_TAG, account.getEmail());

            if (!GoogleSignIn.hasPermissions(gfcontroller.getAccount(), gfcontroller.getFo())) {
                GoogleSignIn.requestPermissions(
                        this,
                        1000,
                        gfcontroller.getAccount(),
                        gfcontroller.getFo());
            }

            // Signed in successfully, show authenticated UI.
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(APP_TAG, "signInResult:failed code=" + e.getStatusCode());
        }
    }

    /**
     * one day data request
     */
    private void startDataCollection(){

        dataBase.newDay();//new day
        dataDays=dfc.getMiss(); //get day to ask
        String dayToAsk;
        Collections.sort(dataDays);

        for (int i = 0; i < 1 && dataDays.size()>0; i++) {
            dayToAsk=dataDays.get(i);
            dfc.addDay(dataDays.get(i));

            HWcontroller.readData(dayToAsk);//huawei API request

            try {
                sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            gfcontroller.accessGoogleFit(dayToAsk);//google API request

            executor.execute(dataWriter);//write in memory
        }

    }

    /**
     * Prepare data and run AI
     * @param view main app view
     */
    public void runAI(View view){
        findViewById(R.id.wait).setVisibility(View.VISIBLE);
        int tdata=0;

        ////////prepare number of labels
        int l=0;
        EditText labels = findViewById(R.id.numberOfLabels);

        if(labels.getText().toString().equals("")){
            l=5;
        }else{
            l=Integer.parseInt(labels.getText().toString());
        }
        if(l!=3 && l!=5 && l!=7){l=5;} //label must be 3/5/7 (for now, subject to change)


        ////////prepare number of days

        int d=0;
        EditText daysTest = findViewById(R.id.DaysToTest);
        if(daysTest.getText().toString().equals("")){
            d=1;
        }else{
            d=Integer.parseInt(daysTest.getText().toString());
        }
        if(d<=0){d=1;} //days must be at least 1
        if(d>TOTAL_DAYS_SAVED){d=TOTAL_DAYS_SAVED;}

        ///////choose AI type of test(sleep or activity)

        Switch type = findViewById(R.id.typeSelection);
        boolean IAtype= type.isChecked();



        //////preprocess data, just to not let incorrect data enter

        dataBase.preProcess();

        /////prepare time
        //we get the last day, not counting with today, because the smart band have a little delay
        //give it 1 day off

        long starDay=0;


        Date newDay;
        ZonedDateTime endTime = null;
        DateTimeFormatter formatter = null;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            endTime = LocalDateTime.now().atZone(ZoneId.systemDefault());
            endTime=endTime.minusDays(1); //never reach actual day
            Log.e(APP_TAG, endTime.toString());
            try {
                if(!IAtype) {//if activity start at 00:00 AM, if sleep start at 06:00 AM
                    newDay = dateFormat.parse(endTime.format(formatter) + " 00:00:00");
                }else{
                    newDay = dateFormat.parse(endTime.format(formatter) + " 06:00:00");
                }
                starDay=newDay.getTime();
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        ////////////write arff data train IA

        if(!IAtype) {//if activity start at 00:00 AM, if sleep start at 06:00 AM
            tdata=dataBase.ARFF_activity(this,starDay,d);
        }else{
            tdata=dataBase.ARFF_sleep(this,starDay,d);
        }

        //////update the IA param file
        IA.updateParam(l,tdata-1);//update the params with the number of labels and the total data count

        ///////////lauch IA
        ArrayList<String> test=new ArrayList<>();
        try{
            test=IA.run();
        }catch (Exception err){

           err.printStackTrace();
        }
        writeIAData(test);
        Log.e(APP_TAG,"labels"+l+"days"+d);
        Log.e(APP_TAG,"test"+test.size());
        findViewById(R.id.wait).setVisibility(View.INVISIBLE);

    }

    /**
     * Write in memory the result of the AI
     * @param test list with file + text in the file
     */
    private void writeIAData(ArrayList<String> test) {
        FileOutputStream fos = null;

        try {
            for (int i = 0; i < test.size(); i+=2) {
                if(i<9) {
                    fos = this.openFileOutput(test.get(i), Context.MODE_PRIVATE);
                }else{
                    fos = this.openFileOutput(test.get(i), Context.MODE_APPEND);
                }

                fos.write(test.get(i+1).getBytes());
                fos.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Init the files in memory
     * @throws IOException when no file found
     */
    private void initFiles() throws IOException {
        FileOutputStream fos = null;
        String empty="\n"+"\n"+"\n"+"\n";

        fos = this.openFileOutput(DB_FILE, Context.MODE_PRIVATE);
        fos.write(empty.getBytes());
        fos.close();
        fos = this.openFileOutput(DAY_FILE, Context.MODE_PRIVATE);
        fos.write("".getBytes());
        fos.close();

    }

    /**
     * Start full data request in the API
     */
    private void startFullDataCollection() {
        dataRequest dataR= new dataRequest(dataBase,this,dfc,HWcontroller,gfcontroller,findViewById(R.id.wait));
        findViewById(R.id.wait).setVisibility(View.VISIBLE);
        Toast.makeText(this,"La toma de datos lleva algunos minutos,por favor espere ",Toast.LENGTH_LONG).show();

        executor.execute(dataR);
    }

    /**
     * read the file AI rules
     * @return with the rules
     */
    private String ruleRead() {
        String text="";
        FileInputStream fis = null;
        try {
            fis = this.openFileInput("taxis_rules.txt");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        InputStreamReader inputStreamReader = new InputStreamReader(fis, StandardCharsets.UTF_8);

        try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
            String line = reader.readLine();
            while (line != null) {

                text+=line+"\n";

                line = reader.readLine();
            }
        } catch (IOException e) {
            // Error occurred when opening raw file for reading.
            Log.e(APP_TAG,"reading taxis_rules.txt error");
            text="reading taxis_rules.txt error";
        }

        return text;
    }

    /**
     * read the file AI resume
     * @return with the resume
     */
    private String readResume() {
        String text="";
        FileInputStream fis = null;
        try {
            fis = this.openFileInput("taxis_quaSumm.txt");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        InputStreamReader inputStreamReader = new InputStreamReader(fis, StandardCharsets.UTF_8);

        try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
            String line = reader.readLine();
            while (line != null) {

                text+=line+"\n";

                line = reader.readLine();
            }
        } catch (IOException e) {
            // Error occurred when opening raw file for reading.
            Log.e(APP_TAG,"reading taxis_quaSumm.txt error");
            text="reading taxis_quaSumm.txt error";
        }

        return text;

    }

}