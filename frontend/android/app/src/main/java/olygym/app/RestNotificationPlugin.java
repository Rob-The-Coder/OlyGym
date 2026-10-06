package olygym.app;

import android.content.SharedPreferences;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.lang.ref.WeakReference;

/**
 * JS face of the lock-screen rest timer (lib/rest-notification.js). It pushes the rest into the
 * native mirror the notification is drawn from — {@link RestNotification} — and reports what the
 * notification's buttons did while the app was away, so JS can adopt the result on resume. The
 * countdown is never pushed per second: SystemUI reads it from the mirror's end time.
 */
@CapacitorPlugin(name = "RestNotification")
public class RestNotificationPlugin extends Plugin {

    private static WeakReference<RestNotificationPlugin> instance = new WeakReference<>(null);

    @Override
    public void load() {
        instance = new WeakReference<>(this);
        Log.i("OlyGymRest", "plugin loaded");
        // A rest that outlived the process: re-post it, or clear it if it already ran out. If it
        // is still running, the ticker has to come back with it or the bar would freeze.
        RestNotification.post(getContext());
        if (RestNotification.prefs(getContext()).getBoolean("active", false)) {
            RestTimerService.start(getContext());
        }
    }

    /** The receiver's only way back to JS; a no-op when nothing is listening. */
    static void emit(String type) {
        RestNotificationPlugin p = instance.get();
        if (p != null) p.fire(type);
    }

    private void fire(String type) {
        try {
            JSObject data = new JSObject();
            data.put("type", type);
            notifyListeners("action", data);
        } catch (Exception e) {
            // The bridge can be gone even while the plugin object lingers; JS adopts on resume.
        }
    }

    @PluginMethod
    public void start(PluginCall call) {
        // Read numbers as Number, not getLong/getInt: those only accept a value that arrived as
        // that exact Java type, and a JS number can arrive as Integer, Long or Double depending
        // on its size. A silent default here would reject every rest with no visible reason.
        long endsAt = number(call, "endsAtMs", 0L);
        if (endsAt <= 0) { call.reject("endsAtMs is required"); return; }
        Integer forIdx = has(call, "forIdx") ? (int) number(call, "forIdx", 0L) : null;
        Log.i("OlyGymRest", "start end=" + endsAt + " total=" + number(call, "totalSec", 90L) + " forIdx=" + forIdx);
        RestNotification.save(
                getContext(),
                endsAt,
                (int) number(call, "totalSec", 90L),
                forIdx,
                call.getString("title", getContext().getString(R.string.rest_channel_name)),
                call.getString("text", ""),
                call.getString("sub", ""),
                call.getString("big", ""));
        RestNotification.post(getContext());
        RestTimerService.start(getContext());
        call.resolve();
    }

    private static boolean has(PluginCall call, String name) {
        return call.getData().opt(name) instanceof Number;
    }

    private static long number(PluginCall call, String name, long fallback) {
        Object v = call.getData().opt(name);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }

    @PluginMethod
    public void stop(PluginCall call) {
        RestTimerService.stop(getContext());
        RestNotification.clear(getContext());
        call.resolve();
    }

    @PluginMethod
    public void getState(PluginCall call) {
        SharedPreferences p = RestNotification.prefs(getContext());
        long endsAt = p.getLong("endsAtMs", 0L);
        boolean active = p.getBoolean("active", false) && endsAt > System.currentTimeMillis();
        JSObject o = new JSObject();
        o.put("active", active);
        if (active) {
            o.put("endsAtMs", endsAt);
            o.put("totalSec", p.getInt("totalSec", 0));
            if (p.contains("forIdx")) o.put("forIdx", p.getInt("forIdx", 0));
            o.put("title", p.getString("title", ""));
            o.put("text", p.getString("text", ""));
        }
        call.resolve(o);
    }
}
