package com.nicolaifoldager.p4_prototype;

import android.os.AsyncTask;

public class uploadFiles extends AsyncTask<String, String, String> {

    private boolean running = true;

    @Override
    protected void onCancelled() {
        running = false;
    }

    @Override
    protected String doInBackground(String... filename ) {
        Media media = new Media();

        while(running) {
            media.uploadFTP(filename[0]);
            return null;
        }

        return null;
    }
}
