package com.joker.core.plugin;

import android.content.Context;

import com.joker.core.PluginManager;

/**
 * Created by Joker on 2017/8/4 0004.
 */

public class ComponentsHandler {


    private PluginManager mPluginManager;
    private Context mHostContext;


    public ComponentsHandler(PluginManager pluginManager) {
        this.mPluginManager = pluginManager;
        this.mHostContext = pluginManager.getHostContext();
    }
}
