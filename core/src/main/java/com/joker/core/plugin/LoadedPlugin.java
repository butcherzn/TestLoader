package com.joker.core.plugin;

import android.annotation.TargetApi;
import android.app.Application;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ChangedPackages;
import android.content.pm.FeatureInfo;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.VersionedPackage;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringRes;
import android.support.annotation.WorkerThread;
import android.support.annotation.XmlRes;

import com.joker.core.PluginManager;
import com.joker.core.utils.DexUtils;
import com.joker.core.utils.PackageParserCompat;
import com.joker.core.utils.PluginUtils;
import com.joker.core.utils.RefUtils;
import com.joker.core.utils.RunUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dalvik.system.DexClassLoader;

/**
 * Created by joker on 2017/7/11.
 */

public final class LoadedPlugin {

    private static final String TAG = "LoadedPlugin";

    private PluginManager mPluginManager;
    private Context mHostContext;
    private Context mPluginContext;

    private AssetManager mAssets;
    private Resources mResources;
    private ClassLoader mClassLoader;
    private final PackageParser.Package mPackage;

    private String mPluginPath;
    private PackageInfo mPackageInfo;

    private PluginPackageManager mPluginPackageManager;

    private final File mNativeLibDir;


    private Map<ComponentName, ActivityInfo> mActivityInfos;
    private Map<ComponentName, ServiceInfo> mServiceInfos;
    private Map<ComponentName, ActivityInfo> mReceiverInfos;
    private Map<ComponentName, ProviderInfo> mProviderInfos;
    private Map<String, ProviderInfo> mProviders;
    private Map<ComponentName, InstrumentationInfo> mInstrumentationInfos;


    private Application mApplication;


    public static LoadedPlugin create(PluginManager pluginManager, Context host, File apk) throws Exception {
        //TODO
        return new LoadedPlugin(pluginManager, host, apk);
    }


