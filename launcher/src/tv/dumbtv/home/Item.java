package tv.dumbtv.home;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.Drawable;

/** One tile: an installed app (id = package name) or a TV input (id = input id). */
final class Item {
    final String id;
    final CharSequence label;
    final Intent intent;
    ActivityInfo info;
    boolean leanback;
    int state = -1;
    volatile boolean artRequested;
    volatile Drawable banner, icon;
    volatile int backgroundColor;
    volatile boolean colorRequested;
    /** Continue Watching only: the episode line, how far in (0..1) and the app it belongs to. */
    CharSequence subtitle;
    float progress = -1f;
    String app, poster;
    /** Options › Focus backdrop: this app's art, blurred and screen-sized; made once, on colorIo. */
    volatile android.graphics.Bitmap backdrop;
    volatile float backdropLuminance;
    volatile boolean backdropRequested;
    /** Which Focus effect the backdrop was made for, and the app's colour for fade and halo. */
    volatile String backdropKind;
    volatile int glow;

    Item(String id, CharSequence label, Intent intent) {
        this.id = id;
        this.label = label;
        this.intent = intent;
    }
}
