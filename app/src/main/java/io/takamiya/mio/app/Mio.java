package io.takamiya.mio.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import cn.fsy.plugin.kore.library.KoreEngine;
import cn.fsy.plugin.kore.library.config.PermissionRegistry;
import cn.fsy.plugin.kore.library.config.PermissionDef;
import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.CrashHandler;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.BuildConfig;
import io.takamiya.mio.dsl.MioBridgeRegistry;
import io.takamiya.mio.floating.FloatingWindowService;
import io.takamiya.mio.shizuku.ShizukuHelper;
import io.takamiya.mio.utils.ApkIntegrity;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.RegistryUtils;
import io.takamiya.mio.utils.SecureStrings;
import io.takamiya.mio.utils.ThemeManager;
import io.takamiya.mio.utils.UserToken;

public class Mio extends Application {
    private static final String TAG = "Mio";
    private static Mio INSTANCE;

    public static String APP_ID;
    public static String APP_SECRET;
    public static final int APP_CONFIG_ENGINE = 4;

    public static android.content.Context getAppContext() {
        return INSTANCE;
    }

    public static String getLhrbPublicKeyFingerprint() {
        return SecureStrings.path(94);
    }


    private final AtomicInteger activityCount = new AtomicInteger(0);
    private static boolean clearingData = false;

    public static void setClearingData(boolean value) {
        clearingData = value;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;

        ApkIntegrity.nativeStartIntegrityMonitor();

        o.s.o.l.init(this);
        APP_ID = SecureStrings.appId();
        APP_SECRET = SecureStrings.secret();

        RegistryUtils.init(this);

        ThemeManager.apply();

        UrlBuilder.getInstance().init(SecureStrings.baseUrl());

        CrashHandler.getInstance()
                .setFileNamePrefix("Mio_Crash_")
                .setFileNameSuffix(".log")
                .setAutoCleanDays(30)
                .setDumpDeviceInfo(true)
                .setDumpCauseChain(true)
                .init(this, null, null);

        AppLogger.getInstance()
                .setLocation(AppLogger.LogLocation.INTERNAL_FILES)
                .setStorageMode(AppLogger.StorageMode.BINARY_ONLY)
                .setMaxKeepDays(7)
                .setWriteToLogcat(BuildConfig.DEBUG)
                .setWriteToFile(true)
                .setMinLogLevel(BuildConfig.DEBUG ? AppLogger.DEBUG : AppLogger.INFO)
                .setAsyncMode(true)
                .setBufferSize(4096)
                .init(this);

        // Kore 插件引擎初始化
        KoreEngine.init(getApplicationContext());
        
        // 注册宿主自有桥接
        MioBridgeRegistry.register(getApplicationContext());
        
        // 注册自定义权限
        registerCustomPermissions();
        
        AppLogger.i(TAG, "Kore engine initialized, Mio bridges registered");

        computeIntegrityHashesAsync();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                activityCount.incrementAndGet();
                ApkIntegrity.nativeEnforceGate(activity);
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                if (activityCount.decrementAndGet() == 0) {
                    if (clearingData) {
                        AppLogger.d(TAG, "Data clearing in progress, skipping token cleanup");
                        return;
                    }
                    AppLogger.d(TAG, "App exiting, checking token validity");


                    boolean userTokenExpired;
                    boolean appTokenExpired;
                    try {
                        userTokenExpired = !UserToken.isLoggedIn();
                    } catch (Exception e) {
                        AppLogger.e(TAG, "UserToken check failed, treating as expired: " + e.getMessage());
                        userTokenExpired = true;
                    }
                    try {
                        appTokenExpired = !AppToken.isLoggedIn();
                    } catch (Exception e) {
                        AppLogger.e(TAG, "AppToken check failed, treating as expired: " + e.getMessage());
                        appTokenExpired = true;
                    }

                    if (userTokenExpired) {
                        UserToken.clear();
                        AppLogger.d(TAG, "UserToken expired or corrupted, cleared");
                    } else {
                        AppLogger.d(TAG, "UserToken still valid, keeping");
                    }

                    if (appTokenExpired) {
                        AppToken.clear();
                        AppLogger.d(TAG, "AppToken expired or corrupted, cleared");
                    } else {
                        AppLogger.d(TAG, "AppToken still valid, keeping");
                    }

                    if (!FloatingWindowService.isRunning()) {
                        ShizukuHelper.getInstance().destroy();
                    }
                    AppLogger.d(TAG, "All activities destroyed"
                            + (FloatingWindowService.isRunning() ? ", floating window active" : ", ShizukuHelper destroyed"));
                }
            }

            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
        });
    }

    public static Mio getInstance() {
        return INSTANCE;
    }

    private void computeIntegrityHashesAsync() {
        new Thread(() -> {
            try {
                String apk = getApplicationInfo().sourceDir;
                AppLogger.d(TAG, "APK integrity: " + ApkIntegrity.apkIntegrityHash(apk));
                ApkIntegrity.dexIntegrityHashList(apk);
            } catch (Exception e) {
                AppLogger.w(TAG, "compute integrity hashes failed: " + e.getMessage());
            }
        }).start();
    }

    private void registerCustomPermissions() {
        cn.fsy.plugin.kore.library.config.PermissionRegistry.registerCustomPermission(
            cn.fsy.plugin.kore.library.config.PermissionDef.builder("MIO_FLOATING_WINDOW", "io.takamiya.mio.perms")
                .level(cn.fsy.plugin.kore.library.config.PermissionRegistry.LEVEL_DANGEROUS)
                .desc("悬浮窗权限")
                .hostIdPrefix("Mio.floating")
                .build());
        cn.fsy.plugin.kore.library.config.PermissionRegistry.registerCustomPermission(
            cn.fsy.plugin.kore.library.config.PermissionDef.builder("MIO_INPUT_MONITOR", "io.takamiya.mio.perms")
                .level(cn.fsy.plugin.kore.library.config.PermissionRegistry.LEVEL_DANGEROUS)
                .desc("外接键鼠监听权限")
                .hostIdPrefix("Mio.inputMonitor")
                .build());
        cn.fsy.plugin.kore.library.config.PermissionRegistry.registerCustomPermission(
            cn.fsy.plugin.kore.library.config.PermissionDef.builder("MIO_GAMEPAD", "io.takamiya.mio.perms")
                .level(cn.fsy.plugin.kore.library.config.PermissionRegistry.LEVEL_DANGEROUS)
                .desc("虚拟手柄权限")
                .hostIdPrefix("Mio.gamepad")
                .build());
    }
}
