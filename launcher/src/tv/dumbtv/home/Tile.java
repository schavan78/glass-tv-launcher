package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputManager;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * A card drawn in one pass: banner, or icon plus name, or an input name; focus ring on top.
 * With App tile style › Frosted glass the card is the blurred wallpaper behind it ({@link Glass})
 * under a light or dark wash, the app's icon on it, and an edge that catches the light; the
 * focused card rises with a shadow. Continue Watching cards add a progress bar and two lines.
 */
final class Tile extends View {
    private static final float FOCUS_SCALE = 1.08f, GLASS_SCALE = 1.1f;

    final Item item;
    final String kind;
    private final CharSequence title;
    private final int cardW, cardH, labelH;
    private final boolean labelAlways, labelOnFocus, glass, watch, dock;
    private final float radius, stroke, lift, rise;
    private final RectF card = new RectF();
    private final RectF bar = new RectF();
    private final Path clip = new Path();
    private final Path iconClip = new Path();
    private final RectF iconBox = new RectF();
    private final Path arrow = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint done = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint inner = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint under = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private boolean moving, edgeLight, edgeFocused;
    /** The light sweeping across a newly focused glass card: 0..1 while it runs, -1 otherwise. */
    private float shine = -1f;
    private android.animation.ValueAnimator shineAnim;
    private final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AfterglowTile afterglow;

