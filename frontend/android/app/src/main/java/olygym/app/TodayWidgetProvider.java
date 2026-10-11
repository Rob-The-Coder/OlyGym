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
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * "Today's workout" — the home-screen widget: the exercise you are on, the set in front of you, and
 * the three controls that move through them.
 *
 * It is Home's hero, condensed: the same tracked uppercase overline, the same mark in its circle,
 * the same title proportions, the same accent container. It decides nothing. lib/widget-model.js
 * writes widget.json (the whole current week, with the running session as finished strings and one
 * line per set) into the private data directory beside the state mirror, and this class picks
 * days[today] and sets the views. All copy, all numbering and the whole reading of the plan stay in
 * JS, where they are unit-tested.
 *
 * The controls cannot reach the live session — the state is in the WebView and the widget is looked
 * at when the app is closed — so a tap is queued ({@link WidgetActions}) and applied here on top of
 * the snapshot, which is what lets the card repaint the instant you tap. The app drains the queue
 * when it comes back (lib/widget-actions.js). What a tap does not do is start the rest timer: the
 * rest belongs to the moment you tapped, and by the time the app returns that moment has passed.
 *
 * The card, its mark and the three buttons are painted as bitmaps at the widget's own size. They
 * cannot come from drawables: the fill is the accent's m3 primary-container and the accent is one
 * of eight user-chosen seeds, so a resource would have to be shipped sixteen times over. JS reads
 * both colours off the DOM and they travel in widget.json. The palette follows the APP's theme, not
 * the phone's — someone running OlyGym dark wants the widget dark on a light phone.
 *
 * The file is the contract. Missing (installed but never opened), unreadable, or with no entry for
 * today (the week rolled over while the app was closed) all land on the same honest state instead
 * of presenting a stale session as today's. Waking with the app closed is the launcher's own
 * updatePeriodMillis tick, which re-reads the file and re-picks days[today].
 */
