/** An application that logs the movement speed of the phone, writes it to a *.txt file
 *  named current unix time within the /Android/com.nicolaifoldager.p4_prototype/ directory in the
 *  internal storage. On end of logging it estimates the average speed, no. of entries in the log
 *  and how long it has been logging.
 *
 *  Based on the speed a sound output is manipulated in order to help keep the movement speed of the
 *  phone to a steady pace.
 *
 *  Entries on logging:     Iterations  Speed   Accuracy    Altitude    latitude,longitude
 *  Entries on end:         Number of entries + average speed in kph + time spend logging in seconds
 *
 *  Copyright (C) 2015 Nicolai Foldager
 *
 *  @author  Nicolai Foldager
 *  @version 1.0
 */


package com.nicolaifoldager.p4_prototype;                                                           /*Package signature*/

//-------------------------------- LIBRARIES BELOW -----------------------------------------------//

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

import org.puredata.android.io.AudioParameters;
import org.puredata.android.io.PdAudio;
import org.puredata.android.utils.PdUiDispatcher;
import org.puredata.core.PdBase;
import org.puredata.core.utils.IoUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

//-------------------------------- LIBRARIES ABOVE -----------------------------------------------//


//-------------------------------- ACTIVITY INIT BELOW -------------------------------------------//

public class MainActivity extends ActionBarActivity {                                               /*Extends this ActionBarActivity into our MainActivity. This shows the black bar in the top*/

    private AsyncTask uploadFiles;                                                                  /*AsyncTask for file upload on another thread*/
    private PdUiDispatcher dispatcher;                                                              /*PD dispatcher for audio control*/
    PowerManager.WakeLock mWakeLock = null;                                                         /*Wakelock to keep the cpu running while screen is off during a session*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {                                            /*Runs when the activity is created*/


    //------------------------------------------////
    //-------------- DON'T TOUCH ---------------////
        super.onCreate(savedInstanceState);     ////
        setContentView(R.layout.activity_main); ////
    //-------------- DON'T TOUCH ---------------////
    //------------------------------------------////


//-------------------------------- ANDROID INIT ABOVE --------------------------------------------//


//------------------------------------ WAKELOCK BELOW --------------------------------------------//

        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);             /*Construct a new PowerManager to call from the power service within Android OS*/
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                MainActivity.class.getSimpleName());                                                /*Construct a new partial wakelock to keep the CPU running when the screen is of*/

//------------------------------------ WAKELOCK ABOVE --------------------------------------------//


//-------------------------------- VARIABLES & CONSTRUCTORS BELOW --------------------------------//

        //Call the elements in the UI for onClickListeners
        final Switch switchLogging = (Switch) findViewById(R.id.switchStartLogging);                /*Logging Switch*/
        final Button createFileBtn = (Button) findViewById(R.id.createFile);                        /*Create new file button*/
        final Button setStepLengthBtn = (Button) findViewById(R.id.setStepLengthBtn);

        final RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioGroup);

        final RadioButton rBtnNoSound = (RadioButton) findViewById(R.id.rBtnNoSound);               /*Radiobutton for no sound audio mode*/
        final RadioButton rBtnSound = (RadioButton) findViewById(R.id.rBtnSound);                   /*Radiobutton for sound audio mode*/

        final EditText stepLengthTxt = (EditText) findViewById(R.id.stepLengthField);               /*Textfield to input steplength into*/
        stepLengthTxt.setGravity(Gravity.CENTER);                                                   /*Center it cause sparkles*/

        /**
         * The variables are set as zero-index arrays so we can manipulate them despite them being
         * declared as final.
         *
         * Read more here:
         * https://stackoverflow.com/questions/10166521/the-final-local-variable-cannot-be-assigned
         */

        final double[] totalSpeed = {0.0};                                                          /*Total speed, used to calc average speed*/
        final double[] iterations = {1.0};                                                          /*How many times the location manager have updated the speed (How many entries we have in the log file)*/
        final double[] avgSpeed = {0.0};

        final String[] audioMode = {null};                                                          /*Stores the audioMode*/
        final String[] userId = {"0"};                                                              /*Stores the userID*/
        final String[] fileName = {null};                                                           /*Saves the filename*/
        final double[] stepLength = {0.0};
        final double[] prefsBPMdouble = {0};

        final String[] folderName = {Environment.getExternalStorageDirectory().toString()+
                "/Android/data/com.nicolaifoldager.p4_prototype/"};                                  /*Specify the path to the file directory we will save to*/


        final LocationManager locationManager = (LocationManager) this.getSystemService
                (Context.LOCATION_SERVICE);                                                         /*Construction a LocationManager to call for the location_service in android*/

        //Construct classes - Why global? Cause garbage collection <3
        final Media media = new Media();
        final Logging logWriter = new Logging();

