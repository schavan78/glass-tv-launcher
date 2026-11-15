package tv.dumbtv.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewParent;

/**
 * Frosted glass for Options › App tile style › Frosted glass. One blurred, screen-aligned copy of
 * the wallpaper at 1/{@link #SHRINK} size is shared by every tile; each tile draws the part of it
 * that lies behind the tile, so the tiles look like glass over the picture with no per-frame blur.
 * The wallpaper's slow pan (Options › Living wallpaper) is followed through {@link #follow}.
 */
final class Glass {
    static final String STYLE = "glass";
    static final int SHRINK = 8;
    private static final int RADIUS = 4;

    /** What {@link #prepare} made on the io thread, handed to {@link #set} on the UI thread. */
    static final class Frost {
        final Bitmap bitmap;
        final float luminance;

        Frost(Bitmap bitmap, float luminance) {
            this.bitmap = bitmap;
            this.luminance = luminance;
        }
    }

    private static Bitmap frost, backdrop;
    private static int backdropAlpha;
    private static final Paint OVER = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static final Rect OVER_SRC = new Rect();
    private static boolean light;
    private static float scale = 1f, shiftX, shiftY;
    private static final Paint PAINT = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static final Rect SRC = new Rect();

    private Glass() {}

    static boolean on(Config cfg) {
        return STYLE.equals(cfg.get(Config.TILE_STYLE));
    }

    /** Light glass with dark ink, for a bright wallpaper or Options › Text and glass › Light. */
    static boolean light() {
        return light;
    }

    static void setLight(boolean on) {
        light = on;
    }

    /** io thread: the blurred backdrop for a screen of {@code sw} x {@code sh}, center-cropped like the wallpaper view. */
    static Frost prepare(Bitmap wallpaper, int sw, int sh) {
        if (wallpaper == null || sw <= 0 || sh <= 0) return null;
        int w = Math.max(1, sw / SHRINK), h = Math.max(1, sh / SHRINK);
        Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float s = Math.max(w / (float) wallpaper.getWidth(), h / (float) wallpaper.getHeight());
        Matrix m = new Matrix();
        m.setScale(s, s);
        m.postTranslate((w - wallpaper.getWidth() * s) / 2f, (h - wallpaper.getHeight() * s) / 2f);
        new Canvas(small).drawBitmap(wallpaper, m, PAINT);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        float lum = Tone.luminance(px, w, h);
        Blur.apply(px, w, h, RADIUS);
        small.setPixels(px, 0, w, 0, 0, w, h);
        return new Frost(small, lum);
    }

    /** The blurred wallpaper, also shown as a placeholder while the sharp one decodes. */
    static Bitmap frost() {
        return frost;
    }

