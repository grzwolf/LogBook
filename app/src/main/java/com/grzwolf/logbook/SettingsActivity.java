package com.grzwolf.logbook;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.SpannableString;
import android.util.Log;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import static android.widget.Toast.*;

public class SettingsActivity extends AppCompatActivity {

    // make volatile data static: https://stackoverflow.com/questions/2002288/static-way-to-get-context-in-android
    private static Context context;
    private static AppCompatActivity activity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // apply theme locally to settings; must be called before super.onCreate
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        if ( sharedPref.getBoolean("darkMode",false) ) {
            setTheme(R.style.ThemeOverlay_AppCompat_Dark);
        } else {
            setTheme(R.style.ThemeOverlay_AppCompat_Light);
        }

        super.onCreate(savedInstanceState);

        // make volatile data static
        SettingsActivity.context = getApplicationContext();
        SettingsActivity.activity = this;

        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Settings menu top left back button detection
        if ( item.getItemId() == android.R.id.home ) {
            // since we know, that super.onBackPressed() calls MainActivity onResume(), we tell it with this flag to restart MainActivity
            MainActivity.reReadAppFileData = true;
            // we mimic the same behaviour, as if the Android back button were clicked
            super.onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    // Android back button detection
    @Override
    public void onBackPressed() {
        MainActivity.reReadAppFileData = true;
        super.onBackPressed();
    }

    // make volatile data static
    public static Context getAppContext() {
        return SettingsActivity.context;
    }
    public static Context getActivity() {
        return SettingsActivity.activity;
    }

    // read data from file and handle them as UTF-8 text
    private static String readBackupData() {
        String fileText = "";
        try {
            String downloadDir = "" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String appName = SettingsActivity.context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
            File file = new File(downloadDir, appName + ".txt");
            byte[] byteArray = new byte[(int)file.length()];
            FileInputStream fis = new FileInputStream(file);
            int oneChar;
            int i = 0;
            while ((oneChar = fis.read()) != -1) {
                byteArray[i++] = (byte) oneChar;
            }
            fis.close();
            fileText = new String( byteArray, "UTF-8" );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileText;
    }
    // read data from file and handle them as UTF-8 text
    private static String readAppFileData() {
        String fileText = "";
        try {
            Context context = getAppContext(); // make context static
            String storagePath = context.getExternalFilesDir(null).getAbsolutePath();
            String appName = context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
            File file = new File(storagePath, appName + ".txt");
            byte[] byteArray = new byte[(int)file.length()];
            FileInputStream fis = new FileInputStream(file);
            int oneChar;
            int i = 0;
            while ((oneChar = fis.read()) != -1) {
                byteArray[i++] = (byte) oneChar;
            }
            fis.close();
            fileText = new String( byteArray, "UTF-8" );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileText;
    }
    // write UTF8 text to file
    private static boolean writeAppFileData(String text) {
        boolean retVal = true;
        Context context = getAppContext(); // make context static
        String storagePath = context.getExternalFilesDir(null).getAbsolutePath();
        String appName = context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
        File file = new File(storagePath, appName + ".txt");
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            retVal = false;
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(text.getBytes("UTF8"));
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            retVal = false;
        }
        return retVal;
    }
    // write UTF8 text to file
    private static boolean writeFile(File file, String text) {
        boolean retVal = true;
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            retVal = false;
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(text.getBytes("UTF8"));
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            retVal = false;
        }
        return retVal;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            // show backup data info
            final Preference backupInfo = (Preference) findPreference("BackupInfo");
            String downloadDir = "" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String appName = SettingsActivity.context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
            File file = new File(downloadDir, appName + ".txt");
            if ( file.exists() ) {
                Date lastModDate = new Date(file.lastModified());
                backupInfo.setSummary(file.toString() + getString(R.string.set1)+ lastModDate.toString());
            } else {
                backupInfo.setSummary(getString(R.string.set2));
            }

            // tricky fake buttons in preferences: https://stackoverflow.com/questions/2697233/how-to-add-a-button-to-preferencescreen
            // action after backup
            Preference backup = (Preference) findPreference("Backup");
            backup.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    // does it make sense #1
                    String text = readAppFileData();
                    if ( text.length() == 0 ) {
                        makeText(SettingsActivity.getActivity(), R.string.set3, LENGTH_LONG).show();
                        return true;
                    }
                    // does it make sense #2
                    boolean dataAvailable = false;
                    for ( int i = 0; i<MainActivity.DataStore.dataSection.size(); i++ ) {
                        if ( MainActivity.DataStore.dataSection.get(i).length() > 0 ) {
                            dataAvailable = true;
                        }
                    }
                    if ( !dataAvailable ) {
                        makeText(SettingsActivity.getActivity(), R.string.set4, LENGTH_LONG).show();
                        return true;
                    }
                    // ... are you sure ...
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(R.string.set5);
                    String downloadDir = "" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    String appName = SettingsActivity.context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
                    File file = new File(downloadDir, appName + ".txt");
                    String message = getString(R.string.set6) + file.toString();
                    if ( file.exists() ) {
                        message += getString(R.string.set7);
                    } else {
                        message += getString(R.string.set8);
                    }
                    builder.setMessage(message);
                    // YES
                    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            String text = readAppFileData();
                            String downloadDir = "" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                            String appName = SettingsActivity.context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
                            File file = new File(downloadDir, appName + ".txt");
                            boolean retVal = writeFile(file, text);
                            String msg = retVal ? getString(R.string.set9) : getString(R.string.set10);
                            makeText(SettingsActivity.getActivity(), file.toString() + "\n\n" + msg, LENGTH_LONG).show();
                            if ( retVal ) {
                                Date lastModDate = new Date(file.lastModified());
                                backupInfo.setSummary(file.toString() + getString(R.string.set11)+ lastModDate.toString());
                            }
                            dialog.dismiss();
                            return;
                        }
                    });
                    // NO
                    builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            return;
                        }
                    });
                    AlertDialog alert = builder.create();
                    alert.show();
                    // title and message of AlertDialog.Builder are otherwise invisible in dark theme
                    int textViewId = builder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
                    TextView tv = (TextView) alert.findViewById(textViewId);
                    tv.setTextColor(Color.BLACK);
                    textViewId = builder.getContext().getResources().getIdentifier("android:id/message", null, null);
                    tv = (TextView) alert.findViewById(textViewId);
                    tv.setTextColor(Color.BLACK);

                    return true;
                }
                });
            // action after restore
            Preference restore = (Preference) findPreference("Restore");
            restore.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    // does it make sense
                    String downloadDir = "" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    String appName = SettingsActivity.context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
                    File file = new File(downloadDir, appName + ".txt");
                    if ( !file.exists() ) {
                        makeText(SettingsActivity.getActivity(), R.string.set12, LENGTH_LONG).show();
                        return true;
                    }
                    // check both file dates
                    Date lastModDateBck = new Date(file.lastModified());
                    String storagePathApp = context.getExternalFilesDir(null).getAbsolutePath();
                    File fileApp = new File(storagePathApp, appName + ".txt");
                    String appFileInfo = getString(R.string.not_existing);
                    if ( fileApp.exists() ) {
                        Date lastModDateApp = new Date(fileApp.lastModified());
                        appFileInfo = lastModDateApp.toString();
                    }
                    // ... are you sure ...
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(R.string.set13);
                    builder.setMessage(getString(R.string.set14) + appFileInfo + getString(R.string.set15) + lastModDateBck + getString(R.string.set16));
                    // YES
                    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            String downloadDir = "" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                            String appName = SettingsActivity.context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
                            File file = new File(downloadDir, appName + ".txt");
                            if ( file.exists() ) {
                                String text = readBackupData();
                                boolean retVal = writeAppFileData(text);
                                makeText(SettingsActivity.getActivity(), getString(R.string.set17) + Boolean.toString(retVal), LENGTH_LONG).show();
                            } else {
                                makeText(SettingsActivity.getActivity(), R.string.set18, LENGTH_LONG).show();
                            }
                            dialog.dismiss();
                            return;
                        }
                    });
                    // NO
                    builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            return;
                        }
                    });
                    AlertDialog alert = builder.create();
                    alert.show();
                    // title and message of AlertDialog.Builder are otherwise invisible in dark theme
                    int textViewId = builder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
                    TextView tv = (TextView) alert.findViewById(textViewId);
                    tv.setTextColor(Color.BLACK);
                    textViewId = builder.getContext().getResources().getIdentifier("android:id/message", null, null);
                    tv = (TextView) alert.findViewById(textViewId);
                    tv.setTextColor(Color.BLACK);

                    return true;
                }
            });
        }
    }
}