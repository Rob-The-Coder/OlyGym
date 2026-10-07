package olygym.app.platform

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import kotlinx.serialization.json.JsonObject
import olygym.app.MainActivity
import olygym.app.R
import olygym.app.data.StateStore
import olygym.app.lib.I18nCore
import olygym.app.lib.ReminderAt
import olygym.app.lib.nextReminder

/*
 * The workout-day reminder's alarm and its notification.
 *
 * One alarm is set for the next planned day; when it fires, the receiver posts what was queued and
 * asks for the following one. The alarm is inexact on purpose: setAndAllowWhileIdle needs no
 * exact-alarm permission, works in Doze, and a workout reminder does not need the minute.
 *
 * A receiver can run with no app in sight -- after a reboot, or in a process the system started just
 * for it -- so it reads the profile off disk rather than assuming a loaded store.
 */

object ReminderAlarm {

    private const val CHANNEL_ID = "workout_reminder"
    private const val NOTIFICATION_ID = 3000
    private const val REQUEST = 30
    private const val EXTRA_TITLE = "reminder_title"
    private const val EXTRA_BODY = "reminder_body"

    private fun flags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun channelId(ctx: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_ID
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return CHANNEL_ID
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                I18nCore.t("Workout day reminder"),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            ch.description = I18nCore.t("Reminds you at this time on days that have a routine planned.")
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        return CHANNEL_ID
    }

    /**
     * Set the alarm for the next reminder in [S], or clear it when there is none. Returns what was
     * scheduled, so a caller can say when.
     */
    fun schedule(ctx: Context, S: JsonObject): ReminderAt? {
        cancel(ctx)
        val next = nextReminder(S, System.currentTimeMillis()) ?: return null
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return null
        val i = Intent(ctx, ReminderReceiver::class.java)
            .putExtra(EXTRA_TITLE, next.title)
            .putExtra(EXTRA_BODY, next.body)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis, PendingIntent.getBroadcast(ctx, REQUEST, i, flags()))
        return next
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val i = Intent(ctx, ReminderReceiver::class.java)
        am.cancel(PendingIntent.getBroadcast(ctx, REQUEST, i, flags()))
    }

    /** Post the reminder. The copy was decided when the alarm was set. */
    fun post(ctx: Context, title: String, body: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val open = Intent(ctx, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val content = PendingIntent.getActivity(ctx, REQUEST, open, flags())
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(ctx, channelId(ctx))
        } else {
            Notification.Builder(ctx)
        }
        b.setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        nm.notify(NOTIFICATION_ID, b.build())
    }

    /** Read the profile off disk and (re)set the alarm from it. For the two receivers below. */
    fun rescheduleFromDisk(ctx: Context) {
        val S = StateStore.readFile(File(ctx.filesDir, StateStore.FILE))
        if (S == null) cancel(ctx) else schedule(ctx, S)
    }
}

/** Fires at the scheduled minute: post what was queued, then set the next one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderAlarm.post(
            context,
            intent.getStringExtra("reminder_title") ?: I18nCore.t("Workout day"),
            intent.getStringExtra("reminder_body").orEmpty(),
        )
        // Reading the profile and walking the calendar is disk work, so the receiver's own short
        // lifetime is held open for it instead of doing it on the main thread.
        val pending = goAsync()
        Thread {
            try {
                ReminderAlarm.rescheduleFromDisk(context)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/**
 * A reboot, a clock change or a reinstall clears or invalidates the alarm; without this the reminder
 * for the day it was set for would simply never arrive.
 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                ReminderAlarm.rescheduleFromDisk(context)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
