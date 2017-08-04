package com.joker.core.utils;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * Created by Joker on 2017/8/4 0004.
 */

public class RunUtils {

    private static final int MSG_RUN_ON_UI = 1;

    private static Handler sHandler;


    /**
     * ui 和 work 异步
     *
     * @param runnable
     */
    public static void runOnUiThread(@NonNull Runnable runnable) {
        runOnUiThread(runnable, false);
    }


    /**
     * 在UI线程中运行
     *
     * @param runnable   执行体
     * @param waitUiDone 等待UI线程执行完，该线程再继续
     */
    public static void runOnUiThread(@NonNull Runnable runnable, boolean waitUiDone) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            //在主线程中运行
            runnable.run();
        }
        // 非UI线程
        CountDownLatch countDownLatch = null;
        if (waitUiDone) {
            countDownLatch = new CountDownLatch(1);
        }
        Pair<Runnable, CountDownLatch> pair = Pair.create(runnable, countDownLatch);
        getHandler().obtainMessage(MSG_RUN_ON_UI, pair).sendToTarget();
        if (waitUiDone) {
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }


    /**
     * 获取线程池
     * @return
     */
    public static Executor getThreadPool() {
        return AsyncTask.THREAD_POOL_EXECUTOR;
    }


    private static Handler getHandler() {
        if (sHandler == null) {
            synchronized (RunUtils.class) {
                if (sHandler == null) {
                    sHandler = new InnerHandler();
                }
            }
        }
        return sHandler;
    }


    private static class InnerHandler extends Handler {

        public InnerHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_RUN_ON_UI) {
                Pair<Runnable, CountDownLatch> pair = (Pair<Runnable, CountDownLatch>) msg.obj;
                Runnable runnable = pair.first;
                // 在ui线程
                runnable.run();
                //执行完毕
                if (pair.second != null) {
                    pair.second.countDown();
                }
            }
        }
    }


}
