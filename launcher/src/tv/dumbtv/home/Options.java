package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Home settings, in the style of tvOS: categories on the left, frosted cards on the right over
 * the blurred wallpaper. A choice with a few looks is a list of cards with a check mark; any
 * other is one card that left/right or OK steps through. Every change is saved at once.
 */
public final class Options extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int INK = 0xFF16171A, PAPER = 0xF2FFFFFF;

    private final List<Runnable> refreshers = new ArrayList<>();
    private final List<View> categories = new ArrayList<>();
    private Config cfg;
    private LinearLayout content;
    private ScrollView pane;
    private TextView heading;
    private int current = -1;
    private Config.Choice iconPacks;

    private interface Page {
        void build();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0C0C0E);
        if (Glass.frost() != null) {
            ImageView blur = new ImageView(this);
            blur.setScaleType(ImageView.ScaleType.CENTER_CROP);
            blur.setImageBitmap(Glass.frost());
            root.addView(blur, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        View dim = new View(this);
        dim.setBackgroundColor(0xD90E1013);
        root.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout panes = new LinearLayout(this);
        panes.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(panes, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(48), dp(44), dp(20), dp(24));
        TextView title = Ui.text(this, 28, Ui.TEXT, Ui.MEDIUM);
        title.setText(R.string.options_title);
        title.setPadding(dp(14), 0, 0, dp(22));
        left.addView(title);
        panes.addView(left, new LinearLayout.LayoutParams(dp(330), MATCH));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(20), dp(44), dp(56), dp(20));
        heading = Ui.text(this, 22, Ui.TEXT, Ui.MEDIUM);
        heading.setPadding(dp(4), 0, 0, dp(14));
        right.addView(heading);
        pane = new ScrollView(this);
        pane.setFocusable(false);
        pane.setVerticalScrollBarEnabled(false);
        pane.setClipToPadding(false);
        pane.setPadding(0, dp(4), 0, dp(16));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        pane.addView(content, new FrameLayout.LayoutParams(MATCH, WRAP));
        right.addView(pane, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        TextView hint = Ui.text(this, 13, Ui.MUTED, Ui.MEDIUM);
        hint.setText(R.string.options_hint);
        hint.setPadding(dp(14), dp(8), dp(14), dp(8));
        hint.setBackground(Ui.round(0x1AFFFFFF, dp(16)));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        hlp.topMargin = dp(10);
        right.addView(hint, hlp);
        panes.addView(right, new LinearLayout.LayoutParams(0, MATCH, 1f));

        category(left, Glyph.APPEARANCE, R.string.cat_appearance, this::appearance);
        category(left, Glyph.EFFECT, R.string.cat_effect, this::effect);
        category(left, Glyph.WALLPAPER, R.string.cat_wallpaper, this::wallpaper);
        category(left, Glyph.ROWS, R.string.cat_rows, this::rows);
        category(left, Glyph.CLOCK, R.string.cat_clock, this::clock);
        category(left, Glyph.ADVANCED, R.string.cat_advanced, this::advanced);
        category(left, Glyph.ABOUT, R.string.cat_about, this::about);

        setContentView(root);
        // Finding the packs opens every theme app's resources: keep it off the UI thread.
        new Thread(() -> {
            Config.Choice packs = IconPack.choice(IconPack.installed(getPackageManager()),
                    getString(R.string.icon_pack_off), getString(R.string.icon_pack_none));
            runOnUiThread(() -> {
                iconPacks = packs;
                if (current == 5 && !isDestroyed()) show(5, false);
            });
        }, "icon-packs").start();
        categories.get(0).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg.refresh();
        refresh();
    }

    /** Back from the cards returns to their category; from the categories it closes. */
    @Override
    public void onBackPressed() {
        View f = getCurrentFocus();
        if (f != null && !categories.contains(f) && current >= 0) {
            categories.get(current).requestFocus();
            return;
        }
        super.onBackPressed();
    }

    private void refresh() {
        for (Runnable r : refreshers) r.run();
    }

    // ---------------------------------------------------------------- categories

    private void category(LinearLayout parent, int glyph, int name, Page page) {
        int index = categories.size();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(10), dp(9), dp(14), dp(9));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.round(PAPER, dp(14)));
        bg.addState(new int[] {android.R.attr.state_selected}, Ui.round(0x26FFFFFF, dp(14)));
        bg.addState(new int[0], Ui.round(0x00000000, dp(14)));
        row.setBackground(bg);
        Glyph g = new Glyph(glyph, Ui.TEXT);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(g);
        icon.setPadding(dp(5), dp(5), dp(5), dp(5));
        icon.setBackground(Ui.round(0x1FFFFFFF, dp(8)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView label = Ui.text(this, 17, Ui.TEXT, Ui.MEDIUM);
        label.setText(name);
        label.setDuplicateParentStateEnabled(true);
        label.setTextColor(inked(Ui.TEXT));
        label.setPadding(dp(14), 0, 0, 0);
        row.addView(label);
        row.setOnFocusChangeListener((v, has) -> {
            g.setColor(has ? INK : Ui.TEXT);
            if (has) show(index, false);
        });
        row.setOnClickListener(v -> show(index, true));
        row.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && e.getAction() == KeyEvent.ACTION_DOWN) {
                show(index, true);
                return true;
            }
            return false;
        });
        row.setTag(page);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.bottomMargin = dp(4);
        parent.addView(row, lp);
        categories.add(row);
    }

    /** Builds the cards of category {@code index}; {@code enter} also moves the focus into them. */
    private void show(int index, boolean enter) {
        if (index != current) {
            current = index;
            for (int i = 0; i < categories.size(); i++) categories.get(i).setSelected(i == index);
            refreshers.clear();
            content.removeAllViews();
            View row = categories.get(index);
            heading.setText(((TextView) ((ViewGroup) row).getChildAt(1)).getText());
            ((Page) row.getTag()).build();
            pinEnds();
            pane.scrollTo(0, 0);
            refresh();
        }
        if (enter && content.getChildCount() > 0) {
            for (int i = 0; i < content.getChildCount(); i++) {
                View v = content.getChildAt(i);
                if (v.isFocusable()) {
                    v.requestFocus();
                    break;
                }
            }
        }
    }

    /** Down on the last card and Up on the first stay put, instead of jumping to a category. */
    private void pinEnds() {
        View first = null, last = null;
        for (int i = 0; i < content.getChildCount(); i++) {
            View v = content.getChildAt(i);
            if (!v.isFocusable()) continue;
            if (first == null) first = v;
            last = v;
        }
        if (first == null) return;
        for (View v : new View[] {first, last}) {
            if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        }
        first.setNextFocusUpId(first.getId());
        last.setNextFocusDownId(last.getId());
    }

    // ---------------------------------------------------------------- pages

    private void appearance() {
        list(Config.LAYOUT, R.array.desc_layout);
        step(Config.DOCK_APPS, R.string.desc_dock_apps);
        list(Config.TILE_STYLE, R.array.desc_tile_style);
        list(Config.THEME, R.array.desc_theme);
        step(Config.ACCENT, 0);
        step(Config.TILES, 0);
        step(Config.LABELS, 0);
        step(Config.CHIPS, 0);
        step(Config.POSITION, R.string.desc_position);
    }

    private void effect() {
        list(Config.BACKDROP, R.array.desc_backdrop);
        section(R.string.sec_motion);
        step(Config.MOTION, R.string.desc_motion);
    }

    private void wallpaper() {
        list(Config.BACKGROUND, 0);
        section(R.string.sec_picture);
        step(Config.DIM, 0);
        step(Config.ROTATE, 0);
        action(getString(R.string.next_wallpaper), 0, () -> {
            cfg.nextWallpaper();
            refresh();
        });
    }

    private void rows() {
        section(R.string.sec_rows);
        step(Config.WATCH, R.string.desc_watch);
        step(Config.INPUTS, 0);
        step(Config.SWITCH, 0);
        step(Config.ALL_APPS, 0);
        step(Config.ORDER, 0);
        step(Config.LIBRARY, 0);
        section(R.string.sec_choose);
        action(getString(R.string.apps_on_home), 0, () -> pick(Catalog.APPS));
        action(getString(R.string.inputs_on_home), 0, () -> pick(Catalog.INPUTS));
    }

    private void clock() {
        step(Config.CLOCK, 0);
        step(Config.CLOCK_STYLE, R.string.desc_clock_style);
        step(Config.GREETING, R.string.desc_greeting);
        step(Config.MEMORY, 0);
    }

    private void advanced() {
        section(R.string.sec_family);
        step(Config.LOCK, 0);
        section(R.string.sec_icons);
        if (iconPacks != null) step(iconPacks, 0);
        section(R.string.sec_maintenance);
        action(getString(R.string.reset_layout), 0, this::reset);
        action(getString(R.string.export_settings), 0, this::export);
        action(getString(R.string.open_amazon_home), 0, () -> {
            Guard.amazon(this);
            finish();
        });
    }

    private void about() {
        TextView about = Ui.text(this, 14, Ui.MUTED, Ui.MEDIUM);
        about.setLineSpacing(0, 1.45f);
        about.setPadding(dp(18), dp(16), dp(18), dp(16));
        about.setBackground(Ui.round(0x1AFFFFFF, dp(14)));
        about.setFocusable(true);
        about.setText(getString(R.string.about, cfg.dir + "/" + Config.FILE, Config.WALLPAPERS,
                "/sdcard/Android/media/" + getPackageName() + "/", Library.FILE, Library.DIR));
        content.addView(about, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    // ---------------------------------------------------------------- cards

    private void section(int name) {
        TextView v = Ui.text(this, 12, Ui.FAINT, Ui.MEDIUM);
        v.setText(getString(name).toUpperCase(java.util.Locale.ROOT));
        v.setLetterSpacing(0.12f);
        v.setPadding(dp(18), content.getChildCount() == 0 ? 0 : dp(18), 0, dp(8));
        content.addView(v);
    }

    /** One card per value, the chosen one ticked: for choices that pick a look. */
    private void list(Config.Choice c, int descriptions) {
        if (descriptions != 0 || content.getChildCount() == 0) {
            TextView name = Ui.text(this, 12, Ui.FAINT, Ui.MEDIUM);
            name.setText(Text.title(this, c).toUpperCase(java.util.Locale.ROOT));
            name.setLetterSpacing(0.12f);
            name.setPadding(dp(18), content.getChildCount() == 0 ? 0 : dp(14), 0, dp(8));
            content.addView(name);
        }
        String[] about = descriptions == 0 ? null : getResources().getStringArray(descriptions);
        for (int i = 0; i < c.values.length; i++) {
            String value = c.values[i];
            LinearLayout card = card();
            LinearLayout words = twoLines(Text.name(this, c, value),
                    about != null && i < about.length ? about[i] : null);
            card.addView(words, new LinearLayout.LayoutParams(0, WRAP, 1f));
            Glyph tick = new Glyph(Glyph.CHECK, Ui.TEXT);
            ImageView mark = new ImageView(this);
            mark.setImageDrawable(tick);
            card.addView(mark, new LinearLayout.LayoutParams(dp(22), dp(22)));
            card.setOnClickListener(v -> {
                cfg.set(c.key, value);
                refresh();
            });
            card.setOnFocusChangeListener((v, has) -> tick.setColor(has ? INK : Ui.TEXT));
            refreshers.add(() -> mark.setVisibility(value.equals(cfg.get(c)) ? View.VISIBLE : View.INVISIBLE));
            content.addView(card);
        }
    }

    /** One card whose value left/right or OK steps through. */
    private void step(Config.Choice c, int description) {
        LinearLayout card = card();
        LinearLayout words = twoLines(Text.title(this, c), description == 0 ? null : getString(description));
        card.addView(words, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView value = Ui.text(this, 16, Ui.MUTED, Ui.MEDIUM);
        value.setGravity(Gravity.END);
        value.setDuplicateParentStateEnabled(true);
        card.addView(value, new LinearLayout.LayoutParams(WRAP, WRAP));
        card.setOnClickListener(v -> cycle(c, 1));
        card.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) return cycle(c, -1);
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) return cycle(c, 1);
            return false;
        });
        Runnable show = () -> {
            boolean focused = card.isFocused();
            String text = Text.name(this, c, cfg.get(c));
            if (c == Config.ACCENT) {
                SpannableString s = new SpannableString("●  " + text);
                s.setSpan(new ForegroundColorSpan(cfg.accent()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                value.setText(focused ? "‹  " : "");
                value.append(s);
                if (focused) value.append("  ›");
            } else {
                value.setText(focused ? "‹  " + text + "  ›" : text);
            }
            value.setTextColor(focused ? INK : Ui.MUTED);
            boolean off = (c == Config.DIM || c == Config.ROTATE)
                    && !"image".equals(cfg.get(Config.BACKGROUND))
                    && !"adaptive-tint".equals(cfg.get(Config.BACKGROUND))
                    || c == Config.SWITCH && "off".equals(cfg.get(Config.INPUTS))
                    || c == Config.ALL_APPS && "on".equals(cfg.get(Config.LOCK));
            card.setAlpha(off ? 0.45f : 1f);
        };
        card.setOnFocusChangeListener((v, has) -> show.run());
        refreshers.add(show);
        content.addView(card);
    }

    private boolean cycle(Config.Choice c, int by) {
        int n = c.values.length;
        // Nothing to switch to, as Icon pack with none installed: keep what is saved, so a pack
        // that comes back is still the chosen one.
        if (n < 2) return true;
        int i = Arrays.asList(c.values).indexOf(cfg.get(c));
        cfg.set(c.key, c.values[((i + by) % n + n) % n]);
        refresh();
        if (c == Config.LOCK && "on".equals(cfg.get(c))) {
            Toast.makeText(this, getString(R.string.lock_on, (int) (Home.UNLOCK_MS / 1000)),
                    Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private void action(String text, int description, Runnable run) {
        LinearLayout card = card();
        card.addView(twoLines(text, description == 0 ? null : getString(description)),
                new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView chevron = Ui.text(this, 18, Ui.MUTED, Ui.MEDIUM);
        chevron.setText("›");
        chevron.setDuplicateParentStateEnabled(true);
        chevron.setTextColor(inked(Ui.MUTED));
        card.addView(chevron);
        card.setOnClickListener(v -> run.run());
        content.addView(card);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(18), dp(13), dp(18), dp(13));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.round(PAPER, dp(14)));
        bg.addState(new int[0], Ui.round(0x1FFFFFFF, dp(14)));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.bottomMargin = dp(6);
        card.setLayoutParams(lp);
        return card;
    }

    private LinearLayout twoLines(String title, String subtitle) {
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setDuplicateParentStateEnabled(true);
        TextView a = Ui.text(this, 17, Ui.TEXT, Ui.MEDIUM);
        a.setText(title);
        a.setDuplicateParentStateEnabled(true);
        a.setTextColor(inked(Ui.TEXT));
        words.addView(a);
        if (!TextUtils.isEmpty(subtitle)) {
            TextView b = Ui.text(this, 13, Ui.MUTED, Ui.MEDIUM);
            b.setText(subtitle);
            b.setPadding(0, dp(4), 0, 0);
            b.setDuplicateParentStateEnabled(true);
            b.setTextColor(inked(Ui.MUTED));
            words.addView(b);
        }
        return words;
    }

    /** Dark ink while the card (white when focused) has the focus, {@code rest} otherwise. */
    private static ColorStateList inked(int rest) {
        return new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {rest == Ui.TEXT ? INK : 0xB3000000, rest});
    }

    // ---------------------------------------------------------------- actions

    private void pick(String kind) {
        List<Item> items = Catalog.APPS.equals(kind)
                ? Catalog.apps(this)
                : Catalog.inputs(this, (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE));
        CharSequence[] names = new CharSequence[items.size()];
        boolean[] on = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            names[i] = Catalog.label(cfg, items.get(i));
            on[i] = Catalog.shown(cfg, kind, items.get(i));
        }
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(Catalog.APPS.equals(kind) ? R.string.apps_on_home : R.string.inputs_on_home)
                .setMultiChoiceItems(names, on, (d, which, checked) ->
                        Catalog.setShown(cfg, kind, items.get(which), checked))
                .setPositiveButton(R.string.done, null)
                .show();
    }

    private void export() {
        java.io.File copy = cfg.export();
        Toast.makeText(this, copy == null ? getString(R.string.export_failed)
                : getString(R.string.export_done, copy.getPath()), Toast.LENGTH_LONG).show();
    }

    private void reset() {
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(R.string.reset_title)
                .setMessage(R.string.reset_message)
                .setPositiveButton(R.string.reset, (d, w) -> cfg.removePrefix("name.", "apps.", "inputs.", IconPack.CHOSEN))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
