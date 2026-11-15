package tv.dumbtv.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * If the home screen ever crashes, Android restarts the app but shows Amazon's home, and the
 * HOME guard only takes over the next time Amazon's home appears, so it would look "stuck".
 * This reopens the home screen 1.5 s after a crash, through an alarm (exempt from the 5 s
 * app-switch delay, as Guard's own launch is), then lets the crash go on as usual. It does so
 * at most once in 30 s, so a crash that repeats cannot become a loop.
 */
final class Recover {
    private static final String PREFS = "recover", LAST = "last";
    private static final long WINDOW_MS = 30_000, DELAY_MS = 1_500;
    private static boolean installed;

    private Recover() {}

    static void install(Context c) {
        if (installed) return;
        installed = true;
        Context app = c.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                reopen(app);
            } catch (RuntimeException ignored) {
                // nothing more to do: the crash below still reports as usual
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private static void reopen(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis(), last = p.getLong(LAST, 0);
        if (now - last < WINDOW_MS) return;
        p.edit().putLong(LAST, now).commit(); // commit: the process is about to die
        Intent home = new Intent(c, Home.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 7, home,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + DELAY_MS, pi);
    }
}
