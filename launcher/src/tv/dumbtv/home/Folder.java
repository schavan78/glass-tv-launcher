package tv.dumbtv.home;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Options › Dock › Apps in the dock: the apps that do not fit open from the dock's ••• icon in a
 * frosted grid above it, five to a row, each its rounded icon and name, the selected one white,
 * as a folder does on tvOS and macOS.
 */
final class Folder {
    interface Opener {
        void open(Item it);
    }

    private static final int COLS = 5, INK = 0xFF16171A, PAPER = 0xF2FFFFFF;

    private Folder() {}

    static void show(Activity a, String title, List<Item> items, Config cfg, Opener opener) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 20), dp(a, 16), dp(a, 20), dp(a, 18));
        GradientDrawable bg = Ui.round(0xEB1E1F24, dp(a, 26));
        bg.setStroke(Math.max(1, dp(a, 1)), 0x33FFFFFF);
        box.setBackground(bg);
        TextView name = Ui.text(a, 14, Ui.MUTED, Ui.MEDIUM);
        name.setText(title);
        name.setPadding(dp(a, 8), 0, 0, dp(a, 10));
        box.addView(name);

        LinearLayout row = null;
        View first = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row);
            }
            Item it = items.get(i);
            View cell = cell(a, it, Catalog.label(cfg, it), () -> {
                d.dismiss();
                opener.open(it);
            });
            row.addView(cell);
            if (first == null) first = cell;
        }
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams p = w.getAttributes();
            p.dimAmount = 0.4f;
            p.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            p.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            p.y = dp(a, 150); // above the dock
            w.setAttributes(p);
        }
        d.show();
        if (first != null) first.requestFocus();
    }

    private static View cell(Activity a, Item it, CharSequence label, Runnable click) {
        LinearLayout cell = new LinearLayout(a);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(a, 10), dp(a, 10), dp(a, 10), dp(a, 8));
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setContentDescription(label);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, Ui.round(PAPER, dp(a, 18)));
        s.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
        cell.setBackground(s);

        int size = dp(a, 64);
        ImageView icon = new ImageView(a);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setImageDrawable(art(a, it));
        float radius = size * 0.22f;
        icon.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        icon.setClipToOutline(true);
        cell.addView(icon, new LinearLayout.LayoutParams(size, size));

        TextView t = Ui.text(a, 13, Ui.TEXT, Ui.MEDIUM);
        t.setText(label);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setDuplicateParentStateEnabled(true);
        t.setTextColor(new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {INK, Ui.TEXT}));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, 92), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 6);
        cell.addView(t, lp);
        cell.setOnClickListener(v -> click.run());
        cell.setOnFocusChangeListener((v, has) ->
                v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(120).start());
        return cell;
    }

    /** The app's icon, loaded now if the dock never drew it; its banner if it has no icon. */
    private static Drawable art(Activity a, Item it) {
        if (it.icon != null) return it.icon;
        if (it.info != null) {
            Drawable d = it.info.loadIcon(a.getPackageManager());
            if (d != null) return d;
        }
        return it.banner;
    }

    private static int dp(Activity a, float v) {
        return Ui.dp(a, v);
    }
}
