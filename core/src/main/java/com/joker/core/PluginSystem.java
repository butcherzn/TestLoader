package com.joker.core;

import android.content.Context;

/**
 * Created by joker on 2017/7/11.
 */

public class PluginSystem {

    static Context sContext;

    public static Context getHostContext() {
        return sContext;
    }

}
