package com.michaelchen.awakelog;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by akn320 on 4/8/15.
 */
public class TLSTestTask extends AsyncTask<Void, Void, Double> {

    protected double beginLevel;
    protected MainActivity activity;

    public TLSTestTask(double beginLevel, MainActivity activity) {
        this.beginLevel = beginLevel;
        this.activity = activity;
    }

    @Override
    protected Double doInBackground(Void... params) {
        URL url = null;
        HttpsURLConnection urlConnection = null;
        InputStream in = null;

        System.setProperty("http.keepAlive", "false");
        try {
            url = new URL("https://www.eecs.berkeley.edu");
        }
        catch (MalformedURLException e) {
            assert false;
        }

        double time = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            Log.d("HTTP Test", i + "");
            try {
                urlConnection = (HttpsURLConnection) url.openConnection();
                urlConnection.getInputStream();
                urlConnection.disconnect();
            } catch (IOException e) {
                assert false;
            }

        }
        double elapsed = System.currentTimeMillis() - time;
        return elapsed;
    }

    @Override
    protected void onPostExecute(Double result) {
        Log.d("TLS Test", "finished");
        double diff = beginLevel - activity.getBatteryLevel();

        TextView tv = (TextView) activity.findViewById(R.id.textView4);
        Log.d("TLS Test", "energy: " + diff + " time: " + result);
        tv.setText("energy: " + diff + " time: " + result);
    }
}