//-------------------------------- VARIABLES & CONSTRUCTORS ABOVE --------------------------------//


//---------------------------------------- INIT PD BELOW -----------------------------------------//

        //Initializes the PD library and opens an sound output
            init_pd();
            loadPdPatch();

//---------------------------------------- INIT PD ABOVE -----------------------------------------//


//--------------------------------------- INIT BELOW ---------------------------------------------//

        if(media.rwAccess()) {
            media.createFolder(folderName[0]);                                                      /*Checks if a folder is created, if not it will create one.*/
            Media.createPrefs(folderName[0]);                                                       /*Checks if a preferences file has been created, if not it makes one and assigns a user ID and mode*/

            userId[0] = Media.getId();                                                              /*Gets the ID from the Media class that reads prefs.txt*/
            audioMode[0] = Media.getMode();                                                         /*Gets the audio mode from the Media class that reads prefs.txt*/

            fileName[0] = String.valueOf(userId[0]) + "_" + audioMode[0] + "_" +
                    String.valueOf(System.currentTimeMillis()) + ".txt";                            /*Stores the filename for the current session in a string.*/

        } else {
            Toast.makeText(getApplicationContext(), "Read/write access not granted. Folder has " +
                    "not been created!", Toast.LENGTH_LONG).show();
        }

        checkGPS();                                                                                 /*Runs the method to check if GPS is enabled*/

//--------------------------------------- INIT ABOVE ---------------------------------------------//


//-------------------------------- LOCATION LISTENER BELOW ---------------------------------------//

        //Construct a listener that checks for changes in the location service in Android
        LocationListener locationListener = new LocationListener() {

            public void onLocationChanged(Location location) {                                      /*Run on location changed or update request*/

                float accuracy = location.getAccuracy();                                            /*Get the current accuracy from the location manager in meters*/

                float speedMPS = location.getSpeed();                                               /*Get the current speed from the location manager in m/s*/

                double getLon = location.getLongitude();                                            /*Gets the longitude from the location manager*/
                double getLat = location.getLatitude();                                             /*Gets the latitude from the location manager*/

                if(switchLogging.isChecked()) {
                    try {
                        String msg = iterations[0] + "\t" + speedMPS + "\t" + accuracy + "\t\t"
                                + iterations[0] + "," + getLat + "," + getLon + "\n";
                        logWriter.write(msg);                                                       /*Write the string endMsg to the FileWriter*/
                        totalSpeed[0] += speedMPS;                                                  /*Add the current speed to total speed*/
                        iterations[0] += 1;                                                         /*Add 1 to the iteration counter*/
                    } catch (Exception e) {
                        String toastMsg = e.getMessage();
                        Toast.makeText(getApplicationContext(), toastMsg,
                                Toast.LENGTH_LONG).show();
                        e.printStackTrace(System.out);
                    }

                    if(audioMode[0].equals("cont")) {
                        //Cont sound
                        Log.i("--------------------","1 cont");
                    } else if (audioMode[0].equals("disc")) {
                        //Discrete sound
                        Log.i("--------------------","2 disc");
                    } else {
                        //No sound
                        Log.i("--------------------","3 no sound");
                    }

                    }//If switchLogging is checked
            }//onLocationChanged

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {               /*If the provider status has changed*/
                Log.i("Main/Location listener", "Provider changed to " + provider);
            }

            @Override
            public void onProviderEnabled(String provider) {                                        /*If the provider is enabled*/
                Log.i("Main/Location listener", "Provider changed to " + provider);
            }

            @Override
            public void onProviderDisabled(String provider) {                                       /*If the provider is disabled*/
                Log.i("Main/Location listener", "Provider changed to " + provider);
            }
        };

        final int minUpdateTime = 1000;                                                             /*Minimum time between update requests in milliseconds. 1000 = 1 second*/
        final int minUpdateLocation = 0;                                                            /*Minimum distance between updates in meters. 0 = no min change.*/
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minUpdateTime,
                minUpdateLocation, locationListener);                                               /*Request new location update every minUpdateTime millisecond & minUpdateLocation meters.*/

