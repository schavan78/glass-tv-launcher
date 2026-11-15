package tv.dumbtv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Options › Focus effect › Halo and Spotlight: light that follows the selected tile, drawn on one
 * full-screen layer between the wallpaper and the rows (tiles cannot paint past their own edges
 * without leaving smears). Halo is a soft glow in the app's colour behind its tile; Spotlight
 * dims the screen except a pool of light around the tile. It glides between tiles, fades in and
 * out, and draws nothing at all while off.
 */
final class Spot extends View {
    static final int HALO = 1, SPOTLIGHT = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mode, color;
    private float x, y, size, level, toX, toY, toSize, toLevel;
    private ValueAnimator anim;

    Spot(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    /**
     * Lights the tile whose center is ({@code cx}, {@code cy}) on screen, {@code w} wide, in
     * {@code tint} for the halo; {@code on} false fades the light out where it is.
     */
    void aim(int mode, boolean on, float cx, float cy, float w, int tint) {
        boolean jump = level <= 0.01f || this.mode != mode;
        this.mode = mode;
        if (on) color = tint;
        toX = on ? cx : x;
        toY = on ? cy : y;
        toSize = on ? w : size;
        toLevel = on ? 1f : 0f;
        if (jump && on) {
            x = cx;
            y = cy;
            size = w;
        }
        if (anim != null) anim.cancel();
        float fx = x, fy = y, fs = size, fl = level;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(on ? 260 : 220);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            x = fx + (toX - fx) * f;
            y = fy + (toY - fy) * f;
            size = fs + (toSize - fs) * f;
            level = fl + (toLevel - fl) * f;
            invalidate();
        });
        anim.start();
    }

    /** Follows the tile without animating, while a row scrolls under it. */
    void follow(float cx, float cy) {
        if (level <= 0.01f) return;
        x = toX = cx;
        y = toY = cy;
        invalidate();
    }

    /** How much the spotlight darkens the clock corner (0..1), for choosing the text colour. */
    float dimming() {
        return mode == SPOTLIGHT ? 0.55f * toLevel : 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (level <= 0.01f || size <= 0) return;
        if (mode == HALO) {
            float r = size * 0.95f;
            int a = Math.round(0xC8 * level);
            int c = color & 0xFFFFFF;
            paint.setShader(new RadialGradient(x, y, r,
                    new int[] {a << 24 | c, (a / 2) << 24 | c, c},
                    new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, r, paint);
        } else if (mode == SPOTLIGHT) {
            float r = Math.max(getWidth(), getHeight()) * 0.75f;
            int a = Math.round(0xA6 * level);
            float pool = Math.min(0.9f, size * 0.85f / r);
            paint.setShader(new RadialGradient(x, y, r,
                    new int[] {0, 0, a << 24},
                    new float[] {0f, pool, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }
}
