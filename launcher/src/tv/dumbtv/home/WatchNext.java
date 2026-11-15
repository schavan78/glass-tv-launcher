package tv.dumbtv.home;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.text.TextUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Continue Watching: what apps put in Android TV's Watch Next list (TvProvider, Android 8+).
 * Reading it needs READ_EPG_DATA, granted once over adb. The columns are named, not read with a
 * null projection, because some TV builds add or drop columns. Posters load only from content://
 * and android.resource:// addresses: the launcher has no INTERNET permission, so a web poster
 * falls back to the app's icon on glass. Every failure gives an empty row, which hides itself.
 */
final class WatchNext {
    static final String PERMISSION = "com.android.providers.tv.permission.READ_EPG_DATA";
    private static final Uri URI = Uri.parse("content://android.media.tv/watch_next_program");
    private static final String[] COLUMNS = {
        "_id", "package_name", "title", "episode_title", "season_display_number",
        "episode_display_number", "poster_art_uri", "intent_uri",
        "last_playback_position_millis", "duration_millis",
    };
    private static final int MAX = 12;

    private WatchNext() {}

    /** io thread. Newest first; programs whose app is gone or cannot be opened are skipped. */
    static List<Item> load(Context c, int posterWidth) {
        List<Item> out = new ArrayList<>();
        if (c.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return out;
        ContentResolver cr = c.getContentResolver();
        PackageManager pm = c.getPackageManager();
        try (Cursor q = cr.query(URI, COLUMNS, null, null, "last_engagement_time_utc_millis DESC")) {
            while (q != null && q.moveToNext() && out.size() < MAX) {
                Item it = item(q, pm);
                if (it != null) out.add(it);
            }
        } catch (RuntimeException e) {
            Keeper.note("watch next: " + e.getClass().getSimpleName());
            return new ArrayList<>();
        }
        for (Item it : out) art(c, cr, pm, it, posterWidth);
        return out;
    }

    private static Item item(Cursor q, PackageManager pm) {
        String pkg = q.getString(1), title = q.getString(2);
        if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(title)) return null;
        Intent open = null;
        String uri = q.getString(7);
        if (!TextUtils.isEmpty(uri)) {
            try {
                open = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                open.setSelector(null);
                open.setComponent(null);
                open.setPackage(pkg); // only ever opens the app that published the entry
            } catch (URISyntaxException e) {
                open = null;
            }
        }
        if (open == null || pm.resolveActivity(open, 0) == null) {
            open = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (open == null) open = pm.getLaunchIntentForPackage(pkg);
        }
        if (open == null) return null;
        Item it = new Item("watch:" + q.getLong(0), title, open);
        it.app = pkg;
        it.poster = q.getString(6);
        it.subtitle = subtitle(q.getString(4), q.getString(5), q.getString(3));
        long at = q.isNull(8) ? 0 : q.getLong(8), length = q.isNull(9) ? 0 : q.getLong(9);
        it.progress = length > 0 ? Math.max(0.02f, Math.min(1f, at / (float) length)) : -1f;
        it.leanback = true;
        it.artRequested = true;
        it.colorRequested = true;
        return it;
    }

    /** "S2 E7 · Title", whichever parts the app gave; null when it gave none. */
    static String subtitle(String season, String episode, String name) {
        StringBuilder s = new StringBuilder();
        if (!TextUtils.isEmpty(season)) s.append('S').append(season);
        if (!TextUtils.isEmpty(episode)) s.append(s.length() > 0 ? " " : "").append('E').append(episode);
        if (!TextUtils.isEmpty(name)) s.append(s.length() > 0 ? " · " : "").append(name);
        return s.length() == 0 ? null : s.toString();
    }

    private static void art(Context c, ContentResolver cr, PackageManager pm, Item it, int width) {
        String p = it.poster;
        if (p != null && (p.startsWith(ContentResolver.SCHEME_CONTENT + ":")
                || p.startsWith(ContentResolver.SCHEME_ANDROID_RESOURCE + ":"))) {
            Bitmap b = decode(cr, Uri.parse(p), width);
            if (b != null) it.banner = new BitmapDrawable(c.getResources(), b);
        }
        if (it.banner == null) {
            try {
                it.icon = pm.getApplicationIcon(it.app);
            } catch (PackageManager.NameNotFoundException e) {
                it.icon = null;
            }
        }
    }

    /** Decodes a poster at about {@code width} pixels wide, so a 4K poster costs a card's worth. */
    private static Bitmap decode(ContentResolver cr, Uri uri, int width) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, o);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (o.outWidth <= 0) return null;
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= width) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        try (InputStream in = cr.openInputStream(uri)) {
            return in == null ? null : BitmapFactory.decodeStream(in, null, o);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
