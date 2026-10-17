package com.zfj.imageclipboard;

import java.util.Locale;

/** Pure rules shared by the Android pipeline and host tests. */
public final class Rules {
    private Rules() {}
    public static String canonical(String path) {
        if (path == null) return "";
        return path.replaceFirst("^/storage/emulated/(\\d+)(?=/|$)", "/data/media/$1")
            .replaceFirst("^/sdcard(?=/|$)", "/data/media/0");
    }
    public static boolean matches(String value, String rules) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (String rule : rules.split("[\\r\\n]+")) {
            rule = rule.trim().toLowerCase(Locale.ROOT);
            if (!rule.isEmpty() && lower.contains(rule)) return true;
        }
        return false;
    }
    public static boolean likelyCapture(String value) {
        return matches(value, "camera\nscreenshot\nscreen_shot\nscreen-shot\nscreencapture\nscreen_capture\n截屏\n截图\n相机\nphoto\n/dcim/\nimg_\npxl_\nmmexport");
    }
    public static String rejection(String path, String includes, String excludes, boolean captureOnly) {
        if (matches(path, excludes)) return "排除规则";
        if (!includes.trim().isEmpty() && !matches(path, includes)) return "未匹配包含规则";
        if (captureOnly && !likelyCapture(path)) return "拍照／截图特征不明确";
        return null;
    }
    public static boolean plausibleImage(String mime, String name) {
        if (mime != null && mime.startsWith("image/")) return true;
        return name.toLowerCase(Locale.ROOT).matches(".*\\.(jpe?g|png|webp|heic|heif|avif|gif|bmp)$");
    }
}
