package com.zfj.imageclipboard;
public class CoreTest {
    static int count;
    static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);count++;}
    public static void main(String[] args){
        check(Rules.rejection("/storage/emulated/0/Pictures/SomeApp/photo.png","","",false)==null,"unknown app folders must be allowed");
        check(Rules.rejection("/storage/1234-5678/WeChat/saved.webp","","",false)==null,"SD cards must be allowed");
        check(Rules.rejection("/DCIM/.thumbnails/IMG_1.jpg","","/.thumbnails/",false)!=null,"exclude thumbnail directories");
        check(Rules.rejection("/Pictures/myapp/result.png","myapp","",false)==null,"include by app path");
        check(Rules.rejection("/Pictures/another/result.png","myapp","",false)!=null,"inclusion should not match unrelated paths");
        check(Rules.rejection("/Pictures/result.png","","",true)!=null,"capture mode must not claim perfect classification");
        check(Rules.likelyCapture("/Pictures/截屏/2026.png"),"Chinese screenshot paths");
        check(Rules.likelyCapture("/DCIM/IMG_123.jpg"),"camera names");
        check(Rules.plausibleImage("image/heic","arbitrary"),"MIME overrides absent extension");
        check(Rules.plausibleImage("application/octet-stream","IMAGE.PNG"),"case-insensitive extension fallback");
        check(!Rules.plausibleImage("video/mp4","movie.mp4"),"videos ignored");
        check(Rules.canonical("/storage/emulated/0/Pictures/a.png").equals(Rules.canonical("/sdcard/Pictures/a.png")),"MediaStore and SAF aliases deduplicate");
        DeliveryOrder ordering=new DeliveryOrder();
        check(ordering.accept(100),"first valid image");
        check(ordering.accept(102),"newer image");
        check(!ordering.accept(101),"slow older image cannot overwrite newer image");
        check(!ordering.current(100),"queued main-thread commits must recheck freshness");
        check(ordering.current(102),"newest remains current");
        check(ordering.accept(102),"distinct images sharing MediaStore second must still work");
        System.out.println(count+" core checks passed");
    }
}
