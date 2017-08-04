package com.joker.core.utils;

import android.app.Instrumentation;
import android.content.Context;

/**
 * Created by Joker on 2017/8/4 0004.
 */

public class InstrumentationUtils {

    private static final String ACTIVITY_THREAD = "android.app.ActivityThread";

    private static final String ACTIVITY_THREAD_FIELD = "sCurrentActivityThread";

    private static final String ACTIVITY_THREAD_LOCAL = "sThreadLocal";


    private static Instrumentation sInstrumentation;
    private static Object sActivityThread;


    public static Object getActivityThread(Context base) {
        if (sActivityThread == null) {

            try {
                Class activity_thread_clazz = Class.forName(ACTIVITY_THREAD);
                Object activityThread = null;
                // 获取单例对象
                try {
                    activityThread = RefUtils.getField(activity_thread_clazz, null, ACTIVITY_THREAD_FIELD);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (activityThread == null) {
                    activityThread = ((ThreadLocal<?>) RefUtils.getField(activity_thread_clazz, null, ACTIVITY_THREAD_LOCAL)).get();
                }
                sActivityThread = activityThread;
            } catch (Exception e) {
                e.printStackTrace();
            }


        }
        return sActivityThread;
    }


    public static Instrumentation getInstrumentation(Context context) {
        Object activityThread = getActivityThread(context);
        if (activityThread != null) {
            try {
                sInstrumentation = (Instrumentation) RefUtils.getField(activityThread.getClass(), activityThread, "mInstrumentation");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return sInstrumentation;
    }


    public static void setInstrumentation(Object activityThread, Instrumentation instrumentation) {
        try {
            RefUtils.setField(activityThread.getClass(), activityThread, "mInstrumentation", instrumentation);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
