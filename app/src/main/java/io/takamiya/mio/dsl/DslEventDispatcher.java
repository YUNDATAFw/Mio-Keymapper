package io.takamiya.mio.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.bean.ControlActionItem;
import io.takamiya.mio.config.ShortcutEventType;
import io.takamiya.mio.config.TriggerModel;
import io.takamiya.mio.dsl.bridge.MioEngFunBridge;
import io.takamiya.mio.dsl.MioBridgeRegistry;

/**
 * DSL 事件分发器。
 * 从 ShortcutKeyDispatcher 接收事件，路由到对应的 DSL 插件。
 *
 * 集成点：
 * 1. ShortcutKeyDispatcher.handleKeyAction() 中，如果 actionId 匹配 DSL 插件，
 *    则不走传统 ActionExecutor，而是通过本分发器调用 DSL 插件。
 * 2. ShortcutKeyDispatcher.fireEvent() 中，同理分发 DSL 事件。
 */
public class DslEventDispatcher {

    private static final String TAG = "DslEventDispatcher";

    /**
     * 同一动作同一方向（按下/松开）的去抖窗口。
     * BUTTON 模式下一次物理按压会同时派发 KEY_PRESS + BUTTON_PRESS，
     * 松开时派发 KEY_RELEASE + BUTTON_RELEASE，需合并为一次。
     */
    private static final long DEBOUNCE_MS = 50L;

    private final DslPluginManager pluginManager;
    private final Map<String, Long> lastTransitions = new ConcurrentHashMap<>();

