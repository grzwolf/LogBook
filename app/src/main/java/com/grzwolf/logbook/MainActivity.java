package com.grzwolf.logbook;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.DisplayMetrics;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

/* Changelog
   1.20.3 share "long press" selection: line, day, folder via sharing
   1.20.2 only copy selected text to clipboard, if its length is > 0
   1.20.1 allow empty input if timestamp is added
   1.20.0 folders with property timestamp instead of global setting
   1.19.1 typo fix
   1.19.0 2nd language German
   1.18   final
*/
public class MainActivity extends AppCompatActivity {

    // edit control inside of the AlertDialog.Builder
    EditText inputAlert = null;
    // toggle keyboard type for input alert
    boolean inputAlertKbdToggle = false;
    String inputAlertText = "";
    
    // data store in RAM: LogBook.txt (new on top) <--> DataStore (new on top) <--> EditMain (new OR old on top)
    public static final int SECTIONS_COUNT = 10;
    static class DataStore
    {
        public static void clear()
        {
            namesSection.clear();
            dataSection.clear();
            kbdSection.clear();
            timeSection.clear();
            selectedSection = 0;
        }
        public static List<String> namesSection = new ArrayList<String>();
        public static int selectedSection = 0;
        public static List<String> dataSection = new ArrayList<String>();
        public static List<Integer> kbdSection = new ArrayList<Integer>();
        public static List<Integer> timeSection = new ArrayList<Integer>();
    }
    // timestamp
    class TIMESTAMP {
        static final int
        OFF = 0,
        HHMM = 1,
        HHMMSS = 2;
    }

    // the central edit control in the main activity
    EditText editMain = null;          // DataStore renderer
    boolean editMainEditMode = false;  // edit mode
    String editMainSelectedText = "";  // memorize last selected text
    String editMainSelectedDay = "";  // memorize surrounding day
    boolean editMainDirty = false;     // edit dirty flag
    int editMainSelStart = -1;         // cursor position
    ScrollView editMainScroller = null;// the only way to get an EditText to scroll smoothly, is to put it inside a ScrollView

