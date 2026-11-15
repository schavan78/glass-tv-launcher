package tv.dumbtv.home;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The channels the TV's built-in tuner (antenna or coax cable) found in its last channel scan,
 * for the Cable TV tile. Read from TvProvider with READ_EPG_DATA, which only returns channels
 * the TV lists as browsable. Sorted by channel number, so channel up/down go in guide order.
 */
final class Channels {
    static final class Channel {
        final long id;
        final String number, name;

        Channel(long id, String number, String name) {
            this.id = id;
            this.number = number == null ? "" : number;
            this.name = name == null ? "" : name;
        }

        String title() {
            return TextUtils.isEmpty(name) ? number : number.isEmpty() ? name : number + "  " + name;
        }
    }

    private static final String[] COLUMNS = {
        TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NUMBER,
        TvContract.Channels.COLUMN_DISPLAY_NAME,
    };

    private Channels() {}

    /** Every channel of {@code inputId}, in channel-number order; empty when none or not readable. */
    static List<Channel> load(Context c, String inputId) {
        List<Channel> out = new ArrayList<>();
        try (Cursor q = c.getContentResolver().query(TvContract.buildChannelsUriForInput(inputId),
                COLUMNS, null, null, null)) {
            while (q != null && q.moveToNext()) {
                out.add(new Channel(q.getLong(0), q.getString(1), q.getString(2)));
            }
        } catch (RuntimeException e) {
            Log.w("dumbtv", "tuner channels: " + e);
            return new ArrayList<>();
        }
        Collections.sort(out, (a, b) -> compare(a.number, b.number));
        return out;
    }

    /** "2" < "7.1" < "7.2" < "10" < "10.1"; parts that are not numbers sort after, by text. */
    static int compare(String a, String b) {
        String[] x = a.split("[.\\-]"), y = b.split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            if (i >= x.length) return -1;
            if (i >= y.length) return 1;
            Long p = number(x[i]), q = number(y[i]);
            int d;
            if (p != null && q != null) d = Long.compare(p, q);
            else if (p != null) d = -1;
            else if (q != null) d = 1;
            else d = x[i].compareTo(y[i]);
            if (d != 0) return d;
        }
        return 0;
    }

    private static Long number(String s) {
        if (s.isEmpty() || s.length() > 12) return null;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return null;
        return Long.parseLong(s);
    }
}
