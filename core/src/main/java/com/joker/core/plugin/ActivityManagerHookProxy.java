package com.joker.core.plugin;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.Context;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.joker.core.PluginManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created by Joker on 2017/8/4 0004.
 */

public class ActivityManagerHookProxy implements InvocationHandler {


    private PluginManager pluginManager;
    private IActivityManager mActivityManager;


    public static IActivityManager newInstance(PluginManager pluginManager, IActivityManager activityManager) {
        return (IActivityManager) Proxy.newProxyInstance(activityManager.getClass().getClassLoader(), new Class[]{IActivityManager.class}, new ActivityManagerHookProxy(pluginManager, activityManager));
    }


    private ActivityManagerHookProxy(PluginManager pluginManager, IActivityManager activityManager) {
        this.pluginManager = pluginManager;
        this.mActivityManager = activityManager;
    }


    @Override
    public Object invoke(Object o, Method method, Object[] args) throws Throwable {


        try {
            return method.invoke(this.mActivityManager, args);
        } catch (Throwable throwable) {
            Throwable cause = throwable.getCause();
            if (cause != null && cause instanceof DeadObjectException) {
                IBinder service = ServiceManager.getService(Context.ACTIVITY_SERVICE);
                if (service != null) {
                    IActivityManager am = ActivityManagerNative.asInterface(service);
                    this.mActivityManager = am;

                }
            }
            Throwable c = throwable;
            do {
                if (c instanceof RemoteException) {
                    throw c;
                }
            } while ((c = c.getCause()) != null);

            throw cause != null ? cause : throwable;
        }
    }
}
