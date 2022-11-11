package com.tfg.ierning.AI;

/*
* Copyright 2022 David Padilla Montero
*/

import static com.tfg.ierning.data.Constants.APP_TAG;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import es.ujaen.simidat.agvico.ExperimentTest;

/**
 * Class that implement the controller on the AI and execute it
 *
 * @author David Padilla Montero
 *
 */
public class IAController {

    private ExperimentTest ex; //AI class

    private Context app; //main app android context

    private int nLabels; //number of labels to use
    private int period; //number of data we use in the experiment

    /**
     * Default constructor
     * @param app main android context
     */
    public IAController(Context app) {
        this.app = app;
        nLabels=5;
        ex=new ExperimentTest();
    }

    /**
     * Run the algorithm
     * @return ArrayList<String> with the files names and data of each file
     */
    public ArrayList<String> run(){

        File file = new File(app.getFilesDir(), "param.txt");
        Log.e(APP_TAG,file.getAbsolutePath());

        return ex.run(100000000,true,file.getAbsolutePath(),-1);

    }

    /**
     * Update the param file with the new data
     * @param n number on labels to put in the params file
     * @param totalData total number of data we are using in the algorithm
     */
    public void updateParam(int n, int totalData){
        nLabels=n;
        period=totalData;

        String text="# THIS IS A PARAMETER FILE FOR THE STREAM-MOEA ALGORITHM\n" +
                "#This a comment. Comments starts with a '#' sign.\n" +
                "algorithm = stream-moea\n" +
                "\n" +
                "# The input data, it must be a stream stored in an arff file. Others kinds of streams should be added in next versions.\n" +
                "inputData = /data/data/com.tfg.ierning/files/IerningData.arff\n" +
                "\n" +
                "# The paths of the results files, separated by whitespaces, in this order: training QMs, test QMs for each rule, test QMs summary and rules.\n" +
                "outputData = taxis_tra_qua.txt    taxis_tst_qua.txt     taxis_quaSumm.txt     taxis_rules.txt\n" +
                "\n" +
                "# The number of collected instances before starting the genetic algorithm.\n" +
                "period = "+period+"\n" +
                "\n" +
                "# Parameters of the genetic algorithm\n" +
                "seed = 1\n" +
                "RulesRepresentation = dnf\n" +
                "nLabels = "+nLabels+"\n" +
                "nGen = 70\n" +
                "popLength = 50\n" +
                "crossProb = 0.8\n" +
                "mutProb = 0.1\n" +
                "\n" +
                "\n" +
                "# Use this to set the evaluator: \"byObjectives\" uses presence and objective values in previous timestamps. Other value use only presence in previous steps.\n" +
                "# Use: \"byDiversity\" to apply the evaluator based in the application of the decay factor on the diversity measure\n" +
                "Evaluator = byDiversity\n" +
                "\n" +
                "# The size of the sliding window to be used by the evaluator.\n" +
                "SlidingWindowSize = 5\n" +
                "\n" +
                "# The objectives to be used in the genetic algorithm.\n" +
                "# They must match with the name of the class that represent the quality measure\n" +
                "Obj1 = WRAccNorm\n" +
                "Obj2 = Confidence\n" +
                "Obj3 = TPR\n" +
                "\n" +
                "# The diversity measure to be used. Useful for process like Token Competition.\n" +
                "diversity = WRAccNorm\n" +
                "\n" +
                "# The different filter applied at the end of the evolutionary process. It will be applied in\n" +
                "# order, i.e., the first one is applied over the result, then the second is applied over the result extra-dnf-7-2500cted from the first one and so on.\n" +
                "filter = TokenCompetition";
        try (FileOutputStream fos = app.openFileOutput("param.txt", Context.MODE_PRIVATE)) {
            fos.write(text.getBytes());
        } catch (IOException r) {
            r.printStackTrace();
        }
    }

}
