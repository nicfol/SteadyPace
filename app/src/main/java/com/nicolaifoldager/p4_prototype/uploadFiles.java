package com.nicolaifoldager.p4_prototype;

import android.os.AsyncTask;

public class uploadFiles extends AsyncTask<String, String, String> {

    @Override
    protected String doInBackground(String... filename ) {

        Media media = new Media();
        if(media.uploadFTP(filename[0])) {
            return null;
        } else {
            doInBackground(filename[0]);
            return null;
        }

    }
}
