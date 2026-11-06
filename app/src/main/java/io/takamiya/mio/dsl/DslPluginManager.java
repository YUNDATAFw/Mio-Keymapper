package io.takamiya.mio.dsl;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.fsy.plugin.kore.library.PluginCallback;
import cn.fsy.plugin.kore.library.KoreEngine;
import cn.fsy.plugin.kore.library.KoreException;
import cn.fsy.plugin.kore.library.config.CommonConfig;
import cn.fsy.plugin.kore.library.plugin.PluginItem;
import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.dsl.bridge.MioEngFunBridge;
import io.takamiya.mio.ws.WsEngineHolder;

/**
 * DSL 插件管理器（Kore 版本）。
 * 负责插件的安装、打开、关闭、卸载，以及服务线程的生命周期管理。
 */
public class DslPluginManager {

    private static final String TAG = "DslPluginManager";

    private static volatile DslPluginManager sInstance;

    private final Context context;

    /** packageName → DslPluginHandle */
    private final Map<String, DslPluginHandle> handles = new ConcurrentHashMap<>();

    public DslPluginManager(Context context) {
        this.context = context.getApplicationContext();
        sInstance = this;
    }

    /** 获取当前活动的管理器实例（WS 引擎事件桥接等静态入口使用）。 */
    public static DslPluginManager get() {
        return sInstance;
    }

    // ==================== 安装 ====================

    /**
     * 从 assets 安装插件
     * @param assetName assets 中的 ZIP 文件名
     * @param callback 安装回调
     */
    public void installFromAssets(String assetName, PluginCallback callback) {
        new Thread(() -> {
            try {
                File zip = copyAssetToFilesDir(assetName);
                KoreEngine.install(zip.getAbsolutePath(), callback);
            } catch (Exception e) {
                AppLogger.e(TAG, "installFromAssets failed: " + e.getMessage(), e);
                if (callback != null) {
                    callback.onFailure(new KoreException(KoreException.ErrorCode.INSTALL_FAILED, e.getMessage()));
                }
            }
        }, "kore-install-asset").start();
    }

    /**
     * 从文件路径安装插件
     * @param zipPath ZIP 文件完整路径（.kore.zip 格式）
     * @param callback 安装回调
     */
    public void installFromFile(String zipPath, PluginCallback callback) {
        new Thread(() -> {
            KoreEngine.install(zipPath, callback);
        }, "kore-install-file").start();
    }

    // ==================== 打开 ====================

