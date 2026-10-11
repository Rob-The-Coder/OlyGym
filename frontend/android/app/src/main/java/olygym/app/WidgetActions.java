package olygym.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The home-screen widget's outgoing queue: the taps its three controls collected while the WebView
 * was asleep.
 *
 * The widget cannot touch a running session — the state lives in the WebView, and the widget is
 * looked at when the app is closed — so a tap is written here, the widget repaints itself with the
 * tap applied on top of the snapshot it already has, and the app drains this on its way back up
 * (lib/mobile.js, lib/widget-actions.js). The same shape as the rest timer's -15s/+15s/Skip, which
 * is the one other place this app acts with no JS listening.
 *
 * One JSON array, in order, because a queue of two ticks is two ticks and reordering them would
 * tick the wrong set.
 */
final class WidgetActions {

    private static final String TAG = "OlyGymWidget";
    private static final String PREFS = "olygym_widget";
    private static final String KEY = "pending";
    /** A queue nobody drained is not worth keeping forever; the app is opened most days. */
    private static final int KEEP = 40;

    private WidgetActions() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Append one tap, stamped with the session it belongs to (null when there is none). */
    static void add(Context ctx, String kind, String day) {
        try {
            JSONArray now = read(ctx);
            JSONObject a = new JSONObject();
            a.put("k", kind);
            if (day != null) a.put("d", day);
            now.put(a);
            while (now.length() > KEEP) now.remove(0);
            prefs(ctx).edit().putString(KEY, now.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "could not queue a widget action", e);
        }
    }

    static JSONArray read(Context ctx) {
        try {
            String raw = prefs(ctx).getString(KEY, null);
            return raw == null ? new JSONArray() : new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static List<JSONObject> list(Context ctx) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray arr = read(ctx);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(o);
        }
        return out;
    }

    static void clear(Context ctx) {
        prefs(ctx).edit().remove(KEY).apply();
    }
}
