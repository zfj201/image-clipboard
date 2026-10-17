package com.zfj.imageclipboard;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Size;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int BLUE=0xff2864e8, INK=0xff192b43, MUTED=0xff7a8798;
    private static final int BACKGROUND=0xfff3f5f8, LINE=0xffedf0f5, MINT=0xff89e1cf;
    private com.zfj.imageclipboard.Settings settings;
    private History history;
    private TextView status, statusBadge, lastCopy, folderText, historyCount;
    private Button toggle;
    private LinearLayout historyView;
    private EditText includes, excludes, minSide;
    private Switch captures, compatible;
    private PicturePipeline manualPipeline;
    private boolean receiverRegistered, destroyed;
    private final ExecutorService images=Executors.newSingleThreadExecutor();
    private final android.util.LruCache<String,Bitmap> thumbnails=new android.util.LruCache<>(24);
    private final Handler ticker=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){refreshStatus();ticker.postDelayed(this,5000);}};
    private final BroadcastReceiver refresh=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){refreshStatus();refreshHistory();}};

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // Older branch is protected by a signature-only permission.
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        settings=new com.zfj.imageclipboard.Settings(this);history=new History(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setBackgroundColor(BACKGROUND);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout page=column();page.setPadding(dp(20),dp(20),dp(20),dp(24));scroll.addView(page);setContentView(scroll);
        if(Build.VERSION.SDK_INT>=35)scroll.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(0,bars.top,0,bars.bottom);return insets;});
        buildHeader(page);
        buildState(page);
        buildActions(page);
        buildHistory(page);
        buildSources(page);
        buildRules(page);
        buildSettings(page);
        TextView footer=text("仅在本机处理  ·  不上传你的照片",11,MUTED);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(22),0,0);page.addView(footer);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(refresh,new IntentFilter(MonitorService.REFRESH),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(refresh,new IntentFilter(MonitorService.REFRESH),getPackageName()+".INTERNAL",null);
        receiverRegistered=true;refreshFolders();refreshStatus();refreshHistory();
    }

    private void buildHeader(LinearLayout page){
        LinearLayout bar=row();bar.setPadding(0,0,0,dp(20));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_app);bar.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout name=column();LinearLayout.LayoutParams n=new LinearLayout.LayoutParams(0,-2,1);n.leftMargin=dp(10);bar.addView(name,n);
        TextView title=text("图片自动复制",22,INK);title.setTypeface(null,Typeface.BOLD);name.addView(title);name.addView(text("拍下、截下，随手粘贴",11,MUTED));
        TextView version=text(BuildConfig.DEBUG?"测试版":"v"+BuildConfig.VERSION_NAME,10,MUTED);version.setPadding(dp(9),dp(5),dp(9),dp(5));version.setBackground(shape(Color.WHITE,8));bar.addView(version);
        page.addView(bar);
    }

    private void buildState(LinearLayout page){
        LinearLayout hero=column();hero.setPadding(dp(20),dp(20),dp(20),dp(18));
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff182c49,0xff244770});gradient.setCornerRadius(dp(24));hero.setBackground(gradient);page.addView(hero,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout top=row();TextView caption=text("自动复制",12,0xffb9cadf);top.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
        statusBadge=text("已暂停",10,MINT);statusBadge.setPadding(dp(9),dp(4),dp(9),dp(4));statusBadge.setBackground(shape(0x252bdec0,20));top.addView(statusBadge);hero.addView(top);
        status=text("准备接收下一张图片",22,Color.WHITE);status.setTypeface(null,Typeface.BOLD);status.setPadding(0,dp(14),0,dp(5));hero.addView(status);
        lastCopy=text("免 Root · 拍照与截图自动进入剪贴板",11,0xffb9cadf);lastCopy.setSingleLine();lastCopy.setEllipsize(TextUtils.TruncateAt.END);hero.addView(lastCopy);
        toggle=button("开启自动复制",MINT,0xff183f4d);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(46));p.topMargin=dp(18);hero.addView(toggle,p);
        toggle.setOnClickListener(v->{if(settings.enabled())pause();else requestStart();});
    }

    private void buildActions(LinearLayout page){
        LinearLayout actions=row();LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(12);page.addView(actions,ap);
        Button pick=button("＋  选择图片",Color.WHITE,INK);Button latest=button("↻  复制最近",Color.WHITE,INK);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(46),1);a.rightMargin=dp(5);actions.addView(pick,a);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,dp(46),1);b.leftMargin=dp(5);actions.addView(latest,b);
        pick.setOnClickListener(v->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),22));
        latest.setOnClickListener(v->{for(History.Row r:history.recent())if(r.cache!=null&&new File(getFilesDir(),"images/"+r.cache).isFile()){recopy(r);return;}toast("暂无可重新复制的图片");});
    }

    private void buildHistory(LinearLayout page){
        LinearLayout box=card(page);LinearLayout heading=row();TextView title=heading("最近处理");heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));historyCount=text("",11,MUTED);heading.addView(historyCount);box.addView(heading);
        TextView help=text("上下滑动查看 · 点击图片记录重新复制",11,MUTED);help.setPadding(0,dp(4),0,dp(10));box.addView(help);
        // Keep history bounded and let gestures inside it scroll only this list.
        ScrollView list=new HistoryScrollView();list.setVerticalScrollBarEnabled(true);list.setScrollbarFadingEnabled(false);list.setNestedScrollingEnabled(true);list.setContentDescription("最近处理记录，可在区域内上下滚动");
        historyView=column();list.addView(historyView);box.addView(list,new LinearLayout.LayoutParams(-1,dp(248)));
        TextView verify=text("检查图片剪贴板  →",12,BLUE);verify.setGravity(Gravity.CENTER);verify.setPadding(0,dp(12),0,0);verify.setMinHeight(dp(44));verify.setOnClickListener(v->verifyClipboard());box.addView(verify,new LinearLayout.LayoutParams(-1,-2));
    }

    private void buildSources(LinearLayout page){
        LinearLayout box=card(page);LinearLayout header=row();header.addView(heading("图片来源"),new LinearLayout.LayoutParams(0,-2,1));header.addView(text("不限相册目录",11,MUTED));box.addView(header);
        sourceRow(box,"系统媒体库","相机、系统截图、应用保存到相册的图片","自动发现",BLUE);
        folderText=sourceRow(box,"额外文件夹","补充未进入相册的图片，每 30 秒检查","未添加",0xff168774);
        LinearLayout buttons=row();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(12);box.addView(buttons,bp);
        Button choose=button("＋ 添加文件夹",0xffedf3ff,BLUE),manage=button("管理",0xfff3f5f8,MUTED);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(40),1);p.rightMargin=dp(8);buttons.addView(choose,p);buttons.addView(manage,new LinearLayout.LayoutParams(dp(74),dp(40)));
        choose.setOnClickListener(v->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),21));manage.setOnClickListener(v->manageFolders());
        TextView hint=text("应用私有图片，可通过系统「分享」发送到这里。",11,MUTED);hint.setPadding(0,dp(12),0,0);box.addView(hint);
    }

    private TextView sourceRow(LinearLayout box,String title,String detail,String label,int color){
        LinearLayout line=row();line.setPadding(0,dp(14),0,0);LinearLayout copy=column();copy.addView(text(title,14,INK));TextView sub=text(detail,11,MUTED);sub.setPadding(0,dp(3),dp(8),0);copy.addView(sub);line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView badge=text(label,10,color);line.addView(badge);box.addView(line);return badge;
    }

    private void buildRules(LinearLayout page){
        LinearLayout box=card(page);LinearLayout header=row();header.addView(heading("高级筛选"),new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text("展开  ⌄",12,MUTED);header.addView(arrow);header.setMinimumHeight(dp(32));box.addView(header);
        LinearLayout content=column();content.setVisibility(View.GONE);box.addView(content);header.setOnClickListener(v->{boolean open=content.getVisibility()==View.VISIBLE;content.setVisibility(open?View.GONE:View.VISIBLE);arrow.setText(open?"展开  ⌄":"收起  ⌃");});
        TextView intro=text("默认复制所有新保存图片，覆盖更多应用。",11,MUTED);intro.setPadding(0,dp(12),0,dp(6));content.addView(intro);
        captures=settingSwitch(content,"仅拍照与截图","按名称与路径识别，开启后可能漏掉应用内图片",settings.capturesOnly());
        includes=field(content,"包含关键字","每行一个，留空表示全部",settings.includes(),true);
        excludes=field(content,"排除关键字","每行一个，例如 /thumbnails/",settings.excludes(),true);
        minSide=field(content,"最小边长 / px","0 表示不限",String.valueOf(settings.minSide()),false);minSide.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        compatible=settingSwitch(content,"兼容粘贴格式","HEIF、AVIF、BMP 转为 JPEG，长边最多 4096px",settings.compatibleFormat());
        Button save=button("保存筛选规则",0xffedf3ff,BLUE);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(44));p.topMargin=dp(14);content.addView(save,p);save.setOnClickListener(v->saveRules());
    }

    private void buildSettings(LinearLayout page){
        LinearLayout box=card(page);box.addView(heading("后台与权限"));
        settingsRow(box,"允许后台运行","开启自启动、取消电池限制",()->{try{startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}catch(Exception e){openPermissions();}});
        settingsRow(box,"照片与通知权限","请允许所有照片，保留监听通知",this::openPermissions);
    }

    private void settingsRow(LinearLayout box,String title,String description,Runnable action){
        LinearLayout line=row();line.setPadding(0,dp(14),0,dp(4));LinearLayout copy=column();copy.addView(text(title,14,INK));copy.addView(text(description,11,MUTED));line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));line.addView(text("›",24,MUTED));line.setMinimumHeight(dp(60));line.setOnClickListener(v->action.run());box.addView(line);
    }
    private void openPermissions(){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}

    private Switch settingSwitch(LinearLayout box,String title,String detail,boolean checked){
        LinearLayout line=row();line.setPadding(0,dp(12),0,dp(6));LinearLayout words=column();words.addView(text(title,14,INK));TextView sub=text(detail,11,MUTED);sub.setPadding(0,dp(4),dp(8),0);words.addView(sub);line.addView(words,new LinearLayout.LayoutParams(0,-2,1));Switch control=new Switch(this);control.setContentDescription(title);control.setChecked(checked);control.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{BLUE,0xffbcc5d0}));line.addView(control);line.setOnClickListener(v->control.setChecked(!control.isChecked()));box.addView(line);return control;
    }

    private void requestStart(){
        List<String> needed=new ArrayList<>();String media=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(checkSelfPermission(media)!=PackageManager.PERMISSION_GRANTED)needed.add(media);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!needed.isEmpty()){requestPermissions(needed.toArray(new String[0]),10);return;}enable();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==10)enable();}
    private void enable(){
        String media=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(checkSelfPermission(media)!=PackageManager.PERMISSION_GRANTED&&settings.folders().isEmpty()){toast("请允许所有照片访问，或先添加一个授权文件夹");return;}
        settings.setEnabled(true);try{MonitorService.start(this);toast("已开启，下一张新图片将自动复制");}catch(Exception e){settings.setEnabled(false);toast("启动失败："+e.getMessage());}refreshStatus();
    }
    private void pause(){settings.setEnabled(false);stopService(new Intent(this,MonitorService.class));refreshStatus();}
    private void saveRules(){
        int side;try{side=Integer.parseInt(minSide.getText().toString());if(side<0||side>10000)throw new NumberFormatException();}catch(Exception e){toast("最小边长请填 0 至 10000");return;}
        settings.prefs.edit().putString("includes",includes.getText().toString().trim()).putString("excludes",excludes.getText().toString().trim()).putInt("minSide",side).putBoolean("capturesOnly",captures.isChecked()).putBoolean("compatibleFormat",compatible.isChecked()).apply();restart();toast("规则已保存");
    }
    private void restart(){if(settings.enabled()){stopService(new Intent(this,MonitorService.class));MonitorService.start(this);}}
    private void refreshStatus(){
        if(status==null)return;long beat=settings.prefs.getLong("heartbeat",0);boolean alive=beat>0&&System.currentTimeMillis()-beat<45000;
        status.setText(settings.enabled()?(alive?"随时接收下一张图片":"正在连接图片来源…"):"准备接收下一张图片");statusBadge.setText(settings.enabled()?(alive?"● 监听中":"● 连接中"):"○ 已暂停");toggle.setText(settings.enabled()?"暂停自动复制":"开启自动复制");
        lastCopy.setText(settings.enabled()?(alive?settings.prefs.getString("status","全媒体库监听中")+" · 免 Root":"请保持通知，并允许后台运行"):"免 Root · 拍照与截图自动进入剪贴板");
    }
    private void refreshHistory(){
        if(historyView==null)return;historyView.removeAllViews();List<History.Row> rows=history.recent();int total=Math.min(20,rows.size());historyCount.setText(total+" 条记录");
        if(rows.isEmpty()){LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(16),dp(42),dp(16),dp(32));TextView mark=text("▧",34,0xffb9c9e0);empty.addView(mark);TextView title=text("还没有处理记录",14,MUTED);title.setPadding(0,dp(10),0,dp(5));empty.addView(title);empty.addView(text("开启后，拍照或截图试试",11,MUTED));historyView.addView(empty,new LinearLayout.LayoutParams(-1,-2));return;}
        SimpleDateFormat format=new SimpleDateFormat("HH:mm:ss",Locale.getDefault());int count=0;
        for(History.Row r:rows){
            if(count++>=20)break;if(count>1){View divider=new View(this);divider.setBackgroundColor(LINE);historyView.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));}
            LinearLayout entry=row();entry.setPadding(0,dp(12),dp(6),dp(12));entry.setMinimumHeight(dp(80));
            ImageView thumb=new ImageView(this);thumb.setPadding(dp(8),dp(8),dp(8),dp(8));thumb.setImageResource(R.drawable.ic_launcher_monochrome);thumb.setImageTintList(ColorStateList.valueOf(0xff9baec8));thumb.setBackground(shape(0xfff0f4fa,12));entry.addView(thumb,new LinearLayout.LayoutParams(dp(44),dp(44)));if(r.cache!=null)loadThumbnail(thumb,r.cache);
            LinearLayout words=column();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(12);entry.addView(words,wp);
            LinearLayout top=row();int color=r.status.equals("已复制")?0xff178c79:r.status.equals("失败")?0xffd45d61:MUTED;TextView label=text(r.status,12,color);top.addView(label,new LinearLayout.LayoutParams(0,-2,1));top.addView(text(format.format(new Date(r.time)),10,MUTED));words.addView(top);
            String source=Objects.toString(r.source,"");String name=source.substring(source.lastIndexOf('/')+1);TextView filename=text(name,12,INK);filename.setSingleLine();filename.setEllipsize(TextUtils.TruncateAt.MIDDLE);filename.setPadding(0,dp(3),0,0);words.addView(filename);
            TextView detail=text(Objects.toString(r.detail,""),10,MUTED);detail.setSingleLine();detail.setEllipsize(TextUtils.TruncateAt.END);words.addView(detail);
            entry.setContentDescription(r.status+"，"+r.detail+"，"+source);if(r.cache!=null){entry.setBackground(new RippleDrawable(ColorStateList.valueOf(0xffedf3ff),null,shape(Color.WHITE,10)));entry.setOnClickListener(v->recopy(r));}historyView.addView(entry,new LinearLayout.LayoutParams(-1,-2));
        }
    }
    private void loadThumbnail(ImageView view,String name){
        Bitmap ready=thumbnails.get(name);if(ready!=null){showThumbnail(view,ready);return;}
        images.execute(()->{try{File file=new File(getFilesDir(),"images/"+name);if(!file.isFile())return;Bitmap bitmap=android.media.ThumbnailUtils.createImageThumbnail(file,new Size(dp(44),dp(44)),null);thumbnails.put(name,bitmap);ticker.post(()->{if(!destroyed&&view.isAttachedToWindow())showThumbnail(view,bitmap);});}catch(Exception ignored){}});
    }
    private void showThumbnail(ImageView view,Bitmap bitmap){view.setPadding(0,0,0,0);view.setImageTintList(null);view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setImageBitmap(bitmap);view.setClipToOutline(true);}
    private void recopy(History.Row r){try{if(!new File(getFilesDir(),"images/"+r.cache).isFile()){toast("图片缓存已过期");return;}ImageProvider.copy(this,r.cache);toast("已重新复制图片");}catch(Exception e){toast("复制失败："+e.getMessage());}}
    private void refreshFolders(){if(folderText==null)return;String value=settings.folders();folderText.setText(value.isEmpty()?"未添加":value.split("\n").length+" 个已授权");}
    private void manageFolders(){
        String[] values=settings.folders().split("\n");if(values.length==1&&values[0].isEmpty()){toast("还没有授权文件夹");return;}
        String[] labels=new String[values.length];for(int i=0;i<values.length;i++){String path=android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(values[i]));labels[i]=path.replace("primary:","内部存储 / ");}
        new AlertDialog.Builder(this).setTitle("移除监听文件夹").setItems(labels,(d,which)->{List<String> remaining=new ArrayList<>(Arrays.asList(values));String uri=remaining.remove(which);try{getContentResolver().releasePersistableUriPermission(Uri.parse(uri),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}settings.prefs.edit().putString("folders",String.join("\n",remaining)).apply();restart();refreshFolders();}).setNegativeButton("取消",null).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==21){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);Set<String> values=new LinkedHashSet<>(Arrays.asList(settings.folders().split("\n")));values.remove("");values.add(uri.toString());settings.prefs.edit().putString("folders",String.join("\n",values)).apply();refreshFolders();restart();toast("已添加文件夹");}catch(Exception e){toast("文件夹授权失败："+e.getMessage());}}
        if(request==22){if(manualPipeline!=null)manualPipeline.close();manualPipeline=new PicturePipeline(this,history,()->{toast("处理完成，请查看最近记录");refreshHistory();});manualPipeline.enqueue(new PicturePipeline.Candidate(uri,"手动选择 · "+uri.getAuthority(),System.currentTimeMillis(),true));}
    }
    private void verifyClipboard(){
        try{ClipData data=getSystemService(ClipboardManager.class).getPrimaryClip();if(data==null||data.getItemCount()==0||data.getItemAt(0).getUri()==null){toast("当前剪贴板没有可读取的图片");return;}Uri uri=data.getItemAt(0).getUri();ImageView preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMaxHeight(dp(350));preview.setPadding(dp(16),dp(12),dp(16),dp(12));AlertDialog dialog=new AlertDialog.Builder(this).setTitle("剪贴板图片").setView(preview).setPositiveButton("完成",null).show();
            images.execute(()->{try{Bitmap bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,source)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();double ratio=Math.min(1,1024.0/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*ratio)),Math.max(1,(int)(h*ratio)));});ticker.post(()->{if(!destroyed&&dialog.isShowing())preview.setImageBitmap(bitmap);});}catch(Exception e){ticker.post(()->{if(!destroyed){dialog.dismiss();toast("图片读取失败："+e.getMessage());}});}});
        }catch(Exception e){toast("图片读取失败："+e.getMessage());}
    }

    private LinearLayout row(){LinearLayout box=new LinearLayout(this);box.setGravity(Gravity.CENTER_VERTICAL);return box;}
    private LinearLayout column(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);return box;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);t.setLineSpacing(dp(3),1);return t;}
    private TextView heading(String title){TextView t=text(title,16,INK);t.setTypeface(null,Typeface.BOLD);return t;}
    private GradientDrawable shape(int color,int radius){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;}
    private LinearLayout card(LinearLayout parent){LinearLayout box=column();box.setPadding(dp(18),dp(18),dp(18),dp(16));box.setBackground(shape(Color.WHITE,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);parent.addView(box,p);return box;}
    private Button button(String title,int background,int foreground){Button b=new Button(this);b.setText(title);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(foreground);b.setTypeface(null,Typeface.BOLD);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(10),0,dp(10),0);b.setStateListAnimator(null);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x202864e8),shape(background,12),null));return b;}
    private EditText field(LinearLayout box,String title,String hint,String value,boolean multiline){TextView label=text(title,12,INK);label.setPadding(0,dp(12),0,dp(7));box.addView(label);EditText edit=new EditText(this);edit.setText(value);edit.setHint(hint);edit.setTextSize(13);edit.setTextColor(INK);edit.setHintTextColor(MUTED);edit.setPadding(dp(12),dp(10),dp(12),dp(10));edit.setBackground(shape(BACKGROUND,10));edit.setSingleLine(!multiline);if(multiline){edit.setMinLines(2);edit.setMaxLines(4);edit.setGravity(Gravity.TOP);}box.addView(edit,new LinearLayout.LayoutParams(-1,-2));return edit;}
    private final class HistoryScrollView extends ScrollView {
        HistoryScrollView(){super(MainActivity.this);}
        @Override public boolean dispatchTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)getParent().requestDisallowInterceptTouchEvent(true);boolean handled=super.dispatchTouchEvent(event);if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);return handled;}
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();ticker.post(tick);if(status!=null){refreshStatus();refreshHistory();}if(settings!=null&&settings.enabled())try{MonitorService.start(this);}catch(Exception ignored){}}
    @Override protected void onPause(){ticker.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){destroyed=true;if(receiverRegistered)unregisterReceiver(refresh);if(manualPipeline!=null)manualPipeline.close();images.shutdownNow();ticker.removeCallbacksAndMessages(null);super.onDestroy();}
}
