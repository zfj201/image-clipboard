package com.zfj.imageclipboard.probe;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.*;
import java.io.*;

/** A different UID proves clipboard URI grants work outside the monitored app. */
public final class ProbeActivity extends Activity {
    private TextView status;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);status=new TextView(this);status.setTextSize(22);status.setPadding(44,120,44,44);status.setText("图片自动复制 · 集成验证\n\n测试画面，不包含个人图片。");setContentView(status);
        if("emit".equals(getIntent().getStringExtra("case")))new Handler().postDelayed(this::emit,600);
        else if("share".equals(getIntent().getStringExtra("case")))new Handler().postDelayed(this::share,600);
    }
    private Bitmap bitmap(){int side=getIntent().getIntExtra("size",480);Bitmap b=Bitmap.createBitmap(side,side+160,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(getIntent().getIntExtra("color",0xff2864e8));Paint p=new Paint(3);p.setColor(Color.WHITE);p.setTextSize(side/12f);c.drawText("Image Clipboard",side/12f,side/2f,p);c.drawText("No root / QA",side/12f,side/2f+side/10f,p);return b;}
    private void emit(){
        try {
            String name=getIntent().getStringExtra("name");if(name==null)name="capture-"+System.currentTimeMillis()+".png";
            String path=getIntent().getStringExtra("path");if(path==null)path="Pictures/ImageClipboardQA/";
            boolean corrupt=getIntent().getBooleanExtra("corrupt",false);
            ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,"image/png");v.put(MediaStore.Images.Media.RELATIVE_PATH,path);v.put(MediaStore.Images.Media.IS_PENDING,1);
            Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);
            Bitmap b=bitmap();try(OutputStream out=getContentResolver().openOutputStream(uri)){if(corrupt)out.write(new byte[]{1,2,3,4,5});else b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
            long delay=getIntent().getLongExtra("delay",0);
            String savedName=name;
            new Handler().postDelayed(()->{ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,done,null,null);status.append("\n已保存测试图片："+savedName+"\n"+uri);Log.i("ImageClipboardQA","EMIT "+uri);},delay);
        }catch(Exception e){status.append("\nFAIL "+e);Log.e("ImageClipboardQA","emit failed",e);}
    }
    private void share(){
        try{
            String value=getIntent().getStringExtra("uri");
            if(value==null){Bitmap b=bitmap();try(OutputStream out=new FileOutputStream(new File(getFilesDir(),"private-image.png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();value="content://com.zfj.imageclipboard.probe.private/image";}
            String target=getIntent().getStringExtra("target");if(target==null)target="com.zfj.imageclipboard.debug";
            Uri uri=Uri.parse(value);Intent share=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).setClassName(target,"com.zfj.imageclipboard.ShareActivity");share.setClipData(ClipData.newRawUri("QA",uri));startActivity(share);
        }catch(Exception e){Log.e("ImageClipboardQA","share failed",e);}
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&"read".equals(getIntent().getStringExtra("case")))new Handler().postDelayed(this::read,300);}
    private void read(){
        try{
            ClipData data=getSystemService(ClipboardManager.class).getPrimaryClip();
            if(data==null||data.getItemCount()==0||data.getItemAt(0).getUri()==null)throw new IOException("No image URI on clipboard");
            Uri uri=data.getItemAt(0).getUri();
            Bitmap b;try(InputStream in=getContentResolver().openInputStream(uri)){b=BitmapFactory.decodeStream(in);}
            if(b==null)throw new IOException("Could not decode clipboard image");
            String result="PASS cross-app read "+b.getWidth()+"x"+b.getHeight()+" "+uri;
            status.append("\n\n"+result);Log.i("ImageClipboardQA",result);b.recycle();
        }catch(Exception e){status.append("\n\nFAIL "+e);Log.e("ImageClipboardQA","FAIL clipboard read",e);}
    }
}
