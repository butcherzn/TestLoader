package com.joker.core.plugin;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextThemeWrapper;

import com.joker.core.PluginManager;
import com.joker.core.utils.PluginUtils;
import com.joker.core.utils.RefUtils;

/**
 * Created by Joker on 2017/8/4 0004.
 */

public class InstrumentationProxy extends Instrumentation implements Handler.Callback {

    public static final int LAUNCH_ACTIVITY = 100;


    private PluginManager mPluginManager;
    private Instrumentation mBase;

    public InstrumentationProxy(PluginManager pluginManager, Instrumentation base) {
        this.mPluginManager = pluginManager;
        this.mBase = base;
    }


    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            // TODO 有意思
            cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            LoadedPlugin loadedPlugin = this.mPluginManager.getLoadedPlugin(intent);
            String targetActivity = PluginUtils.getTargetActivity(intent);
            // TODO print log

            if (targetActivity != null) {
                Activity activity = mBase.newActivity(loadedPlugin.getClassLoader(), targetActivity, intent);
                activity.setIntent(intent);
                try {
                    RefUtils.setField(ContextWrapper.class, activity, "mResources", loadedPlugin.getResources());
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
                return activity;
            }


        }
        return mBase.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        Intent intent = activity.getIntent();
        if (PluginUtils.isIntentFromPlugin(intent)) {
            Context baseContext = activity.getBaseContext();


            //是 插件 跳转
            LoadedPlugin loadedPlugin = this.mPluginManager.getLoadedPlugin(intent);
            try {
                // TODO
                RefUtils.setField(ContextWrapper.class, activity, "mBase", loadedPlugin.getPluginContext());
                RefUtils.setField(Activity.class, activity, "mApplication", loadedPlugin.getApplication());
                RefUtils.setField(ContextThemeWrapper.class, activity, "mBase", loadedPlugin.getPluginContext());
                RefUtils.setField(baseContext.getClass(), baseContext, "mResources", loadedPlugin.getResources());

                ActivityInfo activityInfo = loadedPlugin.getActivityInfo(PluginUtils.getComponentName(intent));
                // TODO
                if (activityInfo.screenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                    activity.setRequestedOrientation(activityInfo.screenOrientation);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mBase.callActivityOnCreate(activity, icicle);
    }


    @Override
    public Context getContext() {
        return mBase.getContext();
    }

    @Override
    public Context getTargetContext() {
        return mBase.getTargetContext();
    }

    @Override
    public ComponentName getComponentName() {
        return mBase.getComponentName();
    }

    @Override
    public boolean handleMessage(Message message) {
        return false;
    }
}
