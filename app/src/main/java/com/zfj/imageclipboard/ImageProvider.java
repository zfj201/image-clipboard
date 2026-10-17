package com.zfj.imageclipboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public final class ImageProvider extends ContentProvider {
    static final String AUTHORITY = BuildConfig.APPLICATION_ID+".images";
    @Override public boolean onCreate() { return true; }
    private File file(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (!AUTHORITY.equals(uri.getAuthority()) || uri.getPathSegments().size() != 1 || name == null || !name.matches("[a-f0-9]{64}\\.(jpg|png|webp|heic|gif|avif|img)")) throw new FileNotFoundException("Invalid image URI");
        File f = new File(new File(getContext().getFilesDir(), "images"), name);
        if (!f.isFile()) throw new FileNotFoundException("Image expired");
        return f;
    }
    static Uri uri(String name) { return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(name).build(); }
    static void copy(Context c, String name) {
        Uri image = uri(name);
        ClipboardManager cm = c.getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newUri(c.getContentResolver(), "图片", image));
    }
    @Override public String getType(Uri uri) {
        String p = uri.getLastPathSegment();
        if (p == null) return "image/*";
        if (p.endsWith(".jpg")) return "image/jpeg";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".heic")) return "image/heic";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".avif")) return "image/avif";
        return "image/*";
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor c = new MatrixCursor(columns);
        try {
            File f = file(uri); Object[] row = new Object[columns.length];
            for (int i=0; i<columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = f.getName();
                if (OpenableColumns.SIZE.equals(columns[i])) row[i] = f.length();
            }
            c.addRow(row);
        } catch (FileNotFoundException ignored) {}
        return c;
    }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException("Read only"); }
}
