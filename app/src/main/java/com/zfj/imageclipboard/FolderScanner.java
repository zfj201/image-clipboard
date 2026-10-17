package com.zfj.imageclipboard;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import java.util.*;

/** SAF is opt-in and works only for directories Android lets the user grant. */
final class FolderScanner {
    interface Listener { void image(PicturePipeline.Candidate c); void status(String text); }
    private final Context context;
    private final Listener listener;
    private final Map<String,String> seen=new LinkedHashMap<>();
    private final Set<String> initialized=new HashSet<>();
    private final Handler timeouts=new Handler(Looper.getMainLooper());
    private volatile CancellationSignal active;
    void cancel(){CancellationSignal signal=active;if(signal!=null)signal.cancel();}
    FolderScanner(Context c,Listener listener){context=c;this.listener=listener;}
    void scan() {
        for(String value:new Settings(context).folders().split("\n")) {
            if(value.isEmpty()||Thread.currentThread().isInterrupted())continue;
            try {
                Uri tree=Uri.parse(value);
                boolean baseline=!initialized.contains(value);
                int[] budget={0};
                visit(tree,DocumentsContract.getTreeDocumentId(tree),"",baseline,budget,0);
                initialized.add(value);
            } catch(Exception e){listener.status("授权文件夹读取失败："+e.getMessage());}
        }
    }
    private void visit(Uri tree,String parent,String path,boolean baseline,int[] budget,int depth) throws Exception {
        if(depth>24||budget[0]>5000||Thread.currentThread().isInterrupted())return;
        String[] fields={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_LAST_MODIFIED,DocumentsContract.Document.COLUMN_SIZE};
        CancellationSignal signal=new CancellationSignal();active=signal;
        Runnable timeout=signal::cancel;timeouts.postDelayed(timeout,15000);
        try(Cursor c=context.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent),fields,null,null,null,signal)) {
            if(c==null)return;
            while(c.moveToNext() && !Thread.currentThread().isInterrupted()) {
                if(++budget[0]>5000){listener.status("授权文件夹超过5000项，请改为授权较小的图片子目录");return;}
                String id=c.getString(0),name=c.getString(1),mime=c.getString(2),child=path+"/"+name;
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){visit(tree,id,child,baseline,budget,depth+1);continue;}
                if(!Rules.plausibleImage(mime,name))continue;
                long modified=c.getLong(3),size=c.getLong(4);
                if(size==0)continue;
                String key=tree+":"+id,signature=modified+":"+size,old=seen.put(key,signature);
                if(signature.equals(old))continue;
                if(baseline && modified<new Settings(context).since())continue;
                Uri image=DocumentsContract.buildDocumentUriUsingTree(tree,id);
                String source=id.startsWith("primary:")?"/storage/emulated/0/"+id.substring(8):"文件夹:"+child;
                listener.image(new PicturePipeline.Candidate(image,source,baseline?modified:System.currentTimeMillis(),false));
            }
        }finally{timeouts.removeCallbacks(timeout);if(active==signal)active=null;}
        if(seen.size()>15000)seen.clear();
    }
}
