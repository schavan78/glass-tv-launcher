package tv.dumbtv.home;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Control Panel › Profile: Me, Guests and Kids, each with its own apps in the dock (which are
 * shown and hidden, their order, App order and how many fit). Switching saves the current set
 * under the profile being left and loads the other one's; a profile used for the first time
 * starts as a copy of the current set. Kept in home.conf as profile.&lt;name&gt;.&lt;key&gt;.
 */
final class Profiles {
    static final String KEY = "profile";
    static final String[] NAMES = {"Me", "Guests", "Kids"};
    /** The settings a profile owns; everything else (look, wallpaper, clock) is shared. */
    static final String[] KEYS = {"apps.hide", "apps.show", "apps.order", Config.ORDER.key, Config.DOCK_APPS.key};
    private static final String SAVED = "saved";

    private Profiles() {}

    static String current(Config cfg) {
        String p = cfg.get(KEY, NAMES[0]);
        return Arrays.asList(NAMES).contains(p) ? p : NAMES[0];
    }

    static String next(Config cfg) {
        int i = Arrays.asList(NAMES).indexOf(current(cfg));
        return NAMES[(i + 1) % NAMES.length];
    }

    /** Saves the current dock under the active profile and loads {@code to}'s, in one write. */
    static void switchTo(Config cfg, String to) {
        String from = current(cfg);
        if (from.equals(to) || !Arrays.asList(NAMES).contains(to)) return;
        Map<String, String> changes = new LinkedHashMap<>();
        for (String k : KEYS) changes.put(prefix(from) + k, cfg.get(k, ""));
        changes.put(prefix(from) + SAVED, "1");
        if ("1".equals(cfg.get(prefix(to) + SAVED, ""))) {
            for (String k : KEYS) changes.put(k, cfg.get(prefix(to) + k, ""));
        }
        changes.put(KEY, to);
        cfg.setAll(changes);
    }

    private static String prefix(String name) {
        return KEY + "." + name.toLowerCase(Locale.ROOT) + ".";
    }
}
