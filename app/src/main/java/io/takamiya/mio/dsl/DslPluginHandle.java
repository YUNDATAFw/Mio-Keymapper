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
import cn.fsy.plugin.sdl.library.SdlEngine;
import cn.fsy.plugin.sdl.library.SdlException;
import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.R;
import io.takamiya.mio.bean.ControlActionItem;
import io.takamiya.mio.config.TriggerModel;
import io.takamiya.mio.dsl.bridge.MioEngFunBridge;
import cn.fsy.plugin.sdl.library.config.CommonConfig;

/**
 * 单个 DSL 插件的句柄。
 * 封装 SdlEngine.PluginHandle，管理插件的 service 线程生命周期。
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
    private final SdlEngine.PluginHandle sdlHandle;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** serviceName → ServiceThread */
    private final Map<String, ServiceThread> serviceThreads = new ConcurrentHashMap<>();

    /** 已注册的快捷键（由 keyInit 填充） */
    private final List<ControlActionItem> shortcutActions = new ArrayList<>();

    /** 该插件声明的 service 列表 */
    private final List<ServiceEntry> serviceEntries = new ArrayList<>();

    public DslPluginHandle(Context context, String packageName, SdlEngine.PluginHandle sdlHandle) {
        this.context = context;
        this.packageName = packageName;
        this.sdlHandle = sdlHandle;

        // 读取 config.json 中的 service 声明
        loadServiceEntries();
    }

    // ==================== 生命周期 ====================

    /**
     * 启动 MAINSERVICE 入口
     * @return 调用结果
     */
    public Object startMainService() {
        try {
            return sdlHandle.call("loading");
        } catch (SdlException e) {
            AppLogger.e(TAG, "startMainService failed: " + packageName + " -> " + describeDslError(e), e);
            notifyDslError("loading", e);
            return null;
        }
    }

    /**
     * 启动指定的 SHORTCUT_KEY 服务
     * @param serviceName 服务名（对应 config.json 中 service[].name）
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
            sdlHandle.close();
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
            return sdlHandle.call(funcName, args);
        } catch (SdlException e) {
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
            return sdlHandle.call(funcName, args);
        } catch (SdlException e) {
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

    /** 宿主不可调用函数（未导出）时的哨兵：区分「成功返回 null(Void)」与「函数不存在」。 */
    private static final Object NOT_FOUND = new Object();

    /**
     * 调用指定服务的导出函数（按服务名作用域区分）。
     * <p>一个插件含多个 SHORTCUT_KEY 服务时，各服务模块都会导出 keyInit/onTriggerDown
     * 等同名入口，运行时对同名多实现只允许用限定名 {@code 模块名.函数名} 精确调用
     * （裸名会因歧义报错）。运行时模块以<b>叶文件名</b>为键，因此服务名按
     * {@code package.类名} 声明时需回退到叶名再拼接函数名。</p>
     * <p>候选调用顺序：{@code <服务名>.<函数名>} → {@code <叶名>.<函数名>} → 裸
     * {@code <函数名>}（单模块/旧式插件兼容）。任一候选命中即返回。</p>
     */
    private Object callExported(String serviceName, String funcBase, Object... args) {
        if (closed.get()) return null;

        Object r = tryCallExported(serviceName + "." + funcBase, args);
        if (r != NOT_FOUND) return r;

        String leaf = serviceName;
        int dot = serviceName.lastIndexOf('.');
        if (dot >= 0) leaf = serviceName.substring(dot + 1);
        if (!leaf.equals(serviceName)) {
            r = tryCallExported(leaf + "." + funcBase, args);
            if (r != NOT_FOUND) return r;
        }

        r = tryCallExported(funcBase, args);
        return r == NOT_FOUND ? null : r;
    }

    /** 调用导出函数；未导出返回 {@link #NOT_FOUND}，执行异常按原错误处理返回 null。 */
    private Object tryCallExported(String funcName, Object... args) {
        try {
            return sdlHandle.call(funcName, args);
        } catch (SdlException e) {
            if (isFunctionNotFound(e)) return NOT_FOUND;
            AppLogger.e(TAG, "callExported failed: " + packageName + "." + funcName
                    + " -> " + describeDslError(e), e);
            notifyDslError(funcName, e);
            return null;
        } catch (Exception e) {
            AppLogger.e(TAG, "callExported unexpected: " + packageName + "." + funcName
                    + " - " + e.getMessage(), e);
            return null;
        }
    }

    private boolean isFunctionNotFound(SdlException e) {
        if (e == null || e.getErrorCode() != SdlException.ErrorCode.INTERNAL_ERROR
                || e.getMessage() == null) {
            return false;
        }
        String msg = e.getMessage();
        return msg.contains("宿主可调用函数不存在")
                || msg.contains("模块中不存在宿主可调用函数");
    }

    /** 根据 actionId 解析触发模型（未注册时默认 BUTTON） */
    private TriggerModel resolveTriggerModel(MioEngFunBridge bridge, String actionId) {
        if (bridge != null) {
            ControlActionItem item = bridge.getRegisteredShortcuts().get(actionId);
            if (item != null && item.getTriggerModel() != null) {
                return item.getTriggerModel();
            }
        }
        return TriggerModel.BUTTON;
    }

    // ==================== 触发器事件 ====================

    /**
     * 触发器按下事件 — 仅通知注册了该快捷键的服务。
     * 归属未知（如 keyInit 失败/旧插件）时回退为广播，避免功能失效。
     */
    public void onTriggerDown(String actionId) {
        String owner = resolveOwner(actionId);
        if (owner == null) {
            for (ServiceThread thread : serviceThreads.values()) {
                if (thread.entry.isShortcutKey()) {
                    thread.setTriggerActive(actionId, true);
                }
            }
            return;
        }
        ServiceThread thread = serviceThreads.get(owner);
        if (thread != null) {
            thread.setTriggerActive(actionId, true);
        }
    }

    /**
     * 触发器松开事件
     */
    public void onTriggerUp(String actionId) {
        String owner = resolveOwner(actionId);
        if (owner == null) {
            for (ServiceThread thread : serviceThreads.values()) {
                if (thread.entry.isShortcutKey()) {
                    thread.setTriggerActive(actionId, false);
                }
            }
            return;
        }
        MioEngFunBridge bridge = MioBridgeRegistry.getMioEngFunBridge();
        if (bridge != null) {
            bridge.updateTriggerState(actionId, false);
        }
        ServiceThread thread = serviceThreads.get(owner);
        if (thread != null) {
            thread.setTriggerActive(actionId, false);
        }
    }

    /**
     * 清除指定快捷键的触发器状态（快捷键关闭时调用）
     */
    public void clearTrigger(String actionId) {
        String owner = resolveOwner(actionId);
        if (owner == null) {
            for (ServiceThread thread : serviceThreads.values()) {
                if (thread.entry.isShortcutKey()) {
                    thread.setTriggerActive(actionId, false);
                }
            }
            return;
        }
        MioEngFunBridge bridge = MioBridgeRegistry.getMioEngFunBridge();
        if (bridge != null) {
            bridge.updateTriggerState(actionId, false);
            bridge.clearTriggerState(actionId);
        }
        ServiceThread thread = serviceThreads.get(owner);
        if (thread != null) {
            thread.setTriggerActive(actionId, false);
        }
    }

    /**
     * 查询快捷键归属的服务。归属未知（未注册/keyInit 之前/旧插件）时返回 null。
     */
    private String resolveOwner(String actionId) {
        MioEngFunBridge bridge = MioBridgeRegistry.getMioEngFunBridge();
        return bridge != null ? bridge.getShortcutOwner(actionId) : null;
    }

    // ==================== WS 事件分发 ====================

    /**
     * 向本插件所有 WS_SERVICE 服务广播一个 WS 游戏事件。
     * 事件名 → 入口函数 {@code on<事件名>}；未导出的服务自动跳过。
     *
     * @param eventName 事件名，如 "PlayerMessage"
     * @param rawJson   WS 事件原始 properties JSON 文本
     */
    public void broadcastWsEvent(String eventName, String rawJson) {
        if (closed.get()) return;
        String funcBase = "on" + eventName;
        for (ServiceThread thread : serviceThreads.values()) {
            if (thread.entry.isWs()) {
                thread.enqueueWsEvent(funcBase, rawJson);
            }
        }
    }

    /**
     * 向指定 WS_SERVICE 服务投递一个功能开关回调
     * （只投递给注册该 feature 的归属服务）。
     */
    public void enqueueFeatureToggle(String serviceName, String key, boolean enabled) {
        ServiceThread thread = serviceThreads.get(serviceName);
        if (thread != null && thread.entry.isWs()) {
            thread.enqueueFeatureToggle(key, enabled);
        }
    }

    // ==================== 快捷键注册 ====================

    /**
     * 由 ServiceThread 在 keyInit() 执行后回调，注册快捷键
     */
    void addShortcutAction(ControlActionItem item) {
        synchronized (shortcutActions) {
            shortcutActions.add(item);
        }
    }

    public List<ControlActionItem> getShortcutActions() {
        synchronized (shortcutActions) {
            return new ArrayList<>(shortcutActions);
        }
    }

    // ==================== Service 声明读取 ====================

    private void loadServiceEntries() {
        try {
            File pluginDir = new File(context.getFilesDir(),
                    CommonConfig.plugin_private_package + packageName);
            File configFile = new File(pluginDir, "config.json");
            if (!configFile.isFile()) return;

            String json = readFile(configFile);
            JSONObject root = new JSONObject(json);
            JSONObject plugin = root.optJSONObject("plugin");
            if (plugin == null) return;

            JSONArray services = plugin.optJSONArray("service");
            if (services == null) return;

            for (int i = 0; i < services.length(); i++) {
                JSONObject svc = services.optJSONObject(i);
                if (svc == null) continue;
                String name = svc.optString("name", "");
                String tag = svc.optString("tag", "");
                if (!name.isEmpty()) {
                    serviceEntries.add(new ServiceEntry(name, tag));
                }
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "loadServiceEntries failed: " + e.getMessage(), e);
        }
    }

    private ServiceEntry findServiceEntry(String name) {
        for (ServiceEntry entry : serviceEntries) {
            if (entry.name.equals(name)) return entry;
        }
        return null;
    }

    public List<ServiceEntry> getServiceEntries() { return serviceEntries; }

    // ==================== ServiceThread ====================

    /**
     * 独立线程，运行单个服务。
     * SHORTCUT_KEY：启动时调用 keyInit()，然后循环等待触发器事件，触发时调用 onTriggerDown()。
     * WS_SERVICE：启动时调用 serverInit()，之后从任务队列取 WS 事件/功能开关任务执行。
     */
    private class ServiceThread extends Thread {

        final String serviceName;
        final ServiceEntry entry;
        private final Map<String, Boolean> triggerStates = new ConcurrentHashMap<>();
        /** WS 服务任务队列：事件 / 功能开关 */
        private final BlockingQueue<WsTask> wsTasks = new LinkedBlockingQueue<>();

        ServiceThread(String serviceName, ServiceEntry entry) {
            super("dsl-service-" + packageName + "." + serviceName);
            this.serviceName = serviceName;
            this.entry = entry;
        }

        void setTriggerActive(String actionId, boolean active) {
            triggerStates.put(actionId, active);
        }

        /**
         * 投递一个 WS 事件任务（{@code on<事件名>} 入口）。
         * 仅当对应服务导出了该入口时才投递；未导出静默跳过。
         */
        void enqueueWsEvent(String funcBase, String rawJson) {
            if (closed.get()) return;
            if (!hasExportedHandler(funcBase)) return;
            wsTasks.offer(WsTask.event(funcBase, rawJson));
        }

        /** 投递一个功能开关任务（{@code onWsFeatureToggle} 入口）。 */
        void enqueueFeatureToggle(String key, boolean enabled) {
            if (closed.get()) return;
            if (!hasExportedHandler("onWsFeatureToggle")) return;
            wsTasks.offer(WsTask.toggle(key, enabled));
        }

        /** 该服务的模块是否导出了指定宿主可调用入口（按叶模块名查询）。 */
        private boolean hasExportedHandler(String funcName) {
            try {
                return sdlHandle.hasExported(leafNameOf(serviceName), funcName);
            } catch (Exception e) {
                AppLogger.w(TAG, "hasExported failed: " + serviceName + "." + funcName
                        + " - " + e.getMessage());
                return false;
            }
        }

        @Override
        public void run() {
            AppLogger.i(TAG, "service thread started: " + serviceName);
            try {
                if (entry.isWs()) {
                    runWsService();
                } else {
                    runShortcutService();
                }
            } catch (Exception e) {
                AppLogger.e(TAG, "service thread crashed: " + serviceName + " - " + e.getMessage(), e);
            }
            AppLogger.i(TAG, "service thread stopped: " + serviceName);
        }

        /** WS_SERVICE：serverInit 注册能力，然后循环执行 WS 任务（事件/功能开关）。 */
        private void runWsService() {
            MioEngFunBridge bridge = MioBridgeRegistry.getMioEngFunBridge();
            if (bridge != null) {
                bridge.setCurrentShortcutOwner(serviceName);
                bridge.setCurrentShortcutPackage(packageName);
            }
            try {
                callExported(serviceName, "serverInit");
            } catch (Exception e) {
                AppLogger.e(TAG, "serverInit failed: " + serviceName + " - " + e.getMessage(), e);
            } finally {
                if (bridge != null) {
                    bridge.setCurrentShortcutOwner(null);
                    bridge.setCurrentShortcutPackage(null);
                }
            }

            while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    WsTask task = wsTasks.poll(500, TimeUnit.MILLISECONDS);
                    if (task == null) continue;
                    if (task.isToggle) {
                        callExported(serviceName, "onWsFeatureToggle", task.key, task.enabled);
                    } else {
                        callExported(serviceName, task.funcName, task.rawJson);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (SdlException e) {
                    AppLogger.e(TAG, "ws service loop dsl error: " + serviceName
                            + " -> " + describeDslError(e), e);
                    notifyDslError(serviceName, e);
                } catch (Exception e) {
                    AppLogger.e(TAG, "ws service loop error: " + serviceName + " - " + e.getMessage(), e);
                }
            }
        }

        /** SHORTCUT_KEY：keyInit 注册快捷键，然后循环等待触发器事件。 */
        private void runShortcutService() {
            // 调用 keyInit 注册快捷键。
            // 注册时刻通过 ThreadLocal 记录本服务的归属（在 MioEngFunBridge.onKeyInit 内捕获），
            // 避免并发注册时用快照 diff 产生误归类。
            MioEngFunBridge bridge = MioBridgeRegistry.getMioEngFunBridge();
            if (bridge != null) {
                bridge.setCurrentShortcutOwner(serviceName);
                bridge.setCurrentShortcutPackage(packageName);
            }
            try {
                callExported(serviceName, "keyInit");
            } catch (Exception e) {
                AppLogger.e(TAG, "keyInit failed: " + serviceName + " - " + e.getMessage(), e);
            } finally {
                if (bridge != null) {
                    bridge.setCurrentShortcutOwner(null);
                    bridge.setCurrentShortcutPackage(null);
                }
            }

            // 主循环：等待触发器事件
            while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // 检查是否有活跃的触发器
                    for (Map.Entry<String, Boolean> entry : triggerStates.entrySet()) {
                        if (entry.getValue()) {
                            String actionId = entry.getKey();
                            // 仅处理本服务拥有的快捷键，避免跨服务串扰
                            String owner = resolveOwner(actionId);
                            if (owner != null && !serviceName.equals(owner)) {
                                triggerStates.remove(actionId);
                                continue;
                            }
                            // 更新 MioEngFunBridge 的触发器状态
                            MioEngFunBridge engineBridge = MioBridgeRegistry.getMioEngFunBridge();
                            TriggerModel triggerModel = resolveTriggerModel(engineBridge, actionId);
                            if (engineBridge != null) {
                                engineBridge.updateTriggerState(actionId, true);
                            }
                            // 模型专属触发事件：仅对应模型触发
                            if (triggerModel == TriggerModel.SWITCH) {
                                callExported(serviceName, "onSwitchOn");
                            } else if (triggerModel == TriggerModel.PRESSURE_PLATE) {
                                callExported(serviceName, "onPressurePlatePress");
                            } else {
                                callExported(serviceName, "onButtonPress");
                            }
                            // 通用快捷键事件：任何模型只要被触发就调用（阻塞运行）
                            callExported(serviceName, "onTriggerDown");
                            // 仅当触发器确实已松开才补发松开事件（onTriggerDown 提前返回不算）
                            boolean released = !Boolean.TRUE.equals(triggerStates.get(actionId));
                            // 执行完后重置状态
                            if (engineBridge != null) {
                                engineBridge.updateTriggerState(actionId, false);
                            }
                            triggerStates.put(actionId, false);
                            if (released) {
                                // 模型专属松开事件
                                if (triggerModel == TriggerModel.SWITCH) {
                                    callExported(serviceName, "onSwitchOff");
                                } else if (triggerModel == TriggerModel.PRESSURE_PLATE) {
                                    callExported(serviceName, "onPressurePlateRelease");
                                } else {
                                    callExported(serviceName, "onButtonRelease");
                                }
                                // 通用松开事件
                                callExported(serviceName, "onTriggerUp");
                            }
                        }
                    }
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (SdlException e) {
                    AppLogger.e(TAG, "service loop dsl error: " + serviceName
                            + " -> " + describeDslError(e), e);
                    notifyDslError(serviceName, e);
                    try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
                } catch (Exception e) {
                    AppLogger.e(TAG, "service loop error: " + serviceName + " - " + e.getMessage(), e);
                    try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
                }
            }
        }
    }

    /** WS 服务任务：事件（funcName + 原始 JSON）或功能开关（key + enabled）。 */
    private static class WsTask {
        final boolean isToggle;
        final String funcName;
        final String rawJson;
        final String key;
        final boolean enabled;

        private WsTask(boolean isToggle, String funcName, String rawJson, String key, boolean enabled) {
            this.isToggle = isToggle;
            this.funcName = funcName;
            this.rawJson = rawJson;
            this.key = key;
            this.enabled = enabled;
        }

        static WsTask event(String funcName, String rawJson) {
            return new WsTask(false, funcName, rawJson, null, false);
        }

        static WsTask toggle(String key, boolean enabled) {
            return new WsTask(true, null, null, key, enabled);
        }
    }

    // ==================== ServiceEntry ====================

    public static class ServiceEntry {
        public final String name;
        public final String tag;

        public ServiceEntry(String name, String tag) {
            this.name = name;
            this.tag = tag;
        }

        public boolean isMainService() { return "MAINSERVICE".equals(tag); }
        public boolean isShortcutKey() { return "SHORTCUT_KEY".equals(tag); }
        public boolean isWs() { return "WS_SERVICE".equals(tag); }
        /** config.dsl 配置页声明（见 PluginConfigDeclaration.configModule） */
        public boolean isConfig() { return "CONFIG".equals(tag); }
    }

    // ==================== 工具 ====================

    /**
     * 引擎侧运行时是否仍加载。
     * 插件被 {@code SdlEngine.disablePlugin/close} 后引擎会 unload 运行时，
     * 此时宿主侧句柄已失效（call 会报错），需关闭后重新 open。
     */
    public boolean isEngineLoaded() {
        try {
            return !closed.get() && sdlHandle != null && sdlHandle.isLoaded();
        } catch (Exception e) {
            return false;
        }
    }

    /** 服务名 → 叶模块名（engine 的 loadModule 键为叶名）。 */
    private static String leafNameOf(String serviceName) {
        if (serviceName == null) return serviceName;
        int dot = serviceName.lastIndexOf('.');
        return dot >= 0 ? serviceName.substring(dot + 1) : serviceName;
    }

    /**
     * 把 SdlException 格式化为可读的日志片段。
     * 格式：ErrorCode名(码值, 中文描述) | 异常message
     */
    private static String describeDslError(SdlException e) {
        SdlException.ErrorCode code = e.getErrorCode();
        String codeText = code != null
                ? code.name() + "(" + code.getCode() + "," + code.getDescription() + ")"
                : "UNKNOWN(-)";
        return codeText + " | " + e.getMessage();
    }

    /**
     * 发送插件运行错误的系统通知。
     * 同一插件同一函数名的错误在节流时间内只会更新同一条通知，避免刷屏。
     */
    private void notifyDslError(String funcName, SdlException e) {
        try {
            String key = packageName + "." + funcName;
            long now = System.currentTimeMillis();
            Long last = lastErrorNotifyAt.get(key);
            if (last != null && (now - last) < ERROR_NOTIFY_THROTTLE_MS) {
                return;
            }
            lastErrorNotifyAt.put(key, now);

            ensureDslErrorChannel();
            int notifyId = DSL_ERROR_NOTE_BASE + Math.abs(key.hashCode() % 1000);
            String detail = String.format(Locale.CHINA, "%s\n%s | %s", key, describeDslError(e), e.getMessage());
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DSL_ERROR_CHANNEL_ID)
                    .setSmallIcon(R.drawable.mio)
                    .setContentTitle(context.getString(R.string.dsl_error_notification_title))
                    .setContentText(String.format(Locale.CHINA,
                            context.getString(R.string.dsl_error_notification_content), key, e.getMessage()))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(detail))
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(notifyId, builder.build());
            }
        } catch (Exception ex) {
            AppLogger.w(TAG, "notifyDslError failed: " + ex.getMessage());
        }
    }

    private void ensureDslErrorChannel() {
        if (dslErrorChannelCreated) return;
        synchronized (DslPluginHandle.class) {
            if (dslErrorChannelCreated) return;
            try {
                NotificationChannel channel = new NotificationChannel(
                        DSL_ERROR_CHANNEL_ID,
                        context.getString(R.string.dsl_error_channel_name),
                        NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription(context.getString(R.string.dsl_error_channel_desc));
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.createNotificationChannel(channel);
                    dslErrorChannelCreated = true;
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "ensureDslErrorChannel failed: " + e.getMessage());
            }
        }
    }

    private static String readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int off = 0;
            while (off < buf.length) {
                int r = in.read(buf, off, buf.length - off);
                if (r < 0) break;
                off += r;
            }
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    // ==================== Getter ====================

    public String getPackageName() { return packageName; }
    public boolean isClosed() { return closed.get(); }
}
