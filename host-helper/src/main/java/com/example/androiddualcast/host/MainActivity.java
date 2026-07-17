package com.example.androiddualcast.host;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;

/** 独立虚拟屏幕桌面。 */
public final class MainActivity extends Activity {
    private static final String PREFS = "desktop_settings";
    private LinearLayout appRows;
    private List<ResolveInfo> allApps;
    private final BroadcastReceiver orientationReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            setLandscape("landscape".equals(intent.getStringExtra("mode")));
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(Color.rgb(20, 28, 40));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24), dp(14), dp(24), dp(10));

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        appRows = new LinearLayout(this); appRows.setOrientation(LinearLayout.VERTICAL); scroll.addView(appRows);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root);

        loadApps(); rebuildDesktop();
        IntentFilter orientationFilter = new IntentFilter("com.example.androiddualcast.host.SET_ORIENTATION");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(orientationReceiver, orientationFilter, Context.RECEIVER_EXPORTED);
        else registerReceiver(orientationReceiver, orientationFilter);
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("landscape", true)) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(orientationReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void loadApps() {
        Intent query = new Intent(Intent.ACTION_MAIN); query.addCategory(Intent.CATEGORY_LAUNCHER);
        allApps = getPackageManager().queryIntentActivities(query, 0);
        Collections.sort(allApps, new ResolveInfo.DisplayNameComparator(getPackageManager()));
    }

    private void rebuildDesktop() {
        appRows.removeAllViews(); Set<String> hidden = hiddenPackages();
        ArrayList<View> tiles = new ArrayList<>();
        tiles.add(utilityTile("隐藏应用", "−", 0xff596574, v -> showHiddenAppsDialog()));
        for (ResolveInfo app : allApps) if (!app.activityInfo.packageName.equals(getPackageName()) && !hidden.contains(app.activityInfo.packageName)) tiles.add(appTile(app));
        final int columns = 5;
        for (int start = 0; start < tiles.size(); start += columns) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.TOP);
            for (int column = 0; column < columns; column++) {
                int index = start + column;
                if (index < tiles.size()) row.addView(tiles.get(index), new LinearLayout.LayoutParams(0, dp(126), 1f));
                else row.addView(new Space(this), new LinearLayout.LayoutParams(0, dp(126), 1f));
            }
            appRows.addView(row, new LinearLayout.LayoutParams(-1, dp(126)));
        }
    }

    private View utilityTile(String labelText, String symbol, int color, View.OnClickListener action) {
        LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER); tile.setPadding(dp(6), dp(5), dp(6), dp(4));
        TextView icon = text(symbol, 24, Color.WHITE); icon.setGravity(Gravity.CENTER);
        GradientDrawable iconBackground = new GradientDrawable(); iconBackground.setColor(color); iconBackground.setCornerRadius(dp(16)); icon.setBackground(iconBackground);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView label = text(labelText, 14, Color.WHITE); label.setGravity(Gravity.CENTER); label.setMaxLines(2);
        tile.addView(label, new LinearLayout.LayoutParams(-1, dp(42)));
        tile.setOnClickListener(action); return tile;
    }

    private View appTile(ResolveInfo info) {
        LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER); tile.setPadding(dp(6), dp(5), dp(6), dp(4));
        ImageView icon = new ImageView(this); icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE); icon.setAdjustViewBounds(false);
        Drawable drawable = info.loadIcon(getPackageManager()); icon.setImageDrawable(drawable);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView label = text(String.valueOf(info.loadLabel(getPackageManager())), 14, Color.WHITE); label.setGravity(Gravity.CENTER); label.setMaxLines(2); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(label, new LinearLayout.LayoutParams(-1, dp(42)));
        String pkg = info.activityInfo.packageName;
        tile.setOnClickListener(v -> { Intent launch=getPackageManager().getLaunchIntentForPackage(pkg); if(launch!=null) startActivity(launch); });
        tile.setOnLongClickListener(v -> { Set<String> h=new HashSet<>(hiddenPackages()); h.add(pkg); saveHidden(h); rebuildDesktop(); Toast.makeText(this,"已隐藏 "+label.getText(),Toast.LENGTH_SHORT).show(); return true; });
        return tile;
    }

    private void showHiddenAppsDialog() {
        boolean[] checked=new boolean[allApps.size()]; Set<String> hidden=hiddenPackages();
        for(int i=0;i<allApps.size();i++)checked[i]=hidden.contains(allApps.get(i).activityInfo.packageName);
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(18),dp(6),dp(18),0);
        EditText search=new EditText(this);search.setHint("搜索应用");search.setSingleLine(true);form.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));
        LinearLayout actions=new LinearLayout(this);Button selectAll=new Button(this);selectAll.setText("全选");Button invert=new Button(this);invert.setText("反选");actions.addView(selectAll,new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(invert,new LinearLayout.LayoutParams(0,dp(48),1));form.addView(actions);
        ListView list=new ListView(this);list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);form.addView(list,new LinearLayout.LayoutParams(-1,dp(360)));
        final int[][] visible={new int[0]};
        Runnable refresh=()->{String key=search.getText().toString().trim().toLowerCase(Locale.US);ArrayList<String> names=new ArrayList<>();ArrayList<Integer> indexes=new ArrayList<>();for(int i=0;i<allApps.size();i++){String name=String.valueOf(allApps.get(i).loadLabel(getPackageManager()));if(key.length()==0||name.toLowerCase(Locale.US).contains(key)){names.add(name);indexes.add(i);}}visible[0]=new int[indexes.size()];for(int i=0;i<indexes.size();i++)visible[0][i]=indexes.get(i);list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_multiple_choice,names));list.post(()->{for(int i=0;i<visible[0].length;i++)list.setItemChecked(i,checked[visible[0][i]]);});};
        list.setOnItemClickListener((p,v,pos,id)->checked[visible[0][pos]]=list.isItemChecked(pos));
        selectAll.setOnClickListener(v->{for(int index:visible[0])checked[index]=true;refresh.run();});
        invert.setOnClickListener(v->{for(int index:visible[0])checked[index]=!checked[index];refresh.run();});
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){refresh.run();}public void afterTextChanged(android.text.Editable e){}});refresh.run();
        new AlertDialog.Builder(this).setTitle("隐藏应用").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{Set<String> set=new HashSet<>();for(int i=0;i<checked.length;i++)if(checked[i])set.add(allApps.get(i).activityInfo.packageName);saveHidden(set);rebuildDesktop();}).show();
    }
    private Set<String> hiddenPackages(){return new HashSet<>(getSharedPreferences(PREFS,MODE_PRIVATE).getStringSet("hidden",Collections.emptySet()));}
    private void saveHidden(Set<String> value){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putStringSet("hidden",new HashSet<>(value)).apply();}
    private void setLandscape(boolean enabled){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("landscape",enabled).apply();setRequestedOrientation(enabled?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);}

    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    static String getLocalIp(){
        try {
            ArrayList<NetworkInterface> interfaces=new ArrayList<>(Collections.list(NetworkInterface.getNetworkInterfaces()));
            Collections.sort(interfaces,(a,b)->Integer.compare(interfacePriority(a.getName()),interfacePriority(b.getName())));
            for(NetworkInterface n:interfaces) for(InetAddress a:Collections.list(n.getInetAddresses()))
                if(!a.isLoopbackAddress()&&a instanceof Inet4Address&&a.isSiteLocalAddress()) return a.getHostAddress();
        }catch(Exception ignored){}
        return "未检测到热点地址";
    }
    private static int interfacePriority(String name){String n=name==null?"":name.toLowerCase(Locale.US);if(n.contains("softap")||n.contains("swlan")||n.startsWith("ap"))return 0;if(n.startsWith("wlan"))return 1;return 2;}
}