    /**
     * io thread: the focused app's art as a screen-aligned, blurred picture of the same small
     * size as the frost, for Options › Focus backdrop. Drawn from a copy of the drawable, since
     * the tiles set bounds on the original on the UI thread.
     */
    static Frost backdropOf(android.graphics.drawable.Drawable art, int sw, int sh) {
        if (art == null || sw <= 0 || sh <= 0) return null;
        android.graphics.drawable.Drawable.ConstantState cs = art.getConstantState();
        android.graphics.drawable.Drawable d = cs == null ? null : cs.newDrawable().mutate();
        if (d == null) return null;
        int w = Math.max(1, sw / SHRINK), h = Math.max(1, sh / SHRINK);
        int iw = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : w;
        int ih = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : h;
        float s = Math.max(w / (float) iw, h / (float) ih) * 1.2f; // a little closer than the art
        int dw = Math.round(iw * s), dh = Math.round(ih * s);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
        d.draw(c);
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) px[i] |= 0xFF000000; // transparent art: no holes
        Blur.apply(px, w, h, RADIUS + 2);
        float lum = Tone.luminance(px, w, h);
        b.setPixels(px, 0, w, 0, 0, w, h);
        return new Frost(b, lum);
    }

    /**
     * io thread: Focus effect › Colour fade, a wash of {@code color} rising from the bottom of the
     * screen, clear over the top third where the clock sits, so the text colour never needs to
     * change. Screen-aligned at the frost's size; its luminance is -1 ("leave the ink alone").
     */
    static Frost fadeOf(int color, int sw, int sh) {
        int w = Math.max(1, sw / SHRINK), h = Math.max(1, sh / SHRINK);
        int rgb = color & 0xFFFFFF;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            float t = (y / (float) (h - 1) - 0.3f) / 0.7f; // 0 at 30% down, 1 at the bottom
            t = t <= 0 ? 0 : t >= 1 ? 1 : t * t * (3 - 2 * t); // smoothstep
            for (int x = 0; x < w; x++) {
                // a touch stronger on the left, where the rows start
                float side = 1f - 0.25f * x / (float) (w - 1);
                int a = Math.round(255 * t * side);
                px[y * w + x] = a << 24 | rgb;
            }
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, w, 0, 0, w, h);
        return new Frost(b, -1f);
    }

    /** {@code c} brightened until its strongest channel is about 220, for a glow that shows. */
    static int vivid(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        int max = Math.max(1, Math.max(r, Math.max(g, b)));
        float k = 220f / max;
        return 0xFF000000 | Math.min(255, Math.round(r * k)) << 16
                | Math.min(255, Math.round(g * k)) << 8 | Math.min(255, Math.round(b * k));
    }

    static Bitmap backdropShown() {
        return backdrop;
    }

    /** UI thread: the backdrop the tiles mix over the frost, and how strongly (0..255). */
    static void setBackdrop(Bitmap b, int alpha) {
        backdrop = b;
        backdropAlpha = Math.max(0, Math.min(255, alpha));
    }

    /** UI thread. Null clears the backdrop: a solid background, or no wallpaper yet. */
    static void set(Frost f) {
        frost = f == null ? null : f.bitmap;
    }

    /** The wallpaper view's current zoom and shift, around the screen's center. */
    static void follow(float zoom, float dx, float dy) {
        scale = zoom <= 0 ? 1f : zoom;
        shiftX = dx;
        shiftY = dy;
    }

    /**
     * Draws the frosted backdrop behind {@code v} into {@code dst} (v's own coordinates).
     * Returns false when there is none, so the caller paints its plain fill instead.
     */
    static boolean draw(Canvas canvas, View v, RectF dst) {
        Bitmap f = frost;
        if (f == null) return false;
        float x = 0, y = 0;
        // The view's untransformed place on screen: the focus zoom and lift are applied to the
        // whole tile afterwards, so the backdrop must not move with them.
        View at = v;
        while (at != null) {
            x += at.getLeft();
            y += at.getTop();
            ViewParent p = at.getParent();
            if (!(p instanceof View)) break;
            at = (View) p;
            x -= at.getScrollX();
            y -= at.getScrollY();
        }
        View screen = v.getRootView();
        float cx = screen.getWidth() / 2f, cy = screen.getHeight() / 2f;
        float left = cx + (x + dst.left - cx - shiftX) / scale;
        float top = cy + (y + dst.top - cy - shiftY) / scale;
        float right = left + dst.width() / scale, bottom = top + dst.height() / scale;
        SRC.set(Math.round(left / SHRINK), Math.round(top / SHRINK),
                Math.round(right / SHRINK), Math.round(bottom / SHRINK));
        if (SRC.width() <= 0 || SRC.height() <= 0) return false;
        canvas.drawBitmap(f, SRC, dst, PAINT);
        Bitmap over = backdrop;
        if (over != null && backdropAlpha > 0) {
            // The backdrop view does not pan with the wallpaper: plain screen position.
            float l = x + dst.left, t = y + dst.top;
            OVER_SRC.set(Math.round(l / SHRINK), Math.round(t / SHRINK),
                    Math.round((l + dst.width()) / SHRINK), Math.round((t + dst.height()) / SHRINK));
            OVER.setAlpha(backdropAlpha);
            canvas.drawBitmap(over, OVER_SRC, dst, OVER);
        }
        return true;
    }
}
