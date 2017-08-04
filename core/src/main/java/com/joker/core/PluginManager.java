package com.joker.core;

import android.app.Application;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;

import com.joker.core.plugin.LoadedPlugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Created by joker on 2017/7/11.
 */

public class PluginManager {


    private static volatile PluginManager sInstance;

    private Map<String, LoadedPlugin> mPlugins = new ConcurrentHashMap<>();
    private Context mContext;

    private Instrumentation mInstrumentation;

    public static PluginManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (PluginManager.class) {
                if (sInstance == null) {
                    sInstance = new PluginManager(context);
                }
            }
        }
        return sInstance;
    }


    private PluginManager(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            mContext = context;
        } else {
            mContext = ((Application) appContext).getBaseContext();
        }
        prepare();
    }

    private void prepare() {
        PluginSystem.sContext = getHostContext();


    }

    public Context getHostContext() {
        if (mContext == null) {
            throw new RuntimeException("Big Error");
        }
        return mContext;
    }

    public Instrumentation getInstrumentation(){
        return mInstrumentation;
    }


    public void loadPlugin(File apk) throws Exception {
        if (apk == null) {
            throw new IllegalArgumentException("erro : apk is null");
        }
        if (!apk.exists()) {
            throw new FileNotFoundException(apk.getAbsolutePath());
        }

        LoadedPlugin loadedPlugin = LoadedPlugin.create(this, this.mContext, apk);
        if (loadedPlugin != null) {
            mPlugins.put(loadedPlugin.getPackageName(), loadedPlugin);
            //启动，try to invoke plugin's application
            loadedPlugin.invokeApplication();
        } else {
            throw new RuntimeException("cant load plugin");
        }
    }


    public LoadedPlugin getLoadedPlugin(String packageName) {
        return mPlugins.get(packageName);
    }


    public LoadedPlugin getLoadedPlugin(ComponentName componentName) {
        return getLoadedPlugin(componentName.getPackageName());
    }

    public LoadedPlugin getLoadedPlugin(Intent intent) {
        return null;
    }


    public ResolveInfo resolveActivity(Intent intent) {
        return resolveActivity(intent, 0);
    }

    public ResolveInfo resolveActivity(Intent intent, int flags) {
        for (LoadedPlugin plugin : mPlugins.values()) {
            ResolveInfo resolveInfo = plugin.resolveActivity(intent, flags);
            if (resolveInfo != null) {
                return resolveInfo;
            }
        }
        return null;
    }

    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
        List<ResolveInfo> resolveInfos = new ArrayList<>();
        for (LoadedPlugin plugin : mPlugins.values()) {
            List<ResolveInfo> resolves = plugin.queryIntentActivities(intent, flags);
            if (resolves != null && !resolves.isEmpty()) {
                resolveInfos.addAll(resolves);
            }
        }
        return resolveInfos;
    }


    public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags) {
        List<ResolveInfo> resolveInfos = new ArrayList<>();
        for (LoadedPlugin plugin : mPlugins.values()) {
            List<ResolveInfo> resolves = plugin.queryBroadcastReceivers(intent, flags);
            if (resolves != null && !resolves.isEmpty()) {
                resolveInfos.addAll(resolves);
            }
        }
        return resolveInfos;
    }


    public ResolveInfo resolveService(Intent intent, int flags) {
        for (LoadedPlugin plugin : this.mPlugins.values()) {
            ResolveInfo resolveInfo = plugin.resolveService(intent, flags);
            if (null != resolveInfo) {
                return resolveInfo;
            }
        }

        return null;
    }


    public ProviderInfo resolveContentProvider(String name, int flags) {
        for (LoadedPlugin plugin : mPlugins.values()) {
            ProviderInfo providerInfo = plugin.resolveContentProvider(name, flags);
            if (providerInfo != null) {
                return providerInfo;
            }
        }
        return null;
    }

    public List<ResolveInfo> queryIntentServices(Intent intent, int flags) {
        List<ResolveInfo> resolveInfos = new ArrayList<ResolveInfo>();

        for (LoadedPlugin plugin : this.mPlugins.values()) {
            List<ResolveInfo> result = plugin.queryIntentServices(intent, flags);
            if (null != result && result.size() > 0) {
                resolveInfos.addAll(result);
            }
        }

        return resolveInfos;
    }

}
