package tv.dumbtv.home;

/**
 * Light or dark ink for the home screen, read from the wallpaper. Text sits on the top third
 * (clock, date) and the glass tiles across the middle, so those bands decide; a bright sky over
 * dark trees still asks for dark ink. Pure Java so the thresholds are tested on the host.
 */
final class Tone {
    /** Above this the picture is light: dark text and white glass. */
    static final float LIGHT_ABOVE = 0.58f;
    /** Below this it is dark. Between the two, the current choice stays, so it does not flicker. */
    static final float DARK_BELOW = 0.50f;

    private Tone() {}

    /** Mean relative luminance (0..1) of the top two thirds of ARGB {@code px}, w x h. */
    static float luminance(int[] px, int w, int h) {
        int rows = Math.max(1, h * 2 / 3);
        double sum = 0;
        int n = 0;
        for (int y = 0; y < rows && y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = px[y * w + x];
                sum += 0.2126 * linear((p >> 16) & 0xFF) + 0.7152 * linear((p >> 8) & 0xFF)
                        + 0.0722 * linear(p & 0xFF);
                n++;
            }
        }
        return n == 0 ? 0f : (float) Math.sqrt(sum / n); // back towards perceived brightness
    }

    /** Whether to use light glass for {@code lum}, given the current choice {@code wasLight}. */
    static boolean light(float lum, boolean wasLight) {
        if (lum >= LIGHT_ABOVE) return true;
        if (lum <= DARK_BELOW) return false;
        return wasLight;
    }

    /** White text needs what is behind it at or below this; dark text at or above {@link #LIGHT_FLOOR}. */
    static final float DARK_CEILING = 0.42f, LIGHT_FLOOR = 0.62f;

    /**
     * How strong (0..0.7) a dark wash ({@code lightGlass} false, white text) or a white wash
     * ({@code lightGlass} true, dark text) must be over a picture of brightness {@code lum} for
     * the text on it to read. 0 when the picture already suits the text, as Auto usually picks;
     * more when Text and glass is fixed against the picture (white text on snow).
     */
    static float scrim(float lum, boolean lightGlass) {
        float k = lightGlass
                ? (LIGHT_FLOOR - lum) / Math.max(0.01f, 1f - lum)
                : 1f - DARK_CEILING / Math.max(0.01f, lum);
        return Math.max(0f, Math.min(0.7f, k));
    }

    private static double linear(int c) {
        double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