    // search function
    SearchView searchView = null;
    String searchViewQuery = "";

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    public static void verifyStoragePermissions(AppCompatActivity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    // important control elements in the main activity
    FloatingActionButton fabEdit = null;     // edit button
    FloatingActionButton fabPlus = null;     // jump to input button
    MenuItem actionEditModeMenuItem = null;  // edit button in Menu bar

    // changes made to data sometimes need a jump back to onResume(), this flag controls, how onResume() will proceed with data
    public static boolean reReadAppFileData = false;

    // timer shall show keyboard after an EditText was opened via " ! " widget OR rename file dlg: https://stackoverflow.com/questions/4597690/how-to-set-timer-in-android
    private Timer myTimer = null;
    private void TimerMethod(EditText et)          // runs in timer thread --> !! no UI thread access !!
    {
        MyTimerTask mytimertask = new MyTimerTask(et);
        this.runOnUiThread(mytimertask);
    }
    public class MyTimerTask implements Runnable   // implement Runnable with parameter: https://stackoverflow.com/questions/35521340/parameter-in-runonuithread
    {
        private final EditText et;
        public MyTimerTask(EditText et) {
            this.et = et;
        }
        @Override
        public void run() {
            if ( et != null ) {
                if ( et.isShown() ) {
                    // kill timer
                    if (myTimer != null) {
                        myTimer.cancel();
                        myTimer = null;
                    }
                    // keyboard to show for edittext via simulated touch event (brute force did not work reliably) https://stackoverflow.com/questions/5105354/how-to-show-soft-keyboard-when-edittext-is-focused
                    et.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN , 0, 0, 0));
                    et.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP , 0, 0, 0));
                }
            }
        }
    }

    // copy string to clipboard
    private void setClipboard(Context context, String text) {
        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(text);
        } else {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText(getString(R.string.main1), text);
            clipboard.setPrimaryClip(clip);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }
    @Override
    protected  void onStart() {
        super.onStart();
    }
    @Override
    protected void onStop() {
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Store our shared preference
        SharedPreferences sp = getSharedPreferences("ActivityStatus", MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean("active", false);
        ed.commit();
    }

    // activity_lifecycle.png: this method is called, even when coming back from settings via "Android Back Button"
    @Override
    protected void onResume() {

        // when returned from Settings or called by ActionBar Items: this will continue with onCreate(..) if flag reReadAppFileData is true
        // BACKGROUND: Ideally we would call onCreate directly, but its parameter 'Bundle savedInstanceState' is not accessible, so we can't call onCreate..
        //             The following SOF construct forces a new app instance because manifest indicates android:launchMode="singleInstance".
        //             Therefore the current activity is closed, a new one is created/started and we jump to onCreate.
        if ( reReadAppFileData ) {
            reReadAppFileData = false;
            // https://stackoverflow.com/questions/2482848/how-to-change-current-theme-at-runtime-in-android  Yuriy Yunikov
            /* Just put the code above after you perform changing of theme on the UI or somewhere else.
               All your activities should have method setTheme() called before onCreate(), probably in some parent activity.
               It is also a normal approach to store the theme chosen in SharedPreferences, read it and then set using setTheme() method.
            */
            TaskStackBuilder.create(this)
                    .addNextIntent(new Intent(this, MainActivity.class))
                    .addNextIntent(this.getIntent())
                    .startActivities();  // --> continues with onCreate(..)
        }

        // settings shared preferences
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        editMainEditMode = sharedPref.getBoolean("editMode", false);
//        Toast.makeText(this, "Edit Mode = " + Boolean.toString(editMainEditMode), Toast.LENGTH_LONG).show();

        // prepare editMain EditText being r/o OR editable
        editMain.setGravity(Gravity.LEFT | Gravity.TOP);
        fabEdit.hide();
        fabPlus.hide();
        if ( editMainEditMode ) {
            fabEdit.setImageResource(android.R.drawable.ic_lock_lock);
            if ( actionEditModeMenuItem != null ) {  // icon actionEditModeMenuItem shall toggle
                actionEditModeMenuItem.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_lock_lock));
            }
            editMain.setFocusableInTouchMode(true);
            editMain.setKeyListener(new EditText(getApplicationContext()).getKeyListener());
            editMain.setFocusable(true);
            editMain.setCursorVisible(true);
            editMain.requestFocus();
            // only when app starts in edit mode, the cursor would otherwise be at the end of the text
            editMainScroll();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(editMain, 0);
        } else {
            fabEdit.setImageResource(android.R.drawable.ic_menu_edit);
            if ( actionEditModeMenuItem != null ) {  // icon actionEditModeMenuItem shall toggle
                actionEditModeMenuItem.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_menu_edit));
            }
            editMain.setFocusableInTouchMode(false);
            editMain.clearFocus();
            editMain.setKeyListener(null);
            editMain.setFocusable(false);
            editMain.setCursorVisible(false);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(editMain.getWindowToken(), 0);
            editMainScroll();
            fabPlus.show();
        }

        // the 'do you want to save' dlg does not require an opened kbd
        boolean dlgOpen = sharedPref.getBoolean("saveChangesDialogOpen", false);
        if ( dlgOpen ) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(editMain.getWindowToken(), 0);
        }

        // unsaved changes have priority over jump to input
        editMainDirty = sharedPref.getBoolean("editMainDirty", false);
        if ( editMainDirty ) {
            setTitle(DataStore.namesSection.get(DataStore.selectedSection) + " *");
            Toast.makeText(getApplicationContext(), R.string.main2, Toast.LENGTH_SHORT).show();
            // override editMain with resume-safe stored data - w/o triggering afterTextChanged(Editable s)
            editMainEditMode = false;
            String textChange = sharedPref.getString("textChange", DataStore.dataSection.get(DataStore.selectedSection));
            SpannableString ss = formatText(textChange);
            editMain.setText(ss, EditText.BufferType.SPANNABLE);
            editMainSelStart = Math.min(sharedPref.getInt("textCursorPos", 0), editMain.length());
            editMain.setSelection(editMainSelStart, editMainSelStart);
            editMainEditMode = true;
            // discard jump to direct input from widgget - clear shared pref
            SharedPreferences.Editor spe = sharedPref.edit();
            spe.putBoolean("clickPlus", false);
            spe.commit();
        } else {
            setTitle(DataStore.namesSection.get(DataStore.selectedSection));
            if ( !editMainEditMode ) {
                // if set so in preferences, a user click on " ! " Widget shall open the AlertBuilder dialog immediately
                final boolean jumpToInput = sharedPref.getBoolean("widgetJumpInput", false);
                if ( jumpToInput ) {
                    // 0.0.0.14 simulate click on PlusButton and info stored via shared preferences
                    boolean clickPlus = sharedPref.getBoolean("clickPlus", false);
                    if ( clickPlus ) {
                        // clear shared pref
                        SharedPreferences.Editor spe = sharedPref.edit();
                        spe.putBoolean("clickPlus", false);
                        spe.commit();
                        // remote click on PlusButton
                        fabPlus.performClick();
                    }
                }
            }
        }

        super.onResume();
    }

    // activity_lifecycle.png: onCreate is called, when coming back from Settings
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // dark mode OR light mode; call before super.onCreate
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPref.getBoolean("darkMode", false)) {
            setTheme(R.style.ThemeOverlay_AppCompat_Dark);
        } else {
            setTheme(R.style.ThemeOverlay_AppCompat_Light);
        }
        super.onCreate(savedInstanceState);

        // permissions
        verifyStoragePermissions(this);

        // 3x standard entries
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Store our shared preference
        SharedPreferences sp = getSharedPreferences("ActivityStatus", MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean("active", true);
        ed.commit();

        // we need these controls in so many places ...
        fabEdit = findViewById(R.id.fabEdit);
        fabPlus = findViewById(R.id.fabPlus);
        editMain = findViewById(R.id.editMain);
        editMainScroller = findViewById(R.id.scrollerMain);

        // prevent data loss: any change to EditText shall be saved (temporarily) in SharedPreferences (reason: user pushes Android Home / Back) + set a dirty flag
        editMain.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void afterTextChanged(Editable s) {
                if ( editMainEditMode ) { // this section is not triggered after onResume --> setText, because editMainEditMode was set temporarily to false
                    editMainDirty = true;
                    String saveStr = s.toString();
                    // get show order pref: if BOTTOM, we need to revert the show order from s
                    SHOW_ORDER showOrder = !sharedPref.getBoolean("newOnTop", false) ? SHOW_ORDER.TOP : SHOW_ORDER.BOTTOM;
                    if ( showOrder == SHOW_ORDER.BOTTOM ) {
                        saveStr = convertBottomToTop(s.toString());
                    }
                    // preserve data for resume/back case
                    SharedPreferences.Editor spe = sharedPref.edit();
                    spe.putString("textChange", saveStr);
                    spe.putBoolean("editMainDirty", editMainDirty);
                    editMainSelStart = editMain.getSelectionStart();
                    spe.putInt("textCursorPos", editMainSelStart);
                    spe.commit();
                    fabEdit.hide();
                    setTitle(DataStore.namesSection.get(DataStore.selectedSection) + " *");
                }
            }
        });

        // editMain register the long press event handler
        editMain.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!editMainEditMode && editMainSelectedText.length() > 0) {
                    setClipboard(getApplicationContext(), editMainSelectedText);
                    Toast.makeText(getApplicationContext(), editMainSelectedText, Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        });
        // touch listener will always detect the current text line under the touch
        // NOTE: any call form edtiMain to getParent().requestDisallowInterceptTouchEvent() stops scrolling of an EditText inside a ScrollView completely
        editMain.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                editMainSelectedText = "";
                if (!editMainEditMode /*&& event.getAction() == MotionEvent.ACTION_DOWN*/) {
                    float y = event.getY() + editMain.getScrollY(); // this is the real line number in editMain
                    int line = editMain.getLayout().getLineForVertical((int) y);
                    int start = editMain.getLayout().getLineStart(line);
                    int end = editMain.getLayout().getLineEnd(line);
                    editMainSelectedText = "" + editMain.getText().subSequence(start, end);
                    if (editMainSelectedText.endsWith("\n")) {
                        editMainSelectedText = editMainSelectedText.substring(0, editMainSelectedText.length() - 1);
                    }
                    editMainSelectedText = editMainSelectedText.trim();
                    // 0.0.0.15 prepare start edit at the last known long press position
                    editMainSelStart = start;
                    //  1.20.3 search for line starting with a valid date
                    int dayStart = 0, dayEnd = 0;
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    String[] lines = editMain.getText().toString().split("\n");
                    for (int i = line; i >= 0; i--) {
                        try {
                            dateFormat.parse(lines[i]);
                            dayStart = i;
                            break;
                        } catch (Exception e) {
                        }
                    }
                    //  1.20.3 search for last line of day = "" because \n is converted by split to ""
                    for (int i = line; i < lines.length; i++) {
                        if (lines[i].equals("")) {
                            dayEnd = i;
                            break;
                        }
                    }
                    //  1.20.3 text of day
                    editMainSelectedDay = "";
                    for (int i = dayStart; i <= dayEnd; i++) {
                        editMainSelectedDay += lines[i] + "\n";
                    }
                    if (editMainSelectedDay.endsWith("\n")) {
                        editMainSelectedDay = editMainSelectedDay.substring(0, editMainSelectedDay.length() - 1);
                    }
                    editMainSelectedDay = editMainSelectedDay.trim();
                }
                return false;
            }
        });

        // this is the full sequence from INI file up to show data
        DataStore.clear();                                                           // clear DataStore
        String fileText = readAppFileData(this);                              // raw data from INI as String
        convertIniTextToDataStore(fileText);                                         // convert raw data to DataStore format
        String sectionedText = DataStore.dataSection.get(DataStore.selectedSection); // data from currently selected folder
        SpannableString ss = formatText(sectionedText);                              // format data taking care about: weekdays, show order
        editMain.setText(ss, EditText.BufferType.SPANNABLE);                         // show formatted data in editMain
        setTitle(DataStore.namesSection.get(DataStore.selectedSection));             // set app title accordingly
        editMainScroll();                                                            // scroll editMain to the most recent input depending on show order

        // toggle editMainEditMode vs. lock mode via button
        fabEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // hide input button fabPlus
                fabPlus.hide();
                // it looks awkward, if search stays open
                if ( searchView != null ) {
                    searchView.onActionViewCollapsed();
                }
                // given the editMode is ON, it should be reverted
                if ( editMainEditMode ) {
                    // confirmation needed, only when there was some change
                    if ( editMainDirty ) {
                        // Alert Dialog asks, whether changes shall be saved
                        final Context ct = view.getContext();
                        AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                        builder.setTitle(R.string.main3);
                        builder.setMessage(R.string.main4);
                        final SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putBoolean("saveChangesDialogOpen", true);
                        editor.commit();
                        // change edit mode to R/O but save data before
                        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // change edit mode to OFF
                                editMainEditMode = false;
                                dialog.dismiss();
                                // get text from editMain via shared pref "textChange", which takes care about show order anyway
                                String text = sharedPref.getString("textChange", DataStore.dataSection.get(DataStore.selectedSection));
                                if ( !text.endsWith("\n") ) {
                                    text += "\n";
                                }
                                // 0.0.0.17 search regex pattern for [[n]]]\n --> forbidden input - would interfere with data sections, which begin with [[n]]\n
                                if ( Pattern.compile("\\[\\[\\d*\\]\\]\\n").matcher(text).find() ) {
                                    text = text.replaceAll("\\[\\[", "((");
                                    okBox(getString(R.string.note), getString(R.string.main5));
                                }
                                // update DataStore class
                                DataStore.dataSection.set(DataStore.selectedSection, text);
                                // prepare DataStore for write output
                                String txt = convertDataStoreToIniText();
                                // write to app data file
                                writeAppFileData(getApplicationContext(), txt);
                                // clear DataStore
                                DataStore.clear();
                                // read app data back from file
                                String fileText = readAppFileData(getApplicationContext());
                                // prepare app data for DataStore class
                                convertIniTextToDataStore(fileText);
                                // show in editMain
                                SpannableString ss = formatText(DataStore.dataSection.get(DataStore.selectedSection));
                                editMain.setText(ss, EditText.BufferType.SPANNABLE);
                                // take care about preferences & button appearance & keyboard
                                editor.putBoolean("editMode", editMainEditMode);
                                editor.putBoolean("saveChangesDialogOpen", false);
                                editor.commit();
                                fabEdit.hide();
                                if (editMainEditMode) {
                                    fabEdit.setImageResource(android.R.drawable.ic_lock_lock);
                                } else {
                                    fabEdit.setImageResource(android.R.drawable.ic_menu_edit);
                                }
//                                fabEdit.show();
                                onResume();
                                return;
                            }
                        });
                        // change edit mode to R/O and don't save data
                        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // change edit mode to OFF and leave Alert
                                editMainEditMode = false;
                                // reset dirty flag
                                editMainDirty = false;
                                setTitle(DataStore.namesSection.get(DataStore.selectedSection));
                                editor.putBoolean("editMainDirty", editMainDirty);
                                editor.putInt("textCursorPos", 0);
                                editor.commit();
                                dialog.dismiss();
                                // re read data from file
                                DataStore.clear();
                                final String fileText = readAppFileData(getApplicationContext());
                                convertIniTextToDataStore(fileText);
                                SpannableString ss = formatText(DataStore.dataSection.get(DataStore.selectedSection));
                                editMain.setText(ss, EditText.BufferType.SPANNABLE);
                                // take care about preferences & button appearance & keyboard
                                editor.putBoolean("editMode", editMainEditMode);
                                editor.putBoolean("saveChangesDialogOpen", false);
                                editor.commit();
                                fabEdit.hide();
                                if (editMainEditMode) {
                                    fabEdit.setImageResource(android.R.drawable.ic_lock_lock);
                                } else {
                                    fabEdit.setImageResource(android.R.drawable.ic_menu_edit);
                                }
