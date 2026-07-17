package com.example.androiddualcast.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
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
import android.widget.Toast;
import android.text.TextUtils;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.example.androiddualcast.receiver.adb.WirelessAdbManager;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private interface TestAction {
        void run(Button button);
    }

    private ProjectionView projectionView;
    private ProjectionSettings settings;
    private TouchEventSender sender;
    private AdbServiceLauncher serviceLauncher;
    private TextView statusView;
    private LinearLayout controlBar;
    private LinearLayout menuContent;
    private boolean projectionRequested;
    private boolean pausedForBackground;
    private final ExecutorService connectionTester = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        settings = ProjectionSettings.load(this);
        String hotspotIp = needsAutomaticIp(settings.phoneIp) ? detectHotspotPhoneIp() : null;
        if (hotspotIp != null && needsAutomaticIp(settings.phoneIp)) {
            settings = settings.withPhoneIp(hotspotIp);
            settings.save(this);
        }
        AppLog.write(this, "应用", "启动，版本 0.3.12；手机IP=" + settings.phoneIp
                + (hotspotIp == null ? "（保留设置）" : "（自动识别）"));
        sender = new TouchEventSender(settings);
        projectionView = new ProjectionView(this, settings, sender);
        serviceLauncher = new AdbServiceLauncher(this, settings, projectionView, new AdbServiceLauncher.Listener() {
            @Override
            public void onStatus(int stringRes) {
                statusView.setText(stringRes);
            }

            @Override
            public void onDetail(String detail) {
                if (detail != null && detail.trim().length() > 0) {
                    statusView.setText(detail.trim());
                }
            }
        });
        FrameLayout root = new FrameLayout(this);
        // 等待背景只能放在视频 Surface 后面，避免车机把它合成到画面上方。
        root.setBackgroundColor(Color.rgb(31, 53, 83));
        root.addView(projectionView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (settings.navigationBar) root.addView(createNavigationBar(), navigationLayoutParams());
        controlBar = createControlBar();
        root.addView(controlBar, sideBarLayoutParams(false));
        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        projectionView.release();
        serviceLauncher.close();
        connectionTester.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        if (projectionRequested) {
            pausedForBackground = true;
            projectionView.pauseVideo();
            serviceLauncher.stopSession();
            AppLog.write(this, "生命周期", "进入后台，已安全停止视频会话");
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (projectionRequested && pausedForBackground) {
            pausedForBackground = false;
            statusView.setText("正在自动恢复投屏…");
            statusView.postDelayed(() -> serviceLauncher.connectAndStart(detectHotspotPhoneIp()), 500);
            AppLog.write(this, "生命周期", "返回前台，自动恢复投屏");
        }
    }

    private LinearLayout createControlBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(0xDD000000);
        bar.setPadding(dp(6), dp(5), dp(6), dp(5));

        Button collapseButton = makeButton("◀");
        collapseButton.setTextSize(25f);
        collapseButton.setTextColor(Color.WHITE);
        collapseButton.setBackgroundColor(Color.TRANSPARENT);
        collapseButton.setOnClickListener(v -> setMenuCollapsed(menuContent.getVisibility() == android.view.View.VISIBLE,
                collapseButton));
        bar.addView(collapseButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        menuContent = new LinearLayout(this);
        menuContent.setOrientation(LinearLayout.VERTICAL);
        menuContent.setGravity(Gravity.CENTER);
        bar.addView(menuContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        statusView = new TextView(this);
        statusView.setText(R.string.status_idle);
        statusView.setTextColor(0xffffffff);
        statusView.setTextSize(12f);
        statusView.setGravity(Gravity.CENTER);
        statusView.setMaxLines(3);
        statusView.setEllipsize(TextUtils.TruncateAt.END);
        menuContent.addView(statusView);

        Button settingsButton = makeButton(getString(R.string.settings));
        settingsButton.setOnClickListener(v -> showSettingsDialog());
        menuContent.addView(settingsButton);

        Button usbButton = makeButton("有线 ADB 初始化");
        usbButton.setOnClickListener(v -> UsbAdbInitializer.start(this, new UsbAdbInitializer.Listener() {
            @Override public void onStatus(String message) { statusView.setText(message); }
            @Override public void onSuccess() {
                statusView.setText("有线 ADB 初始化成功，已开启 5555");
                new AlertDialog.Builder(MainActivity.this).setTitle("初始化成功")
                        .setMessage("现在可以拔掉数据线，打开手机热点，让车机连接热点后点击“无线投屏”。")
                        .setPositiveButton("知道了", null).show();
            }
            @Override public void onError(String message) {
                statusView.setText("有线 ADB 初始化失败");
                new AlertDialog.Builder(MainActivity.this).setTitle("初始化失败")
                        .setMessage(message).setPositiveButton("知道了", null).show();
            }
        }));
        menuContent.addView(usbButton);

        Button landscapeButton = makeButton("横屏");
        landscapeButton.setOnClickListener(v -> projectionView.setOrientation(true));
        menuContent.addView(landscapeButton);

        Button portraitButton = makeButton("竖屏");
        portraitButton.setOnClickListener(v -> projectionView.setOrientation(false));
        menuContent.addView(portraitButton);

        Button pairButton = makeButton("无线调试配对/投屏");
        pairButton.setOnClickListener(v -> showPairingDialog());
        menuContent.addView(pairButton);

        Button logButton = makeButton("导出日志");
        logButton.setOnClickListener(v -> exportLog());
        menuContent.addView(logButton);

        return bar;
    }

    private static boolean needsAutomaticIp(String value) {
        String ip = value == null ? "" : value.trim();
        return ip.length() == 0 || ip.endsWith(".");
    }

    private void startProjection() {
        String detected=detectHotspotPhoneIp();
        if(detected!=null&&needsAutomaticIp(settings.phoneIp)){settings=settings.withPhoneIp(detected);settings.save(this);serviceLauncher.updateSettings(settings);}
        projectionRequested=true;sender.connect();serviceLauncher.connectAndStart(detected);
    }

    private LinearLayout createNavigationBar() {
        boolean vertical=settings.navigationPosition>=2;
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(vertical?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);nav.setGravity(Gravity.CENTER);nav.setBackgroundColor(0xaa000000);
        int[] keys={4,3,187};String[] labels={"返回","主页","最近"};
        for(int i=0;i<keys.length;i++){Button b=makeButton(labels[i]);final int key=keys[i];b.setTextColor(Color.WHITE);b.setBackgroundColor(Color.TRANSPARENT);b.setOnClickListener(v->sender.sendKey(key));nav.addView(b,new LinearLayout.LayoutParams(vertical?-1:0,vertical?0:-1,1));}
        return nav;
    }

    private FrameLayout.LayoutParams navigationLayoutParams(){boolean vertical=settings.navigationPosition>=2;FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(vertical?dp(72):-1,vertical?-1:dp(58));p.gravity=settings.navigationPosition==1?Gravity.TOP:settings.navigationPosition==2?Gravity.START:settings.navigationPosition==3?Gravity.END:Gravity.BOTTOM;return p;}

    /** 车机连接手机热点后，Wi-Fi 默认网关就是手机的热点地址。 */
    private String detectHotspotPhoneIp() {
        if (android.os.Build.VERSION.SDK_INT < 21) return null;
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue;
                LinkProperties properties = manager.getLinkProperties(network);
                if (properties == null) continue;
                for (RouteInfo route : properties.getRoutes()) {
                    InetAddress gateway = route.getGateway();
                    if (route.isDefaultRoute() && gateway instanceof java.net.Inet4Address
                            && !gateway.isAnyLocalAddress()) return gateway.getHostAddress();
                }
            }
        } catch (Exception e) {
            AppLog.write(this, "热点", "识别手机热点IP失败：" + e);
        }
        return null;
    }

    private void setMenuCollapsed(boolean collapsed, Button toggle) {
        menuContent.setVisibility(collapsed ? android.view.View.GONE : android.view.View.VISIBLE);
        toggle.setText(collapsed ? "▶" : "◀");
        toggle.setAlpha(collapsed ? 0.55f : 1f);
        controlBar.setBackgroundColor(collapsed ? Color.TRANSPARENT : 0xDD000000);
        controlBar.setPadding(collapsed ? dp(4) : dp(6), collapsed ? dp(4) : dp(5),
                collapsed ? dp(4) : dp(6), collapsed ? dp(4) : dp(5));
        controlBar.setLayoutParams(sideBarLayoutParams(collapsed));
    }


    private void exportLog() {
        connectionTester.execute(() -> {
            try {
                String path = AppLog.exportToDownloads(this);
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("日志已导出")
                        .setMessage("文件位置：" + path + "\n\n请把这个 txt 文件发送给我。")
                        .setPositiveButton("知道了", null).show());
            } catch (Exception e) {
                AppLog.write(this, "日志", "导出失败：" + e);
                runOnUiThread(() -> Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
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

    private FrameLayout.LayoutParams sideBarLayoutParams(boolean collapsed) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(collapsed ? 50 : 112),
                collapsed ? dp(50) : FrameLayout.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.START | Gravity.TOP;
        return params;
    }

    private void showSettingsDialog() {
        LinearLayout form = dialogForm();
        EditText ip = editText(settings.phoneIp, InputType.TYPE_CLASS_PHONE);
        EditText maxWidth = editText(String.valueOf(settings.width), InputType.TYPE_CLASS_NUMBER);
        EditText maxHeight = editText(String.valueOf(settings.height), InputType.TYPE_CLASS_NUMBER);
        EditText bitrate = editText(String.valueOf(settings.bitrateMbps), InputType.TYPE_CLASS_NUMBER);
        EditText fps = editText(String.valueOf(settings.fps), InputType.TYPE_CLASS_NUMBER);
        EditText dpi = editText(String.valueOf(settings.densityDpi), InputType.TYPE_CLASS_NUMBER);
        RadioGroup projectionMode=new RadioGroup(this);projectionMode.setOrientation(RadioGroup.HORIZONTAL);RadioButton mirror=new RadioButton(this);mirror.setText("镜像投屏");RadioButton desktop=new RadioButton(this);desktop.setText("桌面投屏");projectionMode.addView(mirror);projectionMode.addView(desktop);projectionMode.check(settings.mirrorMode?mirror.getId():desktop.getId());
        if(mirror.getId()==-1){mirror.setId(android.view.View.generateViewId());desktop.setId(android.view.View.generateViewId());projectionMode.check(settings.mirrorMode?mirror.getId():desktop.getId());}
        CheckBox nav = checkBox(R.string.navigation_bar, settings.navigationBar);
        Spinner navPosition=new Spinner(this);navPosition.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"下方","上方","左侧","右侧"}));navPosition.setSelection(Math.max(0,Math.min(3,settings.navigationPosition)));
        Spinner displayMode = new Spinner(this);
        displayMode.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"等比完整显示（推荐）", "等比铺满并裁切", "拉伸铺满"}));
        displayMode.setSelection(Math.max(0, Math.min(2, settings.displayMode)));

        addLabeledWithTest(form, R.string.phone_ip, ip, R.string.test_ip,
                button -> testIp(ip, button));
        addLabeledText(form, "最大宽度", maxWidth);
        addLabeledText(form, "最大高度", maxHeight);
        addLabeledView(form, "显示模式", displayMode);
        addLabeled(form, R.string.bitrate, bitrate);
        addLabeled(form, R.string.fps, fps);
        addLabeledText(form, "桌面显示密度 DPI（仅桌面投屏）", dpi);
        addLabeledView(form,"投屏方式",projectionMode);
        form.addView(nav);
        addLabeledView(form,"导航栏位置",navPosition);

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    settings = settings.withPhoneIp(ip.getText().toString().trim())
                            .withStreamingOptions(parseInt(maxWidth, 1280), parseInt(maxHeight, 720),
                                    parseInt(bitrate, 4), parseInt(fps, 25), parseInt(dpi, 240),
                                    projectionMode.getCheckedRadioButtonId()==mirror.getId(), nav.isChecked(), displayMode.getSelectedItemPosition(),navPosition.getSelectedItemPosition());
                    settings.save(this);
                    // 交给 Activity 生命周期统一释放旧连接并按新设置重新初始化。
                    // 手动关闭 sender 后再释放 projectionView 会重复关闭同一个 sender，
                    // 在主线程触发 RejectedExecutionException，表现为保存后卡死或闪退。
                    recreate();
                })
                .show();
    }

    private void showPairingDialog() {
        LinearLayout form = dialogForm();
        TextView hint = new TextView(this);
        hint.setText(R.string.pairing_hint);
        form.addView(hint);
        EditText debugPort = editText(settings.debugPort > 0
                ? String.valueOf(settings.debugPort) : "", InputType.TYPE_CLASS_NUMBER);
        EditText pairPort = editText(settings.pairingPort > 0
                ? String.valueOf(settings.pairingPort) : "", InputType.TYPE_CLASS_NUMBER);
        EditText pairCode = editText(settings.pairingCode, InputType.TYPE_CLASS_NUMBER);
        addLabeledWithTest(form, R.string.debug_port, debugPort, R.string.test_port,
                button -> testPortValue(settings.phoneIp, debugPort, button));
        addLabeledWithTest(form, R.string.pair_port, pairPort, R.string.test_port,
                button -> testPortValue(settings.phoneIp, pairPort, button));
        addLabeled(form, R.string.pair_code, pairCode);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.pairing_title)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton("直接投屏", null)
                .setPositiveButton(R.string.pair, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("配对并投屏");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> verifyPairing(dialog, debugPort, pairPort, pairCode, dialog.getButton(AlertDialog.BUTTON_POSITIVE)));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view->{savePairingFields(debugPort,pairPort,pairCode);dialog.dismiss();startProjection();});
        });
        dialog.setOnDismissListener(ignored -> savePairingFields(debugPort, pairPort, pairCode));
        dialog.show();
    }

    private void savePairingFields(EditText debugPort, EditText pairPort, EditText pairCode) {
        settings = settings.withPairingSettings(parsePortOrZero(debugPort),
                parsePortOrZero(pairPort), pairCode.getText().toString().trim());
        settings.save(this);
    }

    private void verifyPairing(AlertDialog dialog, EditText debugPortField,
            EditText pairPortField, EditText pairCodeField, Button button) {
        savePairingFields(debugPortField, pairPortField, pairCodeField);
        String host = settings.phoneIp == null ? "" : settings.phoneIp.trim();
        int debugPort = parsePort(debugPortField);
        int pairPort = parsePort(pairPortField);
        String pairCode = pairCodeField.getText().toString().trim();
        if (host.length() == 0 || host.endsWith(".")) {
            showTestResult(button, getString(R.string.test_ip_invalid));
            return;
        }
        if (debugPort < 1 || pairPort < 1) {
            showTestResult(button, getString(R.string.test_port_invalid));
            return;
        }
        if (!pairCode.matches("\\d{6}")) {
            showTestResult(button, getString(R.string.pair_code_invalid));
            return;
        }

        button.setEnabled(false);
        connectionTester.execute(() -> {
            try {
                WirelessAdbManager manager = WirelessAdbManager.getInstance(this);
                boolean paired = manager.pair(host, pairPort, pairCode);
                boolean connected = manager.connect(host, debugPort);
                if (connected) {
                    manager.disconnect();
                }
                showPairingResult(dialog, button, paired && connected,
                        paired && connected ? getString(R.string.pairing_verified)
                                : getString(R.string.pairing_connect_failed));
            } catch (Exception exception) {
                showPairingResult(dialog, button, false,
                        getString(R.string.pairing_failed_detail,
                                exception.getClass().getSimpleName()));
            }
        });
    }

    private void showPairingResult(AlertDialog dialog, Button button, boolean success,
            String message) {
        runOnUiThread(() -> {
            button.setEnabled(true);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            statusView.setText(message);
            if (success) {
                dialog.dismiss();
                startProjection();
            }
        });
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
        edit.setTextColor(Color.WHITE);
        edit.setTextSize(16f);
        edit.setPadding(dp(12), dp(6), dp(12), dp(6));
        edit.setBackground(inputBackground());
        return edit;
    }

    private CheckBox checkBox(int label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setChecked(checked);
        return box;
    }

    private void addLabeled(LinearLayout form, int label, EditText editText) {
        LinearLayout row = formRow();
        row.addView(fieldTitle(label), new LinearLayout.LayoutParams(dp(132),
                LinearLayout.LayoutParams.MATCH_PARENT));
        row.addView(editText, new LinearLayout.LayoutParams(0, dp(48), 1f));
        form.addView(row);
    }

    private void addLabeledText(LinearLayout form, String label, EditText editText) {
        addLabeledView(form, label, editText);
    }

    private void addLabeledView(LinearLayout form, String label, android.view.View view) {
        LinearLayout row = formRow();
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(0xffdddddd);
        title.setTextSize(15f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, 0, dp(12), 0);
        row.addView(title, new LinearLayout.LayoutParams(dp(132), LinearLayout.LayoutParams.MATCH_PARENT));
        row.addView(view, new LinearLayout.LayoutParams(0, dp(48), 1f));
        form.addView(row);
    }

    private void addLabeledWithTest(LinearLayout form, int label, EditText editText,
            int buttonText, TestAction action) {
        LinearLayout row = formRow();
        row.addView(fieldTitle(label), new LinearLayout.LayoutParams(dp(132),
                LinearLayout.LayoutParams.MATCH_PARENT));
        row.addView(editText, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button testButton = makeButton(getString(buttonText));
        testButton.setOnClickListener(view -> action.run(testButton));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(96), dp(48));
        buttonParams.setMargins(dp(8), 0, 0, 0);
        row.addView(testButton, buttonParams);
        form.addView(row);
    }

    private LinearLayout formRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        row.setLayoutParams(params);
        return row;
    }

    private TextView fieldTitle(int label) {
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(0xffdddddd);
        title.setTextSize(15f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, 0, dp(12), 0);
        return title;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff1f1f1f);
        background.setStroke(dp(1), 0xff777777);
        background.setCornerRadius(dp(3));
        return background;
    }

    private void testIp(EditText ipField, Button button) {
        String host = ipField.getText().toString().trim();
        if (host.length() == 0) {
            showTestResult(button, getString(R.string.test_ip_empty));
            return;
        }
        button.setEnabled(false);
        connectionTester.execute(() -> {
            try {
                InetAddress address = InetAddress.getByName(host);
                boolean reachable = address.isReachable(2500);
                showTestResult(button, reachable
                        ? getString(R.string.test_ip_success, address.getHostAddress())
                        : getString(R.string.test_ip_no_response, address.getHostAddress()));
            } catch (Exception exception) {
                showTestResult(button, getString(R.string.test_ip_invalid));
            }
        });
    }

    private void testPort(EditText ipField, EditText portField, Button button) {
        testPortValue(ipField.getText().toString().trim(), portField, button);
    }

    private void testPortValue(String host, EditText portField, Button button) {
        int port = parsePort(portField);
        if (host == null || host.trim().length() == 0) {
            showTestResult(button, getString(R.string.test_ip_empty));
            return;
        }
        if (port < 1) {
            showTestResult(button, getString(R.string.test_port_invalid));
            return;
        }
        final String targetHost = host.trim();
        button.setEnabled(false);
        connectionTester.execute(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(targetHost, port), 2500);
                showTestResult(button, getString(R.string.test_port_success, port));
            } catch (Exception exception) {
                showTestResult(button, getString(R.string.test_port_failed, port));
            }
        });
    }

    private int parsePort(EditText editText) {
        int port = parseInt(editText, -1);
        return port >= 1 && port <= 65535 ? port : -1;
    }

    private int parsePortOrZero(EditText editText) {
        int port = parsePort(editText);
        return port > 0 ? port : 0;
    }

    private void showTestResult(Button button, String message) {
        runOnUiThread(() -> {
            button.setEnabled(true);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
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
