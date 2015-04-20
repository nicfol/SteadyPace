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
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

//-------------------------------- LIBRARIES ABOVE -----------------------------------------------//


//-------------------------------- ACTIVITY INIT BELOW -------------------------------------------//

public class MainActivity extends ActionBarActivity {                                               /*Extends this ActionBarActivity into our MainActivity. This shows the black bar in the top*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {                                            /*Runs when the activity is created*/


    //------------------------------------------////
    //-------------- DON'T TOUCH ---------------////
        super.onCreate(savedInstanceState);     ////
        setContentView(R.layout.activity_main); ////
    //-------------- DON'T TOUCH ---------------////
    //------------------------------------------////


//-------------------------------- ANDROID INIT ABOVE --------------------------------------------//


//-------------------------------- VARIABLES & CONSTRUCTORS BELOW --------------------------------//

        //Call the elements in the UI for onClickListeners
        final Switch switchLock = (Switch) findViewById(R.id.switchLock);                           /*Lock Switch*/
        final Switch switchLogging = (Switch) findViewById(R.id.switchStartLogging);                /*Logging Switch*/
        final Button createFileBtn = (Button) findViewById(R.id.createFile);                        /*Create new file button*/

        /**
         * The variables are set as zero-index arrays so we can manipulate them despite them being
         * declared as final.
         *
         * Read more here:
         * https://stackoverflow.com/questions/10166521/the-final-local-variable-cannot-be-assigned
         */

        final double[] totalSpeed = {0.0};                                                          /*Total speed, used to calc average speed*/
        final double[] iterations = {1.0};                                                          /*How many times the location manager have updated the speed (How many entries we have in the log file)*/

        final String[] audioMode = {null};
        final String[] userId = {"0"};
        final String[] fileName = {null};

        final String[] currentFileName = {null};                                                    /*Filename for the logfile.*/
        final String folderName = Environment.getExternalStorageDirectory().toString()+
                "/Android/data/com.nicolaifoldager.p4_prototype/";                                  /*Specify the path to the file directory we will save to*/

        //Construction a LocationManager to call for the location_service in android
        final LocationManager locationManager = (LocationManager) this.getSystemService
                (Context.LOCATION_SERVICE);

        //Construct classes - Why global? Cause garbage collection <3
        final Media media = new Media();
        final Logging logWriter = new Logging();
        final Sound sound = new Sound();

//-------------------------------- VARIABLES & CONSTRUCTORS ABOVE --------------------------------//


//---------------------------------------- INIT PD BELOW -----------------------------------------//

        //Initializes the PD library and opens an sound output

        try {
            sound.init_pd();
        } catch (IOException e) {
            e.printStackTrace(System.out);
        }

//---------------------------------------- INIT PD ABOVE -----------------------------------------//


//--------------------------------------- INIT BELOW ---------------------------------------------//

        if(media.rwAccess()) {
            try {
                media.createFolder(folderName);                                                     /*Checks if a folder is created, if not it will create one.*/
                Media.createPrefs(folderName);                                                      /*Checks if a preferences file has been created, if not it makes one and assigns a user ID and mode*/
            } catch (Exception e) {
                String toastMsg = e.getMessage();
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
                e.printStackTrace(System.out);
            }

            userId[0] = Media.getId();
            audioMode[0] = Media.getMode();

            fileName[0] = String.valueOf(userId[0]) + "_" + audioMode[0] + "_" +
                    String.valueOf(System.currentTimeMillis()) + ".txt";

        } else {
            String toastMsg = "Read/write access not granted. Folder has not been created!";
            Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
        }

        checkGPS();

//--------------------------------------- INIT ABOVE ---------------------------------------------//


//------------------------------------ RADIO BUTTONS BELOW ---------------------------------------//

