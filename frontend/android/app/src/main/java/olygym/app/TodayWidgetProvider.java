package olygym.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
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
 * It is Home's hero, condensed: same tracked uppercase overline, same mark in its circle, same
 * title proportions, same accent container. It draws and decides nothing else. lib/widget-model.js
 * writes widget.json (the whole current week as finished strings per day, plus the two colours to
 * paint them in) into the private data directory beside the state mirror, and this class picks
 * days[today] and sets the views. All copy, all date formatting and the whole reading of the plan
 * stay in JS, where they are unit-tested: re-deriving "what am I training today" from S.weeks in
 * Java would be a second implementation of lib/weeks.js, and the two would drift on the first
 * change to the week model.
 *
 * The card and its mark are painted as bitmaps at the widget's own size. They cannot come from
 * drawables: the fill is the accent's --m3-primary-container and the accent is one of eight
 * user-chosen seeds, so a resource would have to be shipped sixteen times over. JS reads both
 * colours off the DOM and they travel in widget.json.
 *
 * The palette follows the APP's theme, not the phone's night mode: someone running OlyGym dark
 * wants the widget dark on a light phone.
 *
 * The file is the contract. Missing (installed but never opened), unreadable, or with no entry for
 * today (the week rolled over while the app was closed) all land on the same honest state instead
 * of presenting a stale session as today's. Waking with the app closed is the launcher's own
 * updatePeriodMillis tick, which re-reads the file and re-picks days[today]; the JS side pokes
 * {@link #refreshAll} for the change you just made.
 */
public class TodayWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "OlyGymWidget";
    /** Mirrors WIDGET_FILE in frontend/src/lib/mobile.js. */
    static final String WIDGET_FILE = "widget.json";
    /** The m3 shape-xl the hero card uses, and the corner the preview drawable shares. */
    private static final int CARD_RADIUS_DP = 28;
    private static final int MARK_DP = 36;
    /** What the launcher is asked for (res/xml/widget_today_info.xml), used before it tells us. */
    private static final int DEFAULT_W_DP = 300;
    private static final int DEFAULT_H_DP = 190;
    /** Fallback ink and fill for a widget.json that was written by a broken build. */
    private static final int FALLBACK_CONTAINER = 0xFF4C3889;
    private static final int FALLBACK_INK = 0xFFE9DDFF;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context, manager, id));
    }

    /** A resize repaints the card, which is a bitmap and therefore sized to the view. */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        manager.updateAppWidget(id, build(context, manager, id));
    }

    /**
     * Redraw every placed instance. Called from JS ({@link TodayWidgetPlugin}) right after
     * widget.json is rewritten and after a theme or accent change, so the home screen you look at
     * the moment you leave the app is already current — the launcher's own tick would lag both by
     * up to half an hour.
     */
    static void refreshAll(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, TodayWidgetProvider.class));
            for (int id : ids) manager.updateAppWidget(id, build(context, manager, id));
        } catch (Exception e) {
            Log.w(TAG, "widget refresh failed", e);
        }
    }

    private static RemoteViews build(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today);
        JSONObject root = readWidget(context);
        JSONObject day = root == null ? null : dayOf(root, todayIso());
        JSONObject colors = root == null ? null : root.optJSONObject("colors");
        int ink = color(FALLBACK_INK, colors, "onContainer");
        int container = color(FALLBACK_CONTAINER, colors, "container");
        int mark = color(mix(ink, container, 0.14f), colors, "circle");

        // The card, at the size the launcher gives this instance. Bitmaps rather than drawables
        // because the fill is the user's accent (see the class comment).
        int w = Math.max(1, dp(context, option(manager, id, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_W_DP)));
        int h = Math.max(1, dp(context, option(manager, id, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_H_DP)));
        // The root behind the card too: the card is a rounded bitmap, so its corner pixels are
        // transparent and the picker's static drawable would otherwise show through them in the
        // theme it was not drawn for.
        views.setInt(R.id.w_root, "setBackgroundColor", container);
        views.setImageViewBitmap(R.id.w_bg, makeCard(w, h, dp(context, CARD_RADIUS_DP), container));
        views.setImageViewBitmap(R.id.w_mark, makeCircle(dp(context, MARK_DP), mark));

        // Two text levels plus the date: the mark, the title and the status line carry the ink, and
        // the overline, the second line and the body sit behind the alphas set in the layout.
        int[] texts = { R.id.w_over, R.id.w_title, R.id.w_sub, R.id.w_body, R.id.w_foot };
        for (int view : texts) views.setTextColor(view, ink);

        if (day == null) {
            views.setImageViewResource(R.id.w_icon, R.drawable.ic_w_moon);
            views.setInt(R.id.w_icon, "setColorFilter", ink);
            views.setTextViewText(R.id.w_over, "");
            views.setTextViewText(R.id.w_title, context.getString(R.string.widget_stale));
            views.setTextViewText(R.id.w_sub, "");
            views.setTextViewText(R.id.w_body, context.getString(R.string.widget_no_data));
            views.setTextViewText(R.id.w_foot, "");
        } else {
            // optString, not getString: a field JS never wrote must not throw the whole widget
            // away. An empty line is a worse widget, not a broken one.
            views.setImageViewResource(R.id.w_icon, iconOf(day.optString("icon")));
            views.setInt(R.id.w_icon, "setColorFilter", ink);
            views.setTextViewText(R.id.w_over, day.optString("overline"));
            views.setTextViewText(R.id.w_title, day.optString("title"));
            views.setTextViewText(R.id.w_sub, day.optString("subtitle"));
            views.setTextViewText(R.id.w_body, day.optString("body"));
            views.setTextViewText(R.id.w_foot, day.optString("footer"));
        }
        views.setOnClickPendingIntent(R.id.w_root, openApp(context));
        return views;
    }

    /** The state's mark in the app's own stroke set. */
    private static int iconOf(String name) {
        if ("timer".equals(name)) return R.drawable.ic_stat_rest;   // the same timer glyph the rest card uses
        if ("check".equals(name)) return R.drawable.ic_w_check;
        if ("moon".equals(name)) return R.drawable.ic_w_moon;
        return R.drawable.ic_w_dumbbell;
    }

    private static int option(AppWidgetManager manager, int id, String key, int fallback) {
        try {
            Bundle o = manager.getAppWidgetOptions(id);
            int v = o == null ? 0 : o.getInt(key);
            return v > 0 ? v : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    /** A #rrggbb from the JSON, or the given fallback when it is missing or malformed. */
    private static int color(int fallback, JSONObject colors, String key) {
        try {
            String v = colors == null ? null : colors.getString(key);
            return v != null && v.matches("#?[0-9a-fA-F]{6}") ? Color.parseColor(v) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** The mark's wash, the same maths as the app's color-mix(in srgb, ink 14%, container). */
    static int mix(int ink, int container, float t) {
        int r = Math.round(Color.red(ink) * t + Color.red(container) * (1 - t));
        int g = Math.round(Color.green(ink) * t + Color.green(container) * (1 - t));
        int b = Math.round(Color.blue(ink) * t + Color.blue(container) * (1 - t));
        return Color.rgb(r, g, b);
    }

    private static Bitmap makeCard(int w, int h, int radius, int color) {
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        new Canvas(bitmap).drawRoundRect(new RectF(0, 0, w, h), radius, radius, paint);
        return bitmap;
    }

    private static Bitmap makeCircle(int d, int color) {
        Bitmap bitmap = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        new Canvas(bitmap).drawCircle(d / 2f, d / 2f, d / 2f, paint);
        return bitmap;
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
