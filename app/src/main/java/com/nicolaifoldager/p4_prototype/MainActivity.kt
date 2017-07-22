/** An application that logs the movement speed of the phone, writes it to a *.txt file
 * named current unix time within the /Android/com.nicolaifoldager.p4_prototype/ directory in the
 * internal storage. On end of logging it estimates the average speed, no. of entries in the log
 * and how long it has been logging.

 * Based on the speed a sound output is manipulated in order to help keep the movement speed of the
 * phone to a steady pace.

 * Entries on logging:     Iterations  Speed   Accuracy    Altitude    latitude,longitude
 * Entries on end:         Number of entries + average speed in kph + time spend logging in seconds

 * Copyright (C) 2015 Nicolai Foldager

 * @author  Nicolai Foldager
 * *
 * @version 1.0
 */


package com.nicolaifoldager.p4_prototype

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.AsyncTask
import android.os.Environment
import android.os.PowerManager
import android.support.v7.app.ActionBarActivity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast

import org.puredata.android.io.AudioParameters
import org.puredata.android.io.PdAudio
import org.puredata.android.utils.PdUiDispatcher
import org.puredata.core.PdBase
import org.puredata.core.utils.IoUtils

import java.io.File
import java.io.IOException


class MainActivity : ActionBarActivity() {

    /**     Wakelock                                                                               */
    internal var mWakeLock: PowerManager.WakeLock? = null                                                         /*Wakelock to keep the cpu running while screen is off during a session*/

    /**     PD patch dispatcher                                                                    */
    internal var dispatcher: PdUiDispatcher                                                                      /*PD dispatcher for audio control*/

    /**     Construct Logging class                                                                */
    internal val logging = Logging()                                                          /*Constructed globally so it can be closed when the application is closed by onDestroy(); Read more: https://developer.android.com/reference/android/app/Activity.html*/

    /**     Location Manager                                                                       */
    internal var locationManager: Array<LocationManager>? = null                                                       /*Construction a LocationManager to call for the location_service in android*/
    internal var locationListener: LocationListener

