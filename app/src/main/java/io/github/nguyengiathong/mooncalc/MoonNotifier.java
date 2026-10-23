package io.github.nguyengiathong.mooncalc;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the notifications the page schedules ("thông báo" in assets/www/index.html).
 *
 * The page works out the times, so this only keeps its list of {t, id, title, body} (t in ms since
 * the epoch, sorted) in SharedPreferences and walks it with one exact alarm at a time: when the alarm
 * fires, everything due is shown and the alarm moves on to the next entry. Notifications share their
 * id per mark, so the end of a window replaces the notification for its start.
 */
final class MoonNotifier {
    private static final String TAG = "MoonCalc";
    static final String ACTION_FIRE = "io.github.nguyengiathong.mooncalc.NOTIFY";
    private static final String PREFS = "notify";
    private static final String KEY_EVENTS = "events";
    private static final String CHANNEL = "moon_marks";
    private static final int TEST_ID = 8;
    // an entry this late (the phone was off, say) is dropped rather than shown out of the blue
    private static final long LATE_LIMIT = 15 * 60_000L;
    // alarms may fire a little early; entries this close are shown with the current one
    private static final long EARLY_SLACK = 60_000L;

    private MoonNotifier() {}

    /** Replaces the schedule. Returns false when Android does not allow exact alarms, so times may drift. */
    static synchronized boolean setSchedule(Context c, String json) {
        List<JSONObject> events;
        try {
            events = toList(new JSONArray(json));
        } catch (JSONException e) {
            Log.w(TAG, "Bad notification schedule", e);
            return scheduleNext(c);
        }
        // a test notification that has not come yet survives the page's new schedule
        long now = System.currentTimeMillis();
        for (JSONObject e : load(c)) if (e.optInt("id") == TEST_ID && e.optLong("t") > now) events.add(e);
        save(c, events);
        return scheduleNext(c);
    }

    /** A notification a few seconds from now, through the same alarm path as the real ones. */
    static synchronized void scheduleTest(Context c) {
        List<JSONObject> events = load(c);
        try {
            events.add(new JSONObject()
                    .put("t", System.currentTimeMillis() + 5000)
                    .put("id", TEST_ID)
                    .put("title", "MoonCalc Việt")
                    .put("body", "Thông báo thử: điện thoại sẽ báo các mốc trăng bạn chọn như thế này."));
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        save(c, events);
        scheduleNext(c);
    }

    /** The alarm went off: show what is due, drop what is too late, set the alarm for the next entry. */
    static synchronized void fire(Context c) {
        long now = System.currentTimeMillis();
        List<JSONObject> rest = new ArrayList<>();
        for (JSONObject e : load(c)) {
            long t = e.optLong("t");
            if (t > now + EARLY_SLACK) rest.add(e);
            else if (t >= now - LATE_LIMIT) post(c, e);
        }
        save(c, rest);
        scheduleNext(c);
    }

    /**
     * Sets the alarm for the first entry still to come, or cancels it when there is none. Also run after
     * a reboot, an app update or a clock change, since Android clears alarms on reboot.
     */
    static synchronized boolean scheduleNext(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return false;
        PendingIntent alarm = PendingIntent.getBroadcast(c, 0,
                new Intent(c, AlarmReceiver.class).setAction(ACTION_FIRE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long now = System.currentTimeMillis(), next = -1;
        for (JSONObject e : load(c)) {
            long t = e.optLong("t");
            if (t >= now - LATE_LIMIT && (next < 0 || t < next)) next = t;
        }
        if (next < 0) {
            am.cancel(alarm);
            return true;
        }
        next = Math.max(next, now);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm);
                return true;
            } catch (SecurityException e) {
                Log.w(TAG, "Exact alarm refused", e);
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm);
        return false;
    }

    private static void post(Context c, JSONObject e) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Mốc Mặt Trăng", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Bắt đầu và kết thúc các khoảng quanh lúc trăng mọc, lặn, cao nhất, thấp nhất");
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String body = e.optString("body");
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_moon)
                .setColor(c.getColor(R.color.accent))
                .setContentTitle(e.optString("title"))
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setWhen(e.optLong("t"))
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(open);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        }
        try {
            nm.notify(e.optInt("id", 1), b.build());
        } catch (SecurityException ex) {
            Log.w(TAG, "Not allowed to post notifications", ex);
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static List<JSONObject> load(Context c) {
        try {
            return toList(new JSONArray(prefs(c).getString(KEY_EVENTS, "[]")));
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    private static void save(Context c, List<JSONObject> events) {
        events.sort((x, y) -> Long.compare(x.optLong("t"), y.optLong("t")));
        prefs(c).edit().putString(KEY_EVENTS, new JSONArray(events).toString()).commit();
    }

    private static List<JSONObject> toList(JSONArray a) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            if (e != null && e.has("t")) out.add(e);
        }
        return out;
    }
}
