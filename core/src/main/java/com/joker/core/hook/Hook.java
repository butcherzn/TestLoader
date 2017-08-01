package com.joker.core.hook;

import android.app.Instrumentation;
import android.content.Context;
import android.support.annotation.UiThread;

import com.joker.core.utils.RefUtils;

/**
 * Created by joker on 2017/7/11.
 */

public class Hook {

    public static final String ACTIVITY_THREAD_CLASS_NAME = "android.app.ActivityThread";

    private static Object sActivityThread;
    private static Instrumentation sInstrumentation;


    @UiThread
    public static Object getActivityThread(Context context) {
        if (sActivityThread == null) {
            try {
                Class activityThreadClass = Class.forName(ACTIVITY_THREAD_CLASS_NAME);
                Object activityThread = null;
                try {
                    activityThread = RefUtils.getField(activityThreadClass, null, "sCurrentActivityThread");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (activityThread == null) {
                    //
                    activityThread = ((ThreadLocal<?>) RefUtils.getField(activityThreadClass, null, "sThreadLocal")).get();
                }
                sActivityThread = activityThread;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return sActivityThread;
    }

    public static Instrumentation getInstrumentation(Context base) {
        if (getActivityThread(base) != null) {
            try {
                sInstrumentation = (Instrumentation) RefUtils.invoke(sActivityThread.getClass(), sActivityThread, "getInstrumentation");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return sInstrumentation;
    }


    public static void setInstrumentation(Context context, Instrumentation instrumentation){
        if(getActivityThread(context) != null){
            try {
                RefUtils.setField(sActivityThread.getClass(), sActivityThread, "mInstrumentation", instrumentation);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
