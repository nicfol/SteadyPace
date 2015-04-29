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
        super.onCreate(savedInstanceState);                                                         /*Tells the JVM to run both the overwritten code in onCreate and the code we've written here*/
        setContentView(R.layout.activity_main);                                                     /*Tells the JVM which xml layout file it should use*/


        /**     Wakelock                                                                          */
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);             /*Construct a new PowerManager to call from the power service within Android OS*/
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                MainActivity.class.getSimpleName());                                                /*Construct a new partial wakelock to keep the CPU running when the screen is of*/

        /**     Location Manager                                                                  */
        final LocationManager[] locationManager = {(LocationManager) this.getSystemService
                (Context.LOCATION_SERVICE)};                                                        /*Construction a LocationManager to call for the location_service in android*/

        /**     PD Init                                                                           */
        init_pd();
        loadPdPatch();

        /**     Asynchronized task                                                                */
        final AsyncTask[] uploadFiles = new AsyncTask[1];                                           /*AsyncTask for file upload on another thread*/

        /**     Call the elements in the UI                                                       */
        final Switch switchLogging = (Switch) findViewById(R.id.switchStartLogging);                /*Logging Switch*/
        final Button createFileBtn = (Button) findViewById(R.id.createFile);                        /*Create new file button*/
        final Button setStepLengthBtn = (Button) findViewById(R.id.setStepLengthBtn);               /*Button for setting the step length*/

        final RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioGroup);                   /*Group of radio buttons that include the two sound feedback modes*/

        final RadioButton rBtnNoSound = (RadioButton) findViewById(R.id.rBtnNoSound);               /*Radiobutton for no sound audio mode*/
        final RadioButton rBtnSound = (RadioButton) findViewById(R.id.rBtnSound);                   /*Radiobutton for sound audio mode*/

        final EditText stepLengthTxt = (EditText) findViewById(R.id.stepLengthField);               /*Textfield to input step length into*/
        stepLengthTxt.setGravity(Gravity.CENTER);                                                   /*Center it cause sparkles*/

        /**     Variables                                                                         */
        final String userID[] = {null};
        final String audioMode[] = {null};
        final float BPM[] = {0.0f};
        final float[] stepLength = {0};

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

        if (!media.folderExists(folderName[0])) {                                                   /*Checks if the public data folder for the application exists*/
            media.createFolder(folderName[0]);                                                      /*If it doesn't then create one*/
        }

        checkGPS();                                                                                 /*Checks if the location provider is set to the GPS, if it tell them to do so*/

        if(media.prefsExists(folderName[0])) {                                                      /*Checks if the prefs.txt file exists*/
            userID[0] = media.getUserID(folderName[0]);                                             /*Calls for the user ID from the prefs.txt*/
            audioMode[0] = media.getAudioMode(folderName[0]);                                       /*Calls for the audio mode fom the prefs.txt*/
            BPM[0] = media.getBPM(folderName[0]);                                                   /*Calls for the BPM from the prefs.txt*/
            caliAvgSpeed[0] = media.getAvgSpeed(folderName[0]);                                     /*Calls for the speed in the calibration run from the prefs.txt*/
            System.out.println("Prefs does exist: - ID: " + userID[0] + " Audio: " + audioMode[0] +
                    " BPM: " + BPM[0] + " Calibration speed: " + caliAvgSpeed[0]);
            rBtnSound.toggle();                                                                     /*Toggle for sound button as the user already have performed a calibration run*/
            updatePin(2, true);                                                                     /*Update the pin number 2 to green*/
            rBtnNoSound.setEnabled(false);                                                          /*Disables the no feedback sound*/
        } else {
            userID[0] = media.createUserID();                                                       /*Create a User ID*/
            audioMode[0] = media.createAudioMode(folderName[0]);                                    /*Calculate the audio feedback*/
            System.out.println("Prefs does not exist: - ID: " + userID[0] + " Audio: " + audioMode[0] +
                    " BPM: " + BPM[0] + " Calibration speed: " + caliAvgSpeed[0]);
            rBtnNoSound.toggle();                                                                   /*Toggles the no feedback sound*/
            updatePin(2, true);                                                                     /*Update the pin number 2 to green*/
            rBtnSound.setEnabled(false);                                                            /*Disable the feedback button as there have been no calibration run*/
        }


        /**     Create new session button                                                         */
        createFileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(switchLogging.isChecked()) {                                                     /*If the app is logging*/
                    Toast.makeText(getApplicationContext(), "Please stop logging before " +
                                    "starting a new session",
                            Toast.LENGTH_LONG).show();
                } else if(stepLength[0] == 0) {                                                     /*If step length isn't set*/
                    Toast.makeText(getApplicationContext(), "Please set your step length",
                            Toast.LENGTH_LONG).show();
                } else if(!rBtnNoSound.isChecked() && !rBtnSound.isChecked()) {                     /*If neither of the radio buttons are enabled (Shouldn't ever happen)*/
                    Toast.makeText(getApplicationContext(), "Please choose a sound feedback mode",
                            Toast.LENGTH_LONG).show();
                } else {
                    fileName[0] = String.valueOf(userID[0]) + "_" + audioMode[0] + "_" +
                            String.valueOf(System.currentTimeMillis() + ".txt");

                    media.createFile(folderName[0], fileName[0]);                                   /*Create a logfile for this session*/
                    logging.startWriter(folderName[0], fileName[0]);                                /*Starts a file writer to the logfile*/

                    caliAvgSpeed[0] = media.getAvgSpeed(folderName[0]);                 /** DEBUG */

                    updatePin(3, true);                                                             /*Update pin 3 to green*/

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
                if(!checkGPS()) {                                                                   /*If the GPS is off*/
                    Toast.makeText(getApplicationContext(), "Please turn on the GPS",
                            Toast.LENGTH_LONG).show();
                    switchLogging.toggle();
                } else if(stepLength[0] == 0) {                                                     /*If the step length isn't set*/
                    Toast.makeText(getApplicationContext(), "Please set your step length",
                            Toast.LENGTH_LONG).show();
                    switchLogging.toggle();
                } else if(fileName[0] == null) {                                                    /*If a new session hasn't been started*/
                    Toast.makeText(getApplicationContext(), "Please Start a new session",
                            Toast.LENGTH_LONG).show();
                    switchLogging.toggle();
                } else if(!rBtnNoSound.isChecked() && !rBtnSound.isChecked()) {                     /*If neither of the radio buttons are enabled (Shouldn't ever happen)*//*If */
                    Toast.makeText(getApplicationContext(), "Please select an audio mode",
                            Toast.LENGTH_LONG).show();
                    switchLogging.toggle();
                } else if(switchLogging.isChecked()) {                                              /*If the switch is checked to start logging*/
                    updatePin(4, true);                                                             /*Update pin 4 to green*/
                    Log.i("Main/Logging listener", "on / logging");

                    //Disables the controls
                    setStepLengthBtn.setEnabled(false);
                    createFileBtn.setEnabled(false);
                    stepLengthTxt.setFocusable(false);

                    mWakeLock.acquire();                                                            /*Acquire a wakelock to keep the CPU running and keep logging even if the screen is off*/

                    if(!rBtnNoSound.isChecked()) {
                        startAudio();                                                               /*Starts the audio*/
                    }

                    //If the audio mode is set to continuous
                    if (audioMode[0].equals("cont")) {
                        floatToPd("osc_pitch", 200.0f);                                  /** DEBUG*/
                        floatToPd("osc_volume", 50.0f);
                    }

                    //TODO CHANGE THIS TO 300 V
                } else if (iterations[0] < 1) {                                                   /*If the session haven't been running for 5 minutes then tell the user so*/

                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);    /*Construct a new dialog box*/
                    alertDialogBuilder.setMessage("You've only been running for " + iterations[0] / 60 +
                            " minutes, please keep going for at least 5 minutes in order for your session to be accepted.")  /*Sets the message in the dialog box*/
                            .setPositiveButton("Keep going",                                        /*Sets the name of the positive button*/
                                    new DialogInterface.OnClickListener() {                         /*Creates the on click listener service*/
                                        public void onClick(DialogInterface dialog, int id) {       /*What to do on click*/
                                            switchLogging.toggle();
                                            dialog.cancel();                                        /*Destroys the dialog*/
                                        }
                                    });
                    alertDialogBuilder.setNegativeButton("Abort session",                           /*Sets the name of the negative button*/
                            new DialogInterface.OnClickListener() {                                 /*Creates the on click listener service*/
                                public void onClick(DialogInterface dialog, int id) {               /*What to do on click*/

                                    Log.i("Main/Logging listener", "off / not logging");

                                    stopAudio();                                                    /*Stops the audio from the PD patch*/

                                    avgSpeed[0] = totalSpeed[0] / iterations[0];                    /*Calculates the average speed based on the total speed and iterations written to te log*/

                                    logging.stopWriter("");                                         /*Stops the file writer*/

                                    //Reset variables
                                    iterations[0] = 1.0f;
                                    avgSpeed[0] = 0.0f;
                                    totalSpeed[0] = 0.0f;
                                    fileName[0] = null;

                                    //Update the pins
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

                    stopAudio();                                                                    /*Stops the audio from the pd patch*/

                    avgSpeed[0] = totalSpeed[0] / iterations[0];                                    /*Calculate average speed*/

                    BPM[0] = (avgSpeed[0] * 60) / (stepLength[0] / 100);

                    logging.stopWriter("");                                                         /*Stops the file writer*/

                    if(rBtnNoSound.isChecked()) {                                                   /*Only runs if it is a calibration run*/
                        Media prefsMedia = new Media();
                        Logging prefsLogging = new Logging();

                        //Creates prefs.txt, start a file writer to it, writes the string and stops the file writer
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

                if (audioMode[0].equals("disc") && switchLogging.isChecked()) {                     /*Runs only when discrete feedback is chosen and the app is logging*/

                    float deviation = 0.5f;

                    if(location.getSpeed() > caliAvgSpeed[0] * 1.0f + deviation){                   /*If the speed is 5% over the calibration speed*/

                        volume[0] = 1.00f;

                        floatToPd("osc_volume", volume[0]);
                        Log.i("Main/LocationManager", volume[0] + " sent to pd patch1");

                    } else if(location.getSpeed() < caliAvgSpeed[0] * 1.0f - deviation) {           /*If the speed is 5% under the calibration speed*/

                        volume[0] = 1.00f;

                        floatToPd("osc_volume", volume[0]);
                        Log.i("Main/LocationManager", volume[0] + " sent to pd patch2");

                    } else if (PdAudio.isRunning() && location.getSpeed() <= caliAvgSpeed[0] * 1.0f + deviation
                            && location.getSpeed() >= caliAvgSpeed[0] * 1.0f - deviation){          /*If the speed is within the calibration speed +- 5%*/

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

        final int minUpdateTime = 500;                                                             /*Minimum time between update requests in milliseconds. 1000 = 1 second*/
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
            private boolean hasChanged = false;

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (!hasChanged) {                                                                  /*Checks if the pin is already green to avoid multiple animations*/
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

        final Animation imgOut = new AlphaAnimation(1.0f, 0.00f);
        imgOut.setDuration(350);
        final Animation imgIn = new AlphaAnimation(0.00f, 1.0f);
        imgIn.setDuration(350);

        pin[0].startAnimation(imgOut);

        imgOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) { }

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

                    pin[0].startAnimation(imgIn);
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

                    pin[0].startAnimation(imgIn);
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) { }
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