//                                fabEdit.show();
                                onResume();
                                return;
                            }
                        });
                        // do not change edit mode and simply return
                        builder.setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // keep edit mode ON and continue
                                editMainEditMode = true;
                                editor.putBoolean("saveChangesDialogOpen", false);
                                editor.commit();
                                dialog.dismiss();
                            }
                        });
                        AlertDialog alert = builder.create();
                        alert.show();
                        // title and message of AlertDialog.Builder are otherwise invisible in dark theme
                        int textViewId = builder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
                        TextView tv = alert.findViewById(textViewId);
                        tv.setTextColor(Color.BLACK);
                        textViewId = builder.getContext().getResources().getIdentifier("android:id/message", null, null);
                        tv = alert.findViewById(textViewId);
                        tv.setTextColor(Color.BLACK);
                    } else {
                        // change edit mode to OFF
                        editMainEditMode = false;
                    }
                } else {
                    // switch to edit mode ON
                    editMainEditMode = true;
                    editMainScroll();
                }
                // go ahead with app logic
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(view.getContext());
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("editMode", editMainEditMode);
                editor.commit();
                fabEdit.hide();
                if ( editMainEditMode ) {
                    fabEdit.setImageResource(android.R.drawable.ic_lock_lock);
                } else {
                    fabEdit.setImageResource(android.R.drawable.ic_menu_edit);
                }
