package com.github.catvod.crawler;

public class SpiderDebug {
    public static void log(Throwable th) {
        try {
            android.util.Log.d("SpiderLog", th.getMessage(), th);
        } catch (Throwable th1) {

        }
    }

    public static void log(String msg) {
        try {
            android.util.Log.d("SpiderLog", "log="+msg);
        } catch (Throwable th1) {

        }
    }

    public static String ec(int i) {
        return "";
    }
}
