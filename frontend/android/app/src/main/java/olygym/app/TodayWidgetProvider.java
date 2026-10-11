package olygym.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * "Today's workout" — the home-screen widget: what is planned for today, and whether it is done.
 *
 * It draws and decides nothing. lib/widget-model.js writes widget.json (the whole current week as
 * four finished strings per day, plus which palette to use) into the private data directory beside
 * the state mirror, and this class picks days[today] and sets four TextViews. All copy, all date
 * formatting and the whole reading of the plan stay in JS, where they are unit-tested: re-deriving
 * "what am I training today" from S.weeks in Java would be a second implementation of lib/weeks.js,
 * and the two would drift on the first change to the week model.
 *
 * The palette follows the APP's theme, not the phone's night mode: someone running OlyGym dark
 * wants the widget dark on a light phone. Both palettes live in res/values/colors.xml and are
 * picked here from widget.json's "theme". Before the app has ever saved there is no theme to read,
 * so the stale state falls back to the phone's night mode instead of guessing.
 *
 * The file is the contract. Missing (installed but never opened), unreadable, or with no entry for
 * today (the week rolled over while the app was closed) all land on the same honest state instead
 * of presenting a stale session as today's.
 *
 * Waking with the app closed is the launcher's own updatePeriodMillis tick, which re-reads the file
 * and re-picks days[today]; that, not a recomputation, is what keeps it right across midnight. The
 * JS side pokes {@link #refreshAll} for the change you just made.
 */
public class TodayWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "OlyGymWidget";
    /** Mirrors WIDGET_FILE in frontend/src/lib/mobile.js. */
    static final String WIDGET_FILE = "widget.json";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        RemoteViews views = build(context);
        for (int id : ids) manager.updateAppWidget(id, views);
    }

    /**
     * Redraw every placed instance. Called from JS ({@link TodayWidgetPlugin}) right after
     * widget.json is rewritten, so the home screen you look at the moment you leave the app is
     * already current, and after a theme change — the launcher's own tick would lag both by up to
     * half an hour.
     */
    static void refreshAll(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, TodayWidgetProvider.class));
            if (ids.length == 0) return;
            RemoteViews views = build(context);
            for (int id : ids) manager.updateAppWidget(id, views);
        } catch (Exception e) {
            Log.w(TAG, "widget refresh failed", e);
        }
    }

    private static RemoteViews build(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today);
        // One read for the whole build: the theme and today's entry come out of the same object.
        JSONObject root = readWidget(context);
        JSONObject day = root == null ? null : dayOf(root, todayIso());
        boolean light = root == null ? !nightMode(context) : "light".equals(root.optString("theme"));

        views.setInt(R.id.widget_root, "setBackgroundResource",
                light ? R.drawable.widget_bg_light : R.drawable.widget_bg_dark);
        // Two text levels plus the date: no third role, so the overline/wrapper/body all share the
        // variant colour and the title carries the one emphasis.
        int onSurface = context.getColor(light ? R.color.widget_on_surface_light : R.color.widget_on_surface_dark);
        int variant = context.getColor(
                light ? R.color.widget_on_surface_variant_light : R.color.widget_on_surface_variant_dark);
        views.setTextColor(R.id.w_over, variant);
        views.setTextColor(R.id.w_title, onSurface);
        views.setTextColor(R.id.w_body, variant);
        views.setTextColor(R.id.w_foot, variant);

        if (day == null) {
            views.setTextViewText(R.id.w_over, "");
            views.setTextViewText(R.id.w_title, context.getString(R.string.widget_stale));
            views.setTextViewText(R.id.w_body, context.getString(R.string.widget_no_data));
            views.setTextViewText(R.id.w_foot, "");
        } else {
            // optString, not getString: a field JS never wrote must not throw the whole widget
            // away. An empty line is a worse widget, not a broken one.
            views.setTextViewText(R.id.w_over, day.optString("overline"));
            views.setTextViewText(R.id.w_title, day.optString("title"));
            views.setTextViewText(R.id.w_body, day.optString("body"));
            views.setTextViewText(R.id.w_foot, day.optString("footer"));
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context));
        return views;
    }

    private static JSONObject readWidget(Context context) {
        try {
            File file = new File(context.getFilesDir(), WIDGET_FILE);
            if (!file.exists()) return null;
            return new JSONObject(readFully(file));
        } catch (Exception e) {
            // Truncated by a kill mid-write, or written by a newer build. Either way the widget
            // says it has nothing rather than guessing.
            Log.w(TAG, "widget.json unreadable", e);
            return null;
        }
    }

    /** The day entry for an ISO date, or null when that day is not in the file. */
    private static JSONObject dayOf(JSONObject root, String iso) {
        JSONObject days = root.optJSONObject("days");
        return days == null ? null : days.optJSONObject(iso);
    }

    /** No app theme to read yet: follow the phone, the way every other widget on the screen does. */
    private static boolean nightMode(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * ponytail: a ~1 KB synchronous read on the main thread. Every path into here is an update
     * broadcast, and the file is written by the same app on the same device; move it behind
     * goAsync() only if it ever shows up in a trace.
     */
    private static String readFully(File file) throws Exception {
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    /**
     * SimpleDateFormat, not LocalDate: java.time is API 26 and this app's minSdk is 24, and a core
     * library desugaring dependency is not worth one date string.
     */
    private static String todayIso() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static PendingIntent openApp(Context context) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) launch = new Intent();
        return PendingIntent.getActivity(
                context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
