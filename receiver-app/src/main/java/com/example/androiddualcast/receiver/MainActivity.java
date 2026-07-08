package com.example.androiddualcast.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private ProjectionView projectionView;
    private ProjectionSettings settings;
    private TouchEventSender sender;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        settings = ProjectionSettings.load(this);
        sender = new TouchEventSender(settings);
        projectionView = new ProjectionView(this, settings, sender);

        FrameLayout root = new FrameLayout(this);
        root.addView(projectionView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(createControlBar(), sideBarLayoutParams());
        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        projectionView.release();
        super.onDestroy();
    }

    private LinearLayout createControlBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(0xDD000000);
        int pad = dp(8);
        bar.setPadding(pad, pad, pad, pad);

        Button settingsButton = makeButton(getString(R.string.settings));
        settingsButton.setOnClickListener(v -> showSettingsDialog());
        bar.addView(settingsButton);

        Button connectButton = makeButton(getString(R.string.connect_wireless));
        connectButton.setOnClickListener(v -> sender.connect());
        bar.addView(connectButton);

        Button pairButton = makeButton(getString(R.string.open_pairing));
        pairButton.setOnClickListener(v -> showPairingDialog());
        bar.addView(pairButton);

        return bar;
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(2, 2, 2, 2);
        return button;
    }

    private FrameLayout.LayoutParams sideBarLayoutParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(112),
                FrameLayout.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.START;
        return params;
    }

    private void showSettingsDialog() {
        LinearLayout form = dialogForm();
        EditText ip = editText(settings.phoneIp, InputType.TYPE_CLASS_PHONE);
        EditText bitrate = editText(String.valueOf(settings.bitrateMbps), InputType.TYPE_CLASS_NUMBER);
        EditText fps = editText(String.valueOf(settings.fps), InputType.TYPE_CLASS_NUMBER);
        EditText dpi = editText(String.valueOf(settings.densityDpi), InputType.TYPE_CLASS_NUMBER);
        CheckBox mirror = checkBox(R.string.mirror_mode, settings.mirrorMode);
        CheckBox nav = checkBox(R.string.navigation_bar, settings.navigationBar);

        addLabeled(form, R.string.phone_ip, ip);
        addLabeled(form, R.string.bitrate, bitrate);
        addLabeled(form, R.string.fps, fps);
        addLabeled(form, R.string.density_dpi, dpi);
        form.addView(mirror);
        form.addView(nav);

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    settings = settings.withPhoneIp(ip.getText().toString().trim())
                            .withStreamingOptions(parseInt(bitrate, 4), parseInt(fps, 25),
                                    parseInt(dpi, 240), mirror.isChecked(), nav.isChecked());
                    settings.save(this);
                    sender.close();
                    sender = new TouchEventSender(settings);
                    projectionView.release();
                    projectionView = new ProjectionView(this, settings, sender);
                    recreate();
                })
                .show();
    }

    private void showPairingDialog() {
        LinearLayout form = dialogForm();
        TextView hint = new TextView(this);
        hint.setText(R.string.pairing_hint);
        form.addView(hint);
        addLabeled(form, R.string.debug_port, editText("", InputType.TYPE_CLASS_NUMBER));
        addLabeled(form, R.string.pair_port, editText("", InputType.TYPE_CLASS_NUMBER));
        addLabeled(form, R.string.pair_code, editText("", InputType.TYPE_CLASS_NUMBER));

        new AlertDialog.Builder(this)
                .setTitle(R.string.pairing_title)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.pair, null)
                .show();
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        form.setPadding(pad, pad / 2, pad, 0);
        return form;
    }

    private EditText editText(String value, int inputType) {
        EditText edit = new EditText(this);
        edit.setText(value);
        edit.setInputType(inputType);
        edit.setSingleLine(true);
        return edit;
    }

    private CheckBox checkBox(int label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setChecked(checked);
        return box;
    }

    private void addLabeled(LinearLayout form, int label, EditText editText) {
        TextView title = new TextView(this);
        title.setText(label);
        form.addView(title);
        form.addView(editText);
    }

    private int parseInt(EditText editText, int fallback) {
        try {
            return Integer.parseInt(editText.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
