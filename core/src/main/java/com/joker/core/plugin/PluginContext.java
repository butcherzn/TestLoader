package com.joker.core.plugin;

import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;

/**
 * Created by joker on 2017/7/11.
 */

public class PluginContext extends ContextWrapper {

    private LoadedPlugin mPlugin;

    public PluginContext(LoadedPlugin plugin) {
        super(plugin.getPluginManager().getHostContext());
        mPlugin = plugin;
    }

    @Override
    public Context getApplicationContext() {
        return mPlugin.getApplicationContext();
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return mPlugin.getApplicationInfo();
    }

    @Override
    public ContentResolver getContentResolver() {
        return mPlugin.getContentResolver();
    }

    @Override
    public ClassLoader getClassLoader() {
        return mPlugin.getClassLoader();
    }

    @Override
    public String getPackageName() {
        return mPlugin.getPackageName();
    }

    @Override
    public String getPackageResourcePath() {
        return mPlugin.getPackageResourcePath();
    }

    @Override
    public String getPackageCodePath() {
        return mPlugin.getPackageCodePath();
    }

    @Override
    public PackageManager getPackageManager() {
        return mPlugin.getPackageManager();
    }

    @Override
    public Object getSystemService(String name) {
        return super.getSystemService(name);
    }

    @Override
    public Resources getResources() {
        return mPlugin.getResources();
    }


    @Override
    public AssetManager getAssets() {
        return mPlugin.getAssets();
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
    }
}