//-------------------------------- LOCATION LISTENER ABOVE ---------------------------------------//


//-------------------------------- LOGGING  SWITCH BELOW -----------------------------------------//

        //Logging switch
        switchLogging.setOnClickListener(new View.OnClickListener() {                               /*Create a listener service that checks if the switch that controls the logging is pressed*/
            public void onClick(View v) {

                if(fileName[0] == null) {                                                           /*Checks if a file has been created, if not then it'll toast an error*/
                    Toast.makeText(getApplicationContext(), "Please start a new session",
                            Toast.LENGTH_LONG).show();

                    switchLogging.toggle();                                                         /*Toggles the switch back to off state*/
                } else if (!rBtnNoSound.isChecked() && !rBtnSound.isChecked()) {                    /*Checks if a file has been created, if not then it'll toast an error*/
                    Toast.makeText(getApplicationContext(), "Please select an audio mode",
                            Toast.LENGTH_LONG).show();

                    switchLogging.toggle();
                } else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    checkGPS();
                    switchLogging.toggle();
                } else if (switchLogging.isChecked()) {                                             /*If the switch is already checked then run the scope*/

                    if (rBtnSound.isChecked()) {
                        try {
                            BufferedReader prefsReader = new BufferedReader(new FileReader
                                    (folderName[0] + "prefs.txt"));                                 /*Start a filereader from prefs.txt to see get the BPM*/
                            prefsReader.readLine();                                                 /*Skip the first line*/
                            prefsReader.readLine();                                                 /*Skipe the second line*/
                            String prefsBPM = prefsReader.readLine();                               /*Set the third line to string prefsBPM*/

                            //TODO FIX THIS LINE, SHIT DOESN'T WORK, FUCK YOU MIKKEL I H8 U SK8 BOI
                            //prefsBPMdouble[0] = Double.parseDouble(prefsBPM);                       /*parse it to a double*/
                        } catch (Exception e) {
                            Toast.makeText(getApplicationContext(), "Couldn't read preferences," +
                                            " please try again", Toast.LENGTH_LONG).show();
                            switchLogging.toggle();                                                 /*Toggle the switch back due to the error*/
                            e.printStackTrace(System.out);
                        }
                    }

                    startAudio();                                                                   /*Starts the audio feedback*/
                    Log.i("Main/Logging listener", "on / logging");

                    //Disables the controls
                    setStepLengthBtn.setEnabled(false);
                    rBtnNoSound.setEnabled(false);
                    rBtnSound.setEnabled(false);
                    createFileBtn.setEnabled(false);
                    stepLengthTxt.setFocusable(false);

                    mWakeLock.acquire();                                                            /*Acquire a wakelock to keep the CPU running and keep logging even if the screen is off*/
                    updatePin(4);                                                                   /*Makes pin 4 green*/
                } else {

                    if(rBtnNoSound.isChecked()) {                                                   /*Check if no sound feedback has been used*/
                        Logging prefsLog = new Logging();                                           /*Construct a new filewriter to the prefs.txt file*/
                        prefsLog.startWriter(folderName[0], "prefs.txt");                           /*Starts the filewriter*/

                        avgSpeed[0] = totalSpeed[0] / iterations[0];                                /*Calculate average speed in meters per second*/

                        double BPM = (avgSpeed[0] * 60) / (stepLength[0] / 100);                    /*speed in meters pr second divided by step length in cm times 100 to convert to meters equals BPM*/

                        try {
                            prefsLog.write("\n" + BPM);                                             /*Write BPM to the prefs.txt*/
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    stopAudio();                                                                    /*Stops the audio feedback*/
                    Log.i("Main/Logging listener", "off / not logging");

                    //Enables the controls
                    setStepLengthBtn.setEnabled(true);
                    rBtnNoSound.setEnabled(true);
                    rBtnSound.setEnabled(true);
                    createFileBtn.setEnabled(true);
                    stepLengthTxt.setFocusable(true);

                    try {
                        logWriter.stopWriter("");                                                   /*Stops the filewriter*/
                    } catch (IOException e) {
                        e.printStackTrace(System.out);
                    }

                    showUploadDialog();                                                             /*Shows a dialog that the application is trying to upload*/
                    uploadFiles = new uploadFiles().execute(fileName[0]);                           /*Start the upload*/

                    /*
                     * Reset average speed % iteration counter to 0 if they're not.
                     * It also sets the current file name to null, this is to make
                     * sure we don't write to the same file twice.
                     */
                    if(avgSpeed[0] != 0.0 || iterations[0] != 0.0 || fileName[0] != null) {
                        avgSpeed[0] = 0.0;
                        iterations[0] = 1.0;
                        fileName[0] = null;
                        Log.i("Main/Logging listener", "Iterations: " + iterations[0] +
                                " Average Speed: " + avgSpeed + " Current filename: "
                                + fileName[0]);
                    }

                    if (mWakeLock.isHeld()) {                                                       /*Check if a wakelock is held*/
                        mWakeLock.release();                                                        /*Release it so it won't drain battery*/
                        mWakeLock.acquire(120000);                                                  /*Start a new wakelock with a timeout of 2 minutes to ensure that the logfile will be uploaded*/
                    }
                }
            }
        });

//-------------------------------- END LOGGING ABOVE ---------------------------------------------//


//-------------------------------- FILE CREATION BELOW -------------------------------------------//

            createFileBtn.setOnClickListener(new View.OnClickListener() {                           /*Create a listener service that checks if the button that creates a new file has been pressed*/
                @Override
                public void onClick(View v) {

                    if(switchLogging.isChecked()) {
                        String toastMsg = "Please stop logging before starting a new session";
                        Toast.makeText(getApplicationContext(), toastMsg,
                                Toast.LENGTH_LONG).show();
                    } else if(stepLength[0] == 0.0) {
                        String toastMsg = "Please set your step length";
                        Toast.makeText(getApplicationContext(), toastMsg,
                                Toast.LENGTH_LONG).show();

                    } else if(!rBtnNoSound.isChecked() && !rBtnSound.isChecked()) {
                        String toastMsg = "Please set the feedback mode";
                        Toast.makeText(getApplicationContext(), toastMsg,
                                Toast.LENGTH_LONG).show();
                    } else {
                        userId[0] = Media.getId();                                                  /*Calls the user ID from the media class*/
                        audioMode[0] = Media.getMode();                                             /*Calls the audio mode from the media class*/

                        fileName[0] = String.valueOf(userId[0]) + "_" + audioMode[0] + "_" +
                                String.valueOf(System.currentTimeMillis() + ".txt");

                        Media.createFile(folderName[0], fileName[0]);                                  /*Creates a new file with the name of fileName[0] and location of folderName*/
                        Logging.startWriter(folderName[0], fileName[0]);                               /*Starts a writer to the file in folderName/fileName[0]*/

                        updatePin(3);                                                               /*Makes pin 4 green*/
                    }

                }

            });


        /*
         * Anonymous listener to see if the set step length button has been pressed. if it have then
         * check to see if the input is an integer, if it isn't then toast, else then update the
         * pin.
         */
        setStepLengthBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                    try {
                        stepLength[0] = Integer.valueOf(stepLengthTxt.getText().toString());    /*Calls the value stored in the text field and saves it to a String*/
                        updatePin(1);                                                           /*Update the pin on the activity*/
                    } catch (NumberFormatException e) {
                        Toast.makeText(getApplicationContext(), "Please only use " +
                                        "numbers and specify the length in meters, e.g. 65",
                                Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    }

            }

        });

        /*
         * Anonymous listener to see if either of he radio buttons has been checked, if hey have
         *  then update the pin.
         */
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
        {
            boolean hasChanged = false;
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId)
            {
                if(!hasChanged) {
                    hasChanged = true;
                    updatePin(2);                                                                   /*Makes pin 4 green*/
                }
            }
        });

