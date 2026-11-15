package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Small line icons for the settings screen and the Control Panel, drawn from paths so they need
 * no image files and take the text colour (light or dark ink). Each is designed on a 24 x 24 grid.
 */
final class Glyph extends Drawable {
    static final int APPEARANCE = 0, EFFECT = 1, WALLPAPER = 2, ROWS = 3, CLOCK = 4, ADVANCED = 5,
            ABOUT = 6, WIFI = 7, BLUETOOTH = 8, MEMORY = 9, SETTINGS = 10, INPUT = 11, DISPLAY = 12,
            SOUND = 13, HOME = 14, CHECK = 15, MORE = 16;

    private final int kind;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    Glyph(int kind, int color) {
        this.kind = kind;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        setColor(color);
    }

    void setColor(int color) {
        line.setColor(color);
        solid.setColor(color);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        c.save();
        c.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
        c.scale(s, s);
        line.setStrokeWidth(1.8f);
        path.reset();
        switch (kind) {
            case APPEARANCE: // a circle, half filled
                c.drawCircle(12, 12, 8, line);
                r.set(4, 4, 20, 20);
                c.drawArc(r, 90, 180, true, solid);
                break;
            case EFFECT: // a four-point sparkle
                path.moveTo(12, 3); path.quadTo(13, 11, 21, 12); path.quadTo(13, 13, 12, 21);
                path.quadTo(11, 13, 3, 12); path.quadTo(11, 11, 12, 3); path.close();
                c.drawPath(path, solid);
                break;
            case WALLPAPER: // a framed mountain
                r.set(3, 5, 21, 19);
                c.drawRoundRect(r, 2.5f, 2.5f, line);
                path.moveTo(5, 17); path.lineTo(10, 11); path.lineTo(13, 14.5f); path.lineTo(15.5f, 12);
                path.lineTo(19, 17);
                c.drawPath(path, line);
                c.drawCircle(16, 8.5f, 1.4f, solid);
                break;
            case ROWS: // two by two tiles
                for (int i = 0; i < 4; i++) {
                    float x = i % 2 == 0 ? 4 : 13, y = i < 2 ? 4 : 13;
                    r.set(x, y, x + 7, y + 7);
                    c.drawRoundRect(r, 1.8f, 1.8f, line);
                }
                break;
            case CLOCK:
                c.drawCircle(12, 12, 8.5f, line);
                path.moveTo(12, 7.5f); path.lineTo(12, 12); path.lineTo(15, 14);
                c.drawPath(path, line);
                break;
            case ADVANCED: // sliders
                for (int i = 0; i < 3; i++) {
                    float y = 6 + i * 6, k = i == 1 ? 15 : 9;
                    c.drawLine(4, y, 20, y, line);
                    c.drawCircle(k, y, 2.2f, solid);
                }
                break;
            case ABOUT:
                c.drawCircle(12, 12, 8.5f, line);
                c.drawLine(12, 11, 12, 16.5f, line);
                c.drawCircle(12, 7.8f, 1.1f, solid);
                break;
            case WIFI:
                for (int i = 0; i < 3; i++) {
                    float rad = 4 + i * 4;
                    r.set(12 - rad, 18 - rad, 12 + rad, 18 + rad);
                    c.drawArc(r, 225, 90, false, line);
                }
                c.drawCircle(12, 18, 1.4f, solid);
                break;
            case BLUETOOTH:
                path.moveTo(7, 8); path.lineTo(16, 16); path.lineTo(12, 20); path.lineTo(12, 4);
                path.lineTo(16, 8); path.lineTo(7, 16);
                c.drawPath(path, line);
                break;
            case MEMORY: // a chip
                r.set(6, 6, 18, 18);
                c.drawRoundRect(r, 2, 2, line);
                for (int i = 0; i < 3; i++) {
                    float p = 9 + i * 3;
                    c.drawLine(p, 3, p, 6, line); c.drawLine(p, 18, p, 21, line);
                    c.drawLine(3, p, 6, p, line); c.drawLine(18, p, 21, p, line);
                }
                break;
            case SETTINGS: // a gear
                c.drawCircle(12, 12, 3, line);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine(12 + (float) Math.cos(a) * 6.5f, 12 + (float) Math.sin(a) * 6.5f,
                            12 + (float) Math.cos(a) * 9f, 12 + (float) Math.sin(a) * 9f, line);
                }
                c.drawCircle(12, 12, 6.5f, line);
                break;
            case INPUT: // an arrow into a box
                path.moveTo(13, 5); path.lineTo(19, 5); path.lineTo(19, 19); path.lineTo(13, 19);
                c.drawPath(path, line);
                c.drawLine(4, 12, 14, 12, line);
                path.reset(); path.moveTo(10.5f, 8.5f); path.lineTo(14, 12); path.lineTo(10.5f, 15.5f);
                c.drawPath(path, line);
                break;
            case DISPLAY:
                r.set(3, 5, 21, 16);
                c.drawRoundRect(r, 2, 2, line);
                c.drawLine(9, 20, 15, 20, line);
                c.drawLine(12, 16, 12, 20, line);
                break;
            case SOUND:
                path.moveTo(4, 10); path.lineTo(7, 10); path.lineTo(12, 6); path.lineTo(12, 18);
                path.lineTo(7, 14); path.lineTo(4, 14); path.close();
                c.drawPath(path, line);
                r.set(11, 8, 17, 16); c.drawArc(r, -60, 120, false, line);
                r.set(11, 5, 21, 19); c.drawArc(r, -60, 120, false, line);
                break;
            case HOME:
                path.moveTo(4, 11); path.lineTo(12, 4); path.lineTo(20, 11);
                path.moveTo(6.5f, 9.5f); path.lineTo(6.5f, 19); path.lineTo(17.5f, 19); path.lineTo(17.5f, 9.5f);
                c.drawPath(path, line);
                break;
            case CHECK:
                line.setStrokeWidth(2.4f);
                path.moveTo(5, 12.5f); path.lineTo(10, 17); path.lineTo(19, 7);
                c.drawPath(path, line);
                break;
            case MORE: // three dots
                for (int i = 0; i < 3; i++) c.drawCircle(6 + i * 6, 12, 1.9f, solid);
                break;
            default:
                break;
        }
        c.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        line.setAlpha(alpha);
        solid.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        line.setColorFilter(cf);
        solid.setColorFilter(cf);
    }

    @Override
    @SuppressWarnings("deprecation") // required override on API 25
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
