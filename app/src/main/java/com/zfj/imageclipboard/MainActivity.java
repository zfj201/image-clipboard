package com.zfj.imageclipboard;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int BLUE=0xff2864e8,INK=0xff17243b,MUTED=0xff62738c;
    private com.zfj.imageclipboard.Settings settings;
    private History history;
    private TextView status,folderText;
    private Button toggle;
    private LinearLayout historyView;
    private EditText includes,excludes,minSide;
    private Switch captures,compatible;
    private PicturePipeline manualPipeline;
    private final BroadcastReceiver refresh=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){refreshStatus();refreshHistory();}};
    private final Handler ticker=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){refreshStatus();ticker.postDelayed(this,5000);}};
    private boolean receiverRegistered;
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // API <33 branch is protected by our signature-only permission.
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        settings=new com.zfj.imageclipboard.Settings(this);history=new History(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfff4f6fa);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(22),dp(18),dp(22),dp(36));scroll.addView(page);setContentView(scroll);
        if(Build.VERSION.SDK_INT>=35)scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(0,bars.top,0,bars.bottom);return insets;});
        TextView eyebrow=text("LOCAL / IMAGE CLIPBOARD",11,MUTED);eyebrow.setLetterSpacing(.12f);page.addView(eyebrow);
        LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);title.setPadding(0,dp(14),0,dp(16));
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_app);title.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView heading=text("图片自动复制",27,INK);heading.setTypeface(null,Typeface.BOLD);heading.setPadding(dp(14),0,0,0);title.addView(heading);page.addView(title);
        page.addView(text("拍下、截下，然后直接粘贴。",15,MUTED));
        LinearLayout state=card(page);status=text("已暂停",15,INK);state.addView(status);
        state.addView(text("免 Root · 全媒体库 · 仅在本机处理",12,MUTED));
        toggle=button("开启自动复制",true);state.addView(toggle);toggle.setOnClickListener(v->{if(settings.enabled())pause();else requestStart();});
        LinearLayout scope=card(page);section(scope,"监听范围");
        scope.addView(text("所有已挂载存储卷的图片，覆盖系统相机、按键／三指截图，以及其他应用写入相册的图片；不限目录。",14,MUTED));
        captures=new Switch(this);captures.setText("只处理疑似拍照／截图");captures.setTextColor(INK);captures.setTextSize(14);captures.setChecked(settings.capturesOnly());scope.addView(captures);
        scope.addView(text("默认关闭：复制所有新保存图片，覆盖更广。开启后按名称和路径特征筛选，可能漏掉应用内图片。",12,MUTED));
        Button choose=button("添加额外监听文件夹",false);scope.addView(choose);choose.setOnClickListener(v->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),21));
        folderText=text("",12,MUTED);scope.addView(folderText);refreshFolders();
        Button manage=button("管理已授权文件夹",false);scope.addView(manage);manage.setOnClickListener(v->manageFolders());
        scope.addView(text("额外文件夹每30秒补查，可覆盖未进入相册的图片。Android 禁止授权的应用私有目录和 Android/data 无法监听；这种图片可从原应用分享到本工具。",12,MUTED));
        LinearLayout rules=card(page);section(rules,"筛选规则");
        includes=field(rules,"包含关键字（每行一个，留空表示全部）",settings.includes(),true);
        excludes=field(rules,"排除关键字（每行一个）",settings.excludes(),true);
        minSide=field(rules,"最小边长 / px（0 表示不限）",""+settings.minSide(),false);minSide.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        compatible=new Switch(this);compatible.setText("兼容粘贴格式");compatible.setTextColor(INK);compatible.setChecked(settings.compatibleFormat());rules.addView(compatible);
        rules.addView(text("将 HEIF、AVIF、BMP 转为 JPEG，长边最多4096像素。原相册图片不变；关闭则保留原格式。",12,MUTED));
        rules.addView(text("规则匹配图片路径。可填应用保存目录、文件名前缀或扩展名；用于过滤头像、广告和缩略图。",12,MUTED));
        Button save=button("保存规则",false);rules.addView(save);save.setOnClickListener(v->saveRules());
        LinearLayout tools=card(page);section(tools,"快捷操作");
        Button latest=button("重新复制最近一张",false);tools.addView(latest);latest.setOnClickListener(v->{for(History.Row r:history.recent())if(r.cache!=null&&new File(getFilesDir(),"images/"+r.cache).isFile()){recopy(r);return;}toast("暂无可重新复制的图片");});
        Button pick=button("选择一张图片复制",false);tools.addView(pick);pick.setOnClickListener(v->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),22));
        Button verify=button("检查当前图片剪贴板",false);tools.addView(verify);verify.setOnClickListener(v->verifyClipboard());
        Button battery=button("允许后台运行",false);tools.addView(battery);battery.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}});
        Button permissions=button("管理照片与通知权限",false);tools.addView(permissions);permissions.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        tools.addView(text("请在手机设置中允许自启动并取消电池限制。系统强行停止后需重新打开应用。粘贴目标需要支持图片；应用没有网络权限。",12,MUTED));
        LinearLayout recent=card(page);section(recent,"最近处理");historyView=new LinearLayout(this);historyView.setOrientation(LinearLayout.VERTICAL);recent.addView(historyView);
        page.addView(text("v"+BuildConfig.VERSION_NAME+(BuildConfig.DEBUG?" 测试版":"")+"  ·  照片与截图留在你的手机里",12,MUTED));
        if(Build.VERSION.SDK_INT>=33)registerReceiver(refresh,new IntentFilter(MonitorService.REFRESH),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(refresh,new IntentFilter(MonitorService.REFRESH),getPackageName()+".INTERNAL",null);receiverRegistered=true;
        refreshStatus();refreshHistory();
    }
    private void requestStart(){
        List<String> needed=new ArrayList<>();String media=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(checkSelfPermission(media)!=PackageManager.PERMISSION_GRANTED)needed.add(media);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!needed.isEmpty()){requestPermissions(needed.toArray(new String[0]),10);return;}
        enable();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==10)enable();}
    private void enable(){
        String media=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(checkSelfPermission(media)!=PackageManager.PERMISSION_GRANTED&&settings.folders().isEmpty()){toast("请允许所有照片访问，或先添加一个授权文件夹");return;}
        settings.setEnabled(true);
        try{MonitorService.start(this);toast("已开启，下一张新图片将自动复制");}catch(Exception e){settings.setEnabled(false);toast("启动失败："+e.getMessage());}
        refreshStatus();
    }
    private void pause(){settings.setEnabled(false);stopService(new Intent(this,MonitorService.class));refreshStatus();}
    private void saveRules(){
        int side;try{side=Integer.parseInt(minSide.getText().toString());if(side<0||side>10000)throw new NumberFormatException();}catch(Exception e){toast("最小边长请填0至10000");return;}
        settings.prefs.edit().putString("includes",includes.getText().toString().trim()).putString("excludes",excludes.getText().toString().trim()).putInt("minSide",side).putBoolean("capturesOnly",captures.isChecked()).putBoolean("compatibleFormat",compatible.isChecked()).apply();
        restart();toast("规则已保存");
    }
    private void restart(){if(settings.enabled()){stopService(new Intent(this,MonitorService.class));MonitorService.start(this);}}
    private void refreshStatus(){if(status==null)return;long beat=settings.prefs.getLong("heartbeat",0);boolean alive=beat>0&&System.currentTimeMillis()-beat<45000;
        status.setText(settings.enabled()?(alive?settings.prefs.getString("status","全媒体库监听中"):"正在启动或服务被系统暂停"):"自动复制已暂停");toggle.setText(settings.enabled()?"暂停自动复制":"开启自动复制");}
    private void refreshHistory(){if(historyView==null)return;historyView.removeAllViews();List<History.Row> rows=history.recent();if(rows.isEmpty()){historyView.addView(text("尚无记录。开启后拍照或截图试试。",14,MUTED));return;}
        SimpleDateFormat format=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault());int count=0;
        for(History.Row r:rows){if(++count>20)break;TextView row=text(r.status+"  ·  "+format.format(new Date(r.time))+"\n"+r.detail+"\n"+r.source,12,r.status.equals("已复制")?BLUE:MUTED);row.setPadding(0,dp(9),0,dp(9));historyView.addView(row);if(r.cache!=null)row.setOnClickListener(v->recopy(r));}
    }
    private void recopy(History.Row r){try{if(!new File(getFilesDir(),"images/"+r.cache).isFile()){toast("图片缓存已过期");return;}ImageProvider.copy(this,r.cache);toast("已重新复制图片");}catch(Exception e){toast("复制失败："+e.getMessage());}}
    private void refreshFolders(){String value=settings.folders();folderText.setText(value.isEmpty()?"未添加额外文件夹":value.replace("\n","\n\n"));}
    private void manageFolders(){String[] values=settings.folders().split("\n");if(values.length==1&&values[0].isEmpty()){toast("还没有授权文件夹");return;}
        new AlertDialog.Builder(this).setTitle("移除哪个文件夹？").setItems(values,(d,which)->{List<String> remaining=new ArrayList<>(Arrays.asList(values));String uri=remaining.remove(which);try{getContentResolver().releasePersistableUriPermission(Uri.parse(uri),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}settings.prefs.edit().putString("folders",String.join("\n",remaining)).apply();restart();refreshFolders();}).setNegativeButton("取消",null).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==21){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);Set<String> values=new LinkedHashSet<>(Arrays.asList(settings.folders().split("\n")));values.remove("");values.add(uri.toString());settings.prefs.edit().putString("folders",String.join("\n",values)).apply();refreshFolders();restart();toast("已添加文件夹");}catch(Exception e){toast("文件夹授权失败："+e.getMessage());}}
        if(request==22){if(manualPipeline!=null)manualPipeline.close();manualPipeline=new PicturePipeline(this,history,()->{toast("处理完成，请查看最近记录");refreshHistory();});manualPipeline.enqueue(new PicturePipeline.Candidate(uri,"手动选择 · "+uri.getAuthority(),System.currentTimeMillis(),true));}
    }
    private void verifyClipboard(){try{ClipboardManager manager=getSystemService(ClipboardManager.class);ClipData data=manager.getPrimaryClip();if(data==null||data.getItemCount()==0||data.getItemAt(0).getUri()==null){toast("当前剪贴板没有可读取的图片");return;}Uri uri=data.getItemAt(0).getUri();ImageView preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMaxHeight(dp(350));preview.setImageURI(uri);new AlertDialog.Builder(this).setTitle("剪贴板图片预览").setView(preview).setPositiveButton("完成",null).show();}catch(Exception e){toast("图片读取失败："+e.getMessage());}}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    private LinearLayout card(LinearLayout parent){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(18),dp(18),dp(18));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(20));box.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(18);p.bottomMargin=dp(2);parent.addView(box,p);return box;}
    private void section(LinearLayout box,String title){TextView t=text(title,18,INK);t.setTypeface(null,Typeface.BOLD);t.setPadding(0,0,0,dp(10));box.addView(t);}
    private Button button(String title,boolean primary){Button b=new Button(this);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:BLUE);GradientDrawable bg=new GradientDrawable();bg.setColor(primary?BLUE:0xffedf3ff);bg.setCornerRadius(dp(12));b.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(12);b.setLayoutParams(p);return b;}
    private EditText field(LinearLayout box,String hint,String value,boolean multiline){TextView label=text(hint,12,MUTED);label.setPadding(0,dp(12),0,dp(2));box.addView(label);EditText edit=new EditText(this);edit.setText(value);edit.setTextSize(14);edit.setTextColor(INK);edit.setSingleLine(!multiline);if(multiline){edit.setMinLines(2);edit.setMaxLines(5);edit.setGravity(Gravity.TOP);}box.addView(edit,new LinearLayout.LayoutParams(-1,-2));return edit;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();ticker.post(tick);if(status!=null){refreshStatus();refreshHistory();}if(settings!=null&&settings.enabled())try{MonitorService.start(this);}catch(Exception ignored){}}
    @Override protected void onPause(){ticker.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){if(receiverRegistered)unregisterReceiver(refresh);if(manualPipeline!=null)manualPipeline.close();ticker.removeCallbacksAndMessages(null);super.onDestroy();}
}
