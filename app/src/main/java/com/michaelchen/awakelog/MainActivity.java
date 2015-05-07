package com.michaelchen.awakelog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;


public class MainActivity extends ActionBarActivity {

    private PowerManager.WakeLock pWakeLock;
    private int lastBatteryLevel;

    public static final String EXTERN_FILE_NAME = "powerUsage.log";
    private File file;
    private boolean writeToLog = false;
    public static final String ALERT_TITLE = "Identifier in Log";
    public static final String LOG_DIVIDER = ">>>>>>>>>>";
    public static final String LOG_END_DIVIDER = "<<<<<<<<<<";
    public static final String ALERT_MESSAGE = "Used to delineate between logs in file";
    public static final String EXTERN_SITES_FILE = "sites.txt";
    public static final String LOG_TAG_KEY = "logKey";
    public static final String EXTERN_DOWNLOADS_FILE = "download_malware.txt";

    private static final String HANDLER_THREAD = "battery_handler_thread";
    private int iterationCount = 0; // used for httptask to update UI
    private int numTests = 0;
    private int numTestsDone = 0;
    private String currentTag = "";
    private Class<?> currentTask;

    private BroadcastReceiver mbcr = new BroadcastReceiver()
    {
        //onReceive method will receive updates
        public void onReceive(Context c, Intent i)
        {
            //initially level has 0 value
            //after getting update from broadcast receiver
            //it will change and give battery status
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);

            //These calls will only work with certain devices
            // See source.android.com/devices/tech/power/index.html#nexus-devices
            BatteryManager b = new BatteryManager();
            long energy = b.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
            int percent = b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            Log.d("Percent", ""+percent);
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

                if (MainActivity.this.writeToLog) {
                    DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
                    String dateAsString = df.format(d);
                    String write = dateAsString + "," + Long.toString(time) + "," + Integer.toString(level);
                    if (energy != Long.MIN_VALUE) {
                        // Using device that supports precise energy measurements, see above
                        write += "," + Long.toString(energy);
                    }
                    MainActivity.this.appendStorage(write);
                }
            }

            e.putLong(getString(R.string.prev_datetime_key), time);
            e.putInt(getString(R.string.prev_battery_key), level);
            e.apply();
            e.commit();

            Map<String, ?> m =  sharedPref.getAll();
            Log.d("BroadcastReceiver", m.get(getString(R.string.prev_battery_key)).toString());

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.updateBatteryScreen();
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
//        Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);

