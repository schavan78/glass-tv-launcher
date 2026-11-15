package tv.dumbtv.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

/**
 * Control Panel › Sleep timer: turns the TV off after 30, 60, 90 or 120 minutes. Fire OS keeps
 * its own sleep timer and the go-to-sleep permission to Amazon's apps (signature|amazon), so
 * the TV is put to sleep by {@link Guard#sleep}, Android 9's accessibility lock-screen action.
 * A minute before, a notice says so; pressing the tile again changes or cancels the timer.
 */
public final class Sleep extends BroadcastReceiver {
    static final int[] STEPS = {0, 30, 60, 90, 120};
    private static final String PREFS = "sleep", AT = "at", STEP = "step";
    private static final String ACTION_WARN = "tv.dumbtv.home.SLEEP_WARN", ACTION_SLEEP = "tv.dumbtv.home.SLEEP";
    private static final long WARN_MS = 60_000;

    /** When the TV goes off, in wall-clock milliseconds; 0 when no timer is set. */
    static long endsAt(Context c) {
        long at = prefs(c).getLong(AT, 0);
        return at > System.currentTimeMillis() ? at : 0;
    }

    /** The next step after the one set now: off, 30, 60, 90, 120, then off again. */
    static int nextMinutes(Context c) {
        int step = endsAt(c) == 0 ? 0 : prefs(c).getInt(STEP, 0);
        return STEPS[(step + 1) % STEPS.length];
    }

    /** Sets the timer {@code minutes} from now, or cancels it with 0. */
    static void set(Context c, int minutes) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent warn = pending(c, ACTION_WARN), sleep = pending(c, ACTION_SLEEP);
        am.cancel(warn);
        am.cancel(sleep);
        int step = 0;
        for (int i = 0; i < STEPS.length; i++) if (STEPS[i] == minutes) step = i;
        if (minutes <= 0) {
            prefs(c).edit().clear().apply();
            return;
        }
        long at = System.currentTimeMillis() + minutes * 60_000L;
        prefs(c).edit().putLong(AT, at).putInt(STEP, step).apply();
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at - WARN_MS, warn);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, sleep);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        Context app = c.getApplicationContext();
        if (ACTION_WARN.equals(i.getAction())) {
            if (endsAt(app) != 0) Toast.makeText(app, R.string.sleep_warn, Toast.LENGTH_LONG).show();
        } else if (ACTION_SLEEP.equals(i.getAction())) {
            prefs(app).edit().clear().apply();
            if (!Guard.sleep()) Log.w("dumbtv", "sleep timer: the Home guard is off, the TV stays on");
        }
    }

    private static PendingIntent pending(Context c, String action) {
        Intent i = new Intent(action).setClass(c, Sleep.class);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