        final RadioButton radioBtnNon = (RadioButton) findViewById(R.id.radioButtonNon);
        final RadioButton radioBtnDisc = (RadioButton) findViewById(R.id.radioButtonDisc);
        final RadioButton radioBtnCont = (RadioButton) findViewById(R.id.radioButtonCont);

        radioBtnNon.setChecked(true);

//-------------------------------- RADIO BUTTONS ABOVE -------------------------------------------//


//-------------------------------- LOCATION LISTENER BELOW ---------------------------------------//

        //Construct a listener that checks for changes in the location service in Android
        LocationListener locationListener = new LocationListener() {

            public void onLocationChanged(Location location) {                                      /*Run on location changed or update request*/

                float accuracy = location.getAccuracy();                                            /*Get the current accuracy from the location manager in meters*/

                final TextView gpsAccuracy = (TextView) findViewById(R.id.gpsAccuracy);             /*Construct TextView*/
                gpsAccuracy.setText(String.valueOf(accuracy));                                      /*Update the UI to show the current accuracy*/

                float speedMPS = location.getSpeed();                                               /*Get the current speed from the location manager in m/s*/
                float speedKPH = 3.6f * speedMPS;                                                   /*Convert to KPH. 3.6 because that's what you need to go from m/s to km/h*/

                final TextView currentSpeed = (TextView) findViewById(R.id.currentSpeed);           /*Construct TextView*/
                currentSpeed.setText(String.valueOf(speedKPH));                                     /*Update the UI to show the current speed in km/h*/

                double getLon = location.getLongitude();                                            /*Gets the longitude from the location manager*/
                double getLat = location.getLatitude();                                             /*Gets the latitude from the location manager*/

                if(switchLogging.isChecked()) {
                    if (radioBtnNon.isChecked() || radioBtnDisc.isChecked() ||
                            radioBtnCont.isChecked()) {

                    /*Write unix time, speed in kph, accuracy in meters to a new line*/
                        try {
                            String msg = iterations[0] + "\t" + speedKPH + "\t" + accuracy + "\t\t"
                                    + iterations[0] + "," + getLat + "," + getLon + "\n";
                            logWriter.write(msg);                                                   /*Write the string endMsg to the FileWriter*/
                            totalSpeed[0] += speedKPH;                                              /*Add the current speed to total speed*/
                            iterations[0] += 1;                                                     /*Add 1 to the iteration counter*/
                        } catch (IOException e) {
                            String toastMsg = e.getMessage();
                            Toast.makeText(getApplicationContext(), toastMsg,
                                    Toast.LENGTH_LONG).show();
                            e.printStackTrace(System.out);
                        }



                        if (radioBtnDisc.isChecked()) {


                        } else if (radioBtnCont.isChecked()) {

                            sound.floatToPd("Volume", 100.0f);

                        }

                    } else {
                        switchLogging.setChecked(false);
                        String toastMsg = "Please pick an audio mode!";
                        Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
                    }
                }//If switchLogging is checked
            }//onLocationChanged

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {               /*If the provider status has changed*/
                final TextView gpsStatus = (TextView) findViewById(R.id.gpsStatus);                 /*Construct TextView*/
                gpsStatus.setText(provider);
                Log.i("Main/Location listener", "Provider changed to " + provider);
            }

            @Override
            public void onProviderEnabled(String provider) {                                        /*If the provider is enabled*/
                final TextView gpsStatus = (TextView) findViewById(R.id.gpsStatus);                 /*Construct TextView*/
                gpsStatus.setText(provider);
                Log.i("Main/Location listener", "Provider changed to " + provider);
            }

            @Override
            public void onProviderDisabled(String provider) {                                       /*If the provider is disabled*/
                final TextView gpsStatus = (TextView) findViewById(R.id.gpsStatus);                 /*Construct TextView*/
                gpsStatus.setText(provider);
                Log.i("Main/Location listener", "Provider changed to " + provider);
            }
        };

