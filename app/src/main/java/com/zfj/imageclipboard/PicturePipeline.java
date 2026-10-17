package com.zfj.imageclipboard;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/** File reads stay off the main thread. The main-thread commit checks freshness again. */
final class PicturePipeline {
    interface Listener { void changed(); }
    static final class Candidate {
        final Uri uri; final String source; final long time; final boolean explicit;
        Candidate(Uri uri,String source,long time,boolean explicit){this.uri=uri;this.source=source;this.time=time;this.explicit=explicit;}
    }
    private final Context context;
    private final Settings settings;
    private final History history;
    private final Listener listener;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,ScheduledFuture<?>> waiting=new HashMap<>();
    private final DeliveryOrder order=new DeliveryOrder();
    private volatile boolean closed;
    PicturePipeline(Context c, History h, Listener listener){context=c;settings=new Settings(c);history=h;this.listener=listener;}
    synchronized void enqueue(Candidate c) {
        if(closed)return;
        String rejection=c.explicit?null:Rules.rejection(c.source,settings.includes(),settings.excludes(),settings.capturesOnly());
        if(rejection!=null){record(c.source,"忽略",rejection,null,null);return;}
        if(!c.explicit && c.time<settings.since()-1500)return;
        ScheduledFuture<?> previous=waiting.remove(c.source); if(previous!=null)previous.cancel(false);
        waiting.put(c.source,worker.schedule(()->process(c),c.explicit?0:900,TimeUnit.MILLISECONDS));
    }
    void close(){closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);}
    private void process(Candidate c) {
        File temporary=null;
        try {
            if(closed)return;
            if(stale(c))return;
            File dir=new File(context.getFilesDir(),"images"); if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建图片缓存");
            temporary=File.createTempFile("pending-",".tmp",dir);
            String hash=null,mime=null; BitmapFactory.Options bounds=null;
            for(int attempt=0;attempt<5;attempt++) {
                if(Thread.currentThread().isInterrupted())return;
                try(InputStream in=context.getContentResolver().openInputStream(c.uri);OutputStream out=new FileOutputStream(temporary)) {
                    if(in==null)throw new IOException("无法打开图片");
                    hash=Io.copyAndHash(in,out);
                } catch(IOException e) { if(attempt==4)throw e; Thread.sleep(600L*(attempt+1));continue; }
                bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(temporary.getAbsolutePath(),bounds);
                if(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outMimeType!=null) {
                    try {
                        Bitmap preview=ImageDecoder.decodeBitmap(ImageDecoder.createSource(temporary),(decoder,info,source)->{
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                            int width=info.getSize().getWidth(),height=info.getSize().getHeight();
                            double ratio=Math.min(1.0,256.0/Math.max(width,height));
                            decoder.setTargetSize(Math.max(1,(int)(width*ratio)),Math.max(1,(int)(height*ratio)));
                            decoder.setOnPartialImageListener(error->false);
                        });
                        preview.recycle();mime=bounds.outMimeType;break;
                    } catch(IOException e){if(attempt==4)throw e;Thread.sleep(600L*(attempt+1));continue;}
                }
                if(attempt==4)throw new IOException("图片未完整保存，或此系统无法解码该格式");
                Thread.sleep(600L*(attempt+1));
            }
            if(mime==null || hash==null)throw new IOException("无法识别图片");
            if(!c.explicit && Math.min(bounds.outWidth,bounds.outHeight)<settings.minSide()) {record(c.source,"忽略","小于最小边长 "+settings.minSide()+"px",null,null);return;}
            String canonical=Rules.canonical(c.source);
            if(!c.explicit && history.alreadyCopied(canonical,hash))return;
            if(!c.explicit && !order.accept(c.time))return;
            String originalHash=hash, conversion="";
            if(settings.compatibleFormat() && (mime.equals("image/heif")||mime.equals("image/heic")||mime.equals("image/avif")||mime.contains("bmp"))){
                Bitmap compatible=ImageDecoder.decodeBitmap(ImageDecoder.createSource(temporary),(decoder,info,source)->{
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    int width=info.getSize().getWidth(),height=info.getSize().getHeight();double ratio=Math.min(1.0,4096.0/Math.max(width,height));
                    decoder.setTargetSize(Math.max(1,(int)(width*ratio)),Math.max(1,(int)(height*ratio)));
                    decoder.setOnPartialImageListener(error->false);
                });
                try(OutputStream out=new FileOutputStream(temporary)){if(!compatible.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,out))throw new IOException("兼容格式转换失败");}
                finally{compatible.recycle();}
                try(InputStream in=new FileInputStream(temporary)){hash=Io.copyAndHash(in,new OutputStream(){@Override public void write(int b){}@Override public void write(byte[] b,int off,int len){}});}
                conversion=mime+" → JPEG · ";mime="image/jpeg";
            }
            String ext=switch(mime){case "image/jpeg"->"jpg";case "image/png"->"png";case "image/webp"->"webp";case "image/heif","image/heic"->"heic";case "image/avif"->"avif";case "image/gif"->"gif";default->"img";};
            String name=hash+"."+ext; File image=new File(dir,name);
            if(image.exists()) {temporary.delete();image.setLastModified(System.currentTimeMillis());}
            else if(!temporary.renameTo(image))throw new IOException("无法保存剪贴板图片");
            String digest=originalHash, type=mime;
            String dimensions=conversion+bounds.outWidth+" × "+bounds.outHeight;
            main.post(()->{
                if(closed || stale(c))return;
                try {
                    ImageProvider.copy(context,name);
                    history.remember(canonical,digest);
                    record(c.source,"已复制",dimensions,name,type);
                    settings.prefs.edit().putString("latestCache",name).apply();
                } catch(Exception e){record(c.source,"失败","剪贴板写入失败："+e.getMessage(),name,type);}
            });
        } catch(Exception e) {if(!closed)record(c.source,"失败",e.getMessage(),null,null);}
        finally {if(temporary!=null)temporary.delete();synchronized(this){waiting.remove(c.source);}}
    }
    private boolean stale(Candidate c){return !c.explicit && !order.current(c.time);}
    private void record(String source,String status,String detail,String cache,String mime){history.add(source,status,detail,cache,mime);main.post(listener::changed);}
}
