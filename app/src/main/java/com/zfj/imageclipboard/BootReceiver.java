package com.zfj.imageclipboard;
import android.content.*;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))return;
        if(new Settings(c).enabled())try{MonitorService.start(c);}catch(Exception e){new Settings(c).prefs.edit().putString("status","自动恢复被系统限制，请打开应用").apply();}
    }
}