//                fabEdit.show();
                // the jump back to resume is needed to actually implement the switched edit modi
                onResume();
                return;
            }
        });

        //
        // this is the most important function of the app: get a new data set, when clicking the + button
        //
        fabPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // it looks awkward, if search stays open
                if ( searchView != null ) {
                    searchView.onActionViewCollapsed();
                }

                // 0.0.0.16 we need to suppress the return in case inputAlert was restarted due to keyboard type change
                if ( !inputAlertKbdToggle ) {
                    // 0.0.0.14 this happens, when input dlg was openend + user pushed 'Android Home' + "!" again
                    if (inputAlert != null && inputAlert.isShown()) {
                        return;
                    }
                }

                // snackbar with some hint
                Snackbar.make(view, R.string.main6, Snackbar.LENGTH_LONG)
                        .setAction(R.string.hint, null)
                        .show();

                // determine what keyboard type is requested
                final Context context = view.getContext();
                inputAlert = new EditText(context);
                if ( inputAlertKbdToggle ) {
                    // toggle keyboard type
                    inputAlertKbdToggle = false;
                    if ( DataStore.kbdSection.get(DataStore.selectedSection) == InputType.TYPE_CLASS_TEXT ) {
                        DataStore.kbdSection.set(DataStore.selectedSection, InputType.TYPE_CLASS_PHONE);
                    } else {
                        DataStore.kbdSection.set(DataStore.selectedSection, InputType.TYPE_CLASS_TEXT);
                    }
                    // 0.0.0.16 keep inputAlert text, when kbd type was toggled
                    inputAlert.setText(inputAlertText);
                    showEditTextContextMenu(inputAlert, false);
                    inputAlert.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            inputAlert.setSelection(inputAlertText.length());
                        }
                    },2000);
                }
                // always get kbd from section settings
                inputAlert.setInputType(DataStore.kbdSection.get(DataStore.selectedSection));

                // force always black color in EditText input of AlertDialog
                inputAlert.setTextColor(Color.BLACK);

                // show a customized dialog to obtain a text input: https://stackoverflow.com/questions/10903754/input-text-dialog-android
                ContextThemeWrapper themedContext = new ContextThemeWrapper(context, android.R.style.Theme_Holo_Light_Dialog_NoActionBar);
                final AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
                builder.setTitle(R.string.main7);
                builder.setView(inputAlert);
                int section = DataStore.selectedSection;
                int dataKbdType = DataStore.kbdSection.get(DataStore.selectedSection);
                int editKbdType = inputAlert.getInputType();
                // AlertDialog with one extra button: https://stackoverflow.com/questions/12244297/how-to-add-multiple-buttons-on-a-single-alertdialog
                String txt = editKbdType == InputType.TYPE_CLASS_TEXT ? getString(R.string.main8) : getString(R.string.main9);
                builder.setItems(new CharSequence[] {txt}, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // restart input dialog with toggled keyboard
                                if ( which == 0 ) {
                                    if ( inputAlert != null && inputAlert.getVisibility() == View.VISIBLE ) {
                                        inputAlertKbdToggle = true;
                                        inputAlertText = inputAlert.getText().toString();
                                        fabPlus.performClick();
                                        return;
                                    }
                                }
                            }
                        });
                // app exit button
                builder.setNeutralButton(R.string.exit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // just leave app, this will jump afterwards to home screen
                        finish();
                    }
                });
                // button ok
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // get input data from AlertDialog, empty input shall not add a logbook entry
                        String newText = inputAlert.getText().toString();
                        int timestampType = DataStore.timeSection.get(DataStore.selectedSection);
                        if ( newText.isEmpty() && timestampType == TIMESTAMP.OFF ) { // 1.20.1 empty input is ok, if timestamp is added
                            fabPlus.show();
                            // close input dialog
                            dialog.cancel();
                            // w/o return, it would add an empty logbook entry
                            return;
                        }
                        // 0.0.0.17 search regex pattern for [[n]]]\n --> forbidden input - would interfere with data sections, which begin with [[n]]\n
                        if ( Pattern.compile("\\[\\[\\d*\\]\\]\\n").matcher(newText).find() ) {
                            newText = newText.replace("[[", "((");
                            okBox(getString(R.string.note), getString(R.string.main5));
                        }
                        // get all history data from editMain
                        String tvText = DataStore.dataSection.get(DataStore.selectedSection);
                        // a common date format string
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                        // parse last date stamp from history; supposed be in 1st row ...
                        String[] parts = tvText.split("\n");
                        if ( parts.length == 0 ) {
                            parts = new String[]{""};
                        }
                        String dateStringLast = parts[0] != null ? parts[0] : "1970-01-01";
                        Date dateLast;
                        try {
                            dateLast = dateFormat.parse(dateStringLast);
                        } catch (Exception ex) { //  ... if no data is found, we start over with EPOC
                            dateLast = new Date(1970, 1, 1);
                        }
                        // get today date stamp w/o time
                        Date dateToday;
                        try {
                            dateToday = dateFormat.parse(dateFormat.format(new Date()));
                        } catch (Exception ex) {
                            dateToday = new Date();
                        }
                        // keep latest date stamp always on top
                        int combineNdx = 1;
                        String dateStr = dateStringLast + "\n";
                        // if topmost date is not today, we need to add the today's date stamp
                        if ( dateLast.compareTo(dateToday) != 0 ) {
                            dateStr = new SimpleDateFormat("yyyy-MM-dd EEE").format(dateToday) + "\n";
                            combineNdx = 0;
                        }
                        // add time at the beginning of the newText
                        String timeStr = "";
                        if ( timestampType != TIMESTAMP.OFF ) {
                            String timeFormat = "HH:mm";
                            if ( timestampType == TIMESTAMP.HHMMSS ) {
                                timeFormat = "HH:mm:ss";
                            }
                            timeStr = new SimpleDateFormat(timeFormat).format(new Date()) + " ";
                        }
                        if ( combineNdx == 0 ) {
                            newText = timeStr + newText + "\n";
                        } else {
                            newText = timeStr + newText;
                        }
                        // build final string after input
                        String finalStr = dateStr;
                        finalStr += newText;
                        for ( int i = combineNdx; i < parts.length; i++ ) {
                            finalStr += "\n" + parts[i];
                        }
                        if ( !finalStr.endsWith("\n") ) {
                            finalStr += "\n";
                        }
                        // save and re-read saved data
                        DataStore.dataSection.set(DataStore.selectedSection, finalStr);      // update DataStore class
                        String iniData = convertDataStoreToIniText();                        // combine DataStore for write to file
                        writeAppFileData(context, iniData);                                  // write to app data file
                        DataStore.clear();                                                   // clear DataStore class
                        String fileText = readAppFileData(context);                          // read app data file
                        convertIniTextToDataStore(fileText);                                 // split app data file to DataStore class
                        String dsText = DataStore.dataSection.get(DataStore.selectedSection);// raw data from DataStore
                        SpannableString ss = formatText(dsText);                             // format output to spannable
                        editMain.setText(ss, EditText.BufferType.SPANNABLE);                 // set editMain text
                        editMainScroll();                                                    // scroll to end if needed
                        // was hidden during input
                        fabPlus.show();
                    }
                });
                // cancel AlertDialog.Builder
                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // was hidden during input
                        fabPlus.show();
                        dialog.cancel();
                    }
                });

                // finally show AlertBuilder dialog
                AlertDialog dlg = builder.create();
                ListView listView = dlg.getListView();
                listView.setDivider(new ColorDrawable(Color.GRAY)); // set color
                listView.setDividerHeight(2); // set height
                dlg.show();

                // prevent dlg from disappear, when tap outside: https://stackoverflow.com/questions/42254443/alertdialog-disappears-when-touch-is-outside-android
                dlg.setCanceledOnTouchOutside(false);

                // title of AlertDialog.Builder is otherwise invisible in dark theme
                int textViewId = builder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
                TextView tv = dlg.findViewById(textViewId);
                tv.setTextColor(Color.BLACK);

                // both red bottom buttons shall be been hidden when AlertDialog.Builder is shown
                fabEdit.hide();
                fabPlus.hide();

                // show keyboard for EditText in AlertDialog.Builder via timer: https://stackoverflow.com/questions/4597690/how-to-set-timer-in-android
                // NOT CLEAR: I didn't get reliably to show keyboard from here, especially not when triggered from: click widget --> simulated click on +
                inputAlert.requestFocus();
                if (myTimer == null) {
                    myTimer = new Timer();
                    myTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            TimerMethod(inputAlert);
                        }
                    }, 50, 10);
                }
            } // end of AlertDialog.Builder
        }); // end of FloatingActionButton R.id.fabPlus OnClickListener()

    } // end of onCreate()


    /*
       There are three stages of data handling
       1) String readAppFileData() / void writeAppFileData(String)
          - file LogBook.txt in app's private folder
          - basic file IO ops
          - one single String for both input and output
          - data are always new_on_top

       2) void convertIniTextToDataStore(String) / String convertDataStoreToIniText()
          - transfer between String and DataStore
          - FROM INI: structured text file inside of input String is resolved into DataStore
          - TO INI: DataStore is combined into a structured String
          - data are always new_on_top

       2) String formatText(String)
          - gets a String as input from DataStore
          - formats a String to be shown in editMain
          - takes care about new_on_ top or new_at_bottom
          - takes care about search results
    */

    // LogBook.txt PARSER: pre format data from file into DataStore class --> provide multiple file sections + their names + file selection index + keyboard type
    /*  file format    N <= SECTIONS_COUNT
        ~~~~~~~~~~~
        file0:<filename>\n
        file1:<filename>\n
             ...
        fileN:<filename>\n
        index:<number>\n
        keyboard0:<type>\n
        keyboard1:<type>\n
             ...
        keyboardN:<type>\n
        timestamp0:<type>\n
        timestamp1:<type>\n
             ...
        timestampN:<type>\n
        [[0]]
        ... content ...\n
        [[1]]
        ... content ...\n
             ...
        [[N]]
        ... content ...\n
     */
    void convertIniTextToDataStore(String text) {
        
        // no INI file at all --> we generate an empty DataStorage
        if ( text.length() == 0 ) {
            DataStore.clear();
            DataStore.namesSection.add("folder");
            DataStore.dataSection.add("");
            DataStore.kbdSection.add(InputType.TYPE_CLASS_TEXT);
            DataStore.timeSection.add(TIMESTAMP.HHMM);
            DataStore.selectedSection = 0;
            String txt = convertDataStoreToIniText();          // combine DataStore class to String
            writeAppFileData(getApplicationContext(), txt);    // write to file
            return;
        }

        // split the whole file text into lines
        String[] parts = text.split("\n");

        // search header for fileN: keywords + index:
        boolean noHeader = false;
        int lastHeaderLine = 0;
        if ( parts[0].startsWith("file0:") ) {
            for ( int i=0; i<parts.length; i++ ) {
                String key = "file" + i + ":";
                if ( parts[i].startsWith(key) ) {
                    String[] keyVal = parts[i].split(":");
                    if ( keyVal.length > 1 && keyVal[1].length() > 0 ) {
                        DataStore.namesSection.add(keyVal[1]);
                        lastHeaderLine = i;
                    } else {
                        noHeader = true;
                        DataStore.namesSection.add(key.substring(0, key.length()-1));
                    }
                } else {
                    // next line after fileX: section shall contain the selected file index
                    if ( parts[i].startsWith("index:") ) {
                        String[] keyVal = parts[i].split(":");
                        if ( keyVal.length > 1 && keyVal[1].length() > 0 ) {
                            try {
                                DataStore.selectedSection = Integer.parseInt(keyVal[1]);
                            } catch (NumberFormatException e) {
                                DataStore.selectedSection = 0;
                            }
                            lastHeaderLine = i;
                        } else {
                            noHeader = true;
                            DataStore.selectedSection = 0;
                        }
                    } else {
                        // partial missing header
                        noHeader = true;
                        DataStore.selectedSection = 0;
                    }
                    // after finding the file index, we simply stop
                    break;
                }
            }
        } else {
            // app data file does not have section headers --> only 1 active file section exists
            noHeader = true;
            DataStore.namesSection.add("folder");
            DataStore.selectedSection = 0;
        }

        // next search for keyboard type sections
        int ndx = 0;
        for ( int i=0; i<parts.length; i++) {
            String key = "keyboard" + ndx + ":";
            if ( parts[i].startsWith(key) ) {
                String[] keyVal = parts[i].split(":");
                try {
                    DataStore.kbdSection.add(Integer.parseInt(keyVal[1]));
                } catch (NumberFormatException e) {
                    DataStore.kbdSection.add(0);
                }
                ndx++;
            }
        }
        int len = DataStore.kbdSection.size();
        for ( int i=len; i<DataStore.namesSection.size(); i++ ) {
            DataStore.kbdSection.add(InputType.TYPE_CLASS_TEXT);
        }

        // next search for timestamp type sections
        ndx = 0;
        for ( int i=0; i<parts.length; i++) {
            String key = "timestamp" + ndx + ":";
            if ( parts[i].startsWith(key) ) {
                String[] keyVal = parts[i].split(":");
                try {
                    DataStore.timeSection.add(Integer.parseInt(keyVal[1]));
                } catch (NumberFormatException e) {
                    DataStore.timeSection.add(TIMESTAMP.HHMM);
                }
                ndx++;
            }
        }
        len = DataStore.timeSection.size();
        for ( int i=len; i<DataStore.namesSection.size(); i++ ) {
            DataStore.timeSection.add(TIMESTAMP.HHMM);
        }

        // next search for real data sections
        String collector = "";
        ndx = 0;
        for ( int i=0; i<parts.length; i++) {
            String key = "[[" + ndx + "]]";
            if ( parts[i].equals(key) ) {
                if ( ndx > 0 ) {  // we need to reject the first match
                    DataStore.dataSection.add(collector);
                }
                ndx++;
                collector = "";
            } else {
                collector += parts[i] + "\n";
            }
        }
        DataStore.dataSection.add(collector);
        len = DataStore.dataSection.size();
        // fill not existing file sections with empty strings
        for ( int i=len; i<DataStore.namesSection.size(); i++ ) {
            DataStore.dataSection.add("");
        }

        // let's correct any missing header info by writing data back to app file data
        if ( noHeader ) {
            String txt = convertDataStoreToIniText();          // combine DataStore class to String
            writeAppFileData(getApplicationContext(), txt);    // write to file
        }
    }
    // arrange data from DataStore class into a String for later write to INI file
    String convertDataStoreToIniText() {
        // section names
        String txt = "";
        for ( int i=0; i<SECTIONS_COUNT; i++ ) {
            if ( i >= DataStore.namesSection.size() ) {
                break;
            }
            txt += "file" + i + ":" + DataStore.namesSection.get(i) + "\n";
        }
        // selected section index
        txt += "index:" + DataStore.selectedSection + "\n";
        // section keyboard types
        for ( int i=0; i<SECTIONS_COUNT; i++ ) {
            if ( i >= DataStore.namesSection.size() ) {
                break;
            }
            txt += "keyboard" + i + ":" + DataStore.kbdSection.get(i) + "\n";
        }
        // section timestamp types
        for ( int i=0; i<SECTIONS_COUNT; i++ ) {
            if ( i >= DataStore.timeSection.size() ) {
                break;
            }
            txt += "timestamp" + i + ":" + DataStore.timeSection.get(i) + "\n";
        }
        // section data
        for ( int i=0; i<SECTIONS_COUNT; i++ ) {
            if ( i >= DataStore.dataSection.size() ) {
                break;
            }
            txt += "[[" + i + "]]\n";
            txt += DataStore.dataSection.get(i);
        }
        return txt;
    }

    // format text + highlight all query matches according to search query
    private SpannableString formatQueryText(String etText, String query) {
        // have the text formatted in the regular way
        SpannableString ss = formatText(etText);
        // additionally highlight all matches with a search query
        int fromIndex = 0;
        do {
            fromIndex = ss.toString().toLowerCase().indexOf(query.toLowerCase(), fromIndex);
            if ( fromIndex != -1 ) {
                ss.setSpan(new BackgroundColorSpan(0xFFFF0000), fromIndex, fromIndex + query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                fromIndex = fromIndex + query.length() < ss.length() ? fromIndex + query.length() : -1;
            }
        } while ( fromIndex != -1 );
        return ss;
    }
    // format text: input String from DataStore is always 'new_on_top', output String take care about SHOW_ORDER
    public enum SHOW_ORDER {TOP, BOTTOM}
    private SpannableString formatText(String text) {
        // ret val
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        // get show order pref
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SHOW_ORDER showOrder = !prefs.getBoolean("newOnTop", false) ? SHOW_ORDER.TOP : SHOW_ORDER.BOTTOM;
        // prepare for spaces
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        float width = displayMetrics.widthPixels*0.9f;
        // data array
        String[] parts = text.split("\n");
        // arrange data array according to show order
        List<String> tmpList = new ArrayList<String>();
        if ( showOrder == SHOW_ORDER.BOTTOM ) {
            for ( int i=0; i<parts.length; i++) {
                if ( Pattern.matches( "\\d{4}-\\d{2}-\\d{2}.*", parts[i] ) ) {
                    tmpList.add(0, parts[i]);
                } else {
                    if ( parts[i].length() == 0 ) {
                        tmpList.add(0, parts[i]);
                    } else {
                        if ( tmpList.size() > 0 ) {
                            tmpList.add(1, parts[i]);
                        } else {
                            tmpList.add(0, parts[i]);
                        }
                    }
                }
            }
            parts = new String[tmpList.size()];
            parts = tmpList.toArray(parts);
        }
        // loop
        for ( int i=0; i<parts.length; i++) {
            // just trim
            parts[i] = parts[i].trim();
            // a regex pattern for "yyyy-mm-dd EEE", sample 2020-03-03 Thu
            if ( Pattern.matches( "\\d{4}-\\d{2}-\\d{2}.*", parts[i] ) ) {
                // if week day name is missing, add it
                if ( parts[i].length() == 10 ) {
                    parts[i] += dayNameOfWeek(parts[i]);
                }
                // spaces for headlines
                do {
                    parts[i] += " ";
                } while ( editMain.getPaint().measureText(parts[i], 0, parts[i].length()) < width );
                // date stamps shall be bold
                SpannableString ssPart = new SpannableString(parts[i] + "\n");
                // the days Sat and Sun have a different background color
                int dow = dayNumberOfWeek(parts[i]);
                if ( dow == 1 || dow == 7 ) {
                    ssPart.setSpan(new BackgroundColorSpan(0x55FF5555), 0, ssPart.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    ssPart.setSpan(new BackgroundColorSpan(0x66777777), 0, ssPart.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                ssb.append(ssPart);
            } else {
                ssb.append(parts[i] + "\n");
            }
        }
        // remove last "\n" from final string
        if ( ssb.length() > 0 ) {
            ssb.delete(ssb.length() - 1, ssb.length());
        }
        return SpannableString.valueOf(ssb);
    }
    // convert a date string "yyyy-MM-dd" into: 1.) a day of week number (Sun = 1 ... Sat = 7) +  2.) Name Day of Week
    int dayNumberOfWeek(String dateStr) {
        Date date;
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        try {
            date = format.parse(dateStr);
        } catch(Exception ex) {
            date = new Date();
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        return dow;
    }
    String dayNameOfWeek(String dateStr) {
        Date date;
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        try {
            date = format.parse(dateStr);
        } catch(Exception ex) {
            date = new Date();
        }
        String str = new SimpleDateFormat(" EEE", Locale.ENGLISH).format(date);
        return str;
    }

    // read data from file and handle them as UTF-8 text
    private String readAppFileData(Context context) {
        String fileText = "";
        try {
            String storagePath = context.getExternalFilesDir(null).getAbsolutePath();
            String appName = getApplicationInfo().loadLabel(getPackageManager()).toString();
            File file = new File(storagePath, appName + ".txt");
            byte[] byteArray = new byte[(int)file.length()];
            FileInputStream fis = new FileInputStream(file);
            int oneChar;
            int i = 0;
            while ((oneChar = fis.read()) != -1) {
                byteArray[i++] = (byte) oneChar;
            }
            fis.close();
            fileText = new String( byteArray, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileText;
    }
    // write UTF8 text to file and reset editMain's dirty flag
    private void writeAppFileData(Context context, String text) {
        String storagePath = context.getExternalFilesDir(null).getAbsolutePath();
        String appName = getApplicationInfo().loadLabel(getPackageManager()).toString();
        File file = new File(storagePath, appName + ".txt");
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(text.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        editMainDirty = false;
        setTitle(DataStore.namesSection.get(DataStore.selectedSection));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor spe = prefs.edit();
        spe.putBoolean("editMainDirty", editMainDirty);
        spe.putInt("textCursorPos", 0);
        spe.commit();
    }

    // if editMain show order is 'new_at_bottom', this method returns a String 'new_at_top' to comply with DataStore
    String convertBottomToTop(String text) {
        String saveStr = "";
        // data array
        String[] parts = text.split("\n");
        // arrange data array according to show order
        List<String> tmpList = new ArrayList<String>();
        for (int i = 0; i < parts.length; i++) {
            if (Pattern.matches("\\d{4}-\\d{2}-\\d{2}.*", parts[i])) {
                tmpList.add(0, parts[i]);
            } else {
                if (parts[i].length() == 0) {
                    tmpList.add(0, parts[i]);
                } else {
                    if ( tmpList.size() > 0 ) {
                        tmpList.add(1, parts[i]);
                    } else {
                        tmpList.add(0, parts[i]);
                    }
                }
            }
        }
        parts = new String[tmpList.size()];
        parts = tmpList.toArray(parts);
        saveStr = "";
        for (int i = 0; i < parts.length; i++) {
            saveStr += parts[i] + "\n";
        }
        if ( saveStr.length() > 0 ) {
            saveStr = saveStr.substring(0, saveStr.length() - 1);
        }
        return saveStr;
    }

    // scroll editMain to a specific location, depends on SHOW_ORDER or a given cursor placement
    void editMainScroll() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SHOW_ORDER showOrder = !prefs.getBoolean("newOnTop", false) ? SHOW_ORDER.TOP : SHOW_ORDER.BOTTOM;
        // -1 indicates no search operation pending
        if ( editMainSelStart == -1 ) {
            // get show order pref
            if (showOrder == SHOW_ORDER.BOTTOM) {
                editMain.setSelection(editMain.getText().length(), editMain.getText().length());
                editMainScroller.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        editMainScroller.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                },500);
            } else {
                editMain.setSelection(0, 0);
                editMainScroller.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        editMainScroller.fullScroll(ScrollView.FOCUS_UP);
                    }
                },500);
            }
        } else {
            // check length boundaries
            editMainSelStart = editMainSelStart < editMain.length() ? editMainSelStart : 0;
            // scroll to selection an make it visible
            editMain.setSelection(editMainSelStart, editMainSelStart);
            if (editMainSelStart < 200) {
                editMain.setSelection(0, 0);
                editMainScroller.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        editMainScroller.fullScroll(ScrollView.FOCUS_UP);
                    }
                }, 500);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        // icon actionEditModeMenuItem shall toggle
        actionEditModeMenuItem = menu.findItem( R.id.action_EditMode);
        if ( editMainEditMode ) {
            actionEditModeMenuItem.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_lock_lock));
        } else {
            actionEditModeMenuItem.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_menu_edit));
        }

        // add Search Icon to App-Menu-Toolbar and provide infrastructure for a search: https://stackoverflow.com/questions/36056906/how-to-use-searchview-in-the-toolbar-on-clicking-search-icon
        //     !! replace !! app:actionViewClass="android.support.v7.widget.SearchView" --> app:actionViewClass="androidx.appcompat.widget.SearchView"
        final MenuItem myActionMenuItem = menu.findItem( R.id.action_search);
        myActionMenuItem.expandActionView();
        searchView = (SearchView) myActionMenuItem.getActionView();
        // on get focus
        searchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                // if there are unsaved changes, any search would take place in the original data - which may confuse
                if ( editMainEditMode ) {
                    searchView.onActionViewCollapsed();
                    okBox(getString(R.string.note), getString(R.string.main11));
                    return;
                }
                // force keyboard: not really needed
                if ( hasFocus ) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(view, 0);
                    }
                }
            }
        });
        // restore search text
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                searchView.setQuery(searchViewQuery, false);
                return false;
            }
        });
        // search action after pushing the loupe on keyboard
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                editMain.setSelection(0, 0);
                // save query for later use
                searchViewQuery = query;
                // search editMain ... for query and select + scroll where query is found
                int startSearch = editMainSelStart == -1 ? editMainSelStart : editMainSelStart + query.length();
                int findPos = editMain.getText().toString().toLowerCase().indexOf(query.toLowerCase(), startSearch);
                // 0.0.0.16 loop around 1x if query was not found
                if ( findPos == -1 && startSearch > 0 ) {
                    startSearch = -1;
                    findPos = editMain.getText().toString().toLowerCase().indexOf(query.toLowerCase(), startSearch);
                }
                // ... but format text based on DataStore
                String etText = DataStore.dataSection.get(DataStore.selectedSection);
                if ( findPos != -1 ) {
                    SpannableString ss = formatQueryText(etText, query);
                    editMain.setText(ss, EditText.BufferType.SPANNABLE);
                    editMainSelStart = findPos;
                    // if scroll up, there is a need to adjust the scoll position, seems to be caused by EditMain padding on top
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    SHOW_ORDER showOrder = !sharedPref.getBoolean("newOnTop", false) ? SHOW_ORDER.TOP : SHOW_ORDER.BOTTOM;
                    if ( showOrder == SHOW_ORDER.BOTTOM ) {
                        if ( editMainSelStart > 200 ) {
                            // temporarily: enable scroll to selection BUT disable scroll by hand
                            editMain.setFocusableInTouchMode(true);
                            editMain.requestFocus();
                            // we need to scroll further up as supposed, otherwise the selection is slightly out of screen
                            editMain.setSelection(editMainSelStart-200, editMainSelStart-200);
                            // revert temporarily: disable scroll to selection BUT enable scroll by hand
                            final Handler handler = new Handler();  // UI answer: https://stackoverflow.com/questions/3072173/how-to-call-a-method-after-a-delay-in-android
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    editMain.clearFocus();
                                    editMain.setFocusableInTouchMode(false);
                                }
                            }, 2000);
                        } else {
                            // simply scroll all the way up
                            editMain.setSelection(0, 0);
                            editMainScroller.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    editMainScroller.fullScroll(ScrollView.FOCUS_UP);
                                }
                            }, 500);
                        }
                    } else {
                        // 0.0.0.13 otherwise scroll to pos doesn't work - need to revert it to allow scroll by hand
                        if ( editMainSelStart > 200 ) {
                            editMain.setFocusableInTouchMode(true);
                            editMain.requestFocus();
                            editMain.setSelection(editMainSelStart, editMainSelStart);
                            final Handler handler = new Handler();  // UI answer: https://stackoverflow.com/questions/3072173/how-to-call-a-method-after-a-delay-in-android
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    editMain.clearFocus();
                                    editMain.setFocusableInTouchMode(false);
                                }
                            }, 2000);
                        } else {
                            editMain.setSelection(0, 0);
                            editMainScroller.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    editMainScroller.fullScroll(ScrollView.FOCUS_UP);
                                }
                            }, 500);
                        }
                    }
                } else {
                    editMainSelStart = -1;
                    Toast.makeText(getApplicationContext(), "'" + query + "' - not found", Toast.LENGTH_SHORT).show();
                    SpannableString ss = formatText(etText);
                    editMain.setText(ss, EditText.BufferType.SPANNABLE);
                    editMainScroll();
                    editMainScroller.scrollTo(0, 0);
                }
                // save cursor pos
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor spe = prefs.edit();
                spe.putInt("textCursorPos", editMainSelStart);
                spe.commit();
                // quit search
                searchView.onActionViewCollapsed();
                // keep search query in memory
                searchView.setQuery(searchViewQuery, false);
                return false;
            }
            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        });
        return true;
    }

    AlertDialog changeFolderDialog = null;
    AlertDialog moreDialog = null;
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        final int id = item.getItemId();

        // show settings when clicking on HAMBURGER: right click on res folder, selecting New > Image Asset. browse image file, should do the job
        if ( id == R.id.action_Hamburger ) {
            // it looks awkward, if search stays open
            if ( searchView != null ) {
                searchView.onActionViewCollapsed();
            }
            // if there are unsaved changes, settings may change the data - might be confusing
            if ( editMainEditMode ) {
                okBox(getString(R.string.note), getString(R.string.main11));
                return true;
            }
            // start settings activity
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        }

        // content share option
        if ( id == R.id.action_Share ) {
            // it looks awkward, if search stays open
            if ( searchView != null ) {
                searchView.onActionViewCollapsed();
            }
            // if there are unsaved changes, a new selection would discard changes made in the previous selection
            if ( editMainEditMode ) {
                okBox(getString(R.string.note), getString(R.string.main11));
                return true;
            }
            // we cannot accept an empty selection
            if ( editMainSelectedText.length() == 0 ) {
                okBox(getString(R.string.note), getString(R.string.main31));
                return true;
            }
            //  1.20.3 provide options about what to share
            final AlertDialog.Builder shareBuilder = new AlertDialog.Builder(this);
            final CharSequence[] items = {getString(R.string.main32), getString(R.string.main33), getString(R.string.main34)};
            shareBuilder.setTitle(R.string.main35);
            final int[] selected = {0};
            shareBuilder.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            selected[0] = which;
                        }
            });
            shareBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // quit "what to share" dlg
                    dialog.cancel();
                    // the content to share
                    String shareBody = "body here";
                    if ( selected[0] == 0 ) {
                        shareBody = editMainSelectedText;
                    }
                    if ( selected[0] == 1 ) {
                        shareBody = editMainSelectedDay;
                    }
                    if ( selected[0] == 2 ) {
                        shareBody = editMain.getText().toString();
                    }
                    // open share options
                    Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                    sharingIntent.setType("text/plain");
                    String shareSub = "LogBook";
                    sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, shareSub);
                    sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
                    startActivity(Intent.createChooser(sharingIntent, ""));
                }
            });
            shareBuilder.setNegativeButton(R.string.back, null);
            AlertDialog shareDialog = shareBuilder.create();
            ListView listView = shareDialog.getListView();
            listView.setDivider(new ColorDrawable(Color.GRAY));
            listView.setDividerHeight(2);
            shareDialog.show();
            int textViewId = shareBuilder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
            TextView tv = shareDialog.findViewById(textViewId);
            tv.setTextColor(Color.BLACK);
        }

        // change/toggle edit mode via menu button
        if ( id == R.id.action_EditMode ) {
            // it looks awkward, if search stays open
            if ( searchView != null ) {
                searchView.onActionViewCollapsed();
            }
            // always call edit mode function
            fabEdit.performClick();
            return true;
        }

        // change data base folder with radio buttons: https://stackoverflow.com/questions/15762905/how-can-i-display-a-list-view-in-an-android-alert-dialog
        if ( id == R.id.action_ChangeFolder ) {
            // it looks awkward, if search stays open
            if ( searchView != null ) {
                searchView.onActionViewCollapsed();
            }
            // if there are unsaved changes, a new selection would discard changes made in the previous selection
            if ( editMainEditMode ) {
                okBox(getString(R.string.note), getString(R.string.main11));
                return true;
            }
            // CHANGE FOLDER
            final AlertDialog.Builder changeFileBuilder = new AlertDialog.Builder(this);
            changeFileBuilder.setTitle(R.string.main13);
            final int[] selectedSectionTemp = {DataStore.selectedSection};
            final long[] lastClickTime = {System.currentTimeMillis()};
            final int[] lastSelectedSection = {DataStore.selectedSection};
            // add a radio button list containing all the file name
            String[] array = DataStore.namesSection.toArray(new String[DataStore.namesSection.size()]);
            changeFileBuilder.setSingleChoiceItems(array, DataStore.selectedSection, new DialogInterface.OnClickListener() {
                @Override  
                public void onClick(DialogInterface dialog, int which) {
                    // the current file selection is temporary, unless we confirm with OK
                    selectedSectionTemp[0] = which;
                    // check double click event
                    long nowTime = System.currentTimeMillis();
                    long deltaTime = nowTime - lastClickTime[0];
                    if ( deltaTime<700 && lastSelectedSection[0]==which ) {
                        // now the file selection becomes permanent
                        DataStore.selectedSection = selectedSectionTemp[0];
                        String txt = convertDataStoreToIniText();       // prepare DataStore for write output
                        writeAppFileData(getApplicationContext(), txt); // write to app data file
                        // pick the data section from DataStore, format it and show it in editMain, update App titlebar
                        String sectionedText = DataStore.dataSection.get(DataStore.selectedSection);
                        SpannableString ss = formatText(sectionedText);
                        editMain.setText(ss, EditText.BufferType.SPANNABLE);
                        setTitle(DataStore.namesSection.get(DataStore.selectedSection));
                        editMainScroll();
                        dialog.cancel();
                    }
                    lastSelectedSection[0] = which;
                    lastClickTime[0] = nowTime;
                }
            });
            // CHANGE FOLDER selection OK
            changeFileBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // now the file selection becomes permanent
                    DataStore.selectedSection = selectedSectionTemp[0];
                    String txt = convertDataStoreToIniText();       // prepare DataStore for write output
                    writeAppFileData(getApplicationContext(), txt); // write to app data file
                    // pick the data section from DataStore, format it and show it in editMain, update App titlebar
                    String sectionedText = DataStore.dataSection.get(DataStore.selectedSection);
                    SpannableString ss = formatText(sectionedText);
                    editMain.setText(ss, EditText.BufferType.SPANNABLE);
                    editMainScroll();
                    setTitle(DataStore.namesSection.get(DataStore.selectedSection));
                    dialog.cancel();
                }
            });
            // CHANGE FOLDER selection Cancel
            changeFileBuilder.setNegativeButton(R.string.back, null);
            // CHANGE FILE selection MORE FILE OPTIONS
            changeFileBuilder.setNeutralButton(R.string.more, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogMore, int which) {
                    //  MORE FOLDER OPTIONS
                    final String itemText = DataStore.namesSection.get(selectedSectionTemp[0]);
                    final CharSequence[] items = {getString(R.string.main14), getString(R.string.main14a), getString(R.string.main15), getString(R.string.main16), getString(R.string.main17), getString(R.string.main18)};
                    final AlertDialog.Builder moreBuilder = new AlertDialog.Builder(changeFileBuilder.getContext());
                    moreBuilder.setTitle(getString(R.string.main19) + itemText + "'");
                    moreBuilder.setItems(items, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogRename, int which) {
                            // MORE FOLDER OPTIONS - Rename
                            if (which == 0) {
                                // folder name rename dialog
                                final Context context = moreBuilder.getContext();
                                final EditText input = new EditText(context);
                                input.setInputType(InputType.TYPE_CLASS_TEXT);
                                input.setTextColor(Color.BLACK);
                                input.setText(DataStore.namesSection.get(selectedSectionTemp[0]));
                                showEditTextContextMenu(input, false);  // suppress edit context menu
                                final AlertDialog.Builder renameBuilder = new AlertDialog.Builder(context);
                                renameBuilder.setTitle(R.string.main20);
                                renameBuilder.setView(input);
                                // folder name rename ok
                                renameBuilder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialogChange, int which) {
                                        Editable text = input.getText();
                                        DataStore.namesSection.set(selectedSectionTemp[0], text.toString());
                                        DataStore.selectedSection = selectedSectionTemp[0];
                                        String txt = convertDataStoreToIniText();       // prepare DataStore for write output
                                        writeAppFileData(getApplicationContext(), txt); // write to app data file
                                        // call menu item programmatically: https://stackoverflow.com/questions/30002471/how-to-programmatically-trigger-click-on-a-menuitem-in-android
                                        findViewById(R.id.action_ChangeFolder).callOnClick();
                                        // update app title bar
                                        setTitle(DataStore.namesSection.get(DataStore.selectedSection));
                                        // close parent dialog
                                        changeFolderDialog.cancel();
                                        // but show more dialog
                                        moreDialog.setTitle(getString(R.string.main21) + DataStore.namesSection.get(selectedSectionTemp[0]) + "'");
                                        moreDialog.show();
                                    }
                                });
                                // folder name rename cancel
                                renameBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialogRename, int which) {
                                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                                        moreDialog.show();
                                    }
                                });
                                // folder name rename show
                                Dialog renameDialog = renameBuilder.show();
                                int textViewId = renameBuilder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
                                TextView tv = renameDialog.findViewById(textViewId);
                                tv.setTextColor(Color.BLACK);
                                // tricky way to let the keyboard popup
                                input.requestFocus();
                                if (myTimer == null) {
                                    myTimer = new Timer();
                                    myTimer.schedule(new TimerTask() {
                                        @Override
                                        public void run() {
                                            TimerMethod(input);
                                        }
                                    }, 50, 10);
                                }
                            }
                            // MORE FOLDER OPTIONS - Timestamp
                            if (which == 1) {
                                final CharSequence[] items = {"no timestamp", "hh:mm", "hh:mm:ss"};
                                final int[] selection = {DataStore.timeSection.get(selectedSectionTemp[0])};
//                                final int[] newSelection = {DataStore.timeSection.get(selectedSectionTemp[0])};
                                AlertDialog dialog = null;
                                AlertDialog.Builder builder = new AlertDialog.Builder(moreBuilder.getContext());
                                builder.setTitle("Timestamp setting");
                                builder.setSingleChoiceItems(items, selection[0], new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        selection[0] = which;
                                    }
                                });
                                builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        DataStore.timeSection.set(selectedSectionTemp[0], selection[0]); // ok takes over the recently selected option
                                        String txt = convertDataStoreToIniText();                        // prepare DataStore for write output
                                        writeAppFileData(getApplicationContext(), txt);                  // write to app data file
                                        moreDialog.show();                                               // show more dlg again
                                    }
                                });
                                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        moreDialog.show();
                                    }
                                });
                                dialog = builder.create();
                                ListView listView = dialog.getListView();
                                listView.setDivider(new ColorDrawable(Color.GRAY));
                                listView.setDividerHeight(2);
                                dialog.show();
                                int textViewId = builder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
                                TextView tv = dialog.findViewById(textViewId);
                                tv.setTextColor(Color.BLACK);
