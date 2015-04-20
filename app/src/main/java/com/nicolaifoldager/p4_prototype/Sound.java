package com.nicolaifoldager.p4_prototype;

import android.content.Context;

import org.puredata.android.io.AudioParameters;
import org.puredata.android.io.PdAudio;
import org.puredata.android.utils.PdUiDispatcher;
import org.puredata.core.PdBase;
import org.puredata.core.utils.IoUtils;

import java.io.File;
import java.io.IOException;

public class Sound extends MainActivity {

    private PdUiDispatcher dispatcher;

    /**
     * Initializes PD Library and prepares the audio outlet.
     *
     * @throws IOException
     */
    public void init_pd() throws IOException {

        // Configure the audio glue
        int sampleRate = AudioParameters.suggestSampleRate();
        PdAudio.initAudio(sampleRate, 0, 2, 8, true);

        // Create and install the dispatcher
        dispatcher = new PdUiDispatcher();
        PdBase.setReceiver( dispatcher );

    }

    /**
     * Loads the PD patch so the app can communicate with it.
     *
     * @throws IOException
    */
    void loadPdPatch() throws Exception {

        File dir = getFilesDir();

        IoUtils.extractZipResource(getResources().openRawResource(R.raw.pdpatch), dir, true);
        File patchFile = new File(dir, "pdpatch.pd");
        PdBase.openPatch(patchFile.getAbsolutePath());

        floatToPd("Volume", 0.0f);

    }

    /**
     * Sends a float to the PD patch previously loaded in loadPdPatch method
     *
     * @param receiver  The name of the receiver within the pd patch in a string
     * @param value     The value to send to the receiver as a float
     */
    public void floatToPd(String receiver, Float value) {

        PdBase.sendFloat(receiver, value);

    }

    public void DiscSound() {

        //---

    }

    public void ContSound() {

        //---

    }

}
