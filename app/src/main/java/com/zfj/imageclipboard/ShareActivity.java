package com.zfj.imageclipboard;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.util.List;

public final class ShareActivity extends Activity {
    private PicturePipeline pipeline;
    private History history;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        TextView message=new TextView(this);message.setText("正在复制图片…");message.setTextSize(20);message.setPadding(48,100,48,48);setContentView(message);
        Intent intent=getIntent();
        Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if(uri==null&&intent.getClipData()!=null&&intent.getClipData().getItemCount()>0)uri=intent.getClipData().getItemAt(0).getUri();
        if(!Intent.ACTION_SEND.equals(intent.getAction())||uri==null||!"content".equals(uri.getScheme())){Toast.makeText(this,"请选择一张图片分享到此应用",Toast.LENGTH_LONG).show();finish();return;}
        history=new History(this);
        pipeline=new PicturePipeline(this,history,()->{
            List<History.Row> rows=history.recent();
            if(!rows.isEmpty())Toast.makeText(this,rows.get(0).status+" · "+rows.get(0).detail,Toast.LENGTH_LONG).show();
            finish();
        });
        pipeline.enqueue(new PicturePipeline.Candidate(uri,"分享 · "+uri.getAuthority(),System.currentTimeMillis(),true));
    }
    @Override protected void onDestroy(){if(pipeline!=null)pipeline.close();super.onDestroy();}
}
