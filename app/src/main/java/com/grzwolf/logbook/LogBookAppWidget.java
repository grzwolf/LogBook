package com.grzwolf.logbook;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import androidx.preference.PreferenceManager;

/**
 * Implementation of App Widget functionality.
 */
public class LogBookAppWidget extends AppWidgetProvider {

    // step 1 click on widget: https://stackoverflow.com/questions/2748590/clickable-widgets-in-android
    public static String YOUR_AWESOME_ACTION = "YourAwesomeAction";

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {

        CharSequence widgetText = " ! ";
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.logbook_app_widget);
        views.setTextViewText(R.id.appwidget_text, widgetText);

        // step 2 click on widget: connect user defined action with receiver
        Intent intent = new Intent(context, LogBookAppWidget.class);
        intent.setAction(YOUR_AWESOME_ACTION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.appwidget_text, pendingIntent);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    // step 3 click on widget: receiver for click event on widget
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent.getAction().equals(YOUR_AWESOME_ACTION)) {
            // prepare dealing with main activity
            Intent intentDlg = new Intent (context, MainActivity.class);
            intentDlg.setFlags (Intent.FLAG_ACTIVITY_NEW_TASK);
            // 0.0.0.14 if user clicks on " ! " Widget, the input dialog inside mainactivity shall immediately open: provide flag in shared preferences
            // NOTE: previously a similar behaviour was done via Bundle putExtra, but putExtra is resolved in onCreate - but we need it in onResume
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor spe = sharedPref.edit();
            spe.putBoolean("clickPlus", true);
            spe.commit();
            // start main activity
            context.startActivity (intentDlg);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