    Tile(Context c, Item item, String kind, CharSequence title, int cardW, int cardH,
            String labels, int accent) {
        super(c);
        this.item = item;
        this.kind = kind;
        this.title = title;
        this.cardW = cardW;
        this.cardH = cardH;
        boolean app = Catalog.isApp(kind);
        Config cfg = Config.get(c);
        watch = Catalog.WATCH.equals(kind);
        glass = Glass.on(cfg);
        dock = Catalog.APPS.equals(kind) && "dock".equals(cfg.get(Config.LAYOUT)) && cardW == cardH;
        afterglow = app && "afterglow".equals(cfg.get(Config.TILE_STYLE))
                ? new AfterglowTile(c, item.id, title, accent) : null;
        labelH = watch ? Ui.dp(c, 44) : app && afterglow == null && !dock ? Ui.dp(c, 26) : 0;
        labelAlways = watch || "always".equals(labels);
        labelOnFocus = "focus".equals(labels);
        radius = dock ? cardW * 0.22f : Ui.dp(c, glass ? 14 : 10);
        stroke = Ui.dp(c, afterglow == null ? 2.5f : 1f);
        lift = Ui.dp(c, 6);
        rise = Ui.dp(c, 14);

        fill.setColor(app || watch ? Ui.SURFACE : 0xFF17181B);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        ring.setColor(accent);
        edge.setStyle(Paint.Style.STROKE);
        dot.setColor(0xFF34C759);
        track.setColor(0x59FFFFFF);
        done.setColor(accent == 0xFFFFFFFF ? 0xFFFFFFFF : accent);
        inner.setTypeface(Ui.MEDIUM);
        inner.setTextAlign(Paint.Align.CENTER);
        inner.setTextSize(Ui.dp(c, app ? 14 : 17));
        under.setTypeface(Ui.MEDIUM);
        under.setTextAlign(watch ? Paint.Align.LEFT : Paint.Align.CENTER);
        under.setTextSize(Ui.dp(c, watch ? 15 : 13));
        small.setTypeface(Ui.MEDIUM);
        small.setTextAlign(Paint.Align.LEFT);
        small.setTextSize(Ui.dp(c, 12.5f));
        ink();

        if (glass) {
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, cardW, cardH, radius);
                    o.setAlpha(0.55f);
                }
            });
            setElevation(Ui.dp(c, 2));
            // A far camera keeps the tilt a gentle lean instead of a warp.
            setCameraDistance(c.getResources().getDisplayMetrics().density * 8000);
        }

        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(watch && item.subtitle != null ? title + ", " + item.subtitle : title);
    }

    /** Text and glass colours for the current {@link Glass#light} choice. */
    private void ink() {
        boolean light = glass && Glass.light();
        int text = light ? 0xFF16171A : Ui.TEXT;
        inner.setColor(text);
        under.setColor(text);
        small.setColor(light ? 0x99000000 : Ui.MUTED);
        // Labels sit straight on the background: a soft halo of the opposite tone keeps them
        // readable over snow, a colour wash or a spotlight's edge.
        float r = Ui.dp(getContext(), 6), dy = Ui.dp(getContext(), 1);
        int halo = light ? 0x8CFFFFFF : 0xA6000000;
        under.setShadowLayer(r, 0, dy, halo);
        small.setShadowLayer(r, 0, dy, halo);
        wash.setColor(light ? 0x8CFFFFFF : 0x5C0E1014);
        track.setColor(light ? 0x33000000 : 0x59FFFFFF);
    }

    void setMoving(boolean on) {
        moving = on;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(cardW, cardH + labelH);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        card.set(0, 0, cardW, cardH);
        clip.reset();
        clip.addRoundRect(card, radius, radius, Path.Direction.CW);
        if (afterglow != null) afterglow.resize(cardW, cardH);
        setPivotX(cardW / 2f);
        setPivotY(cardH / 2f);
        edgeFocused = !isFocused(); // forces the edge shader to be made on the next draw
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        if (shineAnim != null) {
            shineAnim.cancel();
            shineAnim = null;
            shine = -1f;
        }
        if (dock) {
            // The dock magnifies its icons itself (Dock, run from the focus listener inside
            // super's call above), so no animation is cancelled here; only the shine is added.
            if (focused && glass) startShine();
            invalidate();
            return;
        }
        animate().cancel();
        float to = glass ? GLASS_SCALE : FOCUS_SCALE;
        if (focused) {
            if (glass) {
                // Parallax: the card leans in from the side the focus came from, then settles,
                // and a band of light sweeps across it once.
                setRotationY(direction == View.FOCUS_RIGHT ? -6f : direction == View.FOCUS_LEFT ? 6f : 0f);
                startShine();
            }
            animate().scaleX(to).scaleY(to).translationY(glass ? -lift : 0).rotationY(0f)
                    .translationZ(glass ? rise : 0).setDuration(glass ? 260 : 110).start();
        } else if (glass) {
            animate().scaleX(1f).scaleY(1f).translationY(0).translationZ(0).rotationY(0f)
                    .setDuration(120).start();
        } else {
            setScaleX(1f);
            setScaleY(1f);
        }
        invalidate();
    }

    /** A band of light sweeps across the card once. */
    private void startShine() {
        shineAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        shineAnim.setDuration(700);
        shineAnim.setStartDelay(90);
        shineAnim.addUpdateListener(a -> {
            shine = (Float) a.getAnimatedValue();
            invalidate();
        });
        shineAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                shine = -1f;
                invalidate();
            }
        });
        shineAnim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (glass) ink();
        Drawable banner = item.banner, icon = item.icon;
        canvas.save();
        canvas.clipPath(clip);
        // A banner fills the card, as on Apple TV; only cards without one show the glass.
        boolean frosted = glass && banner == null && Glass.draw(canvas, this, card);
        if (frosted) canvas.drawRect(card, wash);
        else canvas.drawRect(card, fill);
        if (dock && (icon != null || banner != null)) {
            // A dock icon fills its rounded square; an app with only a banner shows its middle.
            Drawable art = icon != null ? icon : banner;
            if (icon != null) {
                art.setBounds(0, 0, cardW, cardH);
            } else {
                int w = Math.round(cardH * 16f / 9f);
                art.setBounds((cardW - w) / 2, 0, (cardW + w) / 2, cardH);
            }
            art.draw(canvas);
        } else if (afterglow != null) {
            afterglow.draw(canvas, icon, cardW, cardH);
        } else if (watch) {
            drawWatch(canvas, banner, icon);
        } else if (glass && banner == null && icon != null) {
            // A square launcher icon has its own background with hard corners; rounded like an
            // app icon it sits on the glass instead of looking cut out and pasted on.
            int size = cardH * 62 / 100;
            int left = (cardW - size) / 2, top = (cardH - size) / 2;
            iconBox.set(left, top, left + size, top + size);
            iconClip.reset();
            iconClip.addRoundRect(iconBox, size * 0.22f, size * 0.22f, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(iconClip);
            icon.setBounds(left, top, left + size, top + size);
            icon.draw(canvas);
            canvas.restore();
        } else if (banner != null) {
            banner.setBounds(0, 0, cardW, cardH);
            banner.draw(canvas);
        } else if (icon != null) {
            int size = cardH * 46 / 100;
            int left = (cardW - size) / 2, top = cardH * 12 / 100;
            icon.setBounds(left, top, left + size, top + size);
            icon.draw(canvas);
            drawLine(canvas, inner, cardW / 2f, cardH - cardH * 13 / 100f);
        } else if (!Catalog.isApp(kind)) {
            drawLine(canvas, inner, cardW / 2f, cardH / 2f - (inner.ascent() + inner.descent()) / 2);
        }
        if (moving) canvas.drawColor(0x99000000);
        if (shine >= 0f) drawShine(canvas);
        canvas.restore();

        if (afterglow != null) afterglow.edge(canvas, cardW, cardH, radius);
        if (glass) drawEdge(canvas);

        if (item.state == TvInputManager.INPUT_STATE_CONNECTED) {
            float r = Ui.dp(getContext(), 3.5f);
            canvas.drawCircle(cardW - r * 3, r * 3, r, dot);
        }
        if (!glass && isFocused() || moving) {
            float half = stroke / 2;
            ring.setPathEffect(moving ? new DashPathEffect(new float[] {stroke * 3, stroke * 2}, 0) : null);
            canvas.drawRoundRect(half, half, cardW - half, cardH - half, radius - half, radius - half, ring);
        }
        if (moving) drawArrows(canvas);
        if (watch) {
            drawTwoLines(canvas);
        } else {
            boolean label = (banner != null || glass && icon != null)
                    && (labelAlways || labelOnFocus && isFocused());
            if (labelH > 0 && label) {
                drawLine(canvas, under, cardW / 2f, cardH + labelH - Ui.dp(getContext(), 6));
            }
        }
    }

    /** A soft diagonal band of light, from just off the left edge to just off the right. */
    private void drawShine(Canvas canvas) {
        float band = cardW * 0.35f;
        float cx = -band + shine * (cardW + band * 2);
        int peak = Math.round(0x55 * (float) Math.sin(Math.PI * shine)); // fades in and out
        shinePaint.setShader(new LinearGradient(cx - band, 0, cx + band, cardH,
                new int[] {0x00FFFFFF, peak << 24 | 0xFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(card, shinePaint);
    }

    /** A thin rim, brighter along the top: the edge of the glass catching the light. */
    private void drawEdge(Canvas canvas) {
        boolean focused = isFocused();
        boolean light = Glass.light();
        if (focused != edgeFocused || light != edgeLight || edge.getShader() == null) {
            edgeFocused = focused;
            edgeLight = light;
            int top = focused ? 0xF2FFFFFF : light ? 0xB3FFFFFF : 0x66FFFFFF;
            int bottom = focused ? 0x4DFFFFFF : light ? 0x33FFFFFF : 0x14FFFFFF;
            edge.setShader(new LinearGradient(0, 0, 0, cardH, top, bottom, Shader.TileMode.CLAMP));
            edge.setStrokeWidth(Ui.dp(getContext(), focused ? 2f : 1f));
        }
        float half = edge.getStrokeWidth() / 2;
        canvas.drawRoundRect(half, half, cardW - half, cardH - half, radius - half, radius - half, edge);
    }

    /** Continue Watching: the poster, or the app's icon on glass, with a progress bar. */
    private void drawWatch(Canvas canvas, Drawable poster, Drawable icon) {
        if (poster != null) {
            int pw = poster.getIntrinsicWidth(), ph = poster.getIntrinsicHeight();
            if (pw > 0 && ph > 0) {
                float s = Math.max(cardW / (float) pw, cardH / (float) ph);
                int w = Math.round(pw * s), h = Math.round(ph * s);
                poster.setBounds((cardW - w) / 2, (cardH - h) / 2, (cardW + w) / 2, (cardH + h) / 2);
            } else {
                poster.setBounds(0, 0, cardW, cardH);
            }
            poster.draw(canvas);
        } else if (icon != null) {
            int size = cardH * 40 / 100;
            int left = (cardW - size) / 2, top = (cardH - size) / 2 - cardH / 12;
            icon.setBounds(left, top, left + size, top + size);
            icon.draw(canvas);
        }
        if (item.progress > 0f) {
            float pad = Ui.dp(getContext(), 10), h = Ui.dp(getContext(), 4);
            float y = cardH - pad - h;
            bar.set(pad, y, cardW - pad, y + h);
            canvas.drawRoundRect(bar, h / 2, h / 2, track);
            bar.right = pad + (cardW - pad * 2) * Math.min(1f, item.progress);
            canvas.drawRoundRect(bar, h / 2, h / 2, done);
        }
    }

    private void drawTwoLines(Canvas canvas) {
        float x = Ui.dp(getContext(), 2);
        float first = cardH + Ui.dp(getContext(), 20);
        CharSequence a = TextUtils.ellipsize(title, under, cardW - x, TextUtils.TruncateAt.END);
        canvas.drawText(a, 0, a.length(), x, first, under);
        if (!TextUtils.isEmpty(item.subtitle)) {
            CharSequence b = TextUtils.ellipsize(item.subtitle, small, cardW - x, TextUtils.TruncateAt.END);
            canvas.drawText(b, 0, b.length(), x, first + Ui.dp(getContext(), 18), small);
        }
    }

    private void drawLine(Canvas canvas, TextPaint paint, float x, float baseline) {
        float room = cardW - Ui.dp(getContext(), 16);
        CharSequence s = TextUtils.ellipsize(title, paint, room, TextUtils.TruncateAt.END);
        canvas.drawText(s, 0, s.length(), x, baseline, paint);
    }

    private void drawArrows(Canvas canvas) {
        float h = cardH / 6f, w = h * 0.7f, mid = cardH / 2f, pad = h * 0.8f;
        inner.setStyle(Paint.Style.FILL);
        arrow.reset();
        arrow.moveTo(pad, mid);
        arrow.lineTo(pad + w, mid - h / 2);
        arrow.lineTo(pad + w, mid + h / 2);
        arrow.close();
        arrow.moveTo(cardW - pad, mid);
        arrow.lineTo(cardW - pad - w, mid - h / 2);
        arrow.lineTo(cardW - pad - w, mid + h / 2);
        arrow.close();
        canvas.drawPath(arrow, inner);
    }
}
