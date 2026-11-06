package io.takamiya.mio.dsl;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.core.app.NotificationCompat;
import cn.fsy.plugin.kore.library.KoreEngine;
import cn.fsy.plugin.kore.library.KoreException;
import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.R;
import io.takamiya.mio.bean.ControlActionItem;
import io.takamiya.mio.config.TriggerModel;
import io.takamiya.mio.dsl.bridge.MioEngFunBridge;
import cn.fsy.plugin.kore.library.config.CommonConfig;

/**
 * 单个 Kore 插件的句柄。
 * 封装 KoreEngine.PluginHandle，管理插件的 service 线程生命周期。
 *
 * 每个 SHORTCUT_KEY 类型的 service 拥有独立线程：
 * - 启动时调用 keyInit() 注册快捷键
 * - 运行时等待触发器事件，触发时调用 onTriggerDown()
 * - 停止时中断线程
 *
 * 多 SHORTCUT_KEY 服务并存时，各服务模块导出同名入口（keyInit/onTriggerDown 等）
 * 会在全局函数索引中互相覆盖，因此调用统一经 {@link #callExported} 按
 * {@code <服务名>__<函数名>} 前缀作用域解析（服务名 = 模块文件名）。
 */
public class DslPluginHandle {

    private static final String TAG = "DslPluginHandle";

    private static final String DSL_ERROR_CHANNEL_ID = "dsl_plugin_error";
    private static final int DSL_ERROR_NOTE_BASE = 2000;

    /** 相同错误的通知节流时间（毫秒），避免重复错误刷屏 */
    private static final long ERROR_NOTIFY_THROTTLE_MS = 5000L;
    private static volatile boolean dslErrorChannelCreated;
    private static final Map<String, Long> lastErrorNotifyAt = new ConcurrentHashMap<>();

    private final Context context;
    private final String packageName;
    private final cn.fsy.plugin.kore.library.KoreEngine.PluginHandle koreHandle;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** serviceName → ServiceThread */
    private final Map<String, ServiceThread> serviceThreads = new ConcurrentHashMap<>();

    /** 已注册的快捷键（由 keyInit 填充） */
    private final List<ControlActionItem> shortcutActions = new ArrayList<>();

    /** 该插件声明的 service 列表 */
    private final List<ServiceEntry> serviceEntries = new ArrayList<>();

    public DslPluginHandle(Context context, String packageName, cn.fsy.plugin.kore.library.KoreEngine.PluginHandle koreHandle) {
        this.context = context;
        this.packageName = packageName;
        this.koreHandle = koreHandle;

        // 读取 plugin.json 中的 service 声明
        loadServiceEntries();
    }

    // ==================== 生命周期 ====================

    /**
     * 启动 MAINSERVICE 入口
     * @return 调用结果
     */
    public Object startMainService() {
        try {
            return koreHandle.call("loading");
        } catch (KoreException e) {
            AppLogger.e(TAG, "startMainService failed: " + packageName + " -> " + describeDslError(e), e);
            notifyDslError("loading", e);
            return null;
        }
    }

    /**
     * 启动指定的 SHORTCUT_KEY 服务
     * @param serviceName 服务名（对应 plugin.json 中 service[].name）
     */
    public void startService(String serviceName) {
        if (closed.get()) return;

        ServiceEntry entry = findServiceEntry(serviceName);
        if (entry == null) {
            AppLogger.w(TAG, "startService: service not found: " + serviceName);
            return;
        }

        if (serviceThreads.containsKey(serviceName)) {
            AppLogger.w(TAG, "startService: already running: " + serviceName);
            return;
        }

        ServiceThread thread = new ServiceThread(serviceName, entry);
        serviceThreads.put(serviceName, thread);
        thread.start();
    }

    /**
     * 停止指定服务
     */
    public void stopService(String serviceName) {
        ServiceThread thread = serviceThreads.remove(serviceName);
        if (thread != null) {
            thread.interrupt();
            WsFeatureRegistry.unregisterService(packageName, serviceName);
        }
    }

