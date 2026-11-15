package tv.dumbtv.home;

/**
 * A plain-Java blur for the frosted glass tiles. Android 9 has no RenderEffect, and
 * RenderScript is deprecated, so the wallpaper is shrunk to about 1/8 of the screen and run
 * through three box blurs, which look like one gaussian. On a 240x135 picture that takes a few
 * milliseconds, once per wallpaper, on the io thread. No android.* types, so it runs in tests.
 */
final class Blur {
    private Blur() {}

    /** Blurs ARGB {@code px} ({@code w} x {@code h}) in place. A radius below 1 does nothing. */
    static void apply(int[] px, int w, int h, int radius) {
        if (radius < 1 || w <= 0 || h <= 0 || px.length < w * h) return;
        int[] tmp = new int[w * h];
        for (int pass = 0; pass < 3; pass++) {
            horizontal(px, tmp, w, h, radius);
            vertical(tmp, px, w, h, radius);
        }
    }

    private static void horizontal(int[] in, int[] out, int w, int h, int r) {
        int span = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            long a = 0, red = 0, g = 0, b = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[row + clamp(i, w)];
                a += p >>> 24; red += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = pack(a / span, red / span, g / span, b / span);
                int gone = in[row + clamp(x - r, w)], come = in[row + clamp(x + r + 1, w)];
                a += (come >>> 24) - (gone >>> 24);
                red += ((come >> 16) & 0xFF) - ((gone >> 16) & 0xFF);
                g += ((come >> 8) & 0xFF) - ((gone >> 8) & 0xFF);
                b += (come & 0xFF) - (gone & 0xFF);
            }
        }
    }

    private static void vertical(int[] in, int[] out, int w, int h, int r) {
        int span = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            long a = 0, red = 0, g = 0, b = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[clamp(i, h) * w + x];
                a += p >>> 24; red += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = pack(a / span, red / span, g / span, b / span);
                int gone = in[clamp(y - r, h) * w + x], come = in[clamp(y + r + 1, h) * w + x];
                a += (come >>> 24) - (gone >>> 24);
                red += ((come >> 16) & 0xFF) - ((gone >> 16) & 0xFF);
                g += ((come >> 8) & 0xFF) - ((gone >> 8) & 0xFF);
                b += (come & 0xFF) - (gone & 0xFF);
            }
        }
    }

    private static int clamp(int i, int n) {
        return i < 0 ? 0 : i >= n ? n - 1 : i;
    }

    private static int pack(long a, long r, long g, long b) {
        return (int) (a << 24 | r << 16 | g << 8 | b);
    }
}
