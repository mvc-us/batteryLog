package com.michaelchen.awakelog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Date;
import java.util.Map;


public class MainActivity extends ActionBarActivity {

    private PowerManager.WakeLock pWakeLock;
    private int lastBatteryLevel;

    private BroadcastReceiver mbcr = new BroadcastReceiver()
    {
        //onReceive method will receive updates
        public void onReceive(Context c, Intent i)
        {
            //initially level has 0 value
            //after getting update from broadcast receiver
            //it will change and give battery status
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            long time = new Date().getTime();
            MainActivity.this.updateBatteryLevel(level);
//            ProgressBar pb=(ProgressBar)findViewById(R.id.progressBar1);

            SharedPreferences sharedPref = MainActivity.this.getSharedPreferences(
                    getString(R.string.preference_file_key), Context.MODE_PRIVATE);
            SharedPreferences.Editor e = sharedPref.edit();
            long initTime = sharedPref.getLong(getString(R.string.init_datetime_key), -1);
            if (initTime == -1) {
                e.putLong(getString(R.string.init_datetime_key), time);
                e.putInt(getString(R.string.init_battery_key), level);
            }

            e.putLong(getString(R.string.prev_datetime_key), time);
            e.putInt(getString(R.string.prev_battery_key), level);
            e.apply();
            e.commit();

            Map<String, ?> m =  sharedPref.getAll();
            Log.d("BroadcastReceiver", m.get(getString(R.string.prev_battery_key)).toString());

            MainActivity.this.updateBatteryScreen();
        }
    };

    public double getBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return ((double) level) / ((double) scale);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
//        Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
        registerReceiver(mbcr, ifilter);
    }

    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mbcr);
    }

    protected void updateBatteryLevel(int newLevel) {
        lastBatteryLevel = newLevel;
    }

    protected boolean updateBatteryScreen() {
        int currLevel = lastBatteryLevel;
        SharedPreferences sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        TextView tv=(TextView)findViewById(R.id.textView);
        tv.setText("Current battery level: "+Integer.toString(currLevel)+"%");

        long time = new Date().getTime();

        long initTime = sharedPref.getLong(getString(R.string.init_datetime_key), -1);
        int initLevel = sharedPref.getInt(getString(R.string.init_battery_key), -1);

        TextView tv3=(TextView)findViewById(R.id.textView3);
        long initTimeDiffSec = (time - initTime)/1000;
        initTimeDiffSec = initTimeDiffSec > 0 ? initTimeDiffSec : 1; //to deal with divide by zero error, but screen might be inaccurate then
        double longTermSlope = ((double) currLevel - initLevel)/(3600*initTimeDiffSec); // percent/hr
        tv3.setText("Long Term Trajectory: " + Integer.toString(initLevel)+" to "+
                Integer.toString(currLevel) + " in " + Long.toString(initTimeDiffSec) + "s, slope: " +
                Double.toString(longTermSlope) + "%/hr");

        long prevTime = sharedPref.getLong(getString(R.string.prev_datetime_key), -1);
        int prevLevel = sharedPref.getInt(getString(R.string.init_battery_key), -1);


        TextView tv2 = (TextView) findViewById(R.id.textView2);
        long prevTimeDiffSec = (time - prevTime) / 1000;
        prevTimeDiffSec = prevTimeDiffSec > 0 ? prevTimeDiffSec : 1; //to deal with divide by zero error, but screen might be inaccurate then
        double shortTermSlope = ((double) currLevel - prevLevel)/(3600*prevTimeDiffSec); // percent/hr
        
        tv2.setText("Short Term Trajectory: " + Integer.toString(prevLevel) + " to " +
                Integer.toString(currLevel) + " in " + Long.toString(prevTimeDiffSec) + "s, slope: " +
                Double.toString(shortTermSlope) + "%/hr");

        return prevTime != -1;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onCheckboxClicked(View view) {
        boolean checked = ((CheckBox) view).isChecked();
        switch(view.getId()) {
            case R.id.checkbox_screen_on:
                if (checked)
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                break;

            case R.id.checkbox_wakelock:
                if (checked) {
                    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                    PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                            "MyWakelockTag");
                    wakeLock.acquire();
                    pWakeLock = wakeLock;
                } else {
                    pWakeLock.release();
                }
                break;

            case R.id.checkbox_battery:
                if (!checked) {
                    SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(
                            getString(R.string.preference_file_key), Context.MODE_PRIVATE);
                    sharedPref.edit().clear().commit();
                } else {
                    updateBatteryScreen();
                }
                break;
        }
    }

    public void onButtonClicked(View view) {
        Log.d("Button", "Clicked");
        switch(view.getId()) {
            case R.id.button:
                updateBatteryScreen();
                break;
            case R.id.button2:
                //TLS Test
                Log.d("TLS Test", "starting");
                new TLSTestTask(getBatteryLevel(), this).execute();
                break;
            case R.id.button3:
                //HTTP Test
                Log.d("HTTP Test", "starting");
                new HTTPTestTask(getBatteryLevel(), this).execute();
                break;
        }
    }
}
