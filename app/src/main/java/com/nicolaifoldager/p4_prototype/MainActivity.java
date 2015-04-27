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


package com.nicolaifoldager.p4_prototype;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
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

import java.io.File;
import java.io.IOException;


public class MainActivity extends ActionBarActivity {

    /**     Wakelock                                                                              */
    PowerManager.WakeLock mWakeLock = null;                                                         /*Wakelock to keep the cpu running while screen is off during a session*/

    /**     PD patch dispatcher                                                                   */
    PdUiDispatcher dispatcher;                                                                      /*PD dispatcher for audio control*/

    /**     Construct Logging class                                                               */
    final Logging logging = new Logging();                                                          /*Constructed globally so it can be closed when the application is closed by onDestroy(); Read more: https://developer.android.com/reference/android/app/Activity.html*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        /**     Wakelock                                                                          */
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);             /*Construct a new PowerManager to call from the power service within Android OS*/
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                MainActivity.class.getSimpleName());                                                /*Construct a new partial wakelock to keep the CPU running when the screen is of*/

        /**     Location Manager                                                                  */
        final LocationManager[] locationManager = {(LocationManager) this.getSystemService
                (Context.LOCATION_SERVICE)};                                                         /*Construction a LocationManager to call for the location_service in android*/

        /**     PD Init                                                                           */
        init_pd();
        loadPdPatch();

        /**     Asynchronized task                                                                */
        final AsyncTask[] uploadFiles = new AsyncTask[1];                                                                  /*AsyncTask for file upload on another thread*/

        /**     Call the elements in the UI                                                       */
        final Switch switchLogging = (Switch) findViewById(R.id.switchStartLogging);                /*Logging Switch*/
        final Button createFileBtn = (Button) findViewById(R.id.createFile);                        /*Create new file button*/
        final Button setStepLengthBtn = (Button) findViewById(R.id.setStepLengthBtn);

        final RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioGroup);

        final RadioButton rBtnNoSound = (RadioButton) findViewById(R.id.rBtnNoSound);               /*Radiobutton for no sound audio mode*/
        final RadioButton rBtnSound = (RadioButton) findViewById(R.id.rBtnSound);                   /*Radiobutton for sound audio mode*/

        final EditText stepLengthTxt = (EditText) findViewById(R.id.stepLengthField);               /*Textfield to input step length into*/
        stepLengthTxt.setGravity(Gravity.CENTER);                                                   /*Center it cause sparkles*/

        /**     Variables                                                                         */
        final String userID[] = {null};
        final String audioMode[] = {null};
        final float BPM[] = {0.0f};
        final int[] stepLength = {0};

        final float[] totalSpeed = {0.0f};                                                          /*Total speed, used to calc average speed*/
        final float[] iterations = {1.0f};                                                          /*How many times the location manager have updated the speed (How many entries we have in the log file)*/
        final float[] avgSpeed = {0.0f};
        final float[] volume = {0.0f};
        final float[] caliAvgSpeed = {0.0f};

        final String[] fileName = {null};
        final String[] folderName = {Environment.getExternalStorageDirectory().toString()+
                "/Android/data/com.nicolaifoldager.p4_prototype/"};                                 /*Specify the path to the file directory we will save to*/


        /**     Initialization                                                                    */
        final Media media = new Media();

        if (!media.folderExists(folderName[0])) {
            media.createFolder(folderName[0]);
        }

        checkGPS();

        if(media.prefsExists(folderName[0])) {
            userID[0] = media.getUserID(folderName[0]);
            audioMode[0] = media.getAudioMode(folderName[0]);
            BPM[0] = media.getBPM(folderName[0]);
            caliAvgSpeed[0] = media.getAvgSpeed(folderName[0]);
            System.out.println("Prefs does exist: - ID: " + userID[0] + " Audio: " + audioMode[0] +
                    " BPM: " + BPM[0] + " Calibration speed: " + caliAvgSpeed[0]);
            rBtnSound.toggle();
            updatePin(2, true);
            rBtnNoSound.setEnabled(false);
        } else {
            userID[0] = media.createUserID();
            audioMode[0] = media.createAudioMode(folderName[0]);
            System.out.println("Prefs does not exist: - ID: " + userID[0] + " Audio: " + audioMode[0] +
                    " BPM: " + BPM[0] + " Calibration speed: " + caliAvgSpeed[0]);
            rBtnNoSound.toggle();
            updatePin(2, true);
            rBtnSound.setEnabled(false);
        }


        /**     Create new session button                                                         */
        createFileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(switchLogging.isChecked()) {
                    Toast.makeText(getApplicationContext(), "Please stop logging before " +
                                    "starting a new session",
                            Toast.LENGTH_LONG).show();
                } else if(stepLength[0] == 0) {
                    Toast.makeText(getApplicationContext(), "Please set your step length",
                            Toast.LENGTH_LONG).show();
                } else if(!rBtnNoSound.isChecked() && !rBtnSound.isChecked()) {
                    Toast.makeText(getApplicationContext(), "Please choose a sound feedback mode",
                            Toast.LENGTH_LONG).show();
                } else {
                    fileName[0] = String.valueOf(userID[0]) + "_" + audioMode[0] + "_" +
                            String.valueOf(System.currentTimeMillis() + ".txt");

                    media.createFile(folderName[0], fileName[0]);
                    logging.startWriter(folderName[0], fileName[0]);

                    caliAvgSpeed[0] = media.getAvgSpeed(folderName[0]);                 /** DEBUG */

                    updatePin(3, true);

                    //Reset variables
                    iterations[0] = 1.0f;
                    avgSpeed[0] = 0.0f;
                    totalSpeed[0] = 0.0f;

                }
            }
        });


        /**     Start session                                                                     */
        switchLogging.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!checkGPS()) {
                    Toast.makeText(getApplicationContext(), "Please turn on the GPS",
                            Toast.LENGTH_LONG).show();
                    switchLogging.toggle();
                } else if(stepLength[0] == 0) {
                    Toast.makeText(getApplicationContext(), "Please set your step length",
                            Toast.LENGTH_LONG).show();
                    switchLogging.toggle();
                } else if(fileName[0] == null) {
                    Toast.makeText(getApplicationContext(), "Please Start a new session",
                            Toast.LENGTH_LONG).show();
                    switchLogging.toggle();
                } else if(!rBtnNoSound.isChecked() && !rBtnSound.isChecked()) {
                    Toast.makeText(getApplicationContext(), "Please select an audio mode",
                            Toast.LENGTH_LONG).show();
                    switchLogging.toggle();
                } else if(switchLogging.isChecked()) {
                    updatePin(4, true);
                    Log.i("Main/Logging listener", "on / logging");

                    //Disables the controls
                    setStepLengthBtn.setEnabled(false);
                    createFileBtn.setEnabled(false);
                    stepLengthTxt.setFocusable(false);

                    mWakeLock.acquire();                                                            /*Acquire a wakelock to keep the CPU running and keep logging even if the screen is off*/

                    if(rBtnNoSound.isChecked()) {
                        audioMode[0] = "none";
                    }

                    if (audioMode[0].equals("cont")) {
                        floatToPd("osc_pitch", 200.0f);
                        floatToPd("osc_volume", 50.0f);
                    }

                } else if (iterations[0] < 300) {

                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);                 /*Construct a new dialog box*/
                    alertDialogBuilder.setMessage("You've only been running for " + iterations[0]
                            / 60 + "minutes, please keep going for at least 5 minutes in order for your session to be accepted.")   /*Sets the message in the dialog box*/
                            .setPositiveButton("Keep going",                                         /*Sets the name of the positive button*/
                                    new DialogInterface.OnClickListener() {                                 /*Creates the on click listener service*/
                                        public void onClick(DialogInterface dialog, int id) {
                                            switchLogging.toggle();
                                            dialog.cancel();
                                        }
                                    });
                    alertDialogBuilder.setNegativeButton("Abort session",                                          /*Sets the name of the negative button*/
                            new DialogInterface.OnClickListener() {                                         /*Creates the on click listener service*/
                                public void onClick(DialogInterface dialog, int id) {

                                    Log.i("Main/Logging listener", "off / not logging");

                                    stopAudio();

                                    avgSpeed[0] = totalSpeed[0] / iterations[0];

                                    logging.stopWriter("");

                                    //Reset variables
                                    iterations[0] = 1.0f;
                                    avgSpeed[0] = 0.0f;
                                    totalSpeed[0] = 0.0f;
                                    fileName[0] = null;

                                    //Update the UI
                                    for(int i = 1; i <= 4; i++) {
                                        updatePin(i, false);
                                    }

                                    //Enables the controls
                                    setStepLengthBtn.setEnabled(true);
                                    createFileBtn.setEnabled(true);
                                    stepLengthTxt.setFocusable(true);

                                    if(mWakeLock.isHeld()) {                                        /*Check if a wakelock is held (Psst, it is!)*/
                                        mWakeLock.release();                                        /*Release it*/
                                        mWakeLock.acquire(300000);                                  /*Acquire a new one for 5 minutes to ensure that the log is uploaded*/
                                    }

                                    dialog.cancel();                                                /*Cancels the dialog box*/
                                }
                            });
                    AlertDialog alert = alertDialogBuilder.create();                                /*Constructs the dialog*/
                    alert.show();

                } else {
                    Log.i("Main/Logging listener", "off / not logging");

                    stopAudio();

                    avgSpeed[0] = totalSpeed[0] / iterations[0];

                    //TODO CALCULATE BPM HERE
                    //BPM[0] = (avgSpeed[0] * 60) / (stepLength[0] / 100);

                    logging.stopWriter("");

                    if(rBtnNoSound.isChecked()) {
                        Media prefsMedia = new Media();
                        Logging prefsLogging = new Logging();

                        prefsMedia.createFile(folderName[0], "prefs.txt");
                        prefsLogging.startWriter(folderName[0], "prefs.txt");
                        prefsLogging.stopWriter(userID[0] + "\n" + audioMode[0] + "\n" + BPM[0] +
                        "\n" + avgSpeed[0]);
                    }

                    showUploadDialog();                                                             /*Shows a dialog that the application is trying to upload*/
                    uploadFiles[0] = new uploadFiles().execute(fileName[0]);                        /*Start the upload*/

                    //Reset variables
                    iterations[0] = 1.0f;
                    avgSpeed[0] = 0.0f;
                    totalSpeed[0] = 0.0f;
                    fileName[0] = null;


                    for(int i = 1; i <= 4; i++) {
                        updatePin(i, false);                                                        /*Set pins to red again*/
                    }

                    //Enables the controls
                    setStepLengthBtn.setEnabled(true);
                    createFileBtn.setEnabled(true);
                    stepLengthTxt.setFocusable(true);

                    if(mWakeLock.isHeld()) {                                                        /*Check if a wakelock is held (Psst, it is!)*/
                        mWakeLock.release();                                                        /*Release it*/
                        mWakeLock.acquire(300000);                                                  /*Acquire a new one for 5 minutes to ensure that the log is uploaded*/
                    }

                }
            }
        });


        /**     Location Manager                                                                  */
        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {


                if (audioMode[0].equals("disc") && switchLogging.isChecked()) {

                    if(location.getSpeed() > caliAvgSpeed[0] * 1.05f ){

                        volume[0] = 1.00f;

                        floatToPd("osc_volume", volume[0]);
                        Log.i("Main/LocationManager", volume[0] + " sent to pd patch1");


                    } else if(location.getSpeed() < caliAvgSpeed[0] * 0.95f) {

                        volume[0] = 1.00f;

                        floatToPd("osc_volume", volume[0]);
                        Log.i("Main/LocationManager", volume[0] + " sent to pd patch2");

                    } else if (PdAudio.isRunning() && location.getSpeed() <= caliAvgSpeed[0] * 1.05f && location.getSpeed() >= caliAvgSpeed[0] * 0.95f){

                        volume[0] = 0.00f;

                        floatToPd("osc_volume", volume[0]);
                        Log.i("Main/LocationManager", volume[0] + " sent to pd patch3");

                    }

                }

                if(switchLogging.isChecked()) {

                    try {
                        float accuracy = location.getAccuracy();                                    /*Get the current accuracy from the location manager in meters*/

                        float speedMPS = location.getSpeed();                                       /*Get the current speed from the location manager in m/s*/

                        double getLon = location.getLongitude();                                    /*Gets the longitude from the location manager*/
                        double getLat = location.getLatitude();                                     /*Gets the latitude from the location manager*/

                        String msg = iterations[0] + "\t" + speedMPS + "\t" + accuracy + "\t\t"
                                + iterations[0] + "," + getLat + "," + getLon + "\t\t" +
                                volume[0] + "\n";
                        logging.write(msg);

                        totalSpeed[0] += speedMPS;
                        iterations[0] += 1;

                    } catch (Exception e) {
                        e.printStackTrace(System.out);
                    }

                }

            }

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
        locationManager[0].requestLocationUpdates(LocationManager.GPS_PROVIDER, minUpdateTime,
                minUpdateLocation, locationListener);                                               /*Request new location update every minUpdateTime millisecond & minUpdateLocation meters.*/


        /**     Listener for set step length button                                               */
        setStepLengthBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    stepLength[0] = Integer.valueOf(stepLengthTxt.getText().toString());            /*Calls the value stored in the text field and saves it to a String*/
                    updatePin(1, true);                                                             /*Update the pin on the activity*/

                    try {
                        InputMethodManager inputManager = (InputMethodManager)
                                getSystemService(Context.INPUT_METHOD_SERVICE);                     /*Construct an InputMethodManager to control the keyboard*/

                        inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                                InputMethodManager.HIDE_NOT_ALWAYS);                                /*Set the input method (the keyboard) to close when the step length is set correctly*/
                    } catch (Exception e) {
                        e.printStackTrace(System.out);                                              /*Ignore the exception cause it doesn't change anything if it fails*/
                    }

                } catch (NumberFormatException e) {                                                 /*Checks if anything but a number is input, if it is then toast*/
                    Toast.makeText(getApplicationContext(), "Please only use numbers and specify the length in meters, e.g. 65",
                            Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
        });


        /**     Listener for changes in the radio buttons                                         */
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            boolean hasChanged = false;

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (!hasChanged) {
                    hasChanged = true;
                    updatePin(2, true);                                                             /*Makes pin 4 green*/
                }
            }
        });

    } // onCreate

    @Override
    protected void onDestroy() {                                                                    /*Is called when Android OS kills the application in favour of another*/
        super.onDestroy();

        if(mWakeLock.isHeld()) {
            mWakeLock.release();                                                                    /*Release any wakelocks to avoid battery drain*/
        }

    }

    /**
     * Check is GPS is enabled, if not alert the user and redirect them to the location settings
     * within Android OS
     */
    private boolean checkGPS() {
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
            return false;
        } else {
            return true;
        }
    }

    /**
     * Updates the pin in the left when the user has succesfully completed a task
     *
     * @param pinName   The number on the pin that sould be changed corresponds to the pinName
     * @param color     true = green, false = red
     */
    private void updatePin(final int pinName, final boolean color) {
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
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {

                if (color) {
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

                if (!color) {
                    if (pinName == 1)
                        pin[0].setImageResource(R.mipmap.onered);
                    else if (pinName == 2)
                        pin[0].setImageResource(R.mipmap.twored);
                    else if (pinName == 3)
                        pin[0].setImageResource(R.mipmap.threered);
                    else if (pinName == 4)
                        pin[0].setImageResource(R.mipmap.fourred);

                    pin[0].startAnimation(textIn);
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
    }

    /**
     * Shows the dialog that tells the user, that the upload is doing stuff
     */
    private void showUploadDialog() {
        AlertDialog.Builder alertDialogUpload = new AlertDialog.Builder(this);                      /*Construct a new dialog box*/
        alertDialogUpload.setMessage("Uploading file")                                              /*Sets the message in the dialog box*/
                .setNegativeButton("Okay",                                                          /*Sets the name of the negative button*/
                        new DialogInterface.OnClickListener() {                                     /*Creates the on click listener service*/
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();                                                    /*Cancels the dialog box*/
                            }
                        });
        AlertDialog alert = alertDialogUpload.create();                                             /*Constructs the dialog*/
        alert.show();                                                                               /*Shows the dialog box*/
    }

    /**
     * Initializes PD Library and prepares the audio outlet.
     */
    private void init_pd() {

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
    private void loadPdPatch() {

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
    private void startAudio() { PdAudio.startAudio(this); }

    /**
     * Stop the audio
     */
    private void stopAudio() { PdAudio.stopAudio(); }

} // Main