    protected fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)                                                         /*Tells the JVM to run both the overwritten code in onCreate and the code we've written here*/
        setContentView(R.layout.activity_main)                                                     /*Tells the JVM which xml layout file it should use*/

        /**     Wakelock                                                                           */
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager             /*Construct a new PowerManager to call from the power service within Android OS*/
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                MainActivity::class.java!!.getSimpleName())                                                /*Construct a new partial wakelock to keep the CPU running when the screen is of*/

        /**     PD Init                                                                            */
        init_pd()
        loadPdPatch()

        /**     Asynchronized task                                                                 */
        val uploadFiles = arrayOfNulls<AsyncTask<*, *, *>>(1)                                           /*AsyncTask for file upload on another thread*/

        /**     Call the elements in the UI                                                        */
        val switchLogging = findViewById(R.id.switchStartLogging) as Switch                /*Logging Switch*/
        val createFileBtn = findViewById(R.id.createFile) as Button                        /*Create new file button*/
        val setStepLengthBtn = findViewById(R.id.setStepLengthBtn) as Button               /*Button for setting the step length*/

        val radioGroup = findViewById(R.id.radioGroup) as RadioGroup                   /*Group of radio buttons that include the two sound feedback modes*/

        val rBtnNoSound = findViewById(R.id.rBtnNoSound) as RadioButton               /*Radiobutton for no sound audio mode*/
        val rBtnSound = findViewById(R.id.rBtnSound) as RadioButton                   /*Radiobutton for sound audio mode*/

        val stepLengthTxt = findViewById(R.id.stepLengthField) as EditText               /*Textfield to input step length into*/
        stepLengthTxt.gravity = Gravity.CENTER                                                   /*Center it cause sparkles*/

        /**     Variables                                                                          */
        val userID = arrayOf<String>(null)
        val audioMode = arrayOf<String>(null)
        val BPM = floatArrayOf(0.0f)
        val stepLength = floatArrayOf(0f)

        val totalSpeed = floatArrayOf(0.0f)                                                          /*Total speed, used to calc average speed*/
        val iterations = floatArrayOf(1.0f)                                                          /*How many times the location manager have updated the speed (How many entries we have in the log file)*/
        val avgSpeed = floatArrayOf(0.0f)
        val volume = floatArrayOf(0.0f)
        val caliAvgSpeed = floatArrayOf(0.0f)

        val startTime = LongArray(1)
        val endTime = LongArray(1)
        val runTime = LongArray(1)

        val fileName = arrayOf<String>(null)
        val folderName = arrayOf(Environment.getExternalStorageDirectory().toString() + "/Android/data/com.nicolaifoldager.p4_prototype/")                                 /*Specify the path to the file directory we will save to*/


        /**     Initialization                                                                     */
        val media = Media()

        if (!media.folderExists(folderName[0])) {                                                   /*Checks if the public data folder for the application exists*/
            media.createFolder(folderName[0])                                                      /*If it doesn't then create one*/
        }

        checkGPS()                                                                                 /*Checks if the location provider is set to the GPS, if it tell them to do so*/

        if (media.prefsExists(folderName[0])) {                                                      /*Checks if the prefs.txt file exists*/
            userID[0] = media.getUserID(folderName[0])                                             /*Calls for the user ID from the prefs.txt*/
            audioMode[0] = media.getAudioMode(folderName[0])                                       /*Calls for the audio mode fom the prefs.txt*/
            BPM[0] = media.getBPM(folderName[0])                                                   /*Calls for the BPM from the prefs.txt*/
            caliAvgSpeed[0] = media.getAvgSpeed(folderName[0])                                     /*Calls for the speed in the calibration run from the prefs.txt*/
            println("Prefs does exist: - ID: " + userID[0] + " Audio: " + audioMode[0] +
                    " BPM: " + BPM[0] + " Calibration speed: " + caliAvgSpeed[0])
            rBtnSound.toggle()                                                                     /*Toggle for sound button as the user already have performed a calibration run*/
            updatePin(2, true)                                                                     /*Update the pin number 2 to green*/
            rBtnNoSound.isEnabled = false                                                          /*Disables the no feedback sound*/
        } else {
            userID[0] = media.createUserID()                                                       /*Create a User ID*/
            audioMode[0] = media.createAudioMode(folderName[0])                                    /*Calculate the audio feedback*/
            println("Prefs does not exist: - ID: " + userID[0] + " Audio: " + audioMode[0] +
                    " BPM: " + BPM[0] + " Calibration speed: " + caliAvgSpeed[0])
            rBtnNoSound.toggle()                                                                   /*Toggles the no feedback sound*/
            updatePin(2, true)                                                                     /*Update the pin number 2 to green*/
            rBtnSound.isEnabled = false                                                            /*Disable the feedback button as there have been no calibration run*/
        }


        /**     Create new session button                                                          */
        createFileBtn.setOnClickListener {
            if (switchLogging.isChecked) {                                                     /*If the app is logging*/
                Toast.makeText(getApplicationContext(), "Please stop logging before " + "starting a new session",
                        Toast.LENGTH_LONG).show()
            } else if (stepLength[0] == 0f) {                                                     /*If step length isn't set*/
                Toast.makeText(getApplicationContext(), "Please set your step length",
                        Toast.LENGTH_LONG).show()
            } else if (!rBtnNoSound.isChecked && !rBtnSound.isChecked) {                     /*If neither of the radio buttons are enabled (Shouldn't ever happen)*/
                Toast.makeText(getApplicationContext(), "Please choose a sound feedback mode",
                        Toast.LENGTH_LONG).show()
            } else {
                fileName[0] = userID[0].toString() + "_" + audioMode[0] + "_" +
                        (System.currentTimeMillis().toString() + ".txt").toString()

                media.createFile(folderName[0], fileName[0])                                   /*Create a logfile for this session*/
                logging.startWriter(folderName[0], fileName[0])                                /*Starts a file writer to the logfile*/

                caliAvgSpeed[0] = media.getAvgSpeed(folderName[0])
                /** DEBUG  */
                /** DEBUG  */

                updatePin(3, true)                                                             /*Update pin 3 to green*/

                //Reset variables
                iterations[0] = 1.0f
                avgSpeed[0] = 0.0f
                totalSpeed[0] = 0.0f

            }
        }


        /**     Start session                                                                      */
        switchLogging.setOnClickListener {
            if (!checkGPS()) {                                                                   /*If the GPS is off*/
                Toast.makeText(getApplicationContext(), "Please turn on the GPS",
                        Toast.LENGTH_LONG).show()
                switchLogging.toggle()
            } else if (stepLength[0] == 0f) {                                                     /*If the step length isn't set*/
                Toast.makeText(getApplicationContext(), "Please set your step length",
                        Toast.LENGTH_LONG).show()
                switchLogging.toggle()
            } else if (fileName[0] == null) {                                                    /*If a new session hasn't been started*/
                Toast.makeText(getApplicationContext(), "Please Start a new session",
                        Toast.LENGTH_LONG).show()
                switchLogging.toggle()
            } else if (!rBtnNoSound.isChecked && !rBtnSound.isChecked) {                     /*If neither of the radio buttons are enabled (Shouldn't ever happen)*//*If */
                Toast.makeText(getApplicationContext(), "Please select an audio mode",
                        Toast.LENGTH_LONG).show()
                switchLogging.toggle()
            } else if (switchLogging.isChecked) {                                              /*If the switch is checked to start logging*/
                Log.i("Main/Logging listener", "on / logging")
                updatePin(4, true)                                                             /*Update pin 4 to green*/

                //Disables the controls
                setStepLengthBtn.isEnabled = false
                createFileBtn.isEnabled = false
                stepLengthTxt.isFocusable = false

                startTime[0] = System.currentTimeMillis() * 1000

                mWakeLock!!.acquire()                                                            /*Acquire a wakelock to keep the CPU running and keep logging even if the screen is off*/

                if (!rBtnNoSound.isChecked) {
                    startAudio()                                                               /*Starts the audio*/
                    floatToPd("bpm", BPM[0])                                                   /*Send BPM to the PD patch to give the correct feedback*/
                }

                //If the audio mode is set to continuous
                if (audioMode[0] == "cont") {
                    volume[0] = 0.5f
                    floatToPd("volume", volume[0])
                }


            } else if (iterations[0] < 150) {                                                   /*If the session haven't been running for 5 minutes then tell the user so*/

                val alertDialogBuilder = AlertDialog.Builder(this@MainActivity)    /*Construct a new dialog box*/
                alertDialogBuilder.setMessage("Please keep going for at least 5 minutes in order for your session to be accepted.")  /*Sets the message in the dialog box*/
                        .setPositiveButton("Keep going" /*Sets the name of the positive button*/
                        ) { dialog, id ->
                            /*Creates the on click listener service*/
                            /*What to do on click*/
                            switchLogging.toggle()
                            dialog.cancel()                                        /*Destroys the dialog*/
                        }
                alertDialogBuilder.setNegativeButton("Abort session" /*Sets the name of the negative button*/
                ) { dialog, id ->
                    /*Creates the on click listener service*/
                    /*What to do on click*/

                    Log.i("Main/Logging listener", "off / not logging")

                    stopAudio()                                                    /*Stops the audio from the PD patch*/

                    avgSpeed[0] = totalSpeed[0] / iterations[0]                    /*Calculates the average speed based on the total speed and iterations written to te log*/

                    logging.stopWriter("")                                         /*Stops the file writer*/

                    //Reset variables
                    iterations[0] = 1.0f
                    avgSpeed[0] = 0.0f
                    totalSpeed[0] = 0.0f
                    fileName[0] = null

                    //Update the pins
                    for (i in 1..4) {
                        if (i != 3) {
                            updatePin(i, false)
                        }                         /*Hack to avoid Pin 3 from chaging back to red*/
                    }

                    //Enables the controls
                    setStepLengthBtn.isEnabled = true
                    createFileBtn.isEnabled = true
                    stepLengthTxt.isFocusable = true

                    if (mWakeLock!!.isHeld) {                                        /*Check if a wakelock is held (Psst, it is!)*/
                        mWakeLock!!.release()                                        /*Release it*/
                        mWakeLock!!.acquire(300000)                                  /*Acquire a new one for 5 minutes to ensure that the log is uploaded*/
                    }

                    dialog.cancel()                                                /*Cancels the dialog box*/
                }
                val alert = alertDialogBuilder.create()                                /*Constructs the dialog*/
                alert.show()

            } else {
                Log.i("Main/Logging listener", "off / not logging")

                stopAudio()                                                                    /*Stops the audio from the pd patch*/

                avgSpeed[0] = totalSpeed[0] / iterations[0]                                    /*Calculate average speed*/

                logging.stopWriter("")                                                         /*Stops the file writer*/

                if (rBtnNoSound.isChecked) {                                                   /*Only runs if it is a calibration run*/
                    val prefsMedia = Media()
                    val prefsLogging = Logging()

                    avgSpeed[0] = avgSpeed[0] * 0.90f

                    BPM[0] = avgSpeed[0] * 60 / (stepLength[0] / 100)

                    //Creates prefs.txt, start a file writer to it, writes the string and stops the file writer
                    prefsMedia.createFile(folderName[0], "prefs.txt")
                    prefsLogging.startWriter(folderName[0], "prefs.txt")
                    prefsLogging.stopWriter(userID[0] + "\n" + audioMode[0] + "\n" + BPM[0] +
                            "\n" + avgSpeed[0])

                    //Change the feedback to on, in case the app isn't terminated before next session
                    rBtnNoSound.toggle()
                    rBtnSound.toggle()
                    rBtnNoSound.isEnabled = false
                    rBtnSound.isEnabled = true
                }

                showUploadDialog()                                                             /*Shows a dialog that the application is trying to upload*/
                uploadFiles[0] = uploadFiles().execute(fileName[0])                        /*Start the upload*/

                //Reset variables
                iterations[0] = 1.0f
                avgSpeed[0] = 0.0f
                totalSpeed[0] = 0.0f
                fileName[0] = null


                for (i in 1..4) {
                    updatePin(i, false)                                                        /*Set pins to red again*/
                }

                //Enables the controls
                setStepLengthBtn.isEnabled = true
                createFileBtn.isEnabled = true
                stepLengthTxt.isFocusable = true

                if (mWakeLock!!.isHeld) {                                                        /*Check if a wakelock is held (Psst, it is!)*/
                    mWakeLock!!.release()                                                        /*Release it*/
                    mWakeLock!!.acquire(300000)                                                  /*Acquire a new one for 5 minutes to ensure that the log is uploaded*/
                }

            }
        }


        /**     Location Manager                                                                   */

        locationManager = arrayOf(this.getSystemService(Context.LOCATION_SERVICE) as LocationManager)

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (audioMode[0] == "disc" && switchLogging.isChecked && !rBtnNoSound.isChecked) {                     /*Runs only when discrete feedback is chosen and the app is logging*/

                    val deviation = 0.05f                                                        /*Deviation in speed when the music should engage */

                    floatToPd("bpm", BPM[0])                                                       /*Send BPM to the PD patch to give the correct feedback*/

                    if (location.speed > caliAvgSpeed[0] * 1.0f + deviation) {

                        floatToPd("bpm", BPM[0])                                                   /*Send BPM to the PD patch to give the correct feedback*/

                        volume[0] = 0.55f

                        startAudio()

                    } else if (location.speed < caliAvgSpeed[0] * 1.0f - deviation) {

                        floatToPd("bpm", BPM[0])                                                   /*Send BPM to the PD patch to give the correct feedback*/

                        volume[0] = 0.50f

                        startAudio()

                    } else if (PdAudio.isRunning() && location.speed <= caliAvgSpeed[0] * 1.0f + deviation
                            && location.speed >= caliAvgSpeed[0] * 1.0f - deviation) {

                        floatToPd("bpm", BPM[0])                                                   /*Send BPM to the PD patch to give the correct feedback*/

                        volume[0] = 0.00f

                        stopAudio()
                    }
                }

                if (switchLogging.isChecked) {
                    try {
                        val accuracy = location.accuracy                                    /*Get the current accuracy from the location manager in meters*/
                        val speedMPS = location.speed                                       /*Get the current speed from the location manager in m/s*/
                        val getLon = location.longitude                                    /*Gets the longitude from the location manager*/
                        val getLat = location.latitude                                     /*Gets the latitude from the location manager*/

                        val msg = iterations[0].toString() + "\t" + speedMPS + "\t" + accuracy + "\t\t"
                        (+iterations[0]).toString() + "," + getLat + "," + getLon + "\t\t" +
                                volume[0] + "\n"
                        logging.write(msg)

                        totalSpeed[0] += speedMPS
                        iterations[0] += 1f

                    } catch (e: Exception) {
                        e.printStackTrace(System.out)
                    }

                }
            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {               /*If the provider status has changed*/
                Log.i("Main/Location listener", "Provider changed to " + provider)
            }

            override fun onProviderEnabled(provider: String) {                                        /*If the provider is enabled*/
                Log.i("Main/Location listener", "Provider changed to " + provider)
            }

            override fun onProviderDisabled(provider: String) {                                       /*If the provider is disabled*/
                Log.i("Main/Location listener", "Provider changed to " + provider)
            }
        }

        val minUpdateTime: Long = 500                                                              /*Minimum time between update requests in milliseconds. 1000 = 1 second*/
        val minUpdateLocation = 0.1f                                                            /*Minimum distance between updates in meters. 0 = no min change.*/
        locationManager!![0].requestLocationUpdates(LocationManager.GPS_PROVIDER, minUpdateTime,
                minUpdateLocation, locationListener)                                               /*Request new location update every minUpdateTime millisecond & minUpdateLocation meters.*/


        /**     Listener for set step length button                                                */
        setStepLengthBtn.setOnClickListener {
            try {
                stepLength[0] = Integer.valueOf(stepLengthTxt.text.toString())!!.toFloat()            /*Calls the value stored in the text field and saves it to a String*/
                updatePin(1, true)                                                             /*Update the pin on the activity*/

                try {
                    val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager                     /*Construct an InputMethodManager to control the keyboard*/
                    inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                            InputMethodManager.HIDE_NOT_ALWAYS)                                /*Set the input method (the keyboard) to close when the step length is set correctly*/
                } catch (e: Exception) {
                    e.printStackTrace(System.out)                                              /*Ignore the exception cause it doesn't change anything if it fails*/
                }

            } catch (e: NumberFormatException) {                                                 /*Checks if anything but a number is input, if it is then toast*/
                Toast.makeText(getApplicationContext(), "Please only use numbers and specify the length in meters, e.g. 65",
                        Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }


        /**     Listener for changes in the radio buttons                                          */
        radioGroup.setOnCheckedChangeListener(object : RadioGroup.OnCheckedChangeListener {
            private var hasChanged = false

            override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
                if (!hasChanged) {                                                                  /*Checks if the pin is already green to avoid multiple animations*/
                    hasChanged = true
                    updatePin(2, true)                                                             /*Makes pin 4 green*/
                }
            }
        })

    } // onCreate

    protected fun onDestroy() {                                                                    /*Is called when Android OS kills the application in favour of another*/
        super.onDestroy()

        if (mWakeLock!!.isHeld) {
            mWakeLock!!.release()                                                                    /*Release any wakelocks to avoid battery drain*/
        }

        locationManager!![0].removeUpdates(locationListener)                                         /*If the application is destroyed then stop requesting location updates*/

    }


    protected fun onPause() {
        super.onPause()
    }


    /**
     * Check is GPS is enabled, if not alert the user and redirect them to the location settings
     * within Android OS
     */
    private fun checkGPS(): Boolean {
        val manager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager                                                         /*Construct a new LocationManager to check the GPS provider*/
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {                             /*Check if the location provider is the GPS*/
            val alertDialogBuilder = AlertDialog.Builder(this)                 /*Construct a new dialog box*/
            alertDialogBuilder.setMessage("GPS is disabled. Please enable it before continuing.")   /*Sets the message in the dialog box*/
                    .setPositiveButton("Location settings" /*Sets the name of the positive button*/
                    ) { dialog, id ->
                        /*Creates the on click listener service*/
                        val callGPSSettingIntent = Intent(
                                android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)  /*Links to the location service settings within Android OS*/
                        startActivity(callGPSSettingIntent)                            /*Starts the activity and opens location settings*/
                    }
            alertDialogBuilder.setNegativeButton("Cancel" /*Sets the name of the negative button*/
            ) { dialog, id -> /*Creates the on click listener service*/
                dialog.cancel()                                                        /*Cancels the dialog box*/
            }
            val alert = alertDialogBuilder.create()                                        /*Constructs the dialog*/
            alert.show()                                                                           /*Shows the dialog box*/
            return false
        } else {
            return true
        }
    }

    /**
     * Updates the pin in the left when the user has succesfully completed a task

     * @param pinName   The number on the pin that should be changed corresponds to the pinName
     * *
     * @param color     true = green, false = red
     */
    private fun updatePin(pinName: Int, color: Boolean) {
        val pin = arrayOf<ImageView>(null)

        if (pinName == 1)
            pin[0] = findViewById(R.id.imageViewPin1) as ImageView
        else if (pinName == 2)
            pin[0] = findViewById(R.id.imageViewPin2) as ImageView
        else if (pinName == 3)
            pin[0] = findViewById(R.id.imageViewPin3) as ImageView
        else if (pinName == 4)
            pin[0] = findViewById(R.id.imageViewPin4) as ImageView

        val imgOut = AlphaAnimation(1.0f, 0.00f)
        imgOut.duration = 350
        val imgIn = AlphaAnimation(0.00f, 1.0f)
        imgIn.duration = 350

        pin[0].startAnimation(imgOut)

        imgOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}

            override fun onAnimationEnd(animation: Animation) {

                if (color) {
                    if (pinName == 1)
                        pin[0].setImageResource(R.mipmap.onegreen)
                    else if (pinName == 2)
                        pin[0].setImageResource(R.mipmap.twogreen)
                    else if (pinName == 3)
                        pin[0].setImageResource(R.mipmap.threegreen)
                    else if (pinName == 4)
                        pin[0].setImageResource(R.mipmap.fourgreen)

                    pin[0].startAnimation(imgIn)
                }

                if (!color) {
                    if (pinName == 1)
                        pin[0].setImageResource(R.mipmap.onered)
                    else if (pinName == 2)
                        pin[0].setImageResource(R.mipmap.twored)
                    else if (pinName == 3)
                        pin[0].setImageResource(R.mipmap.threered)
                    else if (pinName == 4)
                        pin[0].setImageResource(R.mipmap.fourred)

                    pin[0].startAnimation(imgIn)
                }
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
    }

    /**
     * Shows the dialog that tells the user, that the upload is doing stuff
     */
    private fun showUploadDialog() {
        val alertDialogUpload = AlertDialog.Builder(this)                      /*Construct a new dialog box*/
        alertDialogUpload.setMessage("Uploading file")                                              /*Sets the message in the dialog box*/
                .setNegativeButton("Okay" /*Sets the name of the negative button*/
                ) { dialog, id -> /*Creates the on click listener service*/
                    dialog.cancel()                                                    /*Cancels the dialog box*/
                }
        val alert = alertDialogUpload.create()                                             /*Constructs the dialog*/
        alert.show()                                                                               /*Shows the dialog box*/
    }

    /**
     * Initializes PD Library and prepares the audio outlet.
     */
    private fun init_pd() {
        try {
            // Configure the audio glue
            val sampleRate = AudioParameters.suggestSampleRate()
            PdAudio.initAudio(sampleRate, 0, 2, 8, true)

            // Create and install the dispatcher
            dispatcher = PdUiDispatcher()
            PdBase.setReceiver(dispatcher)
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    /**
     * Loads the PD patch so the app can communicate with it.
     */
    private fun loadPdPatch() {

        try {
            val dir = getFilesDir()

            IoUtils.extractZipResource(getResources().openRawResource(R.raw.pdpatch), dir, true)
            val patchFile = File(dir, "pdpatch.pd")
            PdBase.openPatch(patchFile.getAbsolutePath())

            Log.i("MainActiviy/loadPdPatch", "Patch loaded")
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    /**
     * Sends a float to the PD patch previously loaded in loadPdPatch method

     * @param receiver  The name of the receiver within the pd patch in a string
     * *
     * @param value     The value to send to the receiver as a float
     */
    fun floatToPd(receiver: String, value: Float?) {
        PdBase.sendFloat(receiver, value!!)
        Log.i("MainActivity/floatToPd", "Send $value to $receiver")
    }

    /**
     * Starts the audio
     */
    private fun startAudio() {
        PdAudio.startAudio(this)
    }

    /**
     * Stop the audio
     */
    private fun stopAudio() {
        PdAudio.stopAudio()
    }

} // Main