//                                int divierId = builder.getContext().getResources().getIdentifier("android:id/titleDivider", null, null);
//                                View divider = dialog.findViewById(divierId);
//                                divider.setBackgroundColor(Color.BLUE);  <-- exception in Android 10
                            }
                            // MORE FOLDER OPTIONS - Clear Data
                            if (which == 2) {
                                final Context context = moreBuilder.getContext();
                                final AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                builder.setTitle(getString(R.string.main22) + itemText + "' ?");
                                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialogChange, int which) {
                                        DataStore.dataSection.set(selectedSectionTemp[0], "");
                                        DataStore.selectedSection = selectedSectionTemp[0];
                                        String txt = convertDataStoreToIniText();       // prepare DataStore for write output
                                        writeAppFileData(getApplicationContext(), txt); // write to app data file
                                        // close parent dialog
                                        changeFolderDialog.cancel();
                                        // restart with resume()
                                        reReadAppFileData = true;
                                        onResume();
                                    }
                                });
                                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialogRename, int which) {
                                        moreDialog.show();
                                    }
                                });
                                Dialog dialog = builder.show();
                                int textViewId = builder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
                                TextView tv = dialog.findViewById(textViewId);
                                tv.setTextColor(Color.BLACK);
                            }
                            //  MORE FOLDER OPTIONS Remove Folder
                            if (which == 3) {
                                final Context context = moreBuilder.getContext();
                                final AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                builder.setTitle(getString(R.string.main23) + itemText + "' ?");
                                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialogChange, int which) {
                                        if ( DataStore.namesSection.size() > 1 ) {
                                            DataStore.namesSection.remove(selectedSectionTemp[0]);
                                            DataStore.kbdSection.remove(selectedSectionTemp[0]);
                                            DataStore.selectedSection = selectedSectionTemp[0] - 1 > 0 ? selectedSectionTemp[0] - 1 : 0;
                                            DataStore.dataSection.remove(selectedSectionTemp[0]);
                                        } else {
                                            DataStore.namesSection.set(selectedSectionTemp[0], getString(R.string.main24));
                                            DataStore.kbdSection.set(selectedSectionTemp[0], InputType.TYPE_CLASS_TEXT);
                                            DataStore.selectedSection = selectedSectionTemp[0];
                                            DataStore.dataSection.set(selectedSectionTemp[0], "");
                                        }
                                        String txt = convertDataStoreToIniText();
                                        writeAppFileData(getApplicationContext(), txt);
                                        // close parent dialog
                                        changeFolderDialog.cancel();
                                        // restart with resume()
                                        reReadAppFileData = true;
                                        onResume();
                                    }
                                });
                                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialogRename, int which) {
                                        moreDialog.show();
                                    }
                                });
                                Dialog dialog = builder.show();
                                int textViewId = builder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
                                TextView tv = dialog.findViewById(textViewId);
                                tv.setTextColor(Color.BLACK);
                            }
                            //  MORE FOLDER OPTIONS Move Folder one step up
                            if (which == 4) {
                                if ( DataStore.namesSection.size() < 2 || selectedSectionTemp[0] == 0 ) {
                                    Toast.makeText(getApplicationContext(), R.string.main25, Toast.LENGTH_SHORT).show();
                                    moreDialog.show();
                                    return;
                                }
                                final Context context = moreBuilder.getContext();
                                final AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                builder.setTitle(getString(R.string.main26) + itemText + getString(R.string.main27));
                                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialogChange, int which) {
                                        // tmp save current -1
                                        String nameTmp = DataStore.namesSection.get(selectedSectionTemp[0]-1);
                                        int kbdTmp = DataStore.kbdSection.get(selectedSectionTemp[0]-1);
                                        int selectionTmp = DataStore.selectedSection = selectedSectionTemp[0]-1;
                                        String dataTmp = DataStore.dataSection.get(selectedSectionTemp[0]-1);
                                        // copy current one level up
                                        DataStore.namesSection.set(selectedSectionTemp[0]-1, DataStore.namesSection.get(selectedSectionTemp[0]));
                                        DataStore.kbdSection.set(selectedSectionTemp[0]-1, DataStore.kbdSection.get(selectedSectionTemp[0]));
                                        DataStore.selectedSection = selectedSectionTemp[0]-1;
                                        DataStore.dataSection.set(selectedSectionTemp[0]-1, DataStore.dataSection.get(selectedSectionTemp[0]));
                                        // copy tmp to current
                                        DataStore.namesSection.set(selectedSectionTemp[0], nameTmp);
                                        DataStore.kbdSection.set(selectedSectionTemp[0], kbdTmp);
                                        DataStore.selectedSection = selectionTmp;
                                        DataStore.dataSection.set(selectedSectionTemp[0], dataTmp);
                                        // make change permanent
                                        String txt = convertDataStoreToIniText();
                                        writeAppFileData(getApplicationContext(), txt);
                                        reReadAppFileData = true;
                                        onResume();
                                    }
                                });
                                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialogRename, int which) {
                                        moreDialog.show();
                                    }
                                });
                                Dialog dialog = builder.show();
                                int textViewId = builder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
                                TextView tv = dialog.findViewById(textViewId);
                                tv.setTextColor(Color.BLACK);
                            }
                            //  MORE FILE OPTIONS Add new Folder
                            if (which == 5) {
                                // reject add item
                                if ( DataStore.namesSection.size() >= SECTIONS_COUNT ) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(moreBuilder.getContext());
                                    builder.setTitle(R.string.note);
                                    builder.setMessage(getString(R.string.main28) + SECTIONS_COUNT + getString(R.string.main29));
                                    builder.setIcon(android.R.drawable.ic_dialog_alert);
                                    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            moreDialog.show();
                                        }
                                    });
                                    Dialog dialog = builder.show();
                                    int textViewId = builder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
                                    TextView tv = dialog.findViewById(textViewId);
                                    tv.setTextColor(Color.BLACK);
                                    textViewId = builder.getContext().getResources().getIdentifier("android:id/message", null, null);
                                    tv = dialog.findViewById(textViewId);
                                    tv.setTextColor(Color.BLACK);
                                    return;
                                }
                                // real add item
                                final Context context = moreBuilder.getContext();
                                final EditText input = new EditText(context);
                                input.setInputType(InputType.TYPE_CLASS_TEXT);
                                input.setTextColor(Color.BLACK);
                                input.setText(R.string.main24);
                                showEditTextContextMenu(input, false);
                                final AlertDialog.Builder addBuilder = new AlertDialog.Builder(context);
                                addBuilder.setTitle(R.string.main30);
                                addBuilder.setView(input);
                                addBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialogChange, int which) {
                                        DataStore.dataSection.add("");
                                        DataStore.namesSection.add(input.getText().toString());
                                        DataStore.kbdSection.add(InputType.TYPE_CLASS_TEXT);
                                        DataStore.selectedSection = DataStore.namesSection.size() - 1;
                                        String txt = convertDataStoreToIniText();
                                        writeAppFileData(getApplicationContext(), txt);
                                        reReadAppFileData = true;
                                        onResume();
                                    }
                                });
                                addBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialogRename, int which) {
                                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                                        moreDialog.show();
                                    }
                                });
                                Dialog dialog = addBuilder.show();
                                int textViewId = addBuilder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
                                TextView tv = dialog.findViewById(textViewId);
                                tv.setTextColor(Color.BLACK);
                                // tricky way to let the keyboard popup
                                input.requestFocus();
                                if (myTimer == null) {
                                    myTimer = new Timer();
                                    myTimer.schedule(new TimerTask() {
                                        @Override
                                        public void run() {
                                            TimerMethod(input);
                                        }
                                    }, 50, 10);
                                }
                            }
                        }
                    });
                    // MORE FOLDER OPTIONS back/cancel
                    moreBuilder.setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            changeFolderDialog.show();
                        }
                    });
                    // MORE FILE OPTIONS show
                    moreDialog = moreBuilder.create();
                    ListView listView = moreDialog.getListView();
                    listView.setDivider(new ColorDrawable(Color.GRAY));
                    listView.setDividerHeight(2);
                    moreDialog.show();
                    int textViewId = moreBuilder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
                    TextView tv = moreDialog.findViewById(textViewId);
                    tv.setTextColor(Color.BLACK);
                }
            });
            // CHANGE FOLDER finally show change folder dialog
            changeFolderDialog = changeFileBuilder.create();
            ListView listView = changeFolderDialog.getListView();
            listView.setDivider(new ColorDrawable(Color.GRAY));
            listView.setDividerHeight(2);
            changeFolderDialog.show();
            int textViewId = changeFileBuilder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
            TextView tv = changeFolderDialog.findViewById(textViewId);
            tv.setTextColor(Color.BLACK);
        }

        return super.onOptionsItemSelected(item);
    }

    // EditText has a context menu. It is annoying, when popping up in an AlertBuilder. https://stackoverflow.com/questions/41673185/disable-edittext-context-menu
    void showEditTextContextMenu(EditText input, final boolean show) {
        input.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                //to keep the text selection capability available ( selection cursor)
                return true;
            }
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                if ( !show ) {
                    menu.clear();
                }
                return false;
            }
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }
            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        });
    }

    // simple AlertBuilder ok box
    void okBox(String title, String message){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage("\n" + message);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        Dialog dialog = builder.show();
        int textViewId = builder.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
        TextView tv = dialog.findViewById(textViewId);
        tv.setTextColor(Color.BLACK);
        textViewId = builder.getContext().getResources().getIdentifier("android:id/message", null, null);
        tv = dialog.findViewById(textViewId);
        tv.setTextColor(Color.BLACK);
    }

}
