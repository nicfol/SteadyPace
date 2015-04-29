package com.nicolaifoldager.p4_prototype;

import android.os.Environment;
import android.util.Log;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;

public class Media {

    String m_userID;
    String m_audioMode;
    float m_BPM;
    float m_avgSpeed;

    /**
     * Check if a directory exists
     *
     * @param directory     The directory to look for as a string
     * @return              Returns false if it doesn't exist, else true
     */
    public boolean folderExists(String directory) {
        File folder = new File(directory);

        if (!folder.exists()) {
            Log.i("Media/folderExists", directory + " doesn't exist");
            return false;
        } else {
            Log.i("Media/folderExists", directory + " exist");
            return true;
        }
    }

    /**
     * Creates a folder within the directory specified in the parameter
     *
     * @param directory     The directory to create a file within.
     */
    public boolean createFolder(String directory) {
        File folder = new File(directory);                                                          /*Constructs a new file object in the directory*/

        if (!folder.exists()) {                                                                     /*Checks if the directory exists, if it doesn't it creates it.*/
            try {
                folder.mkdir();                                                                     /*Creates the directory if it doesn't exist*/
                return true;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if the file prefs.txt exists within the directory specified in the paramter
     *
     * @param directory     The directory to look for as a string
     * @return              Returns true if it exists, else false
     */
    public boolean prefsExists(String directory) {
        File prefs = new File(directory, "prefs.txt");                                              /*Creates a new File object at directory named prefs.txt*/
        if(prefs.exists()) {
            Log.i("Media/prefsExists", prefs.toString() + " exists");
            return true;
        } else {
            Log.i("Media/prefsExists", prefs.toString() + " doesn't exists");
            return false;
        }
    }

    /**
     * Creates a file
     *
     * @param directory     The directory to create a file within.
     * @param name          The name of the file included file extension
     */
    public void createFile(String directory, String name) {
        try {
            File logFile = new File(directory, name);                                               /*Construct a new file at the path and name from the attributes*/
            logFile.createNewFile();                                                                /*Creates a new file in directory/name*/
            Log.i("Media/createFile", "Current file name: " + name);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    /**
     * Creates an "unique" user ID based on a random number between 0 and the integer limit
     *
     * @return  Returns the user ID
     */
    public String createUserID() {
        Random rand = new Random();                                                                 /*Constructs a new Random Objects*/
        int deviceIdInt = rand.nextInt(2147483647);                                                 /*Sets the deviceID as a random integer between 0 and the integer limit*/
        m_userID = String.valueOf(deviceIdInt);                                                     /*Saves the device ID as a string, so we can write it to the log*/
        return m_userID;
    }

    /**
     * Creates an audio mode based on the user Id
     *
     * @param directory     The directory to pass to getUserId() if it doesn't exist.
     * @return              Returns the audio mode
     */
    public String createAudioMode(String directory) {
        int t_userID = Integer.parseInt(getUserID(directory));                                      /*Calls for the user ID*/

        if(t_userID % 2 == 0) {
            m_audioMode = "cont";
            Log.i("Media/createPrefs", m_audioMode+ " feedback chosen");
        } else {
            m_audioMode = "disc";
            Log.i("Media/createPrefs", m_audioMode+ " feedback chosen");
        }
        return m_audioMode;
    }

    /**
     * A getter for the user ID stored within the prefs.txt file in the public directory
     *
     * @param directory     The directory to look within
     * @return              Returns the user ID if it worked, else returns -1 to indicate exception
     */
    public String getUserID(String directory) {
        if(prefsExists(directory)) {
            try {
                BufferedReader prefsReader = new BufferedReader(new FileReader(directory +
                        "prefs.txt"));                                                              /*Starts a new bufferedReader from prefs.txt so we can read from it*/
                m_userID = prefsReader.readLine();                                                  /*Reads the first line and sets the device Id as the outcome*/
                return m_userID;
            } catch (IOException e) {
                e.printStackTrace();
                return "-1";
            }
        } else {
            return createUserID();                                                                  /*If prefs.txt doesn't exist, then create a user ID*/
        }

    }

    /**
     * A getter for the audio mode stored within the prefs.txt file in the public directory
     *
     * @param directory     The directory to look within
     * @return              Returns the audio mode if successful gotten, else -1 to indicate exception
     */
    public String getAudioMode(String directory) {
        try {
            BufferedReader prefsReader = new BufferedReader(new FileReader(directory +
                    "prefs.txt"));                                                                  /*Starts a new bufferedReader from prefs.txt so we can read from it*/
            prefsReader.readLine();                                                                 /*Skip line*/
            m_audioMode = prefsReader.readLine();                                                   /*Reads the first line and sets the device Id as the outcome*/
            return m_audioMode;
        } catch (IOException e) {
            e.printStackTrace();
            return "-1";
        }
    }

    /**
     * A getter for the BPM stored within the prefs.txt file in the public directory
     *
     * @param directory     The directory to look within
     * @return              Returns the BPM if successful, else return -1.0f to indicate exception
     */
    public float getBPM(String directory) {
        try {
            BufferedReader prefsReader = new BufferedReader(new FileReader(directory +
                    "prefs.txt"));                                                                  /*Starts a new bufferedReader from prefs.txt so we can read from it*/
            prefsReader.readLine();                                                                 /*Skip line*/
            prefsReader.readLine();                                                                 /*Skip line*/
            m_BPM = Float.parseFloat(prefsReader.readLine());
            return m_BPM;
        } catch (IOException e) {
            e.printStackTrace();
            return -1.0f;
        }
    }

    /**
     * A getter for the average speed in meters per second stored within the prefs.txtfile in the
     * public directory
     *
     * @param directory     The directory to look within
     * @return              Returns the average speed if successful, else return -1.0f to
     * indicate exception
     */
    public float getAvgSpeed(String directory) {
        try {
            BufferedReader prefsReader = new BufferedReader(new FileReader(directory +
                    "prefs.txt"));                                                                  /*Starts a new bufferedReader from prefs.txt so we can read from it*/
            prefsReader.readLine();                                                                 /*Skip line*/
            prefsReader.readLine();                                                                 /*Skip line*/
            prefsReader.readLine();                                                                 /*Skip line*/
            m_avgSpeed = Float.parseFloat(prefsReader.readLine());                                  /*Read the 4th line*/
            return m_avgSpeed;
        } catch (IOException e) {
            e.printStackTrace();
            return -1.0f;
        }
    }

    private boolean result = false;

    /**
     * Upload a file to a FTP server.
     *
     * @param filename  filename that is going to be uploaded in string without directory but with
     *                  file extension.
     */
    String uploadFTP(final String filename) {

        if (!result) {
            try {
                FTPClient ftpConnection = new FTPClient();                                          /*Construct an FTPClient from the Apache commons library*/
                ftpConnection.connect("31.170.160.101");                                            /*Host for the FTP*/
                ftpConnection.login("a2212160", "fisk123");                                         /*Username and code ord*/

                ftpConnection.enterLocalPassiveMode();                                              /*Use passive mode to connect to the FTP (Connect through port 21)*/
                ftpConnection.setFileType(FTP.BINARY_FILE_TYPE);                                    /*Set the transfer type to binary, meaning we convert everything to binary before uploading*/
                ftpConnection.changeWorkingDirectory("/public_html/logs/");                         /*Change the directory the file will be uploaded to on the FTP server*/

                String data = Environment.getExternalStorageDirectory().toString() +
                        "/Android/data/com.nicolaifoldager.p4_prototype/" + filename;               /*Location and name of the file that is going to be uploaded*/

                FileInputStream in = new FileInputStream(new File(data));                           /*Start a new inputStream from the file we want uploaded so we can read from it to the FTP*/
                result = ftpConnection.storeFile(filename, in);                                     /*Print the result of the file upload*/
                in.close();                                                                         /*Closes the filestream*/

                ftpConnection.logout();                                                             /*Logs out of the session at the FTP server*/
                ftpConnection.disconnect();                                                         /*Disconnects from the FTP server*/

                Log.i("Media/uploadFTP","Log file uploaded: " + result);                            /*Prints the result of the file upload*/

                return "Success!";
            } catch (Exception e) {
                e.printStackTrace(System.out);
                return "ERROR " + e.getMessage();
            }
        }

        return "ERROR";                                                                             /*Shouldn't ever get to this point*/
    }
}