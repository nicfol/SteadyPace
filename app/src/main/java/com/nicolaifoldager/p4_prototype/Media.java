package com.nicolaifoldager.p4_prototype;

import android.os.Environment;
import android.util.Log;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;


public class Media {

    private static String[] deviceIdGlobal = {"0"};
    private static String[] modeGlobal = {"NOTSET"};

    /**
     * Checks if the applications has read/write access to the external storage (SD-Card). On phones
     * without an SD-Card slot it is emulated on the internal storage.
     *
     * @return  True if RW-access has been granted, false if not.
     */
    static boolean rwAccess() {

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * Creates a new folder in the location of directory
     *
     * @param directory The location and name of the folder, e.g. /Android/data/com.your_app
     */
    static void createFolder(String directory) {

        File folder = new File(directory);                                                          /*Constructs a new file object in the directory*/

        if (!folder.exists()) {                                                                     /*Checks if the directory exists, if it doesn't it creates it.*/
            try {
                folder.mkdir();                                                                     /*Creates the directory*/
                Log.i("Media/init", "Directory created!");
            } catch (Exception e) {
                e.printStackTrace(System.out);
                Log.i("Media/init", "Folder creation exception: " + e);
            }
        }

    }

    /**
     * Creates a text file named prefs in the directory specified. If it doesn't exist it will
     * create a new file, pick a random number between 0 and integer limit and pick an audio
     * feedback mode based on the random number.
     *
     * If it does exist it will read the first line as the deviceID and the second as audio mode.
     * afterwards it'll set the global variables to these values.
     *
     * @param directory     Which directory should be prefs.txt file be created in defined as a
     *                      string
     */
    static void createPrefs(String directory) {

        File prefs = new File(directory, "prefs.txt");                                              /*Creates a new File object at directory named prefs.txt*/

        if (prefs.exists()) {                                                                       /*Checks if the file already exists*/
            try {
                BufferedReader prefsReader = new BufferedReader(new FileReader(directory +
                        "prefs.txt"));                                                              /*Starts a new bufferedReader from prefs.txt so we can read from it*/
                deviceIdGlobal[0] = prefsReader.readLine();                                         /*Reads the first line and sets the device Id as the outcome*/
                modeGlobal[0] = prefsReader.readLine();                                             /*Reads the second line and sets the audio mode as the outcome*/

                Log.i("Media/createPrefs", "From prefs, device Id: " + deviceIdGlobal[0] +
                        " audiomode: " + modeGlobal[0]);

            } catch (FileNotFoundException e) {
                e.printStackTrace(System.out);
            } catch (IOException e) {
                e.printStackTrace(System.out);
            }
        } else {
            Logging logging = new Logging();                                                        /*Constructs a new Logging class so we can write to the file*/

            logging.startWriter(directory, "prefs.txt");                                            /*Starts a filewriter to the prefs file*/

            Random rand = new Random();                                                             /*Constructs a new Random Objects*/
            int deviceIdInt = rand.nextInt(2147483647);                                             /*Sets the deviceID as a random integer between 0 and the integer limit*/
            deviceIdGlobal[0] = String.valueOf(deviceIdInt);                                        /*Saves the device ID as a string, so we can write it to the log*/

            try {
                logging.write(deviceIdInt + "\n");                                                  /*Writes the device ID to the prefs.txt*/

                /*
                 * If the deviceID is an even number it saves continuous audio
                 * feedback in the prefs.txt if it's uneven it'll save
                 * discrete audio feedback.
                */
                if (deviceIdInt % 2 == 0) {
                    logging.write("cont");
                    modeGlobal[0] = "cont";
                    Log.i("Media/createPrefs", modeGlobal[0] + " feedback chosen");
                } else {
                    logging.write("disc");
                    modeGlobal[0] = ("disc");
                    Log.i("Media/createPrefs", modeGlobal[0] + " feedback chosen");
                }
            } catch (IOException e) {
                e.printStackTrace(System.out);
            }
        }

    }

    /**
     * Creates a new file in the
     *
     * @param directory Location where the file should be created
     * @param name      Name of the file including file type, e.g. name_of_file.txt
     */
    static void createFile(String directory, String name) {

        try {
            File logFile = new File(directory, name);                                               /*Construct a new file at the path and name from the attributes*/
            logFile.createNewFile();                                                                /*Creates a new file in directory/name*/
            Log.i("Media/createFile", "Current file name: " + name);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }

    }

    /**
     * Upload a file to an FTP server.
     *
     * @param filename  filename that is going to be uploaded in string without directory but with
     *                  file extension.
     */
    static boolean uploadFTP(String filename) {
        boolean result = false;
        try {
            FTPClient ftpConnection = new FTPClient();                                              /*Construct an FTPClient from the Apache commons library*/
            ftpConnection.connect("31.170.160.101");                                                /*Host for the FTP*/
            ftpConnection.login("a2212160", "fisk123");                                             /*Username and password*/

            ftpConnection.enterLocalPassiveMode();                                                  /*Use passive mode to connect to the FTP (Connect through port 21)*/
            ftpConnection.setFileType(FTP.BINARY_FILE_TYPE);                                        /*Set the transfer type to binary, meaning we convert everything to binary before uploading*/
            String data = Environment.getExternalStorageDirectory().toString() +
                    "/Android/data/com.nicolaifoldager.p4_prototype/" + filename;                   /*Location and name of the file that is going to be uploaded*/

            FileInputStream in = new FileInputStream(new File(data));                               /*Start a new inputStream from the file we want uploaded so we can read from it to the FTP*/
            ftpConnection.changeWorkingDirectory("/public_html/logs/");                             /*Change the directory the file will be uploaded to on the FTP server*/
            result = ftpConnection.storeFile(filename, in);                                         /*Print the result of the file upload*/
            in.close();                                                                             /*Closes the filestream*/

            ftpConnection.logout();                                                                 /*Logs out of the session at the FTP server*/
            ftpConnection.disconnect();                                                             /*Disconnects from the FTP server*/

        } catch (Exception e) {
            e.printStackTrace(System.out);
        }

        if (result) {
            Log.i("Media/uploadFTP", "Upload result: succeeded");                                   /*Prints the result of the file upload*/
            return true;
        } else {
            Log.i("Media/uploadFTP", "Upload result: failed");                                      /*Prints the result of the file upload*/
            return false;
        }
    }

    /**
     * Used to get the audio mode that has been used in prior sessions
     *
     * @return Returns the audio mode as a string
     */
    static String getMode() {
        return modeGlobal[0];
    }

    /**
     * Used to get the user id that has been used in prior sessions
     *
     * @return Returns the user Id as an int
     */
    static String getId() {
        return deviceIdGlobal[0];
    }

}
