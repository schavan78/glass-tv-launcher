package tv.dumbtv.home;

/** Host test for Blur and Tone: javac Blur.java Tone.java GlassMathTest.java, then java. */
public final class GlassMathTest {
    private static int failures;

    public static void main(String[] args) {
        blurKeepsFlatColour();
        blurSpreadsAnEdge();
        blurIgnoresBadInput();
        toneReadsBrightAndDark();
        toneHoldsInTheMiddle();
        toneLooksAtTheTopTwoThirds();
        scrimKeepsTextReadable();
        if (failures > 0) {
            System.out.println(failures + " failed");
            System.exit(1);
        }
        System.out.println("GlassMathTest ok");
    }

    private static void blurKeepsFlatColour() {
        int[] px = fill(20, 10, 0xFF336699);
        Blur.apply(px, 20, 10, 3);
        for (int p : px) {
            if (p != 0xFF336699) {
                check(false, "flat colour stays the same, got " + Integer.toHexString(p));
                return;
            }
        }
        check(true, "flat colour stays the same");
    }

    private static void blurSpreadsAnEdge() {
        int w = 20, h = 4;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) px[y * w + x] = x < w / 2 ? 0xFF000000 : 0xFFFFFFFF;
        }
        Blur.apply(px, w, h, 2);
        int left = px[w / 2 - 1] & 0xFF, right = px[w / 2] & 0xFF;
        check(left > 0 && left < 255, "dark side of the edge lightens: " + left);
        check(right > 0 && right < 255, "light side of the edge darkens: " + right);
        check((px[0] & 0xFF) == 0 && (px[w - 1] & 0xFF) == 255, "far ends keep their colour");
        check((px[0] >>> 24) == 0xFF, "alpha stays opaque");
    }

    private static void blurIgnoresBadInput() {
        int[] px = {0xFF112233};
        Blur.apply(px, 1, 1, 0);
        Blur.apply(px, 0, 0, 3);
        Blur.apply(px, 5, 5, 3); // too short: left alone, no exception
        check(px[0] == 0xFF112233, "bad sizes leave the pixels alone");
    }

    private static void toneReadsBrightAndDark() {
        check(Tone.light(Tone.luminance(fill(8, 6, 0xFFF4F6F8), 8, 6), false), "snow is light");
        check(!Tone.light(Tone.luminance(fill(8, 6, 0xFF1A1D22), 8, 6), true), "night is dark");
    }

    private static void toneHoldsInTheMiddle() {
        float mid = (Tone.LIGHT_ABOVE + Tone.DARK_BELOW) / 2;
        check(Tone.light(mid, true), "middle keeps light");
        check(!Tone.light(mid, false), "middle keeps dark");
    }

    private static void toneLooksAtTheTopTwoThirds() {
        int w = 6, h = 9;
        int[] px = fill(w, h, 0xFFFFFFFF);
        for (int i = w * 6; i < w * h; i++) px[i] = 0xFF000000; // dark bottom third only
        check(Tone.light(Tone.luminance(px, w, h), false), "dark foreground under a bright sky reads light");
    }

    private static void scrimKeepsTextReadable() {
        check(Tone.scrim(0.3f, false) == 0f, "white text on a dark picture needs no wash");
        check(Tone.scrim(0.85f, true) == 0f, "dark text on a bright picture needs no wash");
        float k = Tone.scrim(0.85f, false);
        check(k > 0.4f && 0.85f * (1 - k) <= Tone.DARK_CEILING + 0.001f, "white text on snow gets a dark wash: " + k);
        float w = Tone.scrim(0.2f, true);
        check(w > 0.4f && 0.2f + 0.8f * w >= Tone.LIGHT_FLOOR - 0.001f, "dark text on night gets a white wash: " + w);
        check(Tone.scrim(1f, false) <= 0.7f && Tone.scrim(0f, true) <= 0.7f, "never more than 70%");
    }

    private static int[] fill(int w, int h, int c) {
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, c);
        return px;
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }
}
