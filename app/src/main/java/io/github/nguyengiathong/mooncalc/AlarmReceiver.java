package io.github.nguyengiathong.mooncalc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The notification alarm, plus the system events after which it has to be set again. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (MoonNotifier.ACTION_FIRE.equals(intent.getAction())) {
            MoonNotifier.fire(context);
        } else {
            // reboot, app update, clock / time zone change, exact-alarm permission change
            MoonNotifier.scheduleNext(context);
        }
    }
}
