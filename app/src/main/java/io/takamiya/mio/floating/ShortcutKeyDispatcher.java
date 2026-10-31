package io.takamiya.mio.floating;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import androidx.core.util.Consumer;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.exceptions.RegistryException;
import io.takamiya.mio.bean.ControlActionItem;
import io.takamiya.mio.config.Action;
import io.takamiya.mio.config.ActionExecutor;
import io.takamiya.mio.config.ActionGroup;
import io.takamiya.mio.config.ActionItem;
import io.takamiya.mio.config.ActionType;
import io.takamiya.mio.config.ShortcutEventType;
import io.takamiya.mio.config.ToastSettings;
import io.takamiya.mio.config.TriggerModel;
import io.takamiya.mio.gamepad.GamepadButton;
import io.takamiya.mio.gamepad.GamepadEngine;
import io.takamiya.mio.gamepad.GamepadMapping;
import io.takamiya.mio.gamepad.GamepadState;
import io.takamiya.mio.injector.CpsTracker;
import io.takamiya.mio.injector.ImeKeyInjector;
import io.takamiya.mio.injector.InjectionException;
import io.takamiya.mio.injector.KeyInjector;
import io.takamiya.mio.injector.KeyInjectorFactory;
import io.takamiya.mio.injector.KeyInjectorType;
import io.takamiya.mio.injector.ServiceState;
import io.takamiya.mio.injector.ShizukuKeyInjector;
import io.takamiya.mio.shizuku.ShizukuHelper;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.InputModelManager;
import io.takamiya.mio.utils.RegistryUtils;

public class ShortcutKeyDispatcher {

    private static final String TAG = "ShortcutKeyDispatcher";

    private final Context context;
    private final Consumer<String> toastFn;
    private final BiConsumer<String, ToastSettings> toastActionHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, ActionItem> actionMap = new HashMap<>();

    private KeyInjector injector;
    private CpsTracker cpsTracker;
    private ActionExecutor executor;
    private long globalKeyHoldDuration;
    private FloatingCpsIndicator cpsIndicatorRef;
    private FloatingStatusBar statusBarRef;
    private io.takamiya.mio.dsl.DslEventDispatcher dslEventDispatcher;
    private final Map<String, Runnable> pendingRelease = new HashMap<>();

    public ShortcutKeyDispatcher(Context context, Consumer<String> toastFn) {
        this(context, toastFn, null);
    }

    public ShortcutKeyDispatcher(Context context, Consumer<String> toastFn,
                                 BiConsumer<String, ToastSettings> toastActionHandler) {
        this.context = context;
        this.toastFn = toastFn;
        this.toastActionHandler = toastActionHandler;
    }

    public void setGlobalKeyHoldDuration(long duration) {
        this.globalKeyHoldDuration = duration;
        if (executor != null) {
            executor.setGlobalKeyHoldDuration(duration);
        }
    }

    public void setCpsIndicator(FloatingCpsIndicator indicator) {
        cpsIndicatorRef = indicator;
        AppLogger.d(TAG, "setCpsIndicator: indicator=" + (indicator != null) + ", tracker=" + (cpsTracker != null));
        if (indicator != null || statusBarRef != null) {
            ensureTracker();
        }
        refreshCpsTicker();
    }

    public void setStatusBar(FloatingStatusBar statusBar) {
        statusBarRef = statusBar;
        AppLogger.d(TAG, "setStatusBar: statusBar=" + (statusBar != null) + ", tracker=" + (cpsTracker != null));
        if (statusBar != null || cpsIndicatorRef != null) {
            ensureTracker();
        }
        refreshCpsTicker();
    }

    public CpsTracker getCpsTracker() {
        return cpsTracker;
    }

    public void setActions(List<ControlActionItem> items) {
        actionMap.clear();
        if (items != null) {
            for (ControlActionItem item : items) {
                if (item == null || item.getActionItem() == null) continue;
                actionMap.put(keyOf(item), item.getActionItem());
            }
        }
        AppLogger.d(TAG, "setActions: " + actionMap.size() + " action(s)");
    }

