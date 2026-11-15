package tv.dumbtv.home;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.IntConsumer;

/**
 * The menu that opens when OK is held on a tile: a frosted card next to the tile, with its name
 * and the actions as rows, the focused one white, as on tvOS. It replaces a full-width dialog,
 * so the tile it belongs to stays in sight.
 */
final class Popup {
    private static final int INK = 0xFF16171A, PAPER = 0xF2FFFFFF;

    private Popup() {}

    static void show(Activity a, View anchor, CharSequence title, String[] actions, IntConsumer pick) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 10), dp(a, 12), dp(a, 10), dp(a, 10));
        GradientDrawable bg = Ui.round(0xEB1E1F24, dp(a, 22));
        bg.setStroke(Math.max(1, dp(a, 1)), 0x33FFFFFF);
        card.setBackground(bg);

        TextView name = Ui.text(a, 13, Ui.MUTED, Ui.MEDIUM);
        name.setText(title);
        name.setSingleLine(true);
        name.setPadding(dp(a, 14), 0, dp(a, 14), dp(a, 8));
        card.addView(name);

        View first = null;
        for (int i = 0; i < actions.length; i++) {
            int which = i;
            TextView row = Ui.text(a, 17, Ui.TEXT, Ui.MEDIUM);
            row.setText(actions[i]);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding(dp(a, 16), dp(a, 11), dp(a, 24), dp(a, 11));
            StateListDrawable s = new StateListDrawable();
            s.addState(new int[] {android.R.attr.state_focused}, Ui.round(PAPER, dp(a, 14)));
            s.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
            row.setBackground(s);
            row.setTextColor(new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}},
                    new int[] {INK, Ui.TEXT}));
            row.setOnClickListener(v -> {
                d.dismiss();
                pick.accept(which);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(a, 2);
            card.addView(row, lp);
            if (first == null) first = row;
        }
        d.setContentView(card);

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams p = w.getAttributes();
            p.dimAmount = 0.35f;
            p.width = dp(a, 280);
            p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            p.gravity = Gravity.TOP | Gravity.START;
            // Beside the tile: to its right, or to its left near the right edge of the screen.
            int[] at = new int[2];
            anchor.getLocationOnScreen(at);
            int screenW = a.getResources().getDisplayMetrics().widthPixels;
            int screenH = a.getResources().getDisplayMetrics().heightPixels;
            int right = at[0] + Math.round(anchor.getWidth() * anchor.getScaleX()) + dp(a, 16);
            p.x = right + p.width <= screenW - dp(a, 24) ? right : Math.max(dp(a, 24), at[0] - p.width - dp(a, 16));
            int rows = actions.length;
            int estimate = dp(a, 40) + rows * dp(a, 46);
            p.y = Math.max(dp(a, 24), Math.min(at[1], screenH - estimate - dp(a, 24)));
            w.setAttributes(p);
        }
        d.show();
        if (first != null) first.requestFocus();
    }

    private static int dp(Activity a, float v) {
        return Ui.dp(a, v);
    }
}
