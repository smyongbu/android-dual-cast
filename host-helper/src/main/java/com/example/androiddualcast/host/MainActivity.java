package com.example.androiddualcast.host;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int padding = dp(24);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText(R.string.helper_title);
        title.setTextSize(28f);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView body = new TextView(this);
        body.setText(R.string.helper_body);
        body.setTextSize(16f);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(18), 0, dp(18));
        root.addView(body);

        Button overlay = new Button(this);
        overlay.setText(R.string.open_overlay);
        overlay.setOnClickListener(v -> openOverlaySettings());
        root.addView(overlay);

        Button wireless = new Button(this);
        wireless.setText(R.string.open_developer_options);
        wireless.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        root.addView(wireless);

        TextView tipsTitle = new TextView(this);
        tipsTitle.setText(R.string.tips_title);
        tipsTitle.setTextSize(20f);
        tipsTitle.setGravity(Gravity.CENTER);
        tipsTitle.setPadding(0, dp(22), 0, dp(8));
        root.addView(tipsTitle);

        TextView tipsBody = new TextView(this);
        tipsBody.setText(R.string.tips_body);
        tipsBody.setTextSize(15f);
        tipsBody.setGravity(Gravity.CENTER);
        root.addView(tipsBody);

        setContentView(root);
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= 23) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