    public void handleKeyAction(String key, TriggerModel triggerModel, boolean active) {
        ActionItem item = actionMap.get(key);
        if (item == null) {
            AppLogger.w(TAG, "no action for key: " + key);
            return;
        }
        TriggerModel model = triggerModel != null ? triggerModel : TriggerModel.BUTTON;

        if (hasGamepadActions(item)) {
            handleGamepadAction(key, item, model, active);
            return;
        }

        if (model == TriggerModel.SWITCH && !active) {
            if (executor != null) {
                executor.executeOff(item);
            }
            return;
        }

        if (model == TriggerModel.PRESSURE_PLATE) {
            if (!active) {
                long delay = item.getReleaseDelayMs() != null ? item.getReleaseDelayMs() : 0;
                if (delay > 0 && executor != null) {
                    cancelPendingRelease(key);
                    Runnable offRunnable = () -> {
                        pendingRelease.remove(key);
                        if (executor != null) executor.executeOff(item);
                    };
                    pendingRelease.put(key, offRunnable);
                    mainHandler.postDelayed(offRunnable, delay);
                } else if (executor != null) {
                    executor.executeOff(item);
                }
                return;
            }
            cancelPendingRelease(key);
        }

        if (isToastOnly(item)) {
            showToastAction(item);
            return;
        }

        ActionExecutor executor = acquireExecutor();
        if (executor == null) {
            return;
        }

        if (model == TriggerModel.SWITCH) {
            if (executor.needsEngagedExecution(item)) {
                executor.executeOn(item);
            } else {
                executor.execute(item);
            }
        } else if (model == TriggerModel.PRESSURE_PLATE) {
            if (executor.needsEngagedExecution(item)) {
                executor.executeOn(item);
            } else {
                executor.execute(item);
            }
        } else {
            // BUTTON 模式：按下时执行，松手时停止
            if (!active) {
                executor.executeOff(item);
                return;
            }
            if (executor.needsEngagedExecution(item)) {
                executor.executeOn(item);
            } else {
                executor.execute(item);
            }
        }
    }

    public void fireEvent(String key, ShortcutEventType eventType) {
        ActionItem item = actionMap.get(key);
        if (item == null) {
            AppLogger.w(TAG, "fireEvent: no action for key: " + key);
            return;
        }
        fireEventOnItem(item, eventType);
    }

    public void fireEventOnItem(ActionItem item, ShortcutEventType eventType) {
        if (item == null) return;

        if (item.hasEvents()) {
            List<ActionGroup> groups = item.getEventGroups(eventType);
            if (groups != null && !groups.isEmpty()) {
                ActionExecutor exec = acquireExecutor();
                if (exec != null) {
                    AppLogger.d(TAG, "fireEvent: " + eventType + " on " + item.getTitle());
                    exec.executeActions(item, groups);
                }
            }
            return;
        }

        // 无事件组时，fallback 主动作只由 BUTTON 触发器的 KEY_PRESS 驱动（按下→执行，松开→停止）。
        // 同一物理按下产生的 BUTTON_PRESS 不再重复执行。
        // SWITCH/PRESSURE_PLATE 的主动作由各自的激活事件（onKeyAction / KEY_ON / PRESSURE_PLATE_PRESS）驱动，
        // DOWN 阶段不在此提前执行，避免一次按下注入两次。
        TriggerModel model = item.getTriggerModel() != null ? item.getTriggerModel() : TriggerModel.BUTTON;
        if (eventType == ShortcutEventType.KEY_PRESS && model == TriggerModel.BUTTON) {
            String key = findKeyForItem(item);
            if (key != null) {
                handleKeyAction(key, model, true);
            }
        }
    }

    public void fireGlobalEvent(ShortcutEventType eventType) {
        AppLogger.d(TAG, "fireGlobalEvent: " + eventType);
        if (eventType == ShortcutEventType.ENGINE_START && dslEventDispatcher != null) {
            dslEventDispatcher.dispatchEngineStart();
        } else if (eventType == ShortcutEventType.ENGINE_STOP && dslEventDispatcher != null) {
            dslEventDispatcher.dispatchEngineStop();
        }
        for (Map.Entry<String, ActionItem> entry : actionMap.entrySet()) {
            ActionItem item = entry.getValue();
            if (item != null && item.hasEvents()) {
                fireEventOnItem(item, eventType);
            }
        }
    }

    /** 供 FloatingWindowService 注入：引擎启停时分发 onEngineStart/onEngineStop 给 DSL 插件。 */
    public void setDslEventDispatcher(io.takamiya.mio.dsl.DslEventDispatcher dispatcher) {
        this.dslEventDispatcher = dispatcher;
    }

    private boolean hasGamepadActions(ActionItem item) {
        if (item == null) return false;
        for (Action a : item.collectAllActions()) {
            if (a.getTarget() == Action.ActionTarget.GAMEPAD) return true;
        }
        return false;
    }