public class TodayWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "OlyGymWidget";
    /** Mirrors WIDGET_FILE in frontend/src/lib/mobile.js. */
    static final String WIDGET_FILE = "widget.json";
    private static final int CARD_RADIUS_DP = 28;      // the m3 shape-xl the hero card uses
    private static final int MARK_DP = 36;
    private static final int CONTROL_DP = 44;          // an M3 icon button, and the app's own target floor
    private static final int GLYPH_DP = 22;
    private static final int PAD_DP = 16;
    private static final int GAP_DP = 8;
    private static final int DEFAULT_W_DP = 300;
    private static final int DEFAULT_H_DP = 190;
    private static final int FALLBACK_CONTAINER = 0xFF4C3889;
    private static final int FALLBACK_INK = 0xFFE9DDFF;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context, manager, id));
    }

    /** A resize repaints the card and the buttons, which are bitmaps and therefore sized to the view. */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        manager.updateAppWidget(id, build(context, manager, id));
    }

    /**
     * Redraw every placed instance. Called from JS ({@link TodayWidgetPlugin}) right after
     * widget.json is rewritten and after a theme or accent change, and from
     * {@link WidgetActionReceiver} the instant a control is tapped.
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

    /** The session the card is showing, by date, or null. For the receiver's stamp. */
    static String currentSessionDate(Context context) {
        JSONObject root = readWidget(context);
        JSONObject day = root == null ? null : dayOf(root, todayIso());
        JSONObject session = day == null ? null : day.optJSONObject("session");
        String d = session == null ? null : session.optString("d", null);
        return d == null || d.isEmpty() ? null : d;
    }

    private static RemoteViews build(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today);
        JSONObject root = readWidget(context);
        JSONObject day = root == null ? null : dayOf(root, todayIso());
        JSONObject colors = root == null ? null : root.optJSONObject("colors");
        int ink = color(FALLBACK_INK, colors, "onContainer");
        int container = color(FALLBACK_CONTAINER, colors, "container");
        int wash = color(mix(ink, container, 0.14f), colors, "circle");

        int w = Math.max(1, dp(context, option(manager, id, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_W_DP)));
        int h = Math.max(1, dp(context, option(manager, id, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_H_DP)));

        // The root as well as the card: the card is a rounded bitmap, so its corner pixels are
        // transparent and the picker's static drawable would show through them.
        views.setInt(R.id.w_root, "setBackgroundColor", container);
        views.setImageViewBitmap(R.id.w_bg, makeCard(w, h, dp(context, CARD_RADIUS_DP), container));
        views.setImageViewBitmap(R.id.w_mark, makeCircle(dp(context, MARK_DP), wash));

        int[] texts = { R.id.w_over, R.id.w_title, R.id.w_sub, R.id.w_now, R.id.w_scheme };
        for (int view : texts) views.setTextColor(view, ink);

        if (day == null) {
            views.setImageViewResource(R.id.w_icon, R.drawable.ic_w_moon);
            views.setInt(R.id.w_icon, "setColorFilter", ink);
            views.setTextViewText(R.id.w_over, "");
            views.setTextViewText(R.id.w_title, context.getString(R.string.widget_stale));
            views.setTextViewText(R.id.w_sub, "");
            views.setTextViewText(R.id.w_now, "");
            views.setTextViewText(R.id.w_scheme, context.getString(R.string.widget_no_data));
            views.setViewVisibility(R.id.w_prog, View.GONE);
            views.setViewVisibility(R.id.w_acts, View.GONE);
        } else {
            views.setImageViewResource(R.id.w_icon, iconOf(day.optString("icon")));
            views.setInt(R.id.w_icon, "setColorFilter", ink);
            views.setTextViewText(R.id.w_over, day.optString("overline"));
            views.setTextViewText(R.id.w_title, day.optString("title"));
            views.setTextViewText(R.id.w_sub, day.optString("subtitle"));
            JSONObject session = day.optJSONObject("session");
            if (session == null) {
                // Waiting to be started, or a rest day: one line, and nothing to control yet.
                views.setTextViewText(R.id.w_now, day.optString("now"));
                views.setTextViewText(R.id.w_scheme, day.optString("scheme"));
                views.setViewVisibility(R.id.w_prog, View.GONE);
                views.setViewVisibility(R.id.w_acts, View.GONE);
            } else {
                paintSession(context, views, session, ink, container, wash, w);
            }
        }
        views.setOnClickPendingIntent(R.id.w_root, openApp(context));
        return views;
    }

    /**
     * The session, with whatever the controls queued while the app was away applied on top.
     *
     * A projection, not a second model: the snapshot in widget.json already holds each unit with one
     * finished line per set, so a tick is "one more set done" and a move is "one unit along". The
     * app applies the same queue to the real session with lib/widget-actions.js, and the two agree
     * because both are walking the same list in the same order.
     */
    private static void paintSession(Context context, RemoteViews views, JSONObject session,
                                     int ink, int container, int wash, int width) {
        JSONArray units = session.optJSONArray("units");
        int count = units == null ? 0 : units.length();
        int index = Math.max(0, Math.min(count - 1, session.optInt("index")));
        int[] extra = new int[Math.max(1, count)];

        String day = session.optString("d", null);
        List<JSONObject> queued = WidgetActions.list(context);
        for (JSONObject a : queued) {
            String k = a.optString("k");
            // A queue that outlived its session must not tick this one.
            String d = a.optString("d", null);
            if (d != null && day != null && !d.equals(day)) continue;
            if (count == 0) continue;
            if ("next".equals(k)) index = Math.min(index + 1, count - 1);
            else if ("prev".equals(k)) index = Math.max(index - 1, 0);
            else if ("check".equals(k)) {
                JSONObject u = units.optJSONObject(index);
                if (u != null && u.optInt("done") + extra[index] < u.optInt("sets")) extra[index]++;
            }
        }

        JSONObject unit = count == 0 ? null : units.optJSONObject(index);
        String title = unit == null ? "" : unit.optString("title");
        JSONArray schemes = unit == null ? null : unit.optJSONArray("schemes");
        int sets = unit == null ? 0 : unit.optInt("sets");
        // The line for the set in front of you; the last one once they are all ticked, because the
        // card has nowhere else to say "and that is the last".
        int line = schemes == null || schemes.length() == 0 ? 0
                : Math.max(0, Math.min(schemes.length() - 1, unit.optInt("done") + extra[index]));
        String scheme = schemes == null || schemes.length() == 0 ? "" : schemes.optString(line);

        views.setTextViewText(R.id.w_now, title);
        views.setTextViewText(R.id.w_scheme, scheme);
        views.setViewVisibility(R.id.w_prog, View.VISIBLE);
        views.setViewVisibility(R.id.w_acts, View.VISIBLE);

        // The session's own progress: every set in every unit, the ticked ones counted, including
        // whatever the controls queued. Nothing is stored twice — the units already carry both.
        int totalSets = 0;
        int doneSets = 0;
        for (int i = 0; i < count; i++) {
            JSONObject u = units.optJSONObject(i);
            if (u == null) continue;
            totalSets += u.optInt("sets");
            doneSets += u.optInt("done") + extra[i];
        }
        float pct = totalSets == 0 ? 0f : doneSets / (float) totalSets;
        views.setImageViewBitmap(R.id.w_prog,
                makeWave(context, width - dp(context, PAD_DP * 2), dp(context, 6), pct, wash, ink));

        int c = dp(context, CONTROL_DP);
        int pill = Math.max(dp(context, 96),
                width - dp(context, PAD_DP * 2 + CONTROL_DP * 2 + GAP_DP * 2));
        // Standard icon buttons in the card's own ink; the tick is the one filled control, so it is
        // the one the eye lands on. Both fills are the two colours already in hand.
        views.setImageViewBitmap(R.id.w_prev, makeControl(context, c, c, wash, R.drawable.ic_w_prev, ink));
        views.setImageViewBitmap(R.id.w_next, makeControl(context, c, c, wash, R.drawable.ic_w_next, ink));
        views.setImageViewBitmap(R.id.w_check, makeControl(context, pill, c, ink, R.drawable.ic_w_tick, container));
        views.setOnClickPendingIntent(R.id.w_prev, control(context, WidgetActionReceiver.ACTION_PREV, "prev", 1));
        views.setOnClickPendingIntent(R.id.w_check, control(context, WidgetActionReceiver.ACTION_CHECK, "check", 2));
        views.setOnClickPendingIntent(R.id.w_next, control(context, WidgetActionReceiver.ACTION_NEXT, "next", 3));
    }

    private static PendingIntent control(Context context, String action, String kind, int code) {
        Intent intent = new Intent(context, WidgetActionReceiver.class);
        intent.setAction(action);
        intent.putExtra(WidgetActionReceiver.EXTRA_KIND, kind);
        return PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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

    private static float dp(Context context, float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
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

    /**
     * The app's wavy progress indicator, stroked instead of masked.
     *
     * m3.components.css masks the fill with a 20x6 sine stroked at 3.4px and drifts it; RemoteViews
     * has neither a mask nor an animation, so the same sine is drawn here — same period, same
     * stroke, same amplitude (half the bar's height) — and repainted on each save. Still rather
     * than flowing, which is the one thing this cannot borrow.
     */
    private static Bitmap makeWave(Context context, int w, int h, float pct, int track, int fill) {
        int width = Math.max(1, w);
        Bitmap bitmap = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(track);
        float r = h / 2f;
        canvas.drawRoundRect(new RectF(0, 0, width, h), r, r, paint);

        int filled = Math.round(width * Math.max(0f, Math.min(1f, pct)));
        if (filled <= 0) return bitmap;

        float period = dp(context, 20f);
        float amp = h / 2f;
        Path path = new Path();
        path.moveTo(0, h / 2f - amp);
        for (float x = 1; x <= filled + 1; x += 1f) {
            path.lineTo(Math.min(x, filled), h / 2f + amp * (float) Math.sin(2 * Math.PI * x / period - Math.PI / 2));
        }
        Paint wave = new Paint(Paint.ANTI_ALIAS_FLAG);
        wave.setStyle(Paint.Style.STROKE);
        wave.setStrokeWidth(dp(context, 3.4f));
        wave.setStrokeCap(Paint.Cap.ROUND);
        wave.setStrokeJoin(Paint.Join.ROUND);
        wave.setColor(fill);
        canvas.save();
        // The app's mask clips the wave to the bar's own 6px; so does this, or the crests bleed out.
        canvas.clipRect(0, 0, filled, h);
        canvas.drawPath(path, wave);
        canvas.restore();
        return bitmap;
    }

    private static Bitmap makeCircle(int d, int color) {
        Bitmap bitmap = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        new Canvas(bitmap).drawCircle(d / 2f, d / 2f, d / 2f, paint);
        return bitmap;
    }

    /**
     * A control's whole face — fill and glyph — in one bitmap, because its fill is the card's ink and
     * no resource can carry a colour that follows the user's accent. A radius of half the shorter
     * side makes a circle at 44dp square and a pill in the middle of the row.
     */
    private static Bitmap makeControl(Context context, int w, int h, int fill, int glyphRes, int glyph) {
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(fill);
        float r = Math.min(w, h) / 2f;
        canvas.drawRoundRect(new RectF(0, 0, w, h), r, r, paint);
        Drawable d = context.getDrawable(glyphRes);
        if (d != null) {
            int size = dp(context, GLYPH_DP);
            d.setBounds((w - size) / 2, (h - size) / 2, (w + size) / 2, (h + size) / 2);
            d.setTint(glyph);
            d.draw(canvas);
        }
        return bitmap;
    }

    static JSONObject readWidget(Context context) {
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