        //create new handler to handle battery changes
        HandlerThread handlerThread = new HandlerThread(HANDLER_THREAD);
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        Handler handler = new Handler(looper);
        registerReceiver(mbcr, ifilter, null, handler);
//        registerReceiver(mbcr, ifilter);

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
                        SharedPreferences sharedPref = MainActivity.this.getSharedPreferences(
                                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
                        SharedPreferences.Editor e = sharedPref.edit();
                        e.putString(LOG_TAG_KEY, value.toString());
                        e.apply();
                        e.commit();
                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Nothing
            }
        }).show();
    }

    public void onButtonClicked(View view) {
        switch(view.getId()) {
            case R.id.button:
                updateBatteryScreen();
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

    public void startSimulation(View v) {
        final EditText input = new EditText(this);
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(ALERT_TITLE)
                .setMessage(ALERT_MESSAGE)
                .setView(input)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Editable value = input.getText();
                        writeToLog = true;
                        SharedPreferences sharedPref = MainActivity.this.getSharedPreferences(
                                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
                        SharedPreferences.Editor e = sharedPref.edit();
                        e.putString(LOG_TAG_KEY, value.toString());
                        e.apply();
                        e.commit();
                        MainActivity.this.createActionsDialog(value.toString()).show();
                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Nothing
            }
        }).show();
    }

    void incrementCountAndUi(final String flag) {
        iterationCount++;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateSimulationResultView(flag + ": " + Integer.toString(iterationCount) + " iterations");
            }
        });
    }

    public static String inputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder total = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            total.append(line);
        }
        return total.toString();
    }

    private void updateSimulationResultView(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView simText = (TextView) findViewById(R.id.sim_result);
                simText.setText(s);
            }
        });
    }

    private void startWebSimulation(String tag) {

        BackgroundTask task = new BackgroundTask();
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);
    }

    private void startSimulationWithCount(final String tag, final BackgroundTask task) {

        final NumberPicker numberPicker = new NumberPicker(this);
        numberPicker.setMaxValue(5);
        numberPicker.setMinValue(0);
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Number of Tests")
                .setMessage("Number of times test should be run automatically")
                .setView(numberPicker)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        numTests = numberPicker.getValue();
                        numTestsDone = 0;
                        currentTag = tag;
                        iterationCount = 0;
                        currentTask = task.getClass();
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);
                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Nothing
            }
        }).show();

    }

    void onTaskFinished () {
        numTestsDone++;
        MainActivity.this.appendStorage(MainActivity.LOG_DIVIDER + " " + ":" + currentTag);
        if (numTestsDone < numTests) {
            try {
                currentTag += Integer.toString(numTestsDone);
                Constructor<?> ctor = currentTask.getConstructor(MainActivity.class);
                BackgroundTask task = (BackgroundTask) ctor.newInstance(MainActivity.this);
//                BackgroundTask task = new HTTPFixTimeTask();
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, currentTag);
            } catch (Exception e) {
                Log.e("Restart task", "failed load task", e);
            }
        }
        final int testsDone = numTestsDone;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView t = (TextView) findViewById(R.id.textIterations);
                t.setText(getString(R.string.iterations) + Integer.toString(testsDone));
            }
        });
    }

    private void startHttpTask(String tag) {
        iterationCount = 0;
        HTTPTask task = new HTTPTask();
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);
    }

    private void startDownloadsSimulation(String tag) {
        DownloadTask task = new DownloadTask();
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);
    }

    private Dialog createActionsDialog(final String prev) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title)
            .setItems(R.array.test_suites, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    String[] actions = getResources().getStringArray(R.array.test_suites);
                    String action = actions[which];
                    SharedPreferences sharedPref = MainActivity.this.getSharedPreferences(
                            getString(R.string.preference_file_key), Context.MODE_PRIVATE);
                    String tag = sharedPref.getString(LOG_TAG_KEY, LOG_TAG_KEY);
                    MainActivity.this.appendStorage(MainActivity.LOG_DIVIDER + " " + prev + ":" + action);
                    sharedPref.edit().clear().commit();
                    MainActivity.this.updateBatteryScreen();
                    switch (which) {
                        case 0:
                            MainActivity.this.startWebSimulation(tag);
                            break;
                        case 1:
                            MainActivity.this.startSimulationWithCount(tag, new HTTPTask());
                            break;
                        case 2:
                            MainActivity.this.startDownloadsSimulation(tag);
                            break;
                        case 3:
                            MainActivity.this.startSimulationWithCount(tag, new HTTPFixTimeTask());
                            break;
                        case 4:
                            MainActivity.this.startSimulationWithCount("Https comparison", new HTTPComparisonTask());
                    }
                }
            });
        return builder.create();
    }

    private class BackgroundTask extends AsyncTask<String, Void, Boolean> {

        public BackgroundTask() {
            super();
        }

        @Override
        protected Boolean doInBackground(String...flags) {

            double time = System.currentTimeMillis();
            String item = flags.length > 0 ? flags[0] : "";
            boolean ret = runTask(item);
            if (!ret) {
                if (flags.length > 0) {
                    MainActivity.this.updateSimulationResultView(flags[0] + ": " + " failed");
                }
                return false;
            }

            if (flags.length > 0) {
                MainActivity.this.appendStorage(MainActivity.LOG_END_DIVIDER + flags[0]);
                double elapsed = System.currentTimeMillis() - time;
                double minElapsed = elapsed*1.66667e-5;
                int min = (int) minElapsed;
                String classString = this.getClass().toString();
                MainActivity.this.updateSimulationResultView(flags[0] + " " + classString + ": " + Integer.toString(min) + " min");
            }


            return true;
        }

        protected boolean runTask(String item) {
            // Note: Override this function with test you want to run
            for (int j=0; j < 3; j++) {
                try {
                    File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXTERN_SITES_FILE);
                    StringBuilder text = new StringBuilder();
                    BufferedReader br = new BufferedReader(new FileReader(f));
                    String line;
                    while ((line = br.readLine()) != null) {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(line));
                        startActivity(i);
                        Thread.sleep(20000);
                    }
                } catch (Exception e) {
                    Log.d("BackGroundTask", "Website Load Failed");
                    return false;
                }
            }
            return true;
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(Boolean result) {
        }
    }

    private class DownloadTask extends BackgroundTask {
        protected boolean runTask(String item) {
            // Note: Override this function with test you want to run
            for (int j=0; j < 3; j++) {
                try {
                    File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXTERN_DOWNLOADS_FILE);
                    StringBuilder text = new StringBuilder();
                    BufferedReader br = new BufferedReader(new FileReader(f));
                    String line;
                    while ((line = br.readLine()) != null) {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(line));
                        startActivity(i);
                        Thread.sleep(20000);
                    }
                } catch (Exception e) {
                    Log.d("BackGroundTask", "Website Load Failed");
                    return false;
                }
            }
            return true;
        }
    }

    private class HTTPTask extends BackgroundTask {

        public HTTPTask() {
            super();
        }

        public final int TIMEOUT = 5000;
        public final int NUM_ITERATIONS = 400;
//        public final int NUM_ITERATIONS = 1;
        @Override
        protected boolean runTask(String item) {
            URL url = null;
            HttpURLConnection urlConnection = null;

            System.setProperty("http.keepAlive", "false");
            int count = 0;
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                try {
                    File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), MainActivity.EXTERN_SITES_FILE);
                    StringBuilder text = new StringBuilder();
                    BufferedReader br = new BufferedReader(new FileReader(f));
                    String line;
                    while ((line = br.readLine()) != null) {
                        try {
                            url = new URL(line);
                            urlConnection = (HttpURLConnection) url.openConnection();
                            urlConnection.setConnectTimeout(TIMEOUT);
                            urlConnection.setReadTimeout(TIMEOUT);
                            String s = MainActivity.inputStreamToString(urlConnection.getInputStream());
                            urlConnection.disconnect();
                            count++;
                            MainActivity.this.incrementCountAndUi(item + Integer.toString(s.length()));

                        } catch (SocketTimeoutException e) {
                            Log.d("HTTPTask", "Site not responding");
                        } catch (IOException e) {
                            Log.d("HTTPTask", "Site connection failed");
                        }
                    }
                } catch (IOException e) {
                    Log.d("HTTPTask", "File/HTTP IO Failed");
                    return false;
                }

            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            MainActivity.this.onTaskFinished();
        }

    }

    private class HTTPFixTimeTask extends BackgroundTask {

        public HTTPFixTimeTask() {
            super();
        }

        public final int TIMEOUT = 3000;
        public final int NUM_ITERATIONS = 400;
        //        public final int NUM_ITERATIONS = 1;
        @Override
        protected boolean runTask(String item) {
            URL url = null;
            HttpURLConnection urlConnection = null;

            System.setProperty("http.keepAlive", "false");
            int count = 0;
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                try {
                    File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), MainActivity.EXTERN_SITES_FILE);
                    StringBuilder text = new StringBuilder();
                    BufferedReader br = new BufferedReader(new FileReader(f));
                    String line;
                    while ((line = br.readLine()) != null) {
                        try {
                            long startTime = System.currentTimeMillis();
                            url = new URL(line);
                            urlConnection = (HttpURLConnection) url.openConnection();
                            urlConnection.setConnectTimeout(TIMEOUT);
                            urlConnection.setReadTimeout(TIMEOUT);
                            String s = MainActivity.inputStreamToString(urlConnection.getInputStream());
                            urlConnection.disconnect();
                            long endTime = System.currentTimeMillis();
                            long timeDiff = endTime - startTime;
                            Log.d("FixTimeTask", "timeDiff: " + timeDiff);
                            if (timeDiff < TIMEOUT) {
                                Thread.sleep(TIMEOUT - timeDiff);
                            }
                            count++;
                            MainActivity.this.incrementCountAndUi(item + Integer.toString(s.length()));

                        } catch (SocketTimeoutException e) {
                            Log.d("HTTPTask", "Site not responding");
                        } catch (IOException e) {
                            Log.d("HTTPTask", "Site connection failed");
                        } catch (InterruptedException e) {
                            Log.d("HTTPFixTimeTask", "Thread sleep interrupted");
                        }
                    }
                } catch (IOException e) {
                    Log.d("HTTPTask", "File/HTTP IO Failed");
                    return false;
                }

            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            MainActivity.this.onTaskFinished();
        }

    }

    private class HTTPComparisonTask extends BackgroundTask {

        public HTTPComparisonTask() {
            super();
        }

        public static final int ITERTIONS = 1000;
        double tlsTest(String rest) {
            URL url = null;
            HttpsURLConnection urlConnection = null;
            InputStream in = null;

            System.setProperty("http.keepAlive", "false");
            try {
                url = new URL("https://" + rest);
            }
            catch (MalformedURLException e) {
                assert false;
            }

            double time = System.currentTimeMillis();
            for (int i = 0; i < ITERTIONS; i++) {
                try {
                    urlConnection = (HttpsURLConnection) url.openConnection();
                    Reader reader = new InputStreamReader(urlConnection.getInputStream());
                    read(reader);
                    urlConnection.disconnect();
                } catch (IOException e) {
                    assert false;
                }

            }
            double elapsed = System.currentTimeMillis() - time;
            return elapsed;
        }

        double httpTest(String rest) {
            URL url = null;
            HttpURLConnection urlConnection = null;
            InputStream in = null;

            System.setProperty("http.keepAlive", "false");
            try {
                url = new URL("http://" + rest);
            }
            catch (MalformedURLException e) {
                assert false;
            }

            double time = System.currentTimeMillis();
            for (int i = 0; i < ITERTIONS; i++) {
                try {
                    urlConnection = (HttpURLConnection) url.openConnection();
                    Reader reader = new InputStreamReader(urlConnection.getInputStream());
                    read(reader);
                    urlConnection.disconnect();
                } catch (IOException e) {
                    assert false;
                }

            }
            double elapsed = System.currentTimeMillis() - time;
            return elapsed;
        }

        public void read(Reader reader) {
            try {
                while (true) {
                    int ch = reader.read();
                    if (ch==-1) {
                        break;
                    }
                    // System.out.print((char)ch);
                }
            } catch (IOException e) {

            }

        }

        protected boolean runTask(String item) {
            String addr = "secure-woodland-5624.herokuapp.com/data?amount=1024";
            // String addr = "elections.asuc.org";
            System.out.println("tls: " + tlsTest(addr));
            System.out.println("http: " + httpTest(addr));
            return true;
        }
    }

}