    LoadedPlugin(PluginManager pluginManager, Context context, File apk) throws PackageParser.PackageParserException {
        mPluginManager = pluginManager;
        mHostContext = context;
        mPluginPath = apk.getAbsolutePath();
        //TODO 关键 解析apk包
        mPackage = PackageParserCompat.parsePackage(context, apk, PackageParser.PARSE_MUST_BE_APK);
        mPackage.applicationInfo.metaData = mPackage.mAppMetaData;
        mPackageInfo = new PackageInfo();
        mPackageInfo.applicationInfo = mPackage.applicationInfo;
        mPackageInfo.applicationInfo.sourceDir = apk.getAbsolutePath();
        mPackageInfo.signatures = mPackage.mSignatures;
        mPackageInfo.packageName = mPackage.packageName;
        if (pluginManager.getLoadedPlugin(mPackageInfo.packageName) != null) {
            throw new RuntimeException("plugin has already been loaded : " + mPackageInfo.packageName);
        }
        mPackageInfo.versionCode = mPackage.mVersionCode;
        mPackageInfo.versionName = mPackage.mVersionName;
        mPackageInfo.permissions = new PermissionInfo[0];
        mPluginPackageManager = new PluginPackageManager();

        mPluginContext = new PluginContext(this);
        mNativeLibDir = context.getDir(Constants.NATIVE_DIR, Context.MODE_PRIVATE);
        mResources = createResources(context, apk);
        mAssets = mResources.getAssets();
        mClassLoader = createClassLoader(context, apk, mNativeLibDir, context.getClassLoader());

        tryToCopyNativeLib(apk);

        //TODO cache xxxx
        Map<ComponentName, InstrumentationInfo> instrumentaionInfos = new HashMap<>();
        for (PackageParser.Instrumentation info : mPackage.instrumentation) {
            instrumentaionInfos.put(info.getComponentName(), info.info);
        }
        mInstrumentationInfos = Collections.unmodifiableMap(instrumentaionInfos);
        mPackageInfo.instrumentation = instrumentaionInfos.values().toArray(new InstrumentationInfo[instrumentaionInfos.size()]);


        //cache activities
        Map<ComponentName, ActivityInfo> activityInfos = new HashMap<>();
        for (PackageParser.Activity activity : mPackage.activities) {
            activityInfos.put(activity.getComponentName(), activity.info);
        }
        mActivityInfos = Collections.unmodifiableMap(activityInfos);
        mPackageInfo.activities = activityInfos.values().toArray(new ActivityInfo[activityInfos.size()]);

        //cache services
        Map<ComponentName, ServiceInfo> serviceInfos = new HashMap<>();
        for (PackageParser.Service service : mPackage.services) {
            serviceInfos.put(service.getComponentName(), service.info);
        }
        mServiceInfos = Collections.unmodifiableMap(serviceInfos);
        mPackageInfo.services = serviceInfos.values().toArray(new ServiceInfo[serviceInfos.size()]);

        //cache providers
        Map<ComponentName, ProviderInfo> providerInfos = new HashMap<>();
        for (PackageParser.Provider provider : mPackage.providers) {
            providerInfos.put(provider.getComponentName(), provider.info);
        }
        mProviderInfos = Collections.unmodifiableMap(providerInfos);
        mPackageInfo.providers = providerInfos.values().toArray(new ProviderInfo[providerInfos.size()]);

        //register broadcast
        Map<ComponentName, ActivityInfo> receivers = new HashMap<>();
        for (PackageParser.Activity receiver : mPackage.receivers) {
            receivers.put(receiver.getComponentName(), receiver.info);

            try {
                //注册动态
                BroadcastReceiver br = BroadcastReceiver.class.cast(getClassLoader().loadClass(receiver.getComponentName().getClassName()).newInstance());
                for (PackageParser.ActivityIntentInfo aii : receiver.intents) {
                    mHostContext.registerReceiver(br, aii);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mReceiverInfos = Collections.unmodifiableMap(receivers);
        mPackageInfo.receivers = receivers.values().toArray(new ActivityInfo[receivers.size()]);

    }

    private void tryToCopyNativeLib(File apk) {
        Bundle metaData = mPackageInfo.applicationInfo.metaData;
        if (metaData != null && metaData.getBoolean("HAS_LIB")) {
            PluginUtils.copyNativeLib(apk, mHostContext, mPackageInfo, mNativeLibDir);
        }
    }


    @WorkerThread
    private static Resources createResources(Context context, File apk) {
        if (Constants.COMBINE_RESOURCES) {
            //TODO zhangyiming
            return null;
        } else {
            Resources hostResources = context.getResources();
            AssetManager assetManager = createAssetManager(context, apk);
            return new Resources(assetManager, hostResources.getDisplayMetrics(), hostResources.getConfiguration());
        }
    }

    private static AssetManager createAssetManager(Context context, File apk) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            RefUtils.invoke(AssetManager.class, assetManager, "addAssetPath", apk.getAbsolutePath());
            return assetManager;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static ClassLoader createClassLoader(Context context, File apk, File libsDir, ClassLoader parent) {
        File dexOutputDir = context.getDir(Constants.OPTIMIZE_DIR, Context.MODE_PRIVATE);
        String dexOutputPath = dexOutputDir.getAbsolutePath();
        // dexclassloader
        DexClassLoader dexClassLoader = new DexClassLoader(apk.getAbsolutePath(), dexOutputPath, libsDir.getAbsolutePath(), parent);
        if (Constants.COMBINE_CLASSLOADER) {
            //操作合并
            //TODO
            try {
                DexUtils.insertDex(dexClassLoader);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return dexClassLoader;
    }


    private static ResolveInfo chooseBestActivity(Intent intent, String s, int flags, List<ResolveInfo> resolveInfos) {
        return resolveInfos.get(0);
    }


    private Application makeApplication(boolean forceDefaultAppClass, Instrumentation instrumentation) {
        if (null != this.mApplication) {
            return this.mApplication;
        }
        String applicationClass = this.mPackage.applicationInfo.className;
        if (forceDefaultAppClass || null == applicationClass) {
            applicationClass = "android.app.Application";
        }
        // mApplication is null
        try {
            this.mApplication = instrumentation.newApplication(this.mClassLoader, applicationClass, this.mPluginContext);
            instrumentation.callApplicationOnCreate(this.mApplication);
            return this.mApplication;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public void invokeApplication() {
        if (mApplication != null) {
            return;
        }
        RunUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mApplication = makeApplication(false, mPluginManager.getInstrumentation());
            }
        }, true);
    }


    public String getPluginPath() {
        return mPluginPath;
    }


    public PluginManager getPluginManager() {
        return mPluginManager;
    }


    public Context getApplicationContext() {
        return null;
    }

    public ApplicationInfo getApplicationInfo() {
        return mPackageInfo.applicationInfo;
    }

    public ActivityInfo getActivityInfo(ComponentName componentName) {
        return mActivityInfos.get(componentName);
    }

    public ServiceInfo getServiceInfo(ComponentName componentName) {
        return mServiceInfos.get(componentName);
    }

    public ProviderInfo getProviderInfo(ComponentName componentName) {
        return mProviderInfos.get(componentName);
    }

    public ActivityInfo getReceiverInfo(ComponentName componentName) {
        return mReceiverInfos.get(componentName);
    }


    public ContentResolver getContentResolver() {
        return null;
    }

    public ClassLoader getClassLoader() {
        return mClassLoader;
    }

    public String getPackageName() {
        return mPackageInfo.packageName;
    }


    public String getPackageResourcePath() {
        int mUid = Process.myUid();
        ApplicationInfo appInfo = mPackageInfo.applicationInfo;
        return appInfo.uid == mUid ? appInfo.sourceDir : appInfo.publicSourceDir;
    }

    public String getPackageCodePath() {
        return mPackageInfo.applicationInfo.sourceDir;
    }

    public PackageManager getPackageManager() {
        return mPluginPackageManager;
    }


    public Resources getResources() {
        return mResources;
    }


    public AssetManager getAssets() {
        return mAssets;
    }


    public Resources.Theme getTheme() {
        return null;
    }

    public void setTheme() {

    }


    public Intent getLaunchIntent() {
        ContentResolver contentResolver = mPluginContext.getContentResolver();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (PackageParser.Activity activity : mPackage.activities) {
            for (PackageParser.ActivityIntentInfo intentInfo : activity.intents) {
                if (intentInfo.match(contentResolver, launcher, false, "LoadedPlugin") > 0) {
                    return Intent.makeMainActivity(activity.getComponentName());
                }
            }
        }
        return null;
    }


    public Intent getLeanbackLanuchIntent() {
        ContentResolver resolver = this.mPluginContext.getContentResolver();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);

        for (PackageParser.Activity activity : this.mPackage.activities) {
            for (PackageParser.ActivityIntentInfo intentInfo : activity.intents) {
                if (intentInfo.match(resolver, launcher, false, "LoadedPlugin") > 0) {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setComponent(activity.getComponentName());
                    intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                    return intent;
                }
            }
        }

        return null;
    }


    public ResolveInfo resolveActivity(Intent intent, int flags) {
        List<ResolveInfo> resolveInfos = queryIntentActivities(intent, flags);
        if (resolveInfos == null || resolveInfos.isEmpty()) {
            return null;
        }
        ContentResolver contentResolver = getContentResolver();
        return chooseBestActivity(intent, intent.resolveTypeIfNeeded(contentResolver), flags, resolveInfos);
    }


    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
        ComponentName component = intent.getComponent();
        List<ResolveInfo> resolveInfos = new ArrayList<>();

        ContentResolver contentResolver = getContentResolver();

        for (PackageParser.Activity activity : mPackage.activities) {
            if (activity.getComponentName().equals(component)) {
                ResolveInfo resolveInfo = new ResolveInfo();
                resolveInfo.activityInfo = activity.info;
                resolveInfos.add(resolveInfo);
            } else if (component == null) {
                for (PackageParser.ActivityIntentInfo intentInfo : activity.intents) {
                    if (intentInfo.match(contentResolver, intent, true, "LoadedPlugin") > 0) {
                        ResolveInfo resolveInfo = new ResolveInfo();
                        resolveInfo.activityInfo = activity.info;
                        resolveInfos.add(resolveInfo);
                        break;
                    }
                }
            }
        }
        return resolveInfos;

    }


    public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags) {
        ComponentName componentName = intent.getComponent();
        List<ResolveInfo> resolveInfos = new ArrayList<>();

        ContentResolver contentResolver = getContentResolver();
        for (PackageParser.Activity receiver : mPackage.receivers) {
            if (receiver.getComponentName().equals(componentName)) {
                ResolveInfo resolveInfo = new ResolveInfo();
                resolveInfo.activityInfo = receiver.info;
                resolveInfos.add(resolveInfo);
            } else if (componentName == null) {
                for (PackageParser.ActivityIntentInfo intentInfo : receiver.intents) {
                    if (intentInfo.match(contentResolver, intent, true, TAG) > 0) {
                        ResolveInfo resolveInfo = new ResolveInfo();
                        resolveInfo.activityInfo = receiver.info;
                        resolveInfos.add(resolveInfo);
                        break;
                    }
                }
            }
        }
        return resolveInfos;
    }


    public List<ResolveInfo> queryIntentServices(Intent intent, int flags) {
        ComponentName component = intent.getComponent();
        List<ResolveInfo> resolveInfos = new ArrayList<ResolveInfo>();
        ContentResolver resolver = this.mPluginContext.getContentResolver();

        for (PackageParser.Service service : this.mPackage.services) {
            if (service.getComponentName().equals(component)) {
                ResolveInfo resolveInfo = new ResolveInfo();
                resolveInfo.serviceInfo = service.info;
                resolveInfos.add(resolveInfo);
            } else if (component == null) {
                // only match implicit intent
                for (PackageParser.ServiceIntentInfo intentInfo : service.intents) {
                    if (intentInfo.match(resolver, intent, true, TAG) >= 0) {
                        ResolveInfo resolveInfo = new ResolveInfo();
                        resolveInfo.serviceInfo = service.info;
                        resolveInfos.add(resolveInfo);
                        break;
                    }
                }
            }
        }

        return resolveInfos;
    }


    public ResolveInfo resolveService(Intent intent, int flags) {
        List<ResolveInfo> query = this.queryIntentServices(intent, flags);
        if (null == query || query.isEmpty()) {
            return null;
        }

        ContentResolver resolver = this.mPluginContext.getContentResolver();
        return chooseBestActivity(intent, intent.resolveTypeIfNeeded(resolver), flags, query);
    }


    public ProviderInfo resolveContentProvider(String name, int flags) {
        return mProviders.get(name);
    }


    private class PluginPackageManager extends PackageManager {

        private PackageManager mHostPackageManager = mHostContext.getPackageManager();


        @Override
        public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
            LoadedPlugin plugin = mPluginManager.getLoadedPlugin(packageName);
            if (plugin != null) {
                return plugin.mPackageInfo;
            }
            return mHostPackageManager.getPackageInfo(packageName, flags);
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public PackageInfo getPackageInfo(VersionedPackage versionedPackage, int i) throws NameNotFoundException {
            return mHostPackageManager.getPackageInfo(versionedPackage, i);
        }

        @Override
        public String[] currentToCanonicalPackageNames(String[] names) {
            return mHostPackageManager.currentToCanonicalPackageNames(names);
        }

        @Override
        public String[] canonicalToCurrentPackageNames(String[] names) {
            return mHostPackageManager.canonicalToCurrentPackageNames(names);
        }

        @Override
        public Intent getLaunchIntentForPackage(String packageName) {
            LoadedPlugin plugin = mPluginManager.getLoadedPlugin(packageName);
            if (plugin != null) {
                return plugin.getLaunchIntent();
            }
            return mHostPackageManager.getLaunchIntentForPackage(packageName);
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public Intent getLeanbackLaunchIntentForPackage(String packageName) {
            LoadedPlugin plugin = mPluginManager.getLoadedPlugin(packageName);
            if (plugin != null) {
                return plugin.getLeanbackLanuchIntent();
            }
            return mHostPackageManager.getLeanbackLaunchIntentForPackage(packageName);
        }

        @Override
        public int[] getPackageGids(String s) throws NameNotFoundException {
            return mHostPackageManager.getPackageGids(s);
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public int[] getPackageGids(String s, int i) throws NameNotFoundException {
            return mHostPackageManager.getPackageGids(s, i);
        }

        @Override
        public int getPackageUid(String s, int i) throws NameNotFoundException {
            Object uid = RefUtils.invokeSafely(PackageManager.class, mHostPackageManager, "getPackageUid", new Class[]{String.class, int.class}, s, i);
            if (uid != null) {
                return (int) uid;
            } else {
                throw new NameNotFoundException(s);
            }
        }

        @Override
        public PermissionInfo getPermissionInfo(String name, int flags) throws NameNotFoundException {
            return mHostPackageManager.getPermissionInfo(name, flags);
        }

        @Override
        public List<PermissionInfo> queryPermissionsByGroup(String s, int i) throws NameNotFoundException {
            return mHostPackageManager.queryPermissionsByGroup(s, i);
        }

        @Override
        public PermissionGroupInfo getPermissionGroupInfo(String s, int i) throws NameNotFoundException {
            return mHostPackageManager.getPermissionGroupInfo(s, i);
        }

        @Override
        public List<PermissionGroupInfo> getAllPermissionGroups(int i) {
            return mHostPackageManager.getAllPermissionGroups(i);
        }

        @Override
        public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(packageName);
            if (loadedPlugin != null) {
                return loadedPlugin.getApplicationInfo();
            }
            return mHostPackageManager.getApplicationInfo(packageName, flags);
        }

        @Override
        public ActivityInfo getActivityInfo(ComponentName componentName, int flags) throws NameNotFoundException {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(componentName);
            if (loadedPlugin != null) {
                return loadedPlugin.getActivityInfo(componentName);
            }
            return mHostPackageManager.getActivityInfo(componentName, flags);
        }

        @Override
        public ActivityInfo getReceiverInfo(ComponentName componentName, int i) throws NameNotFoundException {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(componentName);
            if (loadedPlugin != null) {
                return loadedPlugin.getReceiverInfo(componentName);
            }
            return mHostPackageManager.getReceiverInfo(componentName, i);
        }

        @Override
        public ServiceInfo getServiceInfo(ComponentName componentName, int i) throws NameNotFoundException {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(componentName);
            if (loadedPlugin != null) {
                return loadedPlugin.getServiceInfo(componentName);
            }
            return mHostPackageManager.getServiceInfo(componentName, i);
        }

        @Override
        public ProviderInfo getProviderInfo(ComponentName componentName, int i) throws NameNotFoundException {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(componentName);
            if (loadedPlugin != null) {
                return loadedPlugin.getProviderInfo(componentName);
            }
            return mHostPackageManager.getProviderInfo(componentName, i);
        }

        @Override
        public List<PackageInfo> getInstalledPackages(int flags) {
            return mHostPackageManager.getInstalledPackages(flags);
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public List<PackageInfo> getPackagesHoldingPermissions(String[] permissions, int flags) {
            return mHostPackageManager.getPackagesHoldingPermissions(permissions, flags);
        }

        @Override
        public int checkPermission(String permName, String packageName) {
            return mHostPackageManager.checkPermission(permName, packageName);
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public boolean isPermissionRevokedByPolicy(@NonNull String s, @NonNull String s1) {
            return mHostPackageManager.isPermissionRevokedByPolicy(s, s1);
        }

        @Override
        public boolean addPermission(PermissionInfo permissionInfo) {
            return mHostPackageManager.addPermission(permissionInfo);
        }

        @Override
        public boolean addPermissionAsync(PermissionInfo permissionInfo) {
            return mHostPackageManager.addPermissionAsync(permissionInfo);
        }

        @Override
        public void removePermission(String s) {
            mHostPackageManager.removePermission(s);
        }

        @Override
        public int checkSignatures(String pkg1, String pkg2) {
            return mHostPackageManager.checkPermission(pkg1, pkg2);
        }

        @Override
        public int checkSignatures(int uid1, int uid2) {
            return mHostPackageManager.checkSignatures(uid1, uid2);
        }

        @Nullable
        @Override
        public String[] getPackagesForUid(int uid) {
            return mHostPackageManager.getPackagesForUid(uid);
        }

        @Nullable
        @Override
        public String getNameForUid(int uid) {
            return mHostPackageManager.getNameForUid(uid);
        }

        @Override
        public List<ApplicationInfo> getInstalledApplications(int flags) {
            return mHostPackageManager.getInstalledApplications(flags);
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public boolean isInstantApp() {
            return mHostPackageManager.isInstantApp();
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public boolean isInstantApp(String pkgName) {
            return mHostPackageManager.isInstantApp(pkgName);
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public int getInstantAppCookieMaxBytes() {
            return mHostPackageManager.getInstantAppCookieMaxBytes();
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public byte[] getInstantAppCookie() {
            return mHostPackageManager.getInstantAppCookie();
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void clearInstantAppCookie() {
            mHostPackageManager.clearInstantAppCookie();
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void updateInstantAppCookie(byte[] bytes) {
            mHostPackageManager.updateInstantAppCookie(bytes);
        }

        @Override
        public String[] getSystemSharedLibraryNames() {
            return mHostPackageManager.getSystemSharedLibraryNames();
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public List<SharedLibraryInfo> getSharedLibraries(int i) {
            return mHostPackageManager.getSharedLibraries(i);
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public ChangedPackages getChangedPackages(int i) {
            return mHostPackageManager.getChangedPackages(i);
        }

        @Override
        public FeatureInfo[] getSystemAvailableFeatures() {
            return mHostPackageManager.getSystemAvailableFeatures();
        }

        @Override
        public boolean hasSystemFeature(String name) {
            return mHostPackageManager.hasSystemFeature(name);
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public boolean hasSystemFeature(String name, int flags) {
            return mHostPackageManager.hasSystemFeature(name, flags);
        }

        @Override
        public ResolveInfo resolveActivity(Intent intent, int i) {
            ResolveInfo resolveInfo = mPluginManager.resolveActivity(intent, i);
            if (resolveInfo != null) {
                return resolveInfo;
            }
            return mHostPackageManager.resolveActivity(intent, i);
        }

        @Override
        public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
            ComponentName componentName = intent.getComponent();
            if (componentName == null) {
                //TODO 什么一说
                if (intent.getSelector() != null) {
                    intent = intent.getSelector();
                    componentName = intent.getComponent();
                }
            }

            if (componentName != null) {
                LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(componentName);
                if (loadedPlugin != null) {
                    ActivityInfo activityInfo = loadedPlugin.getActivityInfo(componentName);
                    if (activityInfo != null) {
                        ResolveInfo resolveInfo = new ResolveInfo();
                        resolveInfo.activityInfo = activityInfo;
                        return Arrays.asList(resolveInfo);
                    }
                }
            }

            //plugin all
            List<ResolveInfo> all = new ArrayList<>();
            List<ResolveInfo> pluginResolveInfos = mPluginManager.queryIntentActivities(intent, flags);
            if (pluginResolveInfos != null && pluginResolveInfos.size() > 0) {
                all.addAll(pluginResolveInfos);
            }

            //host
            List<ResolveInfo> hostResolveInfos = mHostPackageManager.queryIntentActivities(intent, flags);
            if (hostResolveInfos != null && hostResolveInfos.size() > 0) {
                all.addAll(hostResolveInfos);
            }

            return all;
        }

        @Override
        public List<ResolveInfo> queryIntentActivityOptions(ComponentName componentName, Intent[] intents, Intent intent, int i) {
            return mHostPackageManager.queryIntentActivityOptions(componentName, intents, intent, i);
        }

        @Override
        public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags) {
            ComponentName componentName = intent.getComponent();
            if (componentName == null) {
                if (intent.getSelector() != null) {
                    Intent selector = intent.getSelector();
                    componentName = selector.getComponent();
                }
            }

            if (componentName != null) {
                LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(componentName);
                if (loadedPlugin != null) {
                    ActivityInfo receiverInfo = loadedPlugin.getReceiverInfo(componentName);
                    if (receiverInfo != null) {
                        ResolveInfo resolveInfo = new ResolveInfo();
                        resolveInfo.activityInfo = receiverInfo;
                        return Arrays.asList(resolveInfo);
                    }
                }
            }


            List<ResolveInfo> all = new ArrayList<>();

            List<ResolveInfo> pluginResolveInfos = mPluginManager.queryBroadcastReceivers(intent, flags);
            if (pluginResolveInfos != null && pluginResolveInfos.size() > 0) {
                all.addAll(pluginResolveInfos);
            }

            List<ResolveInfo> hostResolveInfos = mHostPackageManager.queryBroadcastReceivers(intent, flags);
            if (hostResolveInfos != null && hostResolveInfos.size() > 0) {
                all.addAll(hostResolveInfos);
            }
            return all;
        }

        @Override
        public ResolveInfo resolveService(Intent intent, int flags) {
            ResolveInfo resolveInfo = mPluginManager.resolveService(intent, flags);
            if (resolveInfo != null) {
                return resolveInfo;
            }

            return mHostPackageManager.resolveService(intent, flags);
        }

        @Override
        public List<ResolveInfo> queryIntentServices(Intent intent, int flags) {
            ComponentName component = intent.getComponent();
            if (null == component) {
                if (intent.getSelector() != null) {
                    intent = intent.getSelector();
                    component = intent.getComponent();
                }
            }

            if (null != component) {
                LoadedPlugin plugin = mPluginManager.getLoadedPlugin(component);
                if (null != plugin) {
                    ServiceInfo serviceInfo = plugin.getServiceInfo(component);
                    if (serviceInfo != null) {
                        ResolveInfo resolveInfo = new ResolveInfo();
                        resolveInfo.serviceInfo = serviceInfo;
                        return Arrays.asList(resolveInfo);
                    }
                }
            }

            List<ResolveInfo> all = new ArrayList<ResolveInfo>();

            List<ResolveInfo> pluginResolveInfos = mPluginManager.queryIntentServices(intent, flags);
            if (null != pluginResolveInfos && pluginResolveInfos.size() > 0) {
                all.addAll(pluginResolveInfos);
            }

            List<ResolveInfo> hostResolveInfos = this.mHostPackageManager.queryIntentServices(intent, flags);
            if (null != hostResolveInfos && hostResolveInfos.size() > 0) {
                all.addAll(hostResolveInfos);
            }

            return all;
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public List<ResolveInfo> queryIntentContentProviders(Intent intent, int flags) {
            return mHostPackageManager.queryIntentContentProviders(intent, flags);
        }

        @Override
        public ProviderInfo resolveContentProvider(String packageName, int flags) {
            ProviderInfo providerInfo = mPluginManager.resolveContentProvider(packageName, flags);
            if (null != providerInfo) {
                return providerInfo;
            }
            return mHostPackageManager.resolveContentProvider(packageName, flags);
        }

        @Override
        public List<ProviderInfo> queryContentProviders(String processName, int uid, int flags) {
            return mHostPackageManager.queryContentProviders(processName, uid, flags);
        }

        @Override
        public InstrumentationInfo getInstrumentationInfo(ComponentName componentName, int flags) throws NameNotFoundException {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(componentName);
            if (loadedPlugin != null) {
                return loadedPlugin.mInstrumentationInfos.get(componentName);
            }
            return mHostPackageManager.getInstrumentationInfo(componentName, flags);
        }

        @Override
        public List<InstrumentationInfo> queryInstrumentation(String targetPackage, int flags) {
            return this.mHostPackageManager.queryInstrumentation(targetPackage, flags);
        }

        @Override
        public Drawable getDrawable(String packageName, @DrawableRes int resid, ApplicationInfo applicationInfo) {
            LoadedPlugin plugin = mPluginManager.getLoadedPlugin(packageName);
            if (null != plugin) {
                return plugin.mResources.getDrawable(resid);
            }

            return this.mHostPackageManager.getDrawable(packageName, resid, applicationInfo);
        }

        @Override
        public Drawable getActivityIcon(ComponentName component) throws NameNotFoundException {
            LoadedPlugin plugin = mPluginManager.getLoadedPlugin(component);
            if (null != plugin) {
                return plugin.mResources.getDrawable(plugin.mActivityInfos.get(component).icon);
            }

            return this.mHostPackageManager.getActivityIcon(component);
        }

        @Override
        public Drawable getActivityIcon(Intent intent) throws NameNotFoundException {
            ResolveInfo ri = mPluginManager.resolveActivity(intent);
            if (null != ri) {
                LoadedPlugin plugin = mPluginManager.getLoadedPlugin(ri.resolvePackageName);
                return plugin.mResources.getDrawable(ri.activityInfo.icon);
            }

            return this.mHostPackageManager.getActivityIcon(intent);
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT_WATCH)
        @Override
        public Drawable getActivityBanner(ComponentName componentName) throws NameNotFoundException {
            LoadedPlugin plugin = mPluginManager.getLoadedPlugin(componentName);
            if (null != plugin) {
                return plugin.mResources.getDrawable(plugin.mActivityInfos.get(componentName).banner);
            }

            return this.mHostPackageManager.getActivityBanner(componentName);
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT_WATCH)
        @Override
        public Drawable getActivityBanner(Intent intent) throws NameNotFoundException {
            ResolveInfo ri = mPluginManager.resolveActivity(intent);
            if (null != ri) {
                LoadedPlugin plugin = mPluginManager.getLoadedPlugin(ri.resolvePackageName);
                return plugin.mResources.getDrawable(ri.activityInfo.banner);
            }

            return this.mHostPackageManager.getActivityBanner(intent);
        }

        @Override
        public Drawable getDefaultActivityIcon() {
            return this.mHostPackageManager.getDefaultActivityIcon();
        }

        @Override
        public Drawable getApplicationIcon(ApplicationInfo info) {
            LoadedPlugin plugin = mPluginManager.getLoadedPlugin(info.packageName);
            if (null != plugin) {
                return plugin.mResources.getDrawable(info.icon);
            }

            return this.mHostPackageManager.getApplicationIcon(info);
        }

        @Override
        public Drawable getApplicationIcon(String packageName) throws NameNotFoundException {
            LoadedPlugin plugin = mPluginManager.getLoadedPlugin(packageName);
            if (null != plugin) {
                return plugin.mResources.getDrawable(plugin.mPackage.applicationInfo.icon);
            }

            return this.mHostPackageManager.getApplicationIcon(packageName);
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT_WATCH)
        @Override
        public Drawable getApplicationBanner(ApplicationInfo info) {
            LoadedPlugin plugin = mPluginManager.getLoadedPlugin(info.packageName);
            if (null != plugin) {
                return plugin.mResources.getDrawable(info.banner);
            }

            return this.mHostPackageManager.getApplicationBanner(info);
        }


        @RequiresApi(api = Build.VERSION_CODES.KITKAT_WATCH)
        @Override
        public Drawable getApplicationBanner(String packageName) throws NameNotFoundException {
            LoadedPlugin plugin = mPluginManager.getLoadedPlugin(packageName);
            if (null != plugin) {
                return plugin.mResources.getDrawable(plugin.mPackage.applicationInfo.banner);
            }

            return this.mHostPackageManager.getApplicationBanner(packageName);
        }

        @Override
        public Drawable getActivityLogo(ComponentName componentName) throws NameNotFoundException {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(componentName);
            if (loadedPlugin != null) {
                return loadedPlugin.mResources.getDrawable(loadedPlugin.mActivityInfos.get(componentName).logo);
            }

            return mHostPackageManager.getActivityLogo(componentName);
        }

        @Override
        public Drawable getActivityLogo(Intent intent) throws NameNotFoundException {
            ResolveInfo resolveInfo = mPluginManager.resolveActivity(intent);
            if (resolveInfo != null) {
                LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(resolveInfo.resolvePackageName);
                return loadedPlugin.mResources.getDrawable(resolveInfo.activityInfo.logo);
            }
            return mHostPackageManager.getActivityLogo(intent);
        }

        @Override
        public Drawable getApplicationLogo(ApplicationInfo info) {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(info.packageName);
            if (loadedPlugin != null) {
                return loadedPlugin.mResources.getDrawable(info.logo != 0 ? info.logo : android.R.drawable.sym_def_app_icon);
            }
            return mHostPackageManager.getApplicationLogo(info);
        }

        @Override
        public Drawable getApplicationLogo(String packageName) throws NameNotFoundException {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(packageName);
            if (loadedPlugin != null) {
                int drawRes = loadedPlugin.mPackage.applicationInfo.logo != 0 ? loadedPlugin.mPackage.applicationInfo.logo : android.R.drawable.sym_def_app_icon;
                return loadedPlugin.mResources.getDrawable(drawRes);
            }
            return mHostPackageManager.getApplicationLogo(packageName);
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public Drawable getUserBadgedIcon(Drawable drawable, UserHandle userHandle) {
            return mHostPackageManager.getUserBadgedIcon(drawable, userHandle);
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public Drawable getUserBadgedDrawableForDensity(Drawable drawable, UserHandle userHandle, Rect rect, int i) {
            return mHostPackageManager.getUserBadgedDrawableForDensity(drawable, userHandle, rect, i);
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public CharSequence getUserBadgedLabel(CharSequence charSequence, UserHandle userHandle) {
            return mHostPackageManager.getUserBadgedLabel(charSequence, userHandle);
        }

        @Override
        public CharSequence getText(String packageName, @StringRes int resId, ApplicationInfo applicationInfo) {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(packageName);
            if (loadedPlugin != null) {
                try {
                    return loadedPlugin.mResources.getText(resId);
                } catch (Resources.NotFoundException e) {

                }
            }
            return mHostPackageManager.getText(packageName, resId, applicationInfo);
        }

        @Override
        public XmlResourceParser getXml(String packageName, @XmlRes int resId, ApplicationInfo applicationInfo) {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(packageName);
            if (loadedPlugin != null) {
                try {
                    return loadedPlugin.mResources.getXml(resId);
                } catch (Resources.NotFoundException e) {

                }
            }
            return mHostPackageManager.getXml(packageName, resId, applicationInfo);
        }

        @Override
        public CharSequence getApplicationLabel(ApplicationInfo applicationInfo) {
            LoadedPlugin plugin = mPluginManager.getLoadedPlugin(applicationInfo.packageName);
            if (plugin != null) {
                try {
                    return plugin.mResources.getText(applicationInfo.labelRes);
                } catch (Resources.NotFoundException e) {

                }
            }

            return mHostPackageManager.getApplicationLabel(applicationInfo);
        }

        @Override
        public Resources getResourcesForActivity(ComponentName componentName) throws NameNotFoundException {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(componentName);
            if (loadedPlugin != null) {
                return loadedPlugin.mResources;
            }
            return mHostPackageManager.getResourcesForActivity(componentName);
        }

        @Override
        public Resources getResourcesForApplication(ApplicationInfo applicationInfo) throws NameNotFoundException {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(applicationInfo.packageName);
            if (loadedPlugin != null) {
                return loadedPlugin.mResources;
            }
            return mHostPackageManager.getResourcesForApplication(applicationInfo);
        }

        @Override
        public Resources getResourcesForApplication(String packageName) throws NameNotFoundException {
            LoadedPlugin plugin = mPluginManager.getLoadedPlugin(packageName);
            if (plugin != null) {
                return plugin.mResources;
            }

            return mHostPackageManager.getResourcesForApplication(packageName);
        }

        @Override
        public void verifyPendingInstall(int id, int verificationCode) {
            mHostPackageManager.verifyPendingInstall(id, verificationCode);
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
        @Override
        public void extendVerificationTimeout(int id, int verificationCodeAtTimeout, long millisecondsToDelay) {
            mHostPackageManager.extendVerificationTimeout(id, verificationCodeAtTimeout, millisecondsToDelay);
        }

        @Override
        public void setInstallerPackageName(String targetPackage, String installPackageName) {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(targetPackage);
            if (loadedPlugin != null) {
                return;
            }
            mHostPackageManager.setInstallerPackageName(targetPackage, installPackageName);
        }

        @Override
        public String getInstallerPackageName(String packageName) {
            LoadedPlugin loadedPlugin = mPluginManager.getLoadedPlugin(packageName);
            if (loadedPlugin != null) {
                return mHostContext.getPackageName();
            }
            return mHostPackageManager.getInstallerPackageName(packageName);
        }

        @Override
        public void addPackageToPreferred(String packageName) {
            mHostPackageManager.addPackageToPreferred(packageName);
        }

        @Override
        public void removePackageFromPreferred(String packageName) {
            mHostPackageManager.removePackageFromPreferred(packageName);
        }

        @Override
        public List<PackageInfo> getPreferredPackages(int flags) {
            return mHostPackageManager.getPreferredPackages(flags);
        }

        @Override
        public void addPreferredActivity(IntentFilter intentFilter, int match, ComponentName[] componentNames, ComponentName activity) {
            mHostPackageManager.addPreferredActivity(intentFilter, match, componentNames, activity);
        }

        @Override
        public void clearPackagePreferredActivities(String packageName) {
            mHostPackageManager.clearPackagePreferredActivities(packageName);
        }

        @Override
        public int getPreferredActivities(@NonNull List<IntentFilter> outFilters, @NonNull List<ComponentName> outActivities, String packageName) {
            return mHostPackageManager.getPreferredActivities(outFilters, outActivities, packageName);
        }

        @Override
        public void setComponentEnabledSetting(ComponentName componentName, int newState, int flags) {
            mHostPackageManager.setComponentEnabledSetting(componentName, newState, flags);
        }

        @Override
        public int getComponentEnabledSetting(ComponentName componentName) {
            return mHostPackageManager.getComponentEnabledSetting(componentName);
        }

        @Override
        public void setApplicationEnabledSetting(String packageName, int newState, int flags) {
            mHostPackageManager.setApplicationEnabledSetting(packageName, newState, flags);
        }

        @Override
        public int getApplicationEnabledSetting(String packageName) {
            return mHostPackageManager.getApplicationEnabledSetting(packageName);
        }

        @Override
        public boolean isSafeMode() {
            return mHostPackageManager.isSafeMode();
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void setApplicationCategoryHint(String s, int i) {
            mHostPackageManager.setApplicationCategoryHint(s, i);
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @NonNull
        @Override
        public PackageInstaller getPackageInstaller() {
            return mHostPackageManager.getPackageInstaller();
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public boolean canRequestPackageInstalls() {
            return mHostPackageManager.canRequestPackageInstalls();
        }
    }

}
