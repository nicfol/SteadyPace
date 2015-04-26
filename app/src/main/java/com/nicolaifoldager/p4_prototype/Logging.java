package com.nicolaifoldager.p4_prototype;

import android.util.Log;

import java.io.FileWriter;
import java.io.IOException;

public class Logging {

    final static FileWriter[] logWriter = new FileWriter[1];                                        /*Constructs a new FileWriter globally, so that we can call it anywhere*/

    /**
     * Starts the FileWriter
     *
     * @param folderName    Name of the directory the logfile is within
     * @param name          Name of the file that should be written to
     */
    static void startWriter(String folderName, String name) {

        try {
            logWriter[0] = new FileWriter(folderName + name);                                       /*Starts a new filewriter to the file 'name' at 'folderName'*/
            logWriter[0].flush();                                                                   /*Flushes the FileWriter to make sure everything ins the RAM is written to the permanent storage*/
            Log.i("Logging/StartWriter", "Started a new filewriter to: " + folderName + name);
        } catch (IOException e) {
            e.printStackTrace(System.out);
        }

    }

    /**
     * Writes to the FileWriter initialized when the class is constructed.
     *
     * @param content       String of what should be written to the logfile.
     * @throws IOException
     */
    static void write(String content) throws IOException {

        try {
            logWriter[0].write(content);                                                            /*Writes the data to the logfile*/
            logWriter[0].flush();                                                                   /*Flushes the FileWriter to make sure everything ins the RAM is written to the permanent storage*/
            Log.i("Logging/write", "Written to log: " + content);
        } catch (IOException e) {
            e.printStackTrace(System.out);
        }

    }

    /**
     * Stops the FileWriter initialized when the class is constructed.
     *
     * @param content       iterations, avg. speed and time logging in seconds.
     * @throws IOException
     */
    static void stopWriter(String content) {

        try {
            write(content);                                                                         /*Writes a final String to the log*/

            logWriter[0].flush();                                                                   /*Flushes the FileWriter to make sure everything ins the RAM is written to the permanent storage*/
            logWriter[0].close();                                                                   /*Closes the OutputStreamWriter*/
            Log.i("Logging/stopWriter","Filewriter flushed and closed");
        } catch (IOException e) {
            e.printStackTrace(System.out);
        }

    }
}

