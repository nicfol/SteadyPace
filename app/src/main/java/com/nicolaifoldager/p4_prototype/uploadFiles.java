package com.nicolaifoldager.p4_prototype;

import android.os.AsyncTask;

public class uploadFiles extends AsyncTask<String, String, String> {

    private boolean running = true;
    private int cnt = 0;

    @Override
    protected void onCancelled() {                                                                  /*Runs when the async task is cancelled*/
        super.onCancelled();
        running = false;                                                                            /*Set to false to stop the upload*/
    }

    @Override
    protected String doInBackground(String... filename ) {
        Media media = new Media();

        while(running) {
            media.uploadFTP(filename[0]);                                                           /*Upload the logfile*/
            cnt++;
            if (cnt > 50)
                running = false;
        }

        return null;
    }

    @Override
    protected void onPostExecute(String s) {                                                        /*Runs when the async task have completed doInBackground()*/
        super.onPostExecute(s);
    }
}
