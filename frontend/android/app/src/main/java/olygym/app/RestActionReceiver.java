package olygym.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * The notification's buttons, handled with no app running: it only touches the native mirror
 * ({@link RestNotification}) and tells the live JS bridge when there is one. JS adopts the mirror
 * when it returns (lib/rest-notification.js adoptNativeState), so nothing here has to understand
 * the workout — which is why Set-done is not one of these actions.
 */
public class RestActionReceiver extends BroadcastReceiver {

    private static final long STEP_MS = 15000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        Log.i("OlyGymRest", "action " + action);
        SharedPreferences p = RestNotification.prefs(context);
        boolean active = p.getBoolean("active", false);

        if (RestNotification.ACTION_EXPIRE.equals(action)) {
            RestNotification.clear(context);
            if (active) RestNotificationPlugin.emit("expire");
            return;
        }
        if (!active) return;

        if (RestNotification.ACTION_SKIP.equals(action)) {
            RestNotification.clear(context);
            RestNotificationPlugin.emit("skip");
            return;
        }

        long delta = 0L;
        String type = null;
        if (RestNotification.ACTION_PLUS15.equals(action)) { delta = STEP_MS; type = "plus15"; }
        else if (RestNotification.ACTION_MINUS15.equals(action)) { delta = -STEP_MS; type = "minus15"; }
        if (type == null) return;

        long endsAt = p.getLong("endsAtMs", 0L) + delta;
        if (endsAt <= System.currentTimeMillis()) {
            // Taking off more than is left means "I'm ready now", exactly like the in-app bar.
            RestNotification.clear(context);
            RestNotificationPlugin.emit("skip");
            return;
        }
        p.edit().putLong("endsAtMs", endsAt).apply();
        RestNotification.post(context);
        RestNotificationPlugin.emit(type);
    }
}