        //Request new location update every minUpdateTime millisecond & minUpdateLocation meters.
        final int minUpdateTime = 1000;                                                             /*Minimum time between update requests in milliseconds. 1000 = 1 second*/
        final int minUpdateLocation = 0;                                                            /*Minimum distance between updates in meters. 0 = no min change.*/
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minUpdateTime,
                minUpdateLocation, locationListener);

//-------------------------------- LOCATION LISTENER ABOVE ---------------------------------------//


//-------------------------------- LOCK SWITCH CONTROL BELOW -------------------------------------//

        //Lock switch
        switchLock.setOnClickListener(new View.OnClickListener() {                                  /*Create a listener service that checks if the switch that locks is clicked on*/
            public void onClick(View v) {

                if (switchLock.isChecked()) {                                                       /*If the switch is already checked then run the scope*/
                    //Switch controlling logging
                    switchLogging.setClickable(false);                                              /*Disable the switch that controls the logging*/
                    switchLogging.setAlpha(0.4f);                                                   /*Set it to 50% alpha in the UI*/

                    //Button creating a new file
                    createFileBtn.setClickable(false);                                              /*Disable the button that creates a new file to log to*/
                    createFileBtn.setAlpha(0.4f);                                                   /*Set it to 50% alpha in the UI*/

                } else {
                    //Switch controlling logging
                    switchLogging.setClickable(true);                                               /*Enables the switch that controls the logging*/
                    switchLogging.setAlpha(1.0f);                                                   /*Sets the alpha of the switch to 100% in the ui*/

                    //Button creating a new file
                    createFileBtn.setClickable(true);                                               /*Enables the button that controls the logging*/
                    switchLogging.setAlpha(1.0f);                                                   /*Sets the alpha of the switch to 100% in the ui*/

                }
            }
        });

//-------------------------------- LOCK SWITCH CONTROL ABOVE -------------------------------------//


//-------------------------------- LOGGING  SWITCH BELOW -----------------------------------------//

        //Logging switch
        switchLogging.setOnClickListener(new View.OnClickListener() {                               /*Create a listener service that checks if the switch that controls the logging is pressed*/
            public void onClick(View v) {

                TextView loggingStatus = (TextView) findViewById(R.id.isLogging);                   /*Construct TextView*/

                if(fileName[0] == null) {                                                           /*Checks if a file has been created, if not then it'll toast an error*/
                    Toast.makeText(getApplicationContext(), "Please start a new session",
                            Toast.LENGTH_LONG).show();

                    switchLogging.toggle();                                                         /*Toggles the switch back to off state*/

                } else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    checkGPS();
                    switchLogging.toggle();
                } else if (switchLogging.isChecked()) {                                             /*If the switch is already checked then run the scope*/
                    Log.i("Main/Logging listener", "on / logging");

                    loggingStatus.setTextColor(Color.rgb(0,255,0));                                 /*Set the text color to green in the UI*/
                    loggingStatus.setText("Yes");                                                   /*Change the text to yes in the UI*/

                    //Disable the radio buttons so the user won't accidentally change feedback mode during a session
                    radioBtnNon.setClickable(false);
                    radioBtnDisc.setClickable(false);
                    radioBtnCont.setClickable(false);

                } else {
                    Log.i("Main/Logging listener", "off / not logging");

                    loggingStatus.setTextColor(Color.rgb(255,0,0));                                 /*Set the text color to red in the UI*/
                    loggingStatus.setText("No");                                                    /*Set the text to no in the UI*/

                    //Enables the radio buttons after the session has ended
                    radioBtnNon.setClickable(true);
                    radioBtnDisc.setClickable(true);
                    radioBtnCont.setClickable(true);

                    double loggedTime = minUpdateTime/1000 * iterations[0];                         /*Estimates the time logging in milliseconds. Should take delta of start unix and end unix*/
                    double avgSpeed = totalSpeed[0] / iterations[0];                                /*Divide total speed with the iteration counter to get average speed*/

                    try {
                        String endMsg = "\n\t\t\t\t\t\t" + iterations[0] + "\n\t\t\t\t\t\t" +
                                avgSpeed + "\n\t\t\t\t\t\t" + loggedTime;

                        logWriter.stopWriter(endMsg);                                               /*Writes endMsg to the FileWriter and closes the FileWriter*/
                    } catch (IOException e) {
                        e.printStackTrace(System.out);
                    }

                    //Sets the text in the UI as 0.0 cause if either iterations or avgSpeed it divides by zero and returns NaN
                    if(avgSpeed == 0.0) {
                        final TextView avgSpeedTxt = (TextView) findViewById(R.id.avgSpeed);            /*Construct TextView*/
                        avgSpeedTxt.setText("0");                                                   /*Update average speed in UI*/
                    } else {
                        final TextView avgSpeedTxt = (TextView) findViewById(R.id.avgSpeed);            /*Construct TextView*/
                        avgSpeedTxt.setText(String.valueOf(avgSpeed));                              /*Update average speed in UI*/
                    }

                    Media.uploadFTP(fileName[0]);                                                   /*Upload the logfile to a FTP server.*/

                    //Reset average speed % iteration counter to 0 if they're not. It also sets the
                    // current file name to null, this is to make sure we don't write to the same
                    // file twice.
                    if(avgSpeed != 0.0 || iterations[0] != 0.0 || fileName[0] != null) {
                        avgSpeed = 0.0;
                        iterations[0] = 1.0;
                        fileName[0] = null;
                        Log.i("Main/Logging listener", "Variables reset: avgSpeed, iterations & currentFileName");
                        Log.i("Main/Logging listener", "Iterations: " + iterations[0] +
                                " Average Speed: " + avgSpeed + " Current filename: " + fileName[0]);

                    }
                }
            }
        });

