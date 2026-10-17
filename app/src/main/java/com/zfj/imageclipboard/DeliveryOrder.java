package com.zfj.imageclipboard;

/** Only successfully validated images advance ordering; thumbnails cannot suppress a real photo. */
public final class DeliveryOrder {
    private long newestTime=Long.MIN_VALUE;
    public synchronized boolean accept(long time){if(time<newestTime)return false;newestTime=time;return true;}
    public synchronized boolean current(long time){return time>=newestTime;}
}
