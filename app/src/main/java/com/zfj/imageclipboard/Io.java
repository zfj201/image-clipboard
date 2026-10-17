package com.zfj.imageclipboard;

import java.io.*;
import java.security.MessageDigest;

final class Io {
    static String copyAndHash(InputStream in, OutputStream out) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        byte[] buffer=new byte[64*1024]; int n; long total=0;
        while((n=in.read(buffer))!=-1) {
            total+=n; if(total>128L*1024*1024)throw new IOException("图片超过128MB，请缩小后重试");
            out.write(buffer,0,n); digest.update(buffer,0,n);
        }
        if(total==0)throw new IOException("图片尚未保存完成");
        StringBuilder hex=new StringBuilder(); for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
}
