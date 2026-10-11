package olygym.app;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * JS face of the home-screen widget (lib/mobile.js). One method, one job: the file you just
 * rewrote is the widget's whole state, so redraw it.
 *
 * No state is mirrored here and no listener is registered, unlike {@link RestNotificationPlugin}:
 * widget.json is the authority and the provider reads it, so there is nothing for JS to adopt when
 * the app comes back.
 */
@CapacitorPlugin(name = "TodayWidget")
public class TodayWidgetPlugin extends Plugin {

    @PluginMethod
    public void refresh(PluginCall call) {
        TodayWidgetProvider.refreshAll(getContext());
        call.resolve();
    }
}
