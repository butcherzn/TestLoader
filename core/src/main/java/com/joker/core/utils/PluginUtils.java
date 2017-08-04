package com.joker.core.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;

import com.joker.core.plugin.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by joker on 2017/7/11.
 */

public class PluginUtils {


    public static void copyNativeLib(File apk, Context context, PackageInfo packageInfo, File nativeLibDir) {
        ZipFile zipFile = null;
        try {
            String cpuArch;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cpuArch = Build.SUPPORTED_ABIS[0];
            } else {
                cpuArch = Build.CPU_ABI;
            }
            boolean findSo = false;
            zipFile = new ZipFile(apk.getAbsolutePath());
            ZipEntry entry;
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (entry.getName().endsWith(".so") && entry.getName().contains("lib/" + cpuArch)) {
                    findSo = true;
                    break;
                }
            }
            entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".so")) {
                    continue;
                }
                if ((findSo && entry.getName().contains("lib/" + cpuArch)) || (!findSo && entry.getName().contains("lib/armeabi/"))) {
                    String[] split = entry.getName().split("/");
                    int length = split.length;
                    if (length > 0) {
                        String libName = split[length - 1];
                        File libFile = new File(nativeLibDir.getAbsolutePath() + File.separator + libName);
                        String key = packageInfo.packageName + "_" + libName;
                        if (libFile.exists()) {
                            int versionCode = Settings.getSoVersion(context, key);
                            if (versionCode == packageInfo.versionCode) {
                                continue;
                            }
                        }

                        FileOutputStream fos = new FileOutputStream(libFile);
                        //
                        FileUtils.copyFile(zipFile.getInputStream(entry), fos);
                        Settings.setSoVersion(context, key, packageInfo.versionCode);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 获得目标activity
     *
     * @param intent
     * @return
     */
    public static String getTargetActivity(Intent intent) {
        return intent.getStringExtra(Constants.KEY_TARGET_ACTIVITY);
    }


    public static ComponentName getComponentName(Intent intent) {
        return new ComponentName(intent.getStringExtra(Constants.KEY_TARGET_PACKAGE), intent.getStringExtra(Constants.KEY_TARGET_ACTIVITY));
    }


    /**
     * 是否来自插件
     *
     * @param intent
     * @return
     */
    public static boolean isIntentFromPlugin(Intent intent) {
        return intent.getBooleanExtra(Constants.KEY_IS_FROM_PLUGIN, false);
    }


}