//-------------------------------- FILE CREATION ABOVE -------------------------------------------//

    }//On create

     /**
     * Shows the dialog that tells the user, that the upload is doing stuff
     */
    public void showUploadDialog() {
        AlertDialog.Builder alertDialogUpload = new AlertDialog.Builder(this);                      /*Construct a new dialog box*/
        alertDialogUpload.setMessage("Uploading file")                                              /*Sets the message in the dialog box*/
                .setNegativeButton("Okay",                                                        /*Sets the name of the negative button*/
                        new DialogInterface.OnClickListener() {                                     /*Creates the on click listener service*/
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();                                                    /*Cancels the dialog box*/
                            }
                        });
        AlertDialog alert = alertDialogUpload.create();                                             /*Constructs the dialog*/
        alert.show();                                                                               /*Shows the dialog box*/
    }

    /**
     * Check is GPS is enabled, if not alert the user and redirect them to the location settings
     * within Android OS
     */
    void checkGPS() {

        LocationManager manager = (LocationManager) this.getSystemService
                (Context.LOCATION_SERVICE);                                                         /*Construct a new LocationManager to check the GPS provider*/
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {                             /*Check if the location provider is the GPS*/
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);                 /*Construct a new dialog box*/
            alertDialogBuilder.setMessage("GPS is disabled. Please enable it before continuing.")   /*Sets the message in the dialog box*/
                    .setPositiveButton("Location settings",                                         /*Sets the name of the positive button*/
                            new DialogInterface.OnClickListener() {                                 /*Creates the on click listener service*/
                                public void onClick(DialogInterface dialog, int id) {
                                    Intent callGPSSettingIntent = new Intent(
                                       android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);  /*Links to the location service settings within Android OS*/
                                    startActivity(callGPSSettingIntent);                            /*Starts the activity and opens location settings*/
                                }
                            });
            alertDialogBuilder.setNegativeButton("Cancel",                                          /*Sets the name of the negative button*/
                    new DialogInterface.OnClickListener() {                                         /*Creates the on click listener service*/
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();                                                        /*Cancels the dialog box*/
                        }
                    });
            AlertDialog alert = alertDialogBuilder.create();                                        /*Constructs the dialog*/
            alert.show();                                                                           /*Shows the dialog box*/
        }
    }

    /**
     * Updates the pin in the left when the user has succesfully completed a task
     *
     * @param pinName   The number on the pin that sould be changed corresponds to the pinName
     */
    void updatePin(final int pinName) {
        final ImageView[] pin = {null};

        if (pinName == 1)
            pin[0] = (ImageView) findViewById(R.id.imageViewPin1);
        else if (pinName == 2)
            pin[0] = (ImageView) findViewById(R.id.imageViewPin2);
        else if (pinName == 3)
            pin[0] = (ImageView) findViewById(R.id.imageViewPin3);
        else if (pinName == 4)
            pin[0] = (ImageView) findViewById(R.id.imageViewPin4);

        final Animation textOut = new AlphaAnimation(1.0f, 0.00f);
        textOut.setDuration(350);
        final Animation textIn = new AlphaAnimation(0.00f, 1.0f);
        textIn.setDuration(350);

        pin[0].startAnimation(textOut);

        textOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                if (pinName == 1)
                    pin[0].setImageResource(R.mipmap.onegreen);
                else if (pinName == 2)
                    pin[0].setImageResource(R.mipmap.twogreen);
                else if (pinName == 3)
                    pin[0].setImageResource(R.mipmap.threegreen);
                else if (pinName == 4)
                    pin[0].setImageResource(R.mipmap.fourgreen);

                pin[0].startAnimation(textIn);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
    }

     /**
     * Initializes PD Library and prepares the audio outlet.
     */
    public void init_pd() {

        try {
            // Configure the audio glue
            int sampleRate = AudioParameters.suggestSampleRate();
            PdAudio.initAudio(sampleRate, 0, 2, 8, true);

            // Create and install the dispatcher
            dispatcher = new PdUiDispatcher();
            PdBase.setReceiver(dispatcher);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Loads the PD patch so the app can communicate with it.
     */
    void loadPdPatch() {

        try {
            File dir = getFilesDir();

            IoUtils.extractZipResource(getResources().openRawResource(R.raw.pdpatch), dir, true);
            File patchFile = new File(dir, "pdpatch.pd");
            PdBase.openPatch(patchFile.getAbsolutePath());

            Log.i("MainActiviy/loadPdPatch", "Patch loaded");

            floatToPd("osc_volume", 0.0f);

            floatToPd("osc_pitch", 614.0f);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Sends a float to the PD patch previously loaded in loadPdPatch method
     *
     * @param receiver  The name of the receiver within the pd patch in a string
     * @param value     The value to send to the receiver as a float
     */
    public void floatToPd(String receiver, Float value) {

        PdBase.sendFloat(receiver, value);
        Log.i("MainActivity/floatToPd", "Send " + value + " to " + receiver);

    }

    /**
     * Starts the audio
     */
    public void startAudio() {

        PdAudio.startAudio(this);
    }

    /**
     * Stop the audio
     */
    public void stopAudio() {

        PdAudio.stopAudio();
    }


} //MainActivity