    public DslEventDispatcher(DslPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    // ==================== 触发器事件 ====================

    /**
     * 处理触发器按下/松开事件。
     * 由 ShortcutKeyDispatcher.handleKeyAction() 调用。
     *
     * @param key 完整 key（格式 "packageName.actionId"）
     * @param triggerModel 触发模式
     * @param active true=按下, false=松开
     * @return true 如果事件被 DSL 插件处理
     */
    public boolean handleTriggerEvent(String key, TriggerModel triggerModel, boolean active) {
        if (!isDslShortcut(key)) {
            return false;
        }

        String actionId = extractActionId(key);

        // 合并同一物理按压派发的重复事件（KEY_PRESS + BUTTON_PRESS）
        String transitionKey = actionId + (active ? ":down" : ":up");
        long now = System.currentTimeMillis();
        Long last = lastTransitions.get(transitionKey);
        if (last != null && (now - last) < DEBOUNCE_MS) {
            return true;
        }
        lastTransitions.put(transitionKey, now);

        AppLogger.d(TAG, "handleTriggerEvent: key=" + key + " actionId=" + actionId + " active=" + active);

        if (active) {
            pluginManager.dispatchTriggerDown(actionId);
        } else {
            pluginManager.dispatchTriggerUp(actionId);
        }
        return true;
    }

    private String extractActionId(String key) {
        int dot = key.lastIndexOf('.');
        return dot > 0 ? key.substring(dot + 1) : key;
    }

    // ==================== 按键事件 ====================

    /**
     * 分发按键事件到所有 DSL 插件
     */
    public void dispatchKeyEvent(String keyCode, boolean down) {
        pluginManager.dispatchKeyEvent(keyCode, down);
    }

    // ==================== 实体键事件（外接键鼠监听） ====================

    /**
     * 实体键事件执行器：单线程串行，保证同一实体键的 down/up 顺序；
     * DSL 调用（onKeyOn/onKeyOff 可能阻塞）不占 InputMonitorEngine 的主线程。
     */
    private final ExecutorService physicalExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dsl-physical-events");
        t.setDaemon(true);
        return t;
    });

    /**
     * 当前按住中的实体键。去抖策略：内核保证 down/up 交替，重复 down（噪声/自动重复
     * 残留）靠集合成员判定丢弃；<b>up 永远放行</b>——不用时间窗，避免吞掉快速点按。
     */
    private final Set<String> pressedPhysicalKeys = ConcurrentHashMap.newKeySet();

    /** 提交一条实体键事件（InputMonitorEngine 主线程回调 → 执行器串行处理）。 */
    public void submitPhysicalKey(String bareKeyName, boolean down) {
        if (bareKeyName == null || bareKeyName.isEmpty()) return;
        physicalExecutor.execute(() -> dispatchPhysicalKey(bareKeyName, down));
    }

    /**
     * 分发实体键事件：绑定表触发（dispatchTriggerDown/Up）+ onKeyOn/onKeyOff 广播。
     * 仅在 physicalExecutor 线程调用。
     */
    private void dispatchPhysicalKey(String key, boolean down) {
        if (down) {
            if (!pressedPhysicalKeys.add(key)) {
                return; // 已按住，重复 down
            }
        } else {
            pressedPhysicalKeys.remove(key);
        }

        List<String> ids = null;
        MioEngFunBridge bridge = MioBridgeRegistry.getMioEngFunBridge();
        if (bridge != null) {
            ids = bridge.getPhysicalIdsByKey(key);
            if (ids != null) {
                for (String id : new ArrayList<>(ids)) {
                    if (down) {
                        pluginManager.dispatchTriggerDown(id);
                    } else {
                        pluginManager.dispatchTriggerUp(id);
                    }
                }
            }
        }
        AppLogger.d(TAG, "dispatchPhysicalKey: key=" + key + " down=" + down + " boundIds=" + ids);

        // onKeyOn/onKeyOff 广播：全插件，未导出静默跳过（callIfExported）
        pluginManager.dispatchKeyEvent("KEYCODE_" + key, down);
    }

    /**
     * 监听停止/服务销毁：对所有按住中的实体键补发松开，防宏卡在「按住」状态。
     * 任务投递到执行器，排在未处理的按键事件之后。
     */
    public void releaseAllPhysicalKeys() {
        physicalExecutor.execute(() -> {
            for (String key : new ArrayList<>(pressedPhysicalKeys)) {
                dispatchPhysicalKey(key, false);
            }
        });
    }

    /**
     * 清除指定快捷键的触发器状态（快捷键关闭时调用）
     */
    public void clearTrigger(String key) {
        String actionId = extractActionId(key);
        pluginManager.dispatchClearTrigger(actionId);
    }

    /**
     * 分发手柄按钮事件
     */
    public void dispatchButtonEvent(String button, boolean pressed) {
        pluginManager.dispatchButtonEvent(button, pressed);
    }

    /**
     * 分发扳机轴事件
     */
    public void dispatchTriggerAxis(String axis, int value) {
        pluginManager.dispatchTriggerAxis(axis, value);
    }

    // ==================== 生命周期事件 ====================

    /**
     * 引擎启动事件
     */
    public void dispatchEngineStart() {
        for (DslPluginHandle handle : pluginManager.getAllHandles().values()) {
            handle.callIfExported("onEngineStart");
        }
    }

    /**
     * 引擎停止事件
     */
    public void dispatchEngineStop() {
        for (DslPluginHandle handle : pluginManager.getAllHandles().values()) {
            handle.callIfExported("onEngineStop");
        }
    }

    // ==================== 查询 ====================

    /**
     * 检查指定 actionId 是否由 DSL 插件注册
     * key 格式为 "packageName.actionId"，需要提取 actionId 部分
     */
    public boolean isDslShortcut(String key) {
        MioEngFunBridge bridge = MioBridgeRegistry.getMioEngFunBridge();
        if (bridge == null) return false;
        Map<String, ControlActionItem> shortcuts = bridge.getRegisteredShortcuts();
        if (shortcuts.containsKey(key)) return true;
        int dot = key.lastIndexOf('.');
        if (dot > 0) {
            String actionId = key.substring(dot + 1);
            return shortcuts.containsKey(actionId);
        }
        return false;
    }

    /**
     * 获取所有 DSL 快捷键动作（供 ShortcutKeyDispatcher 合并使用）
     */
    public List<ControlActionItem> getAllDslShortcutActions() {
        return pluginManager.getAllShortcutKeyActions();
    }
}
