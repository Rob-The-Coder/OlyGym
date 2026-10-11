package olygym.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The widget's three controls land here. Recording the tap and repainting is the whole job; the app
 * applies the queue the next time it runs (WidgetActions, lib/widget-actions.js).
 *
 * Explicit intents only — the PendingIntents in TodayWidgetProvider name this class — so nothing
 * else can reach it and no intent-filter is needed.
 */
public class WidgetActionReceiver extends BroadcastReceiver {

    static final String EXTRA_KIND = "k";
    static final String ACTION_PREV = "olygym.app.action.WIDGET_PREV";
    static final String ACTION_NEXT = "olygym.app.action.WIDGET_NEXT";
    static final String ACTION_CHECK = "olygym.app.action.WIDGET_CHECK";

    @Override
    public void onReceive(Context context, Intent intent) {
        String kind = intent == null ? null : intent.getStringExtra(EXTRA_KIND);
        if (kind == null) return;
        // Stamped with the session the card is showing, so a queue that outlives its session cannot
        // tick the next one. Read from the same file the card was drawn from.
        WidgetActions.add(context, kind, TodayWidgetProvider.currentSessionDate(context));
        TodayWidgetProvider.refreshAll(context);
    }
}
