package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Options › Layout › Dock: the frosted bar behind the app icons. It shows the blurred wallpaper
 * behind it ({@link Glass}), a light or dark wash, and a rim that is brighter along the top,
 * like the tiles. It is the background of the row's strip, so it hugs the icons.
 */
final class DockBar extends Drawable {
    private final View owner;
    private final float radius;
    private final RectF box = new RectF();
    private final Path clip = new Path();
    private final Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);

    DockBar(View owner, float radius) {
        this.owner = owner;
        this.radius = radius;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, radius / 22f));
    }

    @Override
    public void draw(Canvas canvas) {
        box.set(getBounds());
        if (box.isEmpty()) return;
        boolean light = Glass.light();
        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        if (!Glass.draw(canvas, owner, box)) canvas.drawColor(light ? 0x8CFFFFFF : 0x8C1C1D22);
        // A thin, bright pane: mostly the blurred picture, lightly frosted, as on macOS.
        wash.setColor(light ? 0x4DFFFFFF : 0x40FFFFFF);
        canvas.drawRect(box, wash);
        canvas.restore();
        rim.setShader(new LinearGradient(0, box.top, 0, box.bottom,
                light ? 0xCCFFFFFF : 0x73FFFFFF, light ? 0x40FFFFFF : 0x14FFFFFF, Shader.TileMode.CLAMP));
        float h = rim.getStrokeWidth() / 2;
        canvas.drawRoundRect(box.left + h, box.top + h, box.right - h, box.bottom - h,
                radius - h, radius - h, rim);
    }

    /** The rounded outline, so the strip's elevation casts a soft shadow under the dock. */
    @Override
    public void getOutline(android.graphics.Outline outline) {
        outline.setRoundRect(getBounds(), radius);
        outline.setAlpha(0.6f);
    }

    @Override
    public void setAlpha(int alpha) {
        wash.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        wash.setColorFilter(cf);
    }

    @Override
    @SuppressWarnings("deprecation") // required override on API 25
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
