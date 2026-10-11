package olygym.app;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

import java.util.List;

/**
 * JS face of the home-screen widget (lib/mobile.js). Two jobs: the file you just rewrote is the
 * widget's whole state, so redraw it; and hand back the taps the widget's controls collected while
 * this WebView was asleep, so the app can apply them (lib/widget-actions.js).
 *
 * No state is mirrored here and no listener is registered, unlike {@link RestNotificationPlugin}:
 * widget.json is the authority for what to draw, and the queue in {@link WidgetActions} is the
 * authority for what happened. Nothing has to be adopted asynchronously.
 */
@CapacitorPlugin(name = "TodayWidget")
public class TodayWidgetPlugin extends Plugin {

    @PluginMethod
    public void refresh(PluginCall call) {
        TodayWidgetProvider.refreshAll(getContext());
        call.resolve();
    }

    /** The taps the widget collected while JS was asleep, oldest first. Unreadable reads as none. */
    @PluginMethod
    public void pending(PluginCall call) {
        JSObject out = new JSObject();
        JSArray actions = new JSArray();
        List<JSONObject> queued = WidgetActions.list(getContext());
        for (JSONObject a : queued) actions.put(a);
        out.put("actions", actions);
        call.resolve(out);
    }

    @PluginMethod
    public void clear(PluginCall call) {
        WidgetActions.clear(getContext());
        call.resolve();
    }
}
