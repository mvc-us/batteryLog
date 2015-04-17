package com.michaelchen.awakelog;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;


public class MainActivity extends ActionBarActivity {

    private PowerManager.WakeLock pWakeLock;
    private int lastBatteryLevel;

    public static final String EXTERN_FILE_NAME = "powerUsage.log";
    private File file;
    private boolean writeToLog = false;
    public static final String ALERT_TITLE = "Identifier in Log";
    public static final String LOG_DIVIDER = ">>>>>>>>>>";
    public static final String ALERT_MESSAGE = "Used to divide between logs in file";

    private BroadcastReceiver mbcr = new BroadcastReceiver()
    {
        //onReceive method will receive updates
        public void onReceive(Context c, Intent i)
        {
            //initially level has 0 value
            //after getting update from broadcast receiver
            //it will change and give battery status
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            Date d = new Date();
            long time = d.getTime();
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

            if (level > 0) {
                e.putLong(getString(R.string.time_log_key) + ":" + Integer.toString(level), time);
                DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
                String dateAsString = df.format(d);
                String write = dateAsString + "," + Long.toString(time) + "," + Integer.toString(level);
                if (MainActivity.this.writeToLog) {
                    MainActivity.this.appendStorage(write);
                }
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
        TextView t = (TextView)findViewById(R.id.textViewHist);
        t.setMovementMethod(new ScrollingMovementMethod());
        file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXTERN_FILE_NAME);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                Log.d("File IO", "failed to make file");
            }
        }
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
        double longTermSlope = ((double) 3600*(currLevel - initLevel))/(initTimeDiffSec); // percent/hr
        tv3.setText("Long Term Trajectory: " + Integer.toString(initLevel)+" to "+
                Integer.toString(currLevel) + " in " + Long.toString(initTimeDiffSec) + "s, slope: " +
                Double.toString(longTermSlope) + "%/hr");

        long prevTime = sharedPref.getLong(getString(R.string.prev_datetime_key), -1);
        int prevLevel = sharedPref.getInt(getString(R.string.init_battery_key), -1);


        TextView tv2 = (TextView) findViewById(R.id.textView2);
        long prevTimeDiffSec = (time - prevTime) / 1000;
        prevTimeDiffSec = prevTimeDiffSec > 0 ? prevTimeDiffSec : 1; //to deal with divide by zero error, but screen might be inaccurate then
        double shortTermSlope = ((double) 3600*(currLevel - prevLevel))/(prevTimeDiffSec); // percent/hr
        
        tv2.setText("Short Term Trajectory: " + Integer.toString(prevLevel) + " to " +
                Integer.toString(currLevel) + " in " + Long.toString(prevTimeDiffSec) + "s, slope: " +
                Double.toString(shortTermSlope) + "%/hr");
        updateBatteryList();

        return prevTime != -1;
    }

    private String batteryHistory() {
        SharedPreferences sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        String complete = "Battery History\n";
        long initTime = sharedPref.getLong(getString(R.string.init_datetime_key), -1);
        for (int i = 100; i > 0; i--) {
            long time = sharedPref.getLong(getString(R.string.time_log_key) + ":" + Integer.toString(i), -1);
            long initTimeDiffSec = (time - initTime)/1000;
            if (time != -1) {
                complete += Integer.toString(i) + "%: " + Long.toString(initTimeDiffSec) + "s\n";
            }
        }
        return complete;
    }

    private void updateBatteryList() {
        TextView t = (TextView)findViewById(R.id.textViewHist);
        t.setText(batteryHistory());
        t.setTextIsSelectable(true);
        t.setKeyListener(null);
        t.setFocusable(true);
    }

    public void copyHistory(View view) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Battery History", batteryHistory());
        clipboard.setPrimaryClip(clip);
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
                    writeToLog = false;
                } else {
                    updateBatteryScreen();
                    startLog();
                }
                break;
        }
    }

    private void startLog() {
        final EditText input = new EditText(this);
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(ALERT_TITLE)
                .setMessage(ALERT_MESSAGE)
                .setView(input)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Editable value = input.getText();
                        MainActivity.this.appendStorage(MainActivity.LOG_DIVIDER + " " + value.toString());
                        writeToLog = true;
                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Nothing
            }
        }).show();
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

    protected void appendStorage(String s) {
        String text = s;
        try {
            BufferedWriter buf = new BufferedWriter(new FileWriter(file, true));
            buf.append(text);
            buf.newLine();
            buf.close();
        } catch (IOException e) {
            Log.d("File IO", "failed to append to log file");
        }
    }
}
