package com.zfj.imageclipboard;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.util.*;
import java.util.concurrent.*;

public final class MonitorService extends Service {
    static final String PAUSE="com.zfj.imageclipboard.PAUSE", REFRESH="com.zfj.imageclipboard.REFRESH";
    private static final int NOTICE=101;
    private HandlerThread observerThread;
    private Handler observerHandler;
    private ScheduledExecutorService scanner;
    private ScheduledExecutorService folderWorker;
    private PicturePipeline pipeline;
    private History history;
    private Settings settings;
    private FolderScanner folders;
    private ContentObserver observer;
    private final Map<String,String> seen=new LinkedHashMap<>();
    private long checkpoint;
    private volatile boolean closing;
    private ScheduledFuture<?> scheduledScan;
    private final Map<Uri,Long> changedItems=new java.util.concurrent.ConcurrentHashMap<>();
    private final BroadcastReceiver volumes=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){scheduleScan();}};
    static void start(Context c){c.startForegroundService(new Intent(c,MonitorService.class));}
    @Override public void onCreate() {
        super.onCreate();settings=new Settings(this);history=new History(this);
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("monitor","图片监听状态",NotificationManager.IMPORTANCE_LOW));
        if(Build.VERSION.SDK_INT>=34)startForeground(NOTICE,notification("正在连接全媒体库"),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTICE,notification("正在连接全媒体库"));
        pipeline=new PicturePipeline(this,history,this::changed);
        scanner=Executors.newSingleThreadScheduledExecutor();
        observerThread=new HandlerThread("media-observer");observerThread.start();observerHandler=new Handler(observerThread.getLooper());
        observer=new ContentObserver(observerHandler){@Override public void onChange(boolean selfChange){scheduleScan();}@Override public void onChange(boolean selfChange,Uri uri){
            if(uri!=null && uri.getLastPathSegment()!=null && uri.getLastPathSegment().matches("[0-9]+"))changedItems.put(uri,System.currentTimeMillis());
            scheduleScan();
        }};
        getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,true,observer);
        folders=new FolderScanner(this,new FolderScanner.Listener(){
            public void image(PicturePipeline.Candidate c){pipeline.enqueue(c);}
            public void status(String s){history.add("授权文件夹","提示",s,null,null);changed();}
        });
        IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_MEDIA_MOUNTED);f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);f.addDataScheme("file");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(volumes,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(volumes,f);
        checkpoint=Math.max(settings.since(),settings.prefs.getLong("checkpoint",settings.since()));
        // Observer handles normal changes; bounded polling recovers missed vendor notifications.
        scanner.scheduleWithFixedDelay(this::scanSafely,0,15,TimeUnit.SECONDS);
        folderWorker=Executors.newSingleThreadScheduledExecutor();
        folderWorker.scheduleWithFixedDelay(()->{if(!closing)folders.scan();},0,settings.folderInterval(),TimeUnit.MILLISECONDS);
        scanner.execute(()->history.cleanCache(this));
        scanner.scheduleWithFixedDelay(()->history.cleanCache(this),1,1,TimeUnit.HOURS);
        settings.prefs.edit().putLong("heartbeat",System.currentTimeMillis()).apply();
    }
    private synchronized void scheduleScan(){
        if(closing||scanner==null||scanner.isShutdown())return;
        if(scheduledScan!=null&&!scheduledScan.isDone())return;
        scheduledScan=scanner.schedule(this::scanSafely,600,TimeUnit.MILLISECONDS);
    }
    private boolean mediaPermission(){return checkSelfPermission(Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
    private void scanSafely(){
        if(closing)return;
        try {
            if(mediaPermission()){
                List<Map.Entry<Uri,Long>> changes=new ArrayList<>(changedItems.entrySet());
                changes.sort(Map.Entry.<Uri,Long>comparingByValue().reversed());
                for(Map.Entry<Uri,Long> change:changes){if(changedItems.remove(change.getKey(),change.getValue()))scanChangedImage(change.getKey(),change.getValue());}
                scanMedia();
            }
            String status=mediaPermission()?"全媒体库监听中":"照片权限不足 · 可使用授权文件夹与分享";
            settings.prefs.edit().putLong("heartbeat",System.currentTimeMillis()).putString("status",status).apply();
            getSystemService(NotificationManager.class).notify(NOTICE,notification(status));
        }catch(Exception e){history.add("媒体库","失败",e.getClass().getSimpleName()+"："+e.getMessage(),null,null);changed();}
    }
    private void scanChangedImage(Uri uri,long observed){
        String[] fields={MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.DATE_ADDED,MediaStore.Images.Media.DATE_MODIFIED,MediaStore.Images.Media.SIZE,MediaStore.Images.Media.IS_PENDING,MediaStore.Images.Media.DATA};
        try(Cursor c=getContentResolver().query(uri,fields,null,null,null)){
            if(c==null||!c.moveToFirst()||c.getInt(5)!=0||c.getLong(4)<=0)return;
            String source=c.getString(6);
            if(source==null||source.isEmpty())source="媒体库/"+Objects.toString(c.getString(1),"")+Objects.toString(c.getString(0),"图片");
            long modified=Math.max(c.getLong(2),c.getLong(3))*1000;
            // An explicit item callback also covers same-size overwrites or preserved timestamps.
            pipeline.enqueue(new PicturePipeline.Candidate(uri,source,Math.max(modified,observed),false));
        }catch(SecurityException ignored){}catch(Exception e){history.add("媒体库变更","提示",e.getMessage(),null,null);}
    }
    private void scanMedia(){
        long now=System.currentTimeMillis();
        long since=Math.max(settings.since()-1500,checkpoint-120000)/1000;
        boolean allComplete=true;
        for(String volume:MediaStore.getExternalVolumeNames(this)) {
            Uri collection=MediaStore.Images.Media.getContentUri(volume);
            String[] columns={MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.DATE_ADDED,MediaStore.Images.Media.DATE_MODIFIED,MediaStore.Images.Media.SIZE,MediaStore.Images.Media.IS_PENDING};
            Bundle args=new Bundle();
            args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,"("+MediaStore.Images.Media.DATE_ADDED+">=? OR "+MediaStore.Images.Media.DATE_MODIFIED+">=?)");
            args.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,new String[]{""+since,""+since});
            args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,MediaStore.Images.Media.DATE_MODIFIED+" DESC, "+MediaStore.Images.Media._ID+" DESC");
            args.putInt(ContentResolver.QUERY_ARG_LIMIT,5000);
            try(Cursor c=getContentResolver().query(collection,columns,args,null)) {
                if(c==null){allComplete=false;continue;}
                while(c.moveToNext()&&!closing) {
                    long id=c.getLong(0),added=c.getLong(3),modified=c.getLong(4),size=c.getLong(5);
                    if(c.getInt(6)!=0||size<=0)continue;
                    Uri image=ContentUris.withAppendedId(collection,id);
                    String signature=modified+":"+size;
                    if(signature.equals(seen.get(image.toString())))continue;
                    seen.put(image.toString(),signature);
                    String source=(volume.equals(MediaStore.VOLUME_EXTERNAL_PRIMARY)?"/storage/emulated/0/":"/storage/"+volume+"/")+Objects.toString(c.getString(2),"")+Objects.toString(c.getString(1),"图片");
                    pipeline.enqueue(new PicturePipeline.Candidate(image,source,Math.max(added,modified)*1000,false));
                }
                if(c.getCount()>=5000){allComplete=false;history.add("媒体库","提示","近期变更达到5000项，建议暂停批量图片导入后重试",null,null);}
            }
        }
        if(seen.size()>12000)seen.clear();
        if(allComplete){checkpoint=now;settings.prefs.edit().putLong("checkpoint",now).apply();}
    }
    private Notification notification(String detail){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pause=PendingIntent.getService(this,1,new Intent(this,MonitorService.class).setAction(PAUSE),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"monitor").setSmallIcon(com.zfj.imageclipboard.R.drawable.ic_launcher_monochrome).setContentTitle("图片自动复制已开启").setContentText(detail).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"暂停",pause).build()).build();
    }
    private void changed(){sendBroadcast(new Intent(REFRESH).setPackage(getPackageName()));}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&PAUSE.equals(intent.getAction())){settings.setEnabled(false);stopSelf();return START_NOT_STICKY;}
        if(!settings.enabled()){stopSelf();return START_NOT_STICKY;}
        scheduleScan();return START_STICKY;
    }
    @Override public void onDestroy(){
        closing=true;
        if(observer!=null)getContentResolver().unregisterContentObserver(observer);
        try{unregisterReceiver(volumes);}catch(Exception ignored){}
        if(observerThread!=null)observerThread.quitSafely();
        if(scanner!=null)scanner.shutdownNow();
        if(folderWorker!=null)folderWorker.shutdownNow();
        if(folders!=null)folders.cancel();
        if(pipeline!=null)pipeline.close();
        settings.prefs.edit().putLong("heartbeat",0).putString("status",settings.enabled()?"服务已停止，请重新打开应用":"已暂停").apply();
        changed();super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
