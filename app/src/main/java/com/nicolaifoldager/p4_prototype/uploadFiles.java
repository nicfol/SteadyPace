package com.nicolaifoldager.p4_prototype;

import android.os.AsyncTask;
import android.util.Log;

public class uploadFiles extends AsyncTask<String, String, String> {

    private boolean running = true;
    private int cnt = 0;

    @Override
    protected void onCancelled() {
        running = false;
        super.onCancelled();
    }

    @Override
    protected String doInBackground(String... filename ) {
        Media media = new Media();

        while(running) {
            media.uploadFTP(filename[0]);
            Log.e("uploadFiles/doInBackgro","count: " + cnt);
            cnt++;
            if (cnt > 50)
                running = false;
        }

        return null;
    }

    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);
    }
}
