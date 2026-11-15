package tv.dumbtv.home;

import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.StateListDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * The Control Panel, opened from the home screen's top bar: frosted tiles for Wi-Fi, Bluetooth,
 * memory, picture and sound, and shortcuts to the inputs, Fire TV's settings and Home settings.
 * Fire OS keeps its own quick settings to itself, so the tiles open the matching pages of
 * Fire TV's Settings (the ones it lets other apps open). Memory frees what Android would close
 * anyway: apps already in the background, never the one playing.
 */
public final class Panel extends Activity {
    static final String FOCUS_INPUTS = "focus_inputs";
    private static final String SETTINGS = "com.amazon.tv.settings.v2";
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int INK = 0xFF16171A, PAPER = 0xF2FFFFFF;

    private TextView memoryState;
    private ProgressBar memoryBar;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0C0C0E);
        if (Glass.frost() != null) {
            ImageView blur = new ImageView(this);
            blur.setScaleType(ImageView.ScaleType.CENTER_CROP);
            blur.setImageBitmap(Glass.frost());
            root.addView(blur, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        View dim = new View(this);
        dim.setBackgroundColor(0xA60B0C0F);
        root.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END);
        glp.setMargins(0, dp(48), dp(48), 0);
        root.addView(grid, glp);

        pill(grid, null, Glyph.WIFI, getString(R.string.panel_wifi), wifiState(),
                () -> open(new Intent("android.settings.WIFI_SETTINGS")));
        View first = lastRow.getChildAt(0);
        pill(lastRow, Glyph.BLUETOOTH, getString(R.string.panel_bluetooth), bluetoothState(),
                () -> open(new Intent("android.settings.CONTROLLERS_SETTINGS").setPackage(SETTINGS)));
        pill(grid, null, Glyph.MEMORY, getString(R.string.panel_memory), "", this::freeMemory);
        memoryState = lastState;
        memoryBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        memoryBar.setMax(100);
        memoryBar.setProgressTintList(ColorStateList.valueOf(0xFFFFFFFF));
        memoryBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x40FFFFFF));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(130), dp(3));
        blp.topMargin = dp(5);
        memoryBar.setMinimumHeight(0);
        ((LinearLayout) memoryState.getParent()).addView(memoryBar, blp);
        pill(lastRow, Glyph.DISPLAY, getString(R.string.panel_display), getString(R.string.panel_display_state),
                () -> open(new Intent("android.settings.DISPLAY_SETTINGS").setPackage(SETTINGS)));
        pill(grid, null, Glyph.ROWS, getString(R.string.panel_apps), getString(R.string.panel_apps_state),
                () -> open(new Intent("android.settings.APPLICATION_SETTINGS")));
        pill(lastRow, Glyph.HOME, getString(R.string.options_title), getString(R.string.panel_home_state),
                () -> open(new Intent(this, Options.class)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(WRAP, WRAP);
        bl.topMargin = dp(10);
        grid.addView(buttons, bl);
        square(buttons, Glyph.INPUT, getString(R.string.row_inputs), () -> {
            startActivity(new Intent(this, Home.class).putExtra(FOCUS_INPUTS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            finish();
        });
        square(buttons, Glyph.SETTINGS, getString(R.string.settings), () -> {
            Intent tv = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS");
            open(tv);
        });

        // Down from the bottom tiles goes to the button straight below, not the nearest centre.
        LinearLayout lastPills = (LinearLayout) grid.getChildAt(grid.getChildCount() - 2);
        for (int i = 0; i < 2 && i < buttons.getChildCount(); i++) {
            View tile = lastPills.getChildAt(i), button = buttons.getChildAt(i);
            tile.setId(View.generateViewId());
            button.setId(View.generateViewId());
            tile.setNextFocusDownId(button.getId());
            button.setNextFocusUpId(tile.getId());
        }

        setContentView(root);
        showMemory();
        first.post(first::requestFocus);
    }

    // ---------------------------------------------------------------- tiles

    private LinearLayout lastRow;
    private TextView lastState;

    /**
     * A wide rounded tile with an icon, a name and a state line. With {@code row} null it starts
     * a new row of two in {@code grid}; otherwise it is the second tile of {@code row}. Returns
     * its name view (its parent's parent is the tile).
     */
    private TextView pill(LinearLayout grid, LinearLayout row, int glyph, String name, String state,
            Runnable click) {
        if (row == null) {
            row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.bottomMargin = dp(10);
            grid.addView(row, lp);
        }
        lastRow = row;
        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setPadding(dp(16), dp(8), dp(16), dp(8));
        tile.setBackground(states(dp(30)));
        Glyph g = new Glyph(glyph, Ui.TEXT);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(g);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(12), 0, 0, 0);
        words.setDuplicateParentStateEnabled(true);
        TextView a = Ui.text(this, 16, Ui.TEXT, Ui.MEDIUM);
        a.setText(name);
        a.setSingleLine(true);
        a.setDuplicateParentStateEnabled(true);
        a.setTextColor(inked(Ui.TEXT, INK));
        TextView b = Ui.text(this, 13, Ui.MUTED, Ui.MEDIUM);
        b.setText(state);
        b.setSingleLine(true);
        b.setPadding(0, dp(3), 0, 0);
        b.setDuplicateParentStateEnabled(true);
        b.setTextColor(inked(Ui.MUTED, 0xB3000000));
        words.addView(a);
        words.addView(b);
        tile.addView(words);
        tile.setOnFocusChangeListener((v, has) -> {
            g.setColor(has ? INK : Ui.TEXT);
            v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(120).start();
        });
        tile.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(220), dp(70));
        if (row.getChildCount() > 0) lp.leftMargin = dp(10);
        row.addView(tile, lp);
        lastState = b;
        return a;
    }

    private void pill(LinearLayout row, int glyph, String name, String state, Runnable click) {
        pill(null, row, glyph, name, state, click);
    }

    /** A round-cornered square button with only an icon; its name is read out, and shown on focus. */
    private void square(LinearLayout row, int glyph, String name, Runnable click) {
        ImageView b = new ImageView(this);
        Glyph g = new Glyph(glyph, Ui.TEXT);
        b.setImageDrawable(g);
        b.setPadding(dp(18), dp(18), dp(18), dp(18));
        b.setFocusable(true);
        b.setClickable(true);
        b.setContentDescription(name);
        b.setBackground(states(dp(18)));
        b.setOnFocusChangeListener((v, has) -> {
            g.setColor(has ? INK : Ui.TEXT);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
            if (has) Toast.makeText(this, name, Toast.LENGTH_SHORT).show();
        });
        b.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(64));
        if (row.getChildCount() > 0) lp.leftMargin = dp(10);
        row.addView(b, lp);
    }

    private StateListDrawable states(float radius) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.round(PAPER, radius));
        android.graphics.drawable.GradientDrawable idle = Ui.round(0x33FFFFFF, radius);
        idle.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
        bg.addState(new int[0], idle);
        return bg;
    }

    private static ColorStateList inked(int rest, int focused) {
        return new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}}, new int[] {focused, rest});
    }

    // ---------------------------------------------------------------- states and actions

    @SuppressWarnings("deprecation") // getActiveNetworkInfo: the API 25 way
    private String wifiState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
        if (n == null || !n.isConnected()) return getString(R.string.panel_not_connected);
        return n.getType() == ConnectivityManager.TYPE_ETHERNET ? getString(R.string.panel_ethernet)
                : getString(R.string.panel_connected);
    }

    private String bluetoothState() {
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        return getString(bt != null && bt.isEnabled() ? R.string.panel_on : R.string.panel_off);
    }

    private void showMemory() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo m = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(m);
        int used = (int) Math.round(100.0 * (m.totalMem - m.availMem) / Math.max(1, m.totalMem));
        memoryState.setText(getString(R.string.panel_memory_state, used));
        memoryBar.setProgress(used);
    }

    /**
     * Asks Android to close every installed app that is only cached in the background. It never
     * touches the app in front, system services, or this launcher, and apps reopen as usual.
     */
    private void freeMemory() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(before);
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo a : apps) {
            if ((a.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || a.packageName.equals(getPackageName())) continue;
            am.killBackgroundProcesses(a.packageName);
        }
        memoryState.postDelayed(() -> {
            ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(after);
            long freed = Math.max(0, after.availMem - before.availMem) / (1024 * 1024);
            showMemory();
            Toast.makeText(this, getString(R.string.panel_freed, freed), Toast.LENGTH_SHORT).show();
        }, 800);
    }

    /**
     * Opens a page of Fire TV's Settings. Fire OS reserves some of them for Amazon's own apps
     * (Display and sounds needs com.amazon.tv.permission.LAUNCHER_SETTINGS); when a page is
     * refused, Fire TV's main Settings screen opens instead, one step away from it.
     */
    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN)
                        .addCategory("android.intent.category.LEANBACK_SETTINGS"));
            } catch (ActivityNotFoundException | SecurityException again) {
                Toast.makeText(this, R.string.could_not_open, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