    /**
     * 关闭插件，停止所有服务
     */
    public void close() {
        if (closed.getAndSet(true)) return;

        for (ServiceThread thread : serviceThreads.values()) {
            thread.interrupt();
        }
        serviceThreads.clear();
        WsFeatureRegistry.unregisterPackage(packageName);

        MioEngFunBridge bridge = MioBridgeRegistry.getMioEngFunBridge();
        if (bridge != null) {
            bridge.clearPackage(packageName);
        }

        try {
            koreHandle.close();
        } catch (Exception e) {
            AppLogger.e(TAG, "close handle failed: " + packageName, e);
        }
    }

    // ==================== 函数调用 ====================

    /**
     * 调用插件导出函数（安全版本，捕获异常）
     */
    public Object callFunction(String funcName, Object... args) {
        if (closed.get()) return null;

        try {
            return koreHandle.call(funcName, args);
        } catch (KoreException e) {
            AppLogger.e(TAG, "callFunction failed: " + packageName + "." + funcName
                    + " -> " + describeDslError(e), e);
            notifyDslError(funcName, e);
            return null;
        } catch (Exception e) {
            AppLogger.e(TAG, "callFunction unexpected: " + packageName + "." + funcName
                    + " - " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 调用插件可选导出函数：插件未实现该函数时静默忽略（不发错误日志/通知）。
     */
    public Object callIfExported(String funcName, Object... args) {
        if (closed.get()) return null;

        try {
            return koreHandle.call(funcName, args);
        } catch (KoreException e) {
            if (isFunctionNotFound(e)) {
                return null;
            }
            AppLogger.e(TAG, "callIfExported failed: " + packageName + "." + funcName
                    + " -> " + describeDslError(e), e);
            notifyDslError(funcName, e);
            return null;
        } catch (Exception e) {
            AppLogger.e(TAG, "callIfExported unexpected: " + packageName + "." + funcName
                    + " - " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 调用指定服务（serviceName__funcName 作用域）
     */
    public Object callExported(String serviceName, String funcBase, Object... args) {
        if (closed.get()) return null;
        String fullFunc = serviceName + "__" + funcBase;
        return callFunction(fullFunc, args);
    }

    /**
     * 尝试调用可选导出（服务作用域）
     */
    public Object tryCallExported(String funcName, Object... args) {
        if (closed.get()) return null;

        try {
            return koreHandle.call(funcName, args);
        } catch (KoreException e) {
            if (isFunctionNotFound(e)) return null;
            throw e;
        }
    }

    // ==================== 触发器事件 ====================

    /** 触发器按下 */
    public void onTriggerDown(String actionId) {
        try {
            callExported(actionId, "onTriggerDown");
        } catch (Exception e) {
            AppLogger.e(TAG, "onTriggerDown error: " + actionId, e);
        }
    }

    /** 触发器松开 */
    public void onTriggerUp(String actionId) {
        try {
            callExported(actionId, "onTriggerUp");
        } catch (Exception e) {
            AppLogger.e(TAG, "onTriggerUp error: " + actionId, e);
        }
    }

    /** 清除触发器状态 */
    public void clearTrigger(String actionId) {
        try {
            callExported(actionId, "onTriggerClear");
        } catch (Exception e) {
            AppLogger.e(TAG, "clearTrigger error: " + actionId, e);
        }
    }

    // ==================== WS 事件广播 ====================

    public void broadcastWsEvent(String eventName, String rawJson) {
        String funcName = "on" + eventName;
        for (ServiceThread thread : serviceThreads.values()) {
            thread.enqueueWsEvent(funcName, rawJson);
        }
    }

    public void enqueueFeatureToggle(String serviceName, String key, boolean enabled) {
        ServiceThread thread = serviceThreads.get(serviceName);
        if (thread != null) {
            thread.enqueueFeatureToggle(key, enabled);
        }
    }

    // ==================== 权限守卫 ====================

    public cn.fsy.plugin.kore.library.internal.running.PermissionGuard getPermissionGuard() {
        return koreHandle.permissionGuard();
    }

    // ==================== Getter ====================

    public String getPackageName() {
        return packageName;
    }

    public cn.fsy.plugin.kore.library.KoreEngine.PluginHandle getKoreHandle() {
        return koreHandle;
    }

    public List<ControlActionItem> getShortcutActions() {
        return shortcutActions;
    }

    public List<ServiceEntry> getServiceEntries() {
        return serviceEntries;
    }

    public boolean isClosed() {
        return closed.get();
    }

    // ==================== 内部方法 ====================

    private void loadServiceEntries() {
        // 读取 plugin.json 中的 services 数组
        try {
            File cfg = new File(new File(context.getFilesDir(), "kore-plugins"), packageName + "/plugin.json");
            if (!cfg.isFile()) {
                AppLogger.w(TAG, "plugin.json not found for " + packageName);
                return;
            }
            try (InputStream in = new FileInputStream(cfg)) {
                byte[] buf = new byte[(int) cfg.length()];
                int off = 0;
                while (off < buf.length) {
                    int r = in.read(buf, off, buf.length - off);
                    if (r < 0) break;
                    off += r;
                }
                JSONObject plugin = new JSONObject(new String(buf, StandardCharsets.UTF_8)).optJSONObject("plugin");
                JSONArray arr = plugin != null ? plugin.optJSONArray("services") : null;
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject s = arr.optJSONObject(i);
                        if (s == null) continue;
                        String name = s.optString("name", "");
                        String type = s.optString("type", "");
                        if (!name.isEmpty() && !type.isEmpty()) {
                            serviceEntries.add(new ServiceEntry(name, type));
                        }
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "loadServiceEntries failed: " + packageName + " - " + e.getMessage());
        }
    }

    private ServiceEntry findServiceEntry(String name) {
        for (ServiceEntry entry : serviceEntries) {
            if (entry.name.equals(name)) return entry;
        }
        return null;
    }

    // ==================== ServiceThread ====================

    class ServiceThread extends Thread {
        private final String serviceName;
        private final ServiceEntry entry;
        private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
        private volatile boolean interrupted = false;

        ServiceThread(String serviceName, ServiceEntry entry) {
            super("DslService-" + packageName + "-" + serviceName);
            this.serviceName = serviceName;
            this.entry = entry;
            setDaemon(true);
        }

        @Override
        public void run() {
            // 1. 调用 keyInit 注册快捷键
            try {
                Object result = koreHandle.call(serviceName + "__keyInit");
                AppLogger.i(TAG, serviceName + " keyInit result: " + result);
            } catch (KoreException e) {
                AppLogger.e(TAG, serviceName + " keyInit failed: " + describeDslError(e), e);
                notifyDslError(serviceName + "__keyInit", e);
                return;
            } catch (Exception e) {
                AppLogger.e(TAG, serviceName + " keyInit unexpected: " + e.getMessage(), e);
                return;
            }

            // 2. 主循环：处理任务队列
            while (!interrupted && !Thread.currentThread().isInterrupted()) {
                try {
                    Runnable task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) task.run();
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    AppLogger.e(TAG, "service loop error: " + serviceName, e);
                }
            }
        }

        void enqueueWsEvent(String funcName, String rawJson) {
            taskQueue.offer(() -> {
                try {
                    callExported(serviceName, funcName, rawJson);
                } catch (Exception e) {
                    AppLogger.e(TAG, "ws event error: " + funcName, e);
                }
            });
        }

        void enqueueFeatureToggle(String key, boolean enabled) {
            taskQueue.offer(() -> {
                try {
                    callExported(serviceName, "onFeatureToggle", key, enabled);
                } catch (Exception e) {
                    AppLogger.e(TAG, "feature toggle error: " + key, e);
                }
            });
        }
    }

    // ==================== ServiceEntry ====================

    public static class ServiceEntry {
        public final String name;
        public final String type;

        ServiceEntry(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public boolean isShortcutKey() {
            return "SHORTCUT_KEY".equalsIgnoreCase(type);
        }

        public boolean isWs() {
            return "WS".equalsIgnoreCase(type);
        }
    }

    // ==================== 错误处理 ====================

    private void notifyDslError(String funcName, KoreException e) {
        // 实现略...
    }

    private boolean isFunctionNotFound(KoreException e) {
        return e.getErrorCode() == KoreException.ErrorCode.BRIDGE_NOT_FOUND;
    }

    private String describeDslError(KoreException e) {
        return "code=" + e.getErrorCode() + ", msg=" + e.getMessage();
    }

    public boolean isLoaded() {
        return koreHandle.isLoaded();
    }

    // ==================== hasExported ====================

    public boolean hasExported(String serviceName, String funcName) {
        return koreHandle.hasExported(serviceName, funcName);
    }
}