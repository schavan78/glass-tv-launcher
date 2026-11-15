package tv.dumbtv.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Shows an HDMI, AV or other passthrough input inside the launcher, through Android's TvView.
 *
 * Handing the passthrough channel URI to the TV's own app is not enough everywhere: on a Fire TV
 * Edition television that app is Amazon's Live TV, which lands in its channel-scan settings when
 * no channels are scanned, and Amazon's home opens inputs through a service that needs a system
 * permission. TvView is the public API for the same thing: it asks the TV input service for a
 * session and hands it this window's surface. No permission is involved, and the picture goes away
 * with this screen, so HOME and BACK behave like everywhere else in the launcher.
 */
public final class Input extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final String EXTRA_ID = "input", EXTRA_NAME = "name";
    private static final long NAME_MS = 2500, SIGNAL_MS = 6000;
    private static final int FAILED = R.string.input_failed, NO_SIGNAL = R.string.input_no_signal;
    private static final String AMAZON_LIVE_TV = "com.amazon.tv.livetv";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final AudioManager.OnAudioFocusChangeListener focus = change -> { };
    private static final String PREFS = "tuner";
    private TvView view;
    /** Cable TV only: the tuner's channels, the one showing and the one before it. */
    private List<Channels.Channel> channels = new java.util.ArrayList<>();
    private int index, previous;
    private TextView name, status;
    private String id;
    private final Runnable hideName = () -> name.animate().alpha(0f).setDuration(250)
            .withEndAction(() -> name.setVisibility(View.GONE)).start();
    private final Runnable noSignal = () -> status(getString(noSignalText()));

    private final TvView.TvInputCallback watch = new TvView.TvInputCallback() {
        @Override
        public void onConnectionFailed(String inputId) {
            status(getString(FAILED));
        }

        @Override
        public void onDisconnected(String inputId) {
            status(getString(FAILED));
        }

        @Override
        public void onVideoAvailable(String inputId) {
            status(null);
        }

        @Override
        public void onVideoUnavailable(String inputId, int reason) {
            // Tuning and buffering are on the way to a picture; everything else means none is coming.
            boolean pending = reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING
                    || reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING;
            if (!pending) status(getString(noSignalText()));
        }
    };

    /**
     * The intent that opens {@code it} on this screen, or null when the TV's own app should get the
     * passthrough URI: always for "tv" if one exists, and for "auto" only when a single app other
     * than Amazon's Live TV claims it.
     */
    private static final String CHANNEL_ITEM = "vnd.android.cursor.item/channel";
    /** No real channel has this id, so Fire TV's player resumes the last channel watched. */
    private static final long ANY_CHANNEL = Integer.MAX_VALUE;

    /**
     * Fire TV's own live player and guide, for the TV's tuner. Fire OS hides every channel list
     * from other apps and reserves its guide and tune links for Amazon's apps; the one door left
     * open is "view this channel". Given a channel that does not exist, the player resumes the
     * last channel watched (cable, antenna or an app's live channel), with the guide and channel
     * up/down working as usual. Null when Amazon's Live TV is not there to take it.
     */
    static Intent liveTv(Context c) {
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(TvContract.buildChannelUri(ANY_CHANNEL), CHANNEL_ITEM)
                .setPackage(AMAZON_LIVE_TV);
        return c.getPackageManager().resolveActivity(i, 0) == null ? null : i;
    }

    static Intent intent(Context c, Config cfg, Item it, CharSequence name) {
        if (Catalog.isTuner(c, it.id)) {
            Intent live = liveTv(c);
            if (live != null) return live;
        }
        String mode = cfg.get(Config.SWITCH);
        List<ResolveInfo> apps = c.getPackageManager().queryIntentActivities(it.intent, 0);
        boolean handOver = "tv".equals(mode) ? !apps.isEmpty()
                : "auto".equals(mode) && apps.size() == 1 && !viewerIsLiveTv(apps.get(0));
        // Amazon's Live TV opens its settings instead of the tuner's channels, so the built-in
        // tuner is always shown here, with channel up and down.
        if (handOver && !Catalog.isTuner(c, it.id)) return null;
        return new Intent(c, Input.class).putExtra(EXTRA_ID, it.id).putExtra(EXTRA_NAME, name);
    }

    /**
     * Amazon's Live TV claims every passthrough URI, then redirects to its own settings or a
     * channel scan instead of showing the input (seen on Fire OS 7 and 8 televisions).
     */
    private static boolean viewerIsLiveTv(ResolveInfo app) {
        return app.activityInfo != null && AMAZON_LIVE_TV.equals(app.activityInfo.packageName);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        id = getIntent().getStringExtra(EXTRA_ID);
        // The video is a layer behind this window; a transparent window lets the text over it blend
        // instead of punching black boxes into the picture.
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        FrameLayout root = new FrameLayout(this);
        if (getSystemService(Context.TV_INPUT_SERVICE) != null) {
            view = new TvView(this);
            view.setCallback(watch);
            view.setFocusable(true);
            root.addView(view, new FrameLayout.LayoutParams(MATCH, MATCH));
        }

        status = Ui.text(this, 22, Ui.MUTED, Ui.MEDIUM);
        status.setVisibility(View.GONE);
        root.addView(status, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER));

        name = Ui.text(this, 20, Ui.TEXT, Ui.MEDIUM);
        name.setText(getIntent().getCharSequenceExtra(EXTRA_NAME));
        name.setPadding(dp(22), dp(10), dp(22), dp(10));
        name.setBackground(Ui.round(0xB3000000, dp(10)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.START);
        lp.setMargins(dp(56), dp(40), 0, 0);
        root.addView(name, lp);
        setContentView(root);
    }

    @Override
    @SuppressWarnings("deprecation") // the AudioFocusRequest replacement is Android 8+, Fire OS 6 is 7.1
    protected void onStart() {
        super.onStart();
        showName();
        if (view == null || id == null) {
            status(getString(FAILED));
            return;
        }
        ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        view.setStreamVolume(1f);
        if (Catalog.isTuner(this, id)) channels = Channels.load(this, id);
        if (!channels.isEmpty()) {
            long last = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(id, -1);
            index = 0;
            for (int i = 0; i < channels.size(); i++) if (channels.get(i).id == last) index = i;
            tuneChannel(false);
        } else {
            // HDMI, or a tuner whose channel list Fire OS keeps to itself: the tuner then plays
            // its own current channel and takes channel up/down itself (keys reach its session
            // through the focused TvView).
            view.tune(id, TvContract.buildChannelUriForPassthroughInput(id));
        }
        view.requestFocus();
        // Most input services report why there is no picture; this covers one that stays silent.
        ui.postDelayed(noSignal, SIGNAL_MS);
    }

    /** Releases the input so the TV stops routing it the moment this screen leaves. */
    @Override
    @SuppressWarnings("deprecation")
    protected void onStop() {
        ui.removeCallbacksAndMessages(null);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (view != null) {
            view.reset();
            ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(focus);
        }
        super.onStop();
    }

    /** BACK leaves; Menu or Info shows the input's name again. Other keys go to the input. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
                if (e.getAction() == KeyEvent.ACTION_UP) finish();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
                if (e.getAction() == KeyEvent.ACTION_DOWN) showName();
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_LAST_CHANNEL:
                if (channels.isEmpty()) return super.dispatchKeyEvent(e);
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    int code = e.getKeyCode();
                    if (code == KeyEvent.KEYCODE_LAST_CHANNEL) {
                        int back = previous;
                        previous = index;
                        index = back;
                    } else {
                        int step = code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1;
                        previous = index;
                        index = (index + step + channels.size()) % channels.size();
                    }
                    tuneChannel(true);
                }
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    /** Tunes the channel at {@link #index}, shows its number and name, and remembers it. */
    private void tuneChannel(boolean remember) {
        Channels.Channel ch = channels.get(index);
        status(null);
        view.tune(id, TvContract.buildChannelUri(ch.id));
        name.setText(ch.title());
        showName();
        ui.removeCallbacks(noSignal);
        ui.postDelayed(noSignal, SIGNAL_MS);
        if (remember) getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(id, ch.id).apply();
    }

    /** For Cable TV the likely reason is an unscanned tuner, so say where the scan is. */
    private int noSignalText() {
        return Catalog.isTuner(this, id) && channels.isEmpty() ? R.string.input_no_signal_tuner : NO_SIGNAL;
    }

    private void showName() {
        ui.removeCallbacks(hideName);
        name.animate().cancel();
        name.setAlpha(1f);
        name.setVisibility(View.VISIBLE);
        ui.postDelayed(hideName, NAME_MS);
    }

    /**
     * Keeps the TV awake while the input shows a picture, as Amazon's own input player does;
     * otherwise the screensaver and then sleep come on mid-film or mid-game. With no
     * signal the normal timeout applies again, so an input left on a dead source still sleeps.
     */
    private void status(String text) {
        Log.i("dumbtv", "input " + id + ": " + (text == null ? "video" : text));
        if (text == null) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ui.removeCallbacks(noSignal);
        status.setText(text);
        status.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