    private void handleGamepadAction(String key, ActionItem item, TriggerModel model, boolean active) {
        if (model == TriggerModel.SWITCH && !active) {
            executeGamepadOff(item);
            return;
        }
        if (model == TriggerModel.PRESSURE_PLATE && !active) {
            long delay = item.getReleaseDelayMs() != null ? item.getReleaseDelayMs() : 0;
            if (delay > 0) {
                cancelPendingRelease(key);
                Runnable offRunnable = () -> {
                    pendingRelease.remove(key);
                    executeGamepadOff(item);
                };
                pendingRelease.put(key, offRunnable);
                mainHandler.postDelayed(offRunnable, delay);
            } else {
                executeGamepadOff(item);
            }
            return;
        }
        cancelPendingRelease(key);
        if (!active) {
            executeGamepadOff(item);
            return;
        }
        executeGamepadOn(item);
    }

    private void executeGamepadOn(ActionItem item) {
        GamepadEngine engine = GamepadEngine.get(context);
        if (engine.getState() == GamepadState.UNINITIALIZED
                && ShizukuKeyInjector.isShizukuRunning()
                && ShizukuKeyInjector.isPermissionGranted()) {
            Thread initThread = new Thread(() -> {
                if (engine.getState() == GamepadState.UNINITIALIZED) {
                    engine.init();
                }
            }, "gamepad-init");
            initThread.setDaemon(true);
            initThread.start();
        }
        for (Action a : item.collectAllActions()) {
            if (a.getTarget() != Action.ActionTarget.GAMEPAD) continue;
            GamepadButton btn = GamepadMapping.getButton(a.getKey());
            if (btn != null) {
                engine.pressButton(btn);
            }
        }
    }

    private void executeGamepadOff(ActionItem item) {
        GamepadEngine engine = GamepadEngine.get(context);
        for (Action a : item.collectAllActions()) {
            if (a.getTarget() != Action.ActionTarget.GAMEPAD) continue;
            GamepadButton btn = GamepadMapping.getButton(a.getKey());
            if (btn != null) {
                engine.releaseButton(btn);
            }
        }
    }

    public void stopAction(String key) {
        ActionItem item = actionMap.get(key);
        if (item == null) return;
        if (executor != null) {
            executor.executeOff(item);
        }
    }

    public void stopAll() {
        cancelAllPendingReleases();
        if (executor != null) {
            executor.stopAll();
        }
        releaseExecutor();
    }

    private void cancelPendingRelease(String key) {
        Runnable pending = pendingRelease.remove(key);
        if (pending != null) {
            mainHandler.removeCallbacks(pending);
        }
    }

    private void cancelAllPendingReleases() {
        for (Runnable r : pendingRelease.values()) {
            mainHandler.removeCallbacks(r);
        }
        pendingRelease.clear();
    }

    private void ensureTracker() {
        if (cpsTracker == null) {
            cpsTracker = new CpsTracker(null);
        }
    }

    private void refreshCpsTicker() {
        if (cpsTracker == null) return;
        cpsTracker.stop();
        if (cpsIndicatorRef != null || statusBarRef != null) {
            cpsTracker.setOnCpsChangedListener((cps, history) -> {
                if (cpsIndicatorRef != null) {
                    cpsIndicatorRef.updateCps(cps);
                }
                if (statusBarRef != null) {
                    statusBarRef.setCpsText(String.valueOf((int) cps));
                }
            });
            cpsTracker.start();
        } else {
            cpsTracker.setOnCpsChangedListener(null);
        }
    }

    private ActionExecutor acquireExecutor() {
        if (injector != null && executor != null) {
            ServiceState state = injector.getServiceState();
            if (state == ServiceState.READY) return executor;
            releaseExecutor();
            notifyState(injector, state);
            return null;
        }

        int model = resolveModel();
        if (model == InputModelManager.MODEL_NONE) {
            handleNoAvailableMode();
            return null;
        }

        KeyInjector created;
        try {
            KeyInjectorType type = model == InputModelManager.MODEL_SHIZUKU
                    ? KeyInjectorType.SHIZUKU
                    : KeyInjectorType.IME;
            created = KeyInjectorFactory.create(type, context);
        } catch (IllegalStateException e) {
            AppLogger.w(TAG, "create injector failed: " + e.getMessage());
            handleCreateFailure(model, e.getMessage());
            return null;
        }

        ServiceState state = created.getServiceState();
        if (state != ServiceState.READY) {
            notifyState(created, state);
            return null;
        }

        injector = created;
        ensureTracker();
        if (cpsTracker != null) {
            cpsTracker.setDelegate(created);
        }
        executor = new ActionExecutor(cpsTracker, this::toast, this::showToastAction);
        executor.setGlobalKeyHoldDuration(globalKeyHoldDuration);
        AppLogger.d(TAG, "acquireExecutor: created, cpsIndicatorRef=" + (cpsIndicatorRef != null));
        refreshCpsTicker();
        fireGlobalEvent(ShortcutEventType.ENGINE_START);
        return executor;
    }

