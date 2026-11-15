package tv.dumbtv.home;

import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Options › Living wallpaper: a slow drift and zoom over the picture, 45 s each way, only while
 * the home screen is in front (started in onResume, stopped in onPause, so it costs nothing while
 * an app plays). The picture is always zoomed in a little more than it moves, so its edges never
 * show. The glass tiles are told about each position, and redrawn a few times a second.
 */
final class Motion {
    private static final long CYCLE_MS = 45_000, GLASS_EVERY_MS = 350;
    private static final float ZOOM = 1.05f, ZOOM_RANGE = 0.04f;

    private final View wall;
    private final Runnable glassChanged;
    private final float shift;
    private ValueAnimator anim;
    private float at;
    private long glassAt;

    Motion(View wall, Runnable glassChanged, float shift) {
        this.wall = wall;
        this.glassChanged = glassChanged;
        this.shift = shift;
    }

    void start() {
        if (anim != null) return;
        wall.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(CYCLE_MS);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.addUpdateListener(a -> place((Float) a.getAnimatedValue(), false));
        anim.start();
        // Continue from where the last visit stopped instead of jumping back to the start.
        anim.setCurrentPlayTime(Math.round(at * CYCLE_MS));
    }

    /** Freezes the picture where it is; {@link #start} carries on from there. */
    void stop() {
        if (anim == null) return;
        anim.cancel();
        anim = null;
        wall.setLayerType(View.LAYER_TYPE_NONE, null);
        glassChanged.run();
    }

    /** Living wallpaper off, or no picture: back to a still, unzoomed wallpaper. */
    void reset() {
        stop();
        at = 0f;
        wall.setScaleX(1f);
        wall.setScaleY(1f);
        wall.setTranslationX(0f);
        wall.setTranslationY(0f);
        Glass.follow(1f, 0f, 0f);
        glassChanged.run();
    }

    private void place(float f, boolean always) {
        at = f;
        float zoom = ZOOM + ZOOM_RANGE * f;
        float dx = (f * 2f - 1f) * shift, dy = (0.5f - f) * shift * 0.5f;
        wall.setScaleX(zoom);
        wall.setScaleY(zoom);
        wall.setTranslationX(dx);
        wall.setTranslationY(dy);
        Glass.follow(zoom, dx, dy);
        long now = SystemClock.uptimeMillis();
        if (always || now - glassAt >= GLASS_EVERY_MS) {
            glassAt = now;
            glassChanged.run();
        }
    }
}
