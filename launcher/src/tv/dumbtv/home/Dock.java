package tv.dumbtv.home;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

/**
 * Options › Layout › Dock, the macOS part: the selected icon grows and rises, its neighbours grow
 * a little and make room, and the app's name floats above it in a frosted bubble. Only transforms
 * change, so the row's layout (and the glass bar behind it) stays put.
 */
final class Dock {
    /** Growth by distance from the selected icon: itself, next to it, two away. */
    private static final float[] GROW = {1.45f, 1.2f, 1.07f};
    private static final long MS = 180;

    private final ViewGroup strip;
    private final TextView callout;
    private final float gap;
    /** Retries while a new dock waits for its first layout (at most a second). */
    private int waits;

    Dock(ViewGroup strip, TextView callout, float gap) {
        this.strip = strip;
        this.callout = callout;
        this.gap = gap;
    }

    /** Magnifies around {@code focused}, a child of the strip, and names it above. */
    void focus(View focused, CharSequence name) {
        int at = strip.indexOfChild(focused);
        if (at < 0) {
            reset();
            return;
        }
        if (focused.getWidth() == 0 || !focused.isLaidOut()) {
            // A freshly built dock (after a settings change) is not laid out yet: try again once
            // it is, or the icons would grow by nothing and the name would sit at the left edge.
            if (waits++ < 20) {
                focused.postDelayed(() -> {
                    if (focused.isFocused()) focus(focused, name);
                }, 50);
            }
            return;
        }
        waits = 0;
        float size = focused.getWidth();
        for (int i = 0; i < strip.getChildCount(); i++) {
            View v = strip.getChildAt(i);
            int d = Math.abs(i - at);
            float s = d < GROW.length ? GROW[d] : 1f;
            float shift = 0;
            if (d > 0) {
                // Room for the selected icon's growth, each grown icon between, and half its own.
                shift = size * (GROW[0] - 1) / 2;
                for (int j = 1; j < d && j < GROW.length; j++) shift += size * (GROW[j] - 1);
                shift += size * (s - 1) / 2;
                if (i < at) shift = -shift;
            }
            v.animate().scaleX(s).scaleY(s).translationX(shift)
                    .translationY(-size * (s - 1) / 2) // grow upwards, from the dock's floor
                    .setInterpolator(new DecelerateInterpolator()).setDuration(MS).start();
        }
        if (name == null) {
            callout.animate().alpha(0f).setDuration(120).start();
            return;
        }
        callout.setText(name);
        callout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] s = new int[2], r = new int[2];
        strip.getLocationInWindow(s);
        ((View) callout.getParent()).getLocationInWindow(r);
        float cx = s[0] + focused.getLeft() + size / 2f - r[0];
        float top = s[1] + focused.getTop() + focused.getHeight() / 2f - size * GROW[0] / 2f
                - size * (GROW[0] - 1) / 2 - r[1];
        callout.setX(cx - callout.getMeasuredWidth() / 2f);
        callout.setY(top - callout.getMeasuredHeight() - gap);
        callout.animate().alpha(1f).setDuration(140).start();
    }

    /** Back to a flat dock, for when the focus leaves it. */
    void reset() {
        for (int i = 0; i < strip.getChildCount(); i++) {
            strip.getChildAt(i).animate().scaleX(1f).scaleY(1f).translationX(0).translationY(0)
                    .setDuration(MS).start();
        }
        callout.animate().alpha(0f).setDuration(120).start();
    }

    /** The frosted name bubble, styled for light or dark glass. */
    static TextView callout(Context c) {
        TextView t = Ui.text(c, 15, Ui.TEXT, Ui.MEDIUM);
        t.setPadding(Ui.dp(c, 14), Ui.dp(c, 7), Ui.dp(c, 14), Ui.dp(c, 7));
        t.setAlpha(0f);
        t.setElevation(Ui.dp(c, 6));
        return t;
    }

    static void ink(TextView t, boolean light) {
        t.setTextColor(light ? 0xFF16171A : Ui.TEXT);
        android.graphics.drawable.GradientDrawable bg = Ui.round(light ? 0xEBFFFFFF : 0xE61E1F24,
                Ui.dp(t.getContext(), 16));
        bg.setStroke(Math.max(1, Ui.dp(t.getContext(), 1)), light ? 0x26000000 : 0x33FFFFFF);
        t.setBackground(bg);
    }
}