    private void notifyState(KeyInjector injector, ServiceState state) {
        switch (state) {
            case NO_SERVICE:
                if (injector instanceof ShizukuKeyInjector) {
                    ShizukuHelper.getInstance().bindShellService();
                    toast("Shizuku服务未连接，正在重连");
                } else {
                    toast("输入法服务未运行");
                }
                break;
            case NOT_READY:
                if (injector instanceof ImeKeyInjector) {
                    toast("输入法未就绪，请先切换到输入法并聚焦输入框");
                } else {
                    toast("Shizuku权限未授予");
                }
                break;
            default:
                break;
        }
    }

    private void handleNoAvailableMode() {
        if (ShizukuKeyInjector.isShizukuRunning() && ShizukuKeyInjector.isPermissionGranted()) {
            ShizukuHelper.getInstance().bindShellService();
            toast("Shizuku服务未连接，正在重连");
        } else if (ShizukuKeyInjector.isShizukuRunning()) {
            toast("Shizuku权限未授予，请在Shizuku中授权");
        } else {
            toast("无可用的输入模式，请连接Shizuku或启用Mio输入法");
        }
    }

    private void handleCreateFailure(int model, String message) {
        if (model == InputModelManager.MODEL_SHIZUKU
                && ShizukuKeyInjector.isShizukuRunning()
                && ShizukuKeyInjector.isPermissionGranted()) {
            ShizukuHelper.getInstance().bindShellService();
            toast("Shizuku服务未连接，正在重连");
        } else {
            toast(message);
        }
    }

    private void releaseExecutor() {
        if (executor != null) {
            fireGlobalEvent(ShortcutEventType.ENGINE_STOP);
            executor.stopAll();
        }
        if (cpsTracker != null) {
            cpsTracker.stop();
            cpsTracker = null;
        }
        executor = null;
        injector = null;
    }

    private int resolveModel() {
        Registry reg = RegistryUtils.get();
        int saved = InputModelManager.MODEL_IME;
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue())) {
                    saved = reg.getInt(DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue());
                }
            } catch (RegistryException e) {
                AppLogger.w(TAG, "failed to load input model: " + e.getMessage());
            }
        }
        return InputModelManager.resolveModel(saved, context);
    }

    private boolean isToastOnly(ActionItem item) {
        List<ActionGroup> groups = item.getGroups();
        if (groups == null || groups.isEmpty()) return false;
        for (ActionGroup group : groups) {
            List<Action> actions = group.getActions();
            if (actions == null || actions.isEmpty()) return false;
            for (Action action : actions) {
                if (action.getType() != ActionType.TOAST) return false;
            }
        }
        return true;
    }

    private void showToastAction(ActionItem item) {
        List<ActionGroup> groups = item.getGroups();
        if (groups == null) return;
        for (ActionGroup group : groups) {
            List<Action> actions = group.getActions();
            if (actions == null) continue;
            for (Action action : actions) {
                if (action.getType() == ActionType.TOAST && toastActionHandler != null) {
                    toastActionHandler.accept(action.getText(), action.getToastSettings());
                }
            }
        }
    }

    private void showToastAction(String text, ToastSettings settings) {
        if (toastActionHandler != null) {
            toastActionHandler.accept(text, settings);
        }
    }

    private String keyOf(ControlActionItem item) {
        return item.getPackageName() + "." + item.getActionId();
    }

    private String findKeyForItem(ActionItem item) {
        for (Map.Entry<String, ActionItem> entry : actionMap.entrySet()) {
            if (entry.getValue() == item) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void toast(String message) {
        AppLogger.d(TAG, "toast: " + message);
        if (toastFn == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            toastFn.accept(message);
        } else {
            mainHandler.post(() -> toastFn.accept(message));
        }
    }
}