    /**
     * 打开插件，返回 DslPluginHandle
     * @param packageName 插件包名
     * @return DslPluginHandle，失败返回 null
     */
    public DslPluginHandle open(String packageName) {
        try {
            cn.fsy.plugin.kore.library.KoreEngine.PluginHandle koreHandle = KoreEngine.open(packageName);
            // 关键：打开后必须显式授权基础权限（Kore 所有权限需显式 grant）
            grantDefaultPermissions(koreHandle);
            DslPluginHandle handle = new DslPluginHandle(context, packageName, koreHandle);
            handles.put(packageName, handle);
            AppLogger.i(TAG, "open: " + packageName);
            return handle;
        } catch (KoreException e) {
            AppLogger.e(TAG, "open failed: " + packageName + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * 授予插件运行最小权限集（打开后立即调用）
     */
    private void grantDefaultPermissions(cn.fsy.plugin.kore.library.KoreEngine.PluginHandle handle) {
        String[] perms = {
                "NETWORK_ACCESS",
                "SHARED_DATA_READ",
                "SHARED_DATA_WRITE",
                "HOST_DATA_READ",
                "HOST_DATA_WRITE"
        };
        for (String p : perms) {
            try {
                handle.permissionGuard().grant(p);
            } catch (Exception e) {
                AppLogger.w(TAG, "grant default perm failed: " + p + " - " + e.getMessage());
            }
        }
        // 额外：读取 plugin.json 声明的权限并授予
        grantDeclaredPermissions(handle);
    }

    /**
     * 读取 plugin.json 中的 permissions 并逐个授予
     */
    private void grantDeclaredPermissions(cn.fsy.plugin.kore.library.KoreEngine.PluginHandle handle) {
        try {
            File cfg = new File(new File(context.getFilesDir(), "kore-plugins"), handle.packageName() + "/plugin.json");
            if (!cfg.isFile()) return;
            try (InputStream in = new FileInputStream(cfg)) {
                byte[] buf = new byte[(int) cfg.length()];
                int off = 0;
                while (off < buf.length) {
                    int r = in.read(buf, off, buf.length - off);
                    if (r < 0) break;
                    off += r;
                }
                JSONObject plugin = new JSONObject(new String(buf, StandardCharsets.UTF_8)).optJSONObject("plugin");
                JSONArray arr = plugin != null ? plugin.optJSONArray("permissions") : null;
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String name = arr.optString(i, "");
                        if (!name.isEmpty()) {
                            try { handle.permissionGuard().grant(name); } catch (Exception ignore) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "grantDeclaredPermissions failed: " + e.getMessage());
        }
    }

    // ==================== 关闭 ====================

    /**
     * 关闭插件（停止所有服务线程，释放句柄）
     */
    public void close(String packageName) {
        DslPluginHandle handle = handles.remove(packageName);
        if (handle != null) {
            handle.close();
            AppLogger.i(TAG, "close: " + packageName);
        }
    }

    // ==================== 卸载 ====================

    /**
     * 卸载插件（先回调 onUninstall 钩子，再关闭句柄并走引擎卸载）
     */
    public void uninstall(String packageName, PluginCallback callback) {
        DslPluginHandle handle = handles.get(packageName);
        if (handle != null) {
            handle.callIfExported("onUninstall");
        }
        close(packageName);
        KoreEngine.uninstall(packageName, callback);
        AppLogger.i(TAG, "uninstall: " + packageName);
    }

    // ==================== 启用/禁用 ====================

    public void enablePlugin(String packageName) {
        try {
            KoreEngine.enablePlugin(packageName);
        } catch (Exception e) {
            AppLogger.e(TAG, "enablePlugin failed: " + packageName + " - " + e.getMessage());
        }
    }

    public void disablePlugin(String packageName) {
        try {
            KoreEngine.disablePlugin(packageName);
        } catch (Exception e) {
            AppLogger.e(TAG, "disablePlugin failed: " + packageName + " - " + e.getMessage());
        }
    }

    public boolean isEnabled(String packageName) {
        try {
            return KoreEngine.isEnabled(packageName);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 查询 ====================

    /**
     * 获取已打开的插件句柄
     */
    public DslPluginHandle getHandle(String packageName) {
        return handles.get(packageName);
    }

    /**
     * 获取所有已打开的句柄
     */
    public Map<String, DslPluginHandle> getAllHandles() {
        return handles;
    }

    /**
     * 获取已安装的插件包名列表（新目录结构：filesDir/kore-plugins/）
     */
    public List<String> getInstalledPackages() {
        List<String> result = new ArrayList<>();
        File root = new File(context.getFilesDir(), "kore-plugins");
        if (root.isDirectory()) {
            File[] children = root.listFiles();
            if (children != null) {
                for (File c : children) {
                    if (c.isDirectory() && new File(c, "plugin.json").isFile()) {
                        result.add(c.getName());
                    }
                }
            }
        }
        return result;
    }

    /**
     * 获取所有 SHORTCUT_KEY 类型的已注册快捷键
     * 从 MioEngFunBridge.registeredShortcuts 读取（由 DSL 插件通过 keyInit 注册）
     */
    public List<io.takamiya.mio.bean.ControlActionItem> getAllShortcutKeyActions() {
        List<io.takamiya.mio.bean.ControlActionItem> result = new ArrayList<>();
        MioEngFunBridge bridge = MioBridgeRegistry.getMioEngFunBridge();
        if (bridge != null) {
            result.addAll(bridge.getRegisteredShortcuts().values());
        }
        return result;
    }

    // ==================== 事件分发 ====================

    /**
     * 解析 actionId 所属插件包名（onKeyInit/onPhysicalKeyInit 登记）。
     * 返回 null 时按旧行为广播给所有插件（未注册/旧数据兼容）。
     */
    private static String resolveOwnerPackage(String actionId) {
        MioEngFunBridge bridge = MioBridgeRegistry.getMioEngFunBridge();
        return bridge != null ? bridge.getShortcutPackage(actionId) : null;
    }

    /**
     * 向已打开插件分发触发器按下事件。
     * 按注册包名过滤目标插件——两个插件可声明同名 SHORTCUT_KEY 服务
     * （如复制版插件），广播式路由会跨插件串扰；包名未知时回退广播。
     * @param actionId 触发器 ID
     */
    public void dispatchTriggerDown(String actionId) {
        String pkg = resolveOwnerPackage(actionId);
        for (DslPluginHandle handle : handles.values()) {
            if (pkg != null && !pkg.equals(handle.getPackageName())) continue;
            handle.onTriggerDown(actionId);
        }
    }

    /**
     * 向已打开插件分发触发器松开事件（路由规则同 {@link #dispatchTriggerDown}）
     */
    public void dispatchTriggerUp(String actionId) {
        String pkg = resolveOwnerPackage(actionId);
        for (DslPluginHandle handle : handles.values()) {
            if (pkg != null && !pkg.equals(handle.getPackageName())) continue;
            handle.onTriggerUp(actionId);
        }
    }

    /**
     * 清除指定快捷键的触发器状态（快捷键关闭时调用；路由规则同 {@link #dispatchTriggerDown}）
     */
    public void dispatchClearTrigger(String actionId) {
        String pkg = resolveOwnerPackage(actionId);
        for (DslPluginHandle handle : handles.values()) {
            if (pkg != null && !pkg.equals(handle.getPackageName())) continue;
            handle.clearTrigger(actionId);
        }
    }

    /**
     * 向所有已打开插件分发按键事件（onKeyOn/onKeyOff；未导出的插件静默跳过）
     */
    public void dispatchKeyEvent(String keyCode, boolean down) {
        String eventName = down ? "onKeyOn" : "onKeyOff";
        for (DslPluginHandle handle : handles.values()) {
            handle.callIfExported(eventName, keyCode);
        }
    }

    /**
     * 向所有已打开插件分发手柄按钮事件
     */
    public void dispatchButtonEvent(String button, boolean pressed) {
        String eventName = pressed ? "onButtonPress" : "onButtonRelease";
        for (DslPluginHandle handle : handles.values()) {
            handle.callFunction(eventName, button);
        }
    }

    /**
     * 向所有已打开插件分发扳机事件
     */
    public void dispatchTriggerAxis(String axis, int value) {
        for (DslPluginHandle handle : handles.values()) {
            handle.callFunction("onTriggerAxis", axis, value);
        }
    }

    // ==================== WS 事件桥 ====================

    /**
     * 注册为 WS 引擎事件转发器：收到一个 WS 游戏事件时广播给所有导出了
     * {@code on<事件名>} 入口的插件服务。
     */
    public void attachWsEventListener() {
        WsEngineHolder.get().addWsEventListener(wsEventListenerFn);
    }

    public void detachWsEventListener() {
        WsEngineHolder.get().removeWsEventListener(wsEventListenerFn);
    }

    private final WsEngineHolder.WsEventListener wsEventListenerFn = this::onWsEvent;

    private void onWsEvent(String eventName, String rawJson) {
        broadcastWsEvent(eventName, rawJson);
    }

    /**
     * 广播一个 WS 事件给所有打开插件（各 handle 内部按导出入口过滤）。
     */
    public void broadcastWsEvent(String eventName, String rawJson) {
        for (DslPluginHandle handle : handles.values()) {
            handle.broadcastWsEvent(eventName, rawJson);
        }
    }

    /**
     * 功能开关：定向投递给注册该 feature 的归属服务。
     * @return 是否已投递
     */
    public boolean routeFeatureToggle(String key, boolean enabled) {
        WsFeatureRegistry.Entry entry = WsFeatureRegistry.get(key);
        if (entry == null) return false;
        DslPluginHandle handle = handles.get(entry.packageName);
        if (handle == null) return false;
        handle.enqueueFeatureToggle(entry.serviceName, key, enabled);
        return true;
    }

    // ==================== 关闭所有 ====================

    public void closeAll() {
        for (DslPluginHandle handle : handles.values()) {
            handle.close();
        }
        handles.clear();
    }

    // ==================== 工具 ====================

    private File copyAssetToFilesDir(String assetName) throws IOException {
        File out = new File(context.getFilesDir(), assetName);
        try (InputStream in = context.getAssets().open(assetName);
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
        }
        return out;
    }

    // ==================== 插件权限 ====================

    /**
     * 读取插件 plugin.json 中声明的权限数组（{name, desc} 对象列表）。
     */
    public static JSONArray readDeclaredPermissions(Context ctx, String pluginPackage) {
        File cfg = new File(new File(ctx.getFilesDir(), "kore-plugins"), pluginPackage + "/plugin.json");
        if (!cfg.isFile()) {
            AppLogger.w(TAG, "plugin.json not found: " + cfg.getAbsolutePath());
            return null;
        }
        try (InputStream in = new FileInputStream(cfg)) {
            byte[] buf = new byte[(int) cfg.length()];
            int off = 0;
            while (off < buf.length) {
                int r = in.read(buf, off, buf.length - off);
                if (r < 0) break;
                off += r;
            }
            JSONObject plugin = new JSONObject(new String(buf, StandardCharsets.UTF_8))
                    .optJSONObject("plugin");
            return plugin != null ? plugin.optJSONArray("permissions") : null;
        } catch (Exception e) {
            AppLogger.e(TAG, "readDeclaredPermissions failed: " + pluginPackage + " - " + e.getMessage());
            return null;
        }
    }
}