//-------------------------------- END LOGGING ABOVE ---------------------------------------------//


//-------------------------------- FILE CREATION BELOW -------------------------------------------//

            createFileBtn.setOnClickListener(new View.OnClickListener() {                           /*Create a listener service that checks if the button that creates a new file has been pressed*/
                @Override

                public void onClick(View v) {

                    if(!switchLogging.isChecked()){

                        userId[0] = Media.getId();
                        audioMode[0] = Media.getMode();

                        fileName[0] = String.valueOf(userId[0]) + "_" + audioMode[0] + "_" +
                                String.valueOf(System.currentTimeMillis() + ".txt");

                        Media.createFile(folderName, fileName[0]);                           /*Creates a new file with the name of fileName[0] and location of folderName*/
                        Logging.startWriter(folderName, fileName[0]);                        /*Starts a writer to the file in folderName/fileName[0]*/
                    } else {
                        String toastMsg = "Please stop logging before starting a new session";
                        Toast.makeText(getApplicationContext(), toastMsg,
                                Toast.LENGTH_LONG).show();
                    }

                }


            });

//-------------------------------- FILE CREATION ABOVE -------------------------------------------//

        StrictMode.ThreadPolicy policy =
                new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    void checkGPS() {

        LocationManager manager = (LocationManager) this.getSystemService
                (Context.LOCATION_SERVICE);                                                         /*Construct a new LocationManager to check the GPS provider*/
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {                             /*Check if the location provider is the GPS*/
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);                 /*Construct a new dialog box*/
            alertDialogBuilder.setMessage("GPS is disabled, please enable it before continuing.")   /*Sets the message in the dialog box*/
                    .setPositiveButton("Location settings",                                         /*Sets the name of the positive button*/
                            new DialogInterface.OnClickListener() {                                 /*Creates the on click listener service*/
                                public void onClick(DialogInterface dialog, int id) {
                                    Intent callGPSSettingIntent = new Intent(
                                       android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);  /*Links to the location service settings within Android OS*/
                                    startActivity(callGPSSettingIntent);
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

} //MainActivity