package com.nicolaifoldager.p4_prototype;

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

        try {
//            loadPdPatch();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }

    }

    /**
     * Loads the PD patch so the app can communicate with it.
     *
     * @throws IOException
     */
    private void loadPdPatch() throws Exception {

        File dir = getFilesDir();
        System.out.println(dir);
        IoUtils.extractZipResource(getResources().openRawResource(R.raw.pdpatch), dir, true);
        File patchFile = new File(dir, "pdpatch.pd");
        PdBase.openPatch(patchFile.getAbsolutePath());

        floatToPd("Volume", 0.0f);

    }

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
