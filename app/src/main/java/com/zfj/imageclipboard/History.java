package com.zfj.imageclipboard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Comparator;

final class History extends SQLiteOpenHelper {
    static final class Row {
        long id, time;
        String source, status, detail, cache, mime;
    }
    History(Context c) { super(c, "history.db", null, 1); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY, time INTEGER NOT NULL, source TEXT, status TEXT, detail TEXT, cache TEXT, mime TEXT)");
        db.execSQL("CREATE TABLE fingerprints(source TEXT PRIMARY KEY, hash TEXT, time INTEGER)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
    synchronized void add(String source, String status, String detail, String cache, String mime) {
        ContentValues v = new ContentValues();
        v.put("time", System.currentTimeMillis()); v.put("source", source); v.put("status", status);
        v.put("detail", detail); v.put("cache", cache); v.put("mime", mime);
        getWritableDatabase().insert("history", null, v);
        getWritableDatabase().execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT 200)");
    }
    synchronized boolean alreadyCopied(String source, String hash) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT hash FROM fingerprints WHERE source=?", new String[]{source})) {
            return c.moveToFirst() && hash.equals(c.getString(0));
        }
    }
    synchronized void remember(String source, String hash) {
        ContentValues v = new ContentValues(); v.put("source", source); v.put("hash", hash); v.put("time", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("fingerprints", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        getWritableDatabase().execSQL("DELETE FROM fingerprints WHERE source NOT IN (SELECT source FROM fingerprints ORDER BY time DESC LIMIT 2000)");
    }
    synchronized List<Row> recent() {
        List<Row> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,time,source,status,detail,cache,mime FROM history ORDER BY id DESC LIMIT 40", null)) {
            while (c.moveToNext()) {
                Row r = new Row(); r.id=c.getLong(0); r.time=c.getLong(1); r.source=c.getString(2); r.status=c.getString(3);
                r.detail=c.getString(4); r.cache=c.getString(5); r.mime=c.getString(6); result.add(r);
            }
        }
        return result;
    }
    synchronized void cleanCache(Context context) {
        // Keep recent items for re-copy; use age rather than the current clipboard, which is private on Android 10+.
        File dir = new File(context.getFilesDir(), "images");
        File[] images = dir.listFiles();
        if (images == null) return;
        long deadline = System.currentTimeMillis() - 7L*24*60*60*1000;
        String latest=new Settings(context).prefs.getString("latestCache", "");
        Arrays.sort(images,Comparator.comparingLong(File::lastModified).reversed());
        long total=0;int count=0;
        for(File f:images){total+=f.length();count++;if(!f.getName().equals(latest)&&(f.lastModified()<deadline||total>256L*1024*1024||count>150))f.delete();}
    }
}
