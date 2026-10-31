package io.takamiya.mio.floating;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;

import cn.fsy.plugin.sdl.library.SdlEngine;
import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.exceptions.RegistryException;
import io.takamiya.mio.R;
import io.takamiya.mio.bean.ControlActionItem;
import io.takamiya.mio.config.TriggerModel;
import io.takamiya.mio.config.ShortcutEventType;
import io.takamiya.mio.configManage.IPluginManager;
import io.takamiya.mio.building.BuildingCommandRunner;
import io.takamiya.mio.building.BuildingInfo;
import io.takamiya.mio.dsl.DslEventDispatcher;
import io.takamiya.mio.dsl.DslPluginHandle;
import io.takamiya.mio.dsl.DslPluginManager;
import io.takamiya.mio.monitor.InputMonitorEngine;
import io.takamiya.mio.ws.feature.WsFeatureCenter;
import io.takamiya.mio.gamepad.GyroController;
import io.takamiya.mio.injector.ImeAutoSwitchHelper;
import io.takamiya.mio.injector.MioAccessibilityService;
import io.takamiya.mio.navigation.HomeFragment;
import io.takamiya.mio.shizuku.ShizukuHelper;
import io.takamiya.mio.utils.ApkIntegrity;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.InputModelManager;
import io.takamiya.mio.utils.RegistryUtils;

public class FloatingWindowService extends Service implements FloatingSettingsPanel.Listener, FloatingControlPanel.Listener, MioAccessibilityService.OverlayTokenListener, ViewModelStoreOwner, LifecycleOwner {

    private static final String TAG = "FloatingWindowService";
    private static final String CHANNEL_ID = "floating_window";
    private static final int NOTIFICATION_ID = 1001;

    private static boolean running = false;
    private static FloatingWindowService sInstance;

    public static FloatingWindowService getInstance() {
        return sInstance;
    }

    public static boolean isRunning() {
        return running;
    }

    public static boolean isAllUiHidden() {
        return sInstance != null && sInstance.allHidden;
    }

    private FloatingBall ball;
    private FloatingWatermark watermark;
    private FloatingStatusBar statusBar;
    private FloatingFeatureList featureList;
    private FloatingFeatureListView legacyFeatureList;
    private FloatingSettingsPanel panel;
    private FloatingControlPanel controlPanel;
    private FloatingMcidPanel commandPanel;
    private FloatingToolboxPanel toolboxPanel;
    private FloatingToolboxDetailPanel toolboxDetailPanel;
    private FloatingSettingsSubPanel settingsSubPanel;
    private FloatingWsPanel wsPanel;
    private FloatingImportBuildingPanel importBuildingPanel;
    private FloatingBuildingCommandPanel buildingCommandPanel;
    private FloatingBuildingActionChip actionChip;
    private BuildingCommandRunner actionChipRunner;
    private FloatingShortcutBar shortcutBar;
    private ShortcutKeyDispatcher shortcutDispatcher;

    public ShortcutKeyDispatcher getShortcutDispatcher() {
        return shortcutDispatcher;
    }
    private FloatingToast floatToast;
    private FloatingCpsIndicator cpsIndicator;
    private FloatingCrosshair crosshair;
    private GyroController gyroController;

    // DSL 插件系统
    private DslPluginManager dslPluginManager;
    private DslEventDispatcher dslEventDispatcher;
    /** 外接键鼠监听 → DSL 事件桥接（实体键绑定触发 + onKeyOn/onKeyOff） */
    private InputMonitorEngine.KeyListener monitorKeyListener;

    private final Map<String, Boolean> shortcutState = new HashMap<>();
    private final List<ControlActionItem> loadedActions = new ArrayList<>();
    private boolean isEditMode = false;
    private boolean actionToastEnabled = true;
    private boolean wasShortcutBarVisible = false;
    private boolean allHidden = false;

    private final LifecycleRegistry mLifecycleRegistry = new LifecycleRegistry(this);
    private ViewModelStore mViewModelStore;

    public static void start(Context context) {
        running = true;
        context.startForegroundService(new Intent(context, FloatingWindowService.class));
    }

    public static void stop(Context context) {
        running = false;
        context.stopService(new Intent(context, FloatingWindowService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mLifecycleRegistry.setCurrentState(Lifecycle.State.RESUMED);

        if (!ApkIntegrity.nativeEnforceService(this)) {
    // native 已stopSelf,不要继续创建window / notification
            return;
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        running = true;

        if (!Settings.canDrawOverlays(this)) {
            AppLogger.e(TAG, "overlay permission not granted, stopping");
            stopSelf();
            return;
        }

        MioAccessibilityService.setOverlayTokenListener(this);

        // 外接键鼠监听：默认随悬浮窗服务启动，设置页可关
        if (io.takamiya.mio.monitor.InputMonitorEngine.getInstance().isPrefEnabled()) {
            io.takamiya.mio.monitor.InputMonitorEngine.getInstance().start();
        }

        Context themed = themedContext();
        actionToastEnabled = loadActionToastEnabled();
        if (isWatermarkEnabled()) {
            watermark = new FloatingWatermark(themed);
            watermark.attach();
        }

        if ((isStatusBarEnabled() || isNewStatusBarEnabled()) && isLandscape()) {
            statusBar = new FloatingStatusBar(themed);
            statusBar.attach();
        } else if (isStatusBarEnabled() || isNewStatusBarEnabled()) {
            statusBar = new FloatingStatusBar(themed);
        }

        featureList = new FloatingFeatureList(themed);
        if (!FloatingFeatureList.savedUseNewFeatureList()) {
            legacyFeatureList = new FloatingFeatureListView(themed);
        }
        if (isLandscape() && isFeatureListEnabled()) {
            if (FloatingFeatureList.savedUseNewFeatureList()) {
                featureList.attach();
            } else if (legacyFeatureList != null) {
                legacyFeatureList.attach();
            }
        }

        ball = new FloatingBall(themed);
        ball.setOnBallClickListener(this::onBallClick);
        ball.attach();

        panel = new FloatingSettingsPanel(themed);
        panel.setListener(this);

        if (loadGyroAimEnabled()) {
            startGyroAim();
        }

        controlPanel = new FloatingControlPanel(themed, shortcutState);
        controlPanel.setListener(this);

        commandPanel = new FloatingMcidPanel(themed);

        toolboxPanel = new FloatingToolboxPanel(themed);
        toolboxPanel.setShowToast(this::showToast);
        toolboxPanel.setOnToolSelectedListener(tab -> {
            if (toolboxDetailPanel != null && toolboxDetailPanel.isAttached()) {
                toolboxDetailPanel.detach();
            }
            toolboxDetailPanel = new FloatingToolboxDetailPanel(themed);
            toolboxDetailPanel.setShowToast(this::showToast);
            toolboxDetailPanel.loadTool(tab);
            toolboxDetailPanel.attach();
            toolboxPanel.close();
        });

        toolboxDetailPanel = new FloatingToolboxDetailPanel(themed);
        toolboxDetailPanel.setShowToast(this::showToast);

        shortcutBar = new FloatingShortcutBar(themed);
        shortcutBar.setOnKeyActionListener(new FloatingShortcutBar.OnKeyActionListener() {
            @Override
            public void onKeyAction(String key, TriggerModel triggerModel, boolean active) {
                FloatingWindowService.this.onShortcutKeyAction(key, triggerModel, active);
            }
            @Override
            public void onKeyEvent(String key, TriggerModel triggerModel, ShortcutEventType eventType) {
                FloatingWindowService.this.onShortcutKeyEvent(key, triggerModel, eventType);
            }
        });

        shortcutDispatcher = new ShortcutKeyDispatcher(themed, this::showToast, this::showActionToast);

        // DSL 插件系统初始化
        dslPluginManager = new DslPluginManager(this);
        dslEventDispatcher = new DslEventDispatcher(dslPluginManager);
        controlPanel.setDslEventDispatcher(dslEventDispatcher);
        // 引擎启停事件转发给 DSL 插件（onEngineStart/onEngineStop）
        shortcutDispatcher.setDslEventDispatcher(dslEventDispatcher);
        // 外接键鼠监听 → DSL：实体键绑定触发（dispatchPhysicalKey）+ onKeyOn/onKeyOff 广播
        monitorKeyListener = new InputMonitorEngine.KeyListener() {
            @Override
            public void onKeyChanged(int androidKeyCode, int evdevCode, boolean down) {
                if (androidKeyCode == 0) {
                    return; // 无 Android 对应键，不参与绑定/广播
                }
                String name = KeyEvent.keyCodeToString(androidKeyCode);
                if (name.startsWith("KEYCODE_")) {
                    name = name.substring("KEYCODE_".length());
                }
                dslEventDispatcher.submitPhysicalKey(name, down);
            }

            @Override
            public void onStateChanged(int state, String detail) {
                if (state == InputMonitorEngine.STATE_IDLE
                        || state == InputMonitorEngine.STATE_FAILED) {
                    // 监听停止/失败：补发松开，防宏卡在按住状态
                    dslEventDispatcher.releaseAllPhysicalKeys();
                }
            }
        };
        InputMonitorEngine.getInstance().addKeyListener(monitorKeyListener);
        // WS 引擎事件转发到 DSL 插件广播
        dslPluginManager.attachWsEventListener();
        // 集中注册宿主内置 WS 功能（见 WsFeatureCenter.registerBuiltins）
        WsFeatureCenter.get().registerBuiltins();
        // 加载已安装的 DSL 插件
        loadDslPlugins();
        bindShizukuIfNeeded();
        autoSwitchImeIfNeeded();

        if (isCpsIndicatorEnabled()) {
            cpsIndicator = new FloatingCpsIndicator(themed);
            cpsIndicator.attach();
            shortcutDispatcher.setCpsIndicator(cpsIndicator);
        }

        if (isCrosshairEnabled()) {
            crosshair = new FloatingCrosshair(themed);
            crosshair.attach();
        }

        floatToast = new FloatingToast(themed);
        maybeAutoLaunchGame();
        AppLogger.i(TAG, "FloatingWindowService created");
    }

    @Override
    public void onOverlayTokenReady() {
        AppLogger.i(TAG, "accessibility overlay token ready, rebuilding pass-through windows as trusted overlays");
        rebuildPassThroughWindows();
    }

    @Override
    public void onOverlayTokenInvalidated() {
        AppLogger.i(TAG, "accessibility overlay token invalidated, rebuilding pass-through windows");
        rebuildPassThroughWindows();
    }

    private void rebuildPassThroughWindows() {
        if (!running || allHidden) {
            return;
        }
        try {
            if (watermark != null && watermark.isAttached()) {
                watermark.detach();
                watermark = new FloatingWatermark(themedContext());
                watermark.attach();
            }
            if (statusBar != null && statusBar.isAttached()) {
                statusBar.detach();
                statusBar = new FloatingStatusBar(themedContext());
                statusBar.attach();
            }
            if (featureList != null && featureList.isAttached()) {
                featureList.detach();
                featureList = new FloatingFeatureList(themedContext());
                featureList.attach();
            }
            if (legacyFeatureList != null && legacyFeatureList.isAttached()) {
                legacyFeatureList.detach();
                legacyFeatureList = new FloatingFeatureListView(themedContext());
                legacyFeatureList.attach();
            }
            if (cpsIndicator != null && cpsIndicator.isAttached()) {
                cpsIndicator.detach();
                cpsIndicator = new FloatingCpsIndicator(themedContext());
                cpsIndicator.attach();
                if (shortcutDispatcher != null) {
                    shortcutDispatcher.setCpsIndicator(cpsIndicator);
                }
            }
            if (shortcutDispatcher != null && isNewStatusBarEnabled()) {
                shortcutDispatcher.setStatusBar(statusBar);
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "rebuildPassThroughWindows failed", e);
        }
    }

    private void onBallClick() {
        if (isLandscape() || isPanelPortraitEnabled()) {
            if (panel != null && !panel.isAttached()) {
                panel.attach();
                bringShortcutKeysToFront();
            }
        } else {
            if (floatToast != null) {
                floatToast.show(getString(R.string.floating_toast_portrait_hint));
            }
        }
    }

    @Override
    public void onOpenFunctionPanel() {
        if (controlPanel != null && !controlPanel.isAttached()) {
            controlPanel.attach();
            bringShortcutKeysToFront();
        }
    }

    @Override
    public void onOpenCommandPanel() {
        if (commandPanel != null && !commandPanel.isAttached()) {
            commandPanel.attach();
            bringShortcutKeysToFront();
        }
    }

    @Override
    public void onOpenToolboxPanel() {
        if (toolboxPanel != null && !toolboxPanel.isAttached()) {
            toolboxPanel.attach();
            bringShortcutKeysToFront();
        }
    }

    @Override
    public void onOpenWsPanel() {
        if (wsPanel == null) {
            wsPanel = new FloatingWsPanel(themedContext(), this);
            wsPanel.setShowToast(this::showToast);
        }
        if (!wsPanel.isAttached()) {
            wsPanel.attach();
            bringShortcutKeysToFront();
        }
    }

    @Override
    public void onOpenImportBuildingPanel() {
        openImportBuildingPanelInternal();
    }

    /** 供应用内设置页打开导入建筑 demo 侧边栏。 */
    public static void openImportBuildingPanel() {
        FloatingWindowService svc = sInstance;
        if (svc == null) return;
        svc.openImportBuildingPanelInternal();
    }

    private void openImportBuildingPanelInternal() {
        if (importBuildingPanel == null) {
            importBuildingPanel = new FloatingImportBuildingPanel(themedContext());
            importBuildingPanel.setShowToast(this::showToast);
            importBuildingPanel.setOnOpenCommands(this::openBuildingCommandPanel);
            importBuildingPanel.setOnOpenImportAction(this::openActionChip);
        }
        if (!importBuildingPanel.isAttached()) {
            importBuildingPanel.attach();
            bringShortcutKeysToFront();
        }
    }

    /** 打开某建筑的命令子面板（手动/方块/其他）。 */
    private void openBuildingCommandPanel(BuildingInfo info) {
        if (buildingCommandPanel == null) {
            buildingCommandPanel = new FloatingBuildingCommandPanel(themedContext());
            buildingCommandPanel.setShowToast(this::showToast);
        }
        if (!buildingCommandPanel.isAttached()) {
            buildingCommandPanel.attach();
            bringShortcutKeysToFront();
        }
        buildingCommandPanel.load(info);
    }

    /**
     * 点 add_link：检测映射引擎 + 无障碍权限 → 打开导入操作 chip → 关闭全部面板。
     * 任一权限缺失：Toast 说明，不打开 chip。
     */
    private void openActionChip(BuildingInfo info) {
        boolean engineOk;
        try {
            engineOk = InputModelManager.resolveModel(readSavedInputModel(), this)
                    != InputModelManager.MODEL_NONE;
        } catch (Exception e) {
            AppLogger.w(TAG, "resolve model failed: " + e.getMessage());
            engineOk = false;
        }
        boolean a11yOk = MioAccessibilityService.isServiceEnabled(this)
                && MioAccessibilityService.getInstance() != null;
        boolean gestureOk = hasGestureCapability();

        if (!engineOk || !a11yOk || !gestureOk) {
            StringBuilder sb = new StringBuilder("无法打开：");
            if (!engineOk) sb.append("映射引擎不可用（IME/Shizuku 均未就绪）；");
            if (!a11yOk) sb.append("无障碍服务未开启；");
            if (a11yOk && !gestureOk) {
                sb.append("无障碍缺少手势模拟能力，请到系统设置关闭后重新开启本应用的无障碍服务；");
            }
            showToast(sb.toString());
            if (!a11yOk) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    AppLogger.w(TAG, "open accessibility settings failed: " + e.getMessage());
                }
            }
            return;
        }

        closeAllPanelsForChip();
        showActionChip(info);
    }

    /** 无障碍服务是否具备手势模拟能力（config 缺 canPerformGestures 时为 false）。 */
    private boolean hasGestureCapability() {
        MioAccessibilityService svc = MioAccessibilityService.getInstance();
        if (svc == null) return false;
        try {
            AccessibilityServiceInfo info = svc.getServiceInfo();
            if (info == null) return false;
            int caps;
            if (Build.VERSION.SDK_INT >= 33) {
                caps = info.getCapabilities();
            } else {
                // API 33 起 capabilities 由字段改为方法，旧版本读字段
                caps = AccessibilityServiceInfo.class.getField("capabilities").getInt(info);
            }
            return (caps & AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0;
        } catch (Throwable t) {
            AppLogger.w(TAG, "gesture capability check failed: " + t.getMessage());
            return false;
        }
    }

    /** 关闭全部面板类悬浮窗（保留悬浮球/快捷键栏/状态栏等常驻元素）。 */
    private void closeAllPanelsForChip() {
        if (panel != null) panel.close();
        if (settingsSubPanel != null) settingsSubPanel.detach();
        if (controlPanel != null) controlPanel.detach();
        if (commandPanel != null) commandPanel.detach();
        if (toolboxPanel != null) toolboxPanel.detach();
        if (toolboxDetailPanel != null) toolboxDetailPanel.detach();
        if (wsPanel != null) wsPanel.detach();
        if (importBuildingPanel != null) importBuildingPanel.detach();
        if (buildingCommandPanel != null) buildingCommandPanel.detach();
    }

    private void showActionChip(BuildingInfo info) {
        if (actionChip == null) {
            actionChip = new FloatingBuildingActionChip(themedContext());
            actionChip.setShowToast(this::showToast);
            actionChip.setOnPaste(() -> {
                if (actionChipRunner != null) actionChipRunner.executeCurrent();
            });
            actionChip.setOnPrev(() -> {
                if (actionChipRunner != null) {
                    int idx = actionChipRunner.step(-1);
                    actionChip.setProgress(idx, actionChipRunner.getTotal());
                }
            });
            actionChip.setOnNext(() -> {
                if (actionChipRunner != null) {
                    int idx = actionChipRunner.step(1);
                    actionChip.setProgress(idx, actionChipRunner.getTotal());
                }
            });
            actionChip.setOnClose(this::closeActionChip);
        }
        if (actionChipRunner == null) {
            actionChipRunner = new BuildingCommandRunner(this);
            actionChipRunner.setOverlapChecker(xy ->
                    actionChip != null && actionChip.overlaps(xy[0], xy[1]));
            actionChipRunner.setListener(new BuildingCommandRunner.Listener() {
                @Override
                public void onProgress(int currentIndex, int total) {
                    if (actionChip != null) actionChip.setProgress(currentIndex, total);
                }

                @Override
                public void onFailed(String message) {
                    showToast("执行失败：" + message);
                }
            });
        }
        actionChipRunner.bind(info.name, info.commandCount);
        actionChip.load(info);
        if (!actionChip.isAttached()) actionChip.attach();
        bringShortcutKeysToFront();
    }

    private void closeActionChip() {
        if (actionChipRunner != null) actionChipRunner.cancel();
        if (actionChip != null) actionChip.detach();
    }

    @Override
    public void onOpenStatusBarSettings() {
        openSettingsSubPanel(FloatingSettingsSubPanel.Page.STATUS_BAR);
    }

    @Override
    public void onOpenFeatureListSettings() {
        openSettingsSubPanel(FloatingSettingsSubPanel.Page.FEATURE_LIST);
    }

    @Override
    public void onOpenCrosshairSettings() {
        openSettingsSubPanel(FloatingSettingsSubPanel.Page.CROSSHAIR);
    }

    private void openSettingsSubPanel(FloatingSettingsSubPanel.Page page) {
        if (settingsSubPanel == null) {
            settingsSubPanel = new FloatingSettingsSubPanel(themedContext());
            settingsSubPanel.setListener(this);
        }
        settingsSubPanel.load(page);
        if (!settingsSubPanel.isAttached()) {
            settingsSubPanel.attach();
            bringShortcutKeysToFront();
        }
    }

    private void bringShortcutKeysToFront() {
        if (shortcutBar != null) {
            shortcutBar.bringToFront();
        }
    }

    @Override
    public void onActionsLoaded(List<ControlActionItem> items) {
        loadedActions.clear();
        if (items != null) {
            loadedActions.addAll(items);
        }
        if (shortcutDispatcher != null) {
            shortcutDispatcher.setActions(loadedActions);
        }
        refreshShortcutBar();
        syncFeatureListActions();
    }

    @Override
    public void onActionVisibilityChanged(ControlActionItem item, boolean visible) {
        if (!visible && item != null && (item.getTriggerModel() == TriggerModel.SWITCH
                || item.getTriggerModel() == TriggerModel.PRESSURE_PLATE)
                && shortcutDispatcher != null) {
            shortcutDispatcher.stopAction(keyOf(item));
        }
        if (!visible && item != null && dslEventDispatcher != null
                && dslEventDispatcher.isDslShortcut(keyOf(item))) {
            dslEventDispatcher.clearTrigger(keyOf(item));
        }
        if (item != null && shortcutDispatcher != null) {
            ShortcutEventType eventType = visible
                    ? ShortcutEventType.REGISTER
                    : ShortcutEventType.UNREGISTER;
            shortcutDispatcher.fireEvent(keyOf(item), eventType);
        }
        refreshShortcutBar();
        syncFeatureListActions();
        if (actionToastEnabled && item != null && item.getTitle() != null) {
            String msg = visible ? item.getTitle() + " 已开启" : item.getTitle() + " 已关闭";
            showToast(msg);
        }
    }

    @Override
    public void onGlobalKeyHoldDuration(long duration) {
        if (shortcutDispatcher != null) {
            shortcutDispatcher.setGlobalKeyHoldDuration(duration);
        }
    }

    private String keyOf(ControlActionItem item) {
        return item.getPackageName() + "." + item.getActionId();
    }

    private void refreshShortcutBar() {
        if (shortcutBar != null && !allHidden) {
            shortcutBar.refresh(loadedActions, shortcutState);
        }
        boolean isVisible = false;
        for (ControlActionItem item : loadedActions) {
            if (Boolean.TRUE.equals(shortcutState.get(keyOf(item)))) {
                isVisible = true;
                break;
            }
        }
        if (shortcutDispatcher != null) {
            if (isVisible && !wasShortcutBarVisible) {
                shortcutDispatcher.fireGlobalEvent(ShortcutEventType.SHORTCUT_BAR_SHOW);
            } else if (!isVisible && wasShortcutBarVisible) {
                shortcutDispatcher.fireGlobalEvent(ShortcutEventType.SHORTCUT_BAR_HIDE);
            }
        }
        wasShortcutBarVisible = isVisible;
    }

    private void onShortcutKeyAction(String key, TriggerModel triggerModel, boolean active) {
        // 拦截 DSL 插件快捷键
        if (dslEventDispatcher != null && dslEventDispatcher.isDslShortcut(key)) {
            // BUTTON 的按下/松开已由 onShortcutKeyEvent 以 KEY_PRESS/KEY_RELEASE 事件送达，
            // 这里跳过避免重复触发；SWITCH/PRESSURE_PLATE 仍由本回调驱动
            if (triggerModel != TriggerModel.BUTTON) {
                dslEventDispatcher.handleTriggerEvent(key, triggerModel, active);
            }
            setFeatureListActive(key, active);
            return;
        }
        if (shortcutDispatcher != null) {
            shortcutDispatcher.handleKeyAction(key, triggerModel, active);
        }
        setFeatureListActive(key, active);
    }

    private void onShortcutKeyEvent(String key, TriggerModel triggerModel, ShortcutEventType eventType) {
        // BUTTON 模式 DSL：按下/松开由事件驱动（KEY_PRESS/BUTTON_PRESS→down、KEY_RELEASE/BUTTON_RELEASE→up）
        if (dslEventDispatcher != null && dslEventDispatcher.isDslShortcut(key)
                && triggerModel == TriggerModel.BUTTON) {
            if (eventType == ShortcutEventType.KEY_PRESS
                    || eventType == ShortcutEventType.BUTTON_PRESS) {
                dslEventDispatcher.handleTriggerEvent(key, triggerModel, true);
                setFeatureListActive(key, true);
            } else if (eventType == ShortcutEventType.KEY_RELEASE
                    || eventType == ShortcutEventType.BUTTON_RELEASE) {
                dslEventDispatcher.handleTriggerEvent(key, triggerModel, false);
                setFeatureListActive(key, false);
            }
            return;
        }
        if (shortcutDispatcher != null) {
            shortcutDispatcher.fireEvent(key, eventType);
        }
    }

    private void syncFeatureListActions() {
        List<ControlActionItem> visible = new ArrayList<>();
        for (ControlActionItem item : loadedActions) {
            if (Boolean.TRUE.equals(shortcutState.get(keyOf(item)))) {
                visible.add(item);
            }
        }
        if (featureList != null) {
            featureList.updateActions(visible);
        }
        if (legacyFeatureList != null) {
            legacyFeatureList.updateActions(visible);
        }
    }

    private void setFeatureListActive(String key, boolean active) {
        if (featureList != null) {
            featureList.setActiveState(key, active);
        }
        if (legacyFeatureList != null) {
            legacyFeatureList.setActiveState(key, active);
        }
    }

    private void bindShizukuIfNeeded() {
        try {
            int model = InputModelManager.resolveModel(readSavedInputModel(), this);
            if (model == InputModelManager.MODEL_SHIZUKU
                    && ShizukuHelper.getInstance().isRunning()
                    && ShizukuHelper.getInstance().isGranted()
                    && !ShizukuHelper.getInstance().isServiceBound()) {
                ShizukuHelper.getInstance().bindShellService();
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "bindShizukuIfNeeded failed: " + e.getMessage());
        }
    }

    private int readSavedInputModel() {
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
        return saved;
    }

    @Override
    public void onToggleWatermark(boolean enabled) {
        persistWatermarkEnabled(enabled);
        if (enabled) {
            if (allHidden) return;
            if (watermark == null) {
                watermark = new FloatingWatermark(themedContext());
            }
            if (!watermark.isAttached()) watermark.attach();
        } else {
            if (watermark != null && watermark.isAttached()) {
                watermark.detach();
            }
        }
    }

    @Override
    public void onToggleStatusBar(boolean enabled) {
        persistStatusBarEnabled(enabled);
        if (enabled) {
            if (allHidden) return;
            setStatusBarStyle(false);
            if (statusBar == null) {
                statusBar = new FloatingStatusBar(themedContext());
            }
            if (isLandscape() && !statusBar.isAttached()) {
                statusBar.attach();
            }
            if (shortcutDispatcher != null) {
                shortcutDispatcher.setStatusBar(null);
            }
        } else {
            if (!isNewStatusBarEnabled() && statusBar != null && statusBar.isAttached()) {
                statusBar.detach();
            }
        }
    }

    @Override
    public void onToggleNewStatusBar(boolean enabled) {
        persistNewStatusBarEnabled(enabled);
        if (enabled) {
            if (allHidden) return;
            setStatusBarStyle(true);
            if (statusBar == null) {
                statusBar = new FloatingStatusBar(themedContext());
            }
            if (isLandscape() && !statusBar.isAttached()) {
                statusBar.attach();
            }
            if (shortcutDispatcher != null) {
                shortcutDispatcher.setStatusBar(statusBar);
            }
        } else {
            if (shortcutDispatcher != null) {
                shortcutDispatcher.setStatusBar(null);
            }
            if (!isStatusBarEnabled() && statusBar != null && statusBar.isAttached()) {
                statusBar.detach();
            }
        }
    }

    private void setStatusBarStyle(boolean useNew) {
        if (statusBar == null) {
            statusBar = new FloatingStatusBar(themedContext());
        }
        statusBar.setUseNewStyle(useNew);
    }

    @Override
    public void onToggleFeatureList(boolean enabled) {
        persistFeatureListEnabled(enabled);
        if (enabled) {
            if (allHidden) return;
            if (isLandscape()) {
                if (FloatingFeatureList.savedUseNewFeatureList()) {
                    if (featureList == null) {
                        featureList = new FloatingFeatureList(themedContext());
                    }
                    if (!featureList.isAttached()) {
                        featureList.attach();
                    }
                } else {
                    if (legacyFeatureList == null) {
                        legacyFeatureList = new FloatingFeatureListView(themedContext());
                    }
                    if (!legacyFeatureList.isAttached()) {
                        legacyFeatureList.attach();
                    }
                }
                syncFeatureListActions();
            }
        } else {
            if (featureList != null && featureList.isAttached()) {
                featureList.detach();
            }
            if (legacyFeatureList != null && legacyFeatureList.isAttached()) {
                legacyFeatureList.detach();
            }
        }
    }

    @Override
    public void onToggleEditMode(boolean enabled) {
        isEditMode = enabled;
        if (shortcutBar != null) {
            shortcutBar.setEditMode(enabled);
        }
        if (crosshair != null) {
            crosshair.setEditMode(enabled);
        }
    }

    @Override
    public void onToggleAllHidden(boolean enabled) {
        setAllHidden(enabled);
    }

    private void setAllHidden(boolean hidden) {
        allHidden = hidden;
        if (hidden) {
            if (shortcutBar != null) shortcutBar.detach();
            if (watermark != null) watermark.detach();
            if (statusBar != null) statusBar.detach();
            if (featureList != null) featureList.detach();
            if (legacyFeatureList != null) legacyFeatureList.detach();
            if (cpsIndicator != null) cpsIndicator.detach();
            if (crosshair != null) crosshair.detach();
            if (floatToast != null) floatToast.dismiss();
            if (panel != null) panel.close();
            if (controlPanel != null) controlPanel.detach();
            if (commandPanel != null) commandPanel.detach();
            if (toolboxPanel != null) toolboxPanel.detach();
            if (toolboxDetailPanel != null) toolboxDetailPanel.detach();
            wasShortcutBarVisible = false;
        } else {
            if (isWatermarkEnabled()) {
                if (watermark == null) {
                    watermark = new FloatingWatermark(themedContext());
                }
                if (!watermark.isAttached()) watermark.attach();
            }
            if (isStatusBarEnabled() || isNewStatusBarEnabled()) {
                if (statusBar == null) {
                    statusBar = new FloatingStatusBar(themedContext());
                }
                if (isLandscape() && !statusBar.isAttached()) {
                    statusBar.attach();
                }
            }
            if (isNewStatusBarEnabled() && shortcutDispatcher != null) {
                shortcutDispatcher.setStatusBar(statusBar);
            }
            if (isLandscape() && isFeatureListEnabled()) {
                if (FloatingFeatureList.savedUseNewFeatureList()) {
                    if (featureList == null) {
                        featureList = new FloatingFeatureList(themedContext());
                    }
                    if (!featureList.isAttached()) {
                        featureList.attach();
                    }
                } else {
                    if (legacyFeatureList == null) {
                        legacyFeatureList = new FloatingFeatureListView(themedContext());
                    }
                    if (!legacyFeatureList.isAttached()) {
                        legacyFeatureList.attach();
                    }
                }
                syncFeatureListActions();
            }
            if (isCpsIndicatorEnabled()) {
                if (cpsIndicator == null) {
                    cpsIndicator = new FloatingCpsIndicator(themedContext());
                }
                if (!cpsIndicator.isAttached()) cpsIndicator.attach();
                if (shortcutDispatcher != null) {
                    shortcutDispatcher.setCpsIndicator(cpsIndicator);
                }
            }
            if (isCrosshairEnabled()) {
                if (crosshair == null) {
                    crosshair = new FloatingCrosshair(themedContext());
                }
                if (!crosshair.isAttached()) crosshair.attach();
            }
            refreshShortcutBar();
        }
    }

    private boolean imeRestoreAttempted = false;

    @Override
    public void onExitService() {
        if (controlPanel != null) {
            controlPanel.detach();
        }
        if (commandPanel != null) {
            commandPanel.detach();
        }
        if (toolboxPanel != null) {
            toolboxPanel.detach();
        }
        if (toolboxDetailPanel != null) {
            toolboxDetailPanel.detach();
        }
        if (panel != null) {
            panel.close();
        }
        restoreImeIfNeeded();
        stopSelf();
    }

    private void restoreImeIfNeeded() {
        if (imeRestoreAttempted) {
            return;
        }
        imeRestoreAttempted = true;
        if (readSavedInputModel() != InputModelManager.MODEL_IME) {
            AppLogger.d(TAG, "restoreImeIfNeeded: not in IME mode, skip");
            return;
        }
        ImeAutoSwitchHelper.restorePreviousIme(this);
    }

    @Override
    public void onSizeChanged(int scalePercent) {
        float scale = Math.max(0.8f, Math.min(1.2f, scalePercent / 100f));
        if (ball != null) ball.updateScale(scale);
        if (watermark != null) watermark.updateScale(scale);
        if (statusBar != null) statusBar.updateScale(scale);
        if (featureList != null) featureList.updateScale(scale);
        if (legacyFeatureList != null) legacyFeatureList.updateScale(scale);
        if (floatToast != null) floatToast.updateScale(scale);
        if (shortcutBar != null) shortcutBar.updateScale(scale);
    }

    @Override
    public void onStatusBarYChanged(int yDp) {
        if (statusBar != null) {
            statusBar.updateY(yDp);
        }
    }

    @Override
    public void onStatusBarAlphaChanged(int alphaPercent) {
        persistStatusBarAlpha(alphaPercent);
        float alpha = alphaPercent / 100f;
        if (ball != null) ball.setWindowAlpha(alpha);
        if (watermark != null) watermark.setWindowAlpha(alpha);
        if (statusBar != null) statusBar.setWindowAlpha(alpha);
        if (featureList != null) featureList.setWindowAlpha(alpha);
        if (legacyFeatureList != null) legacyFeatureList.setWindowAlpha(alpha);
        if (cpsIndicator != null) cpsIndicator.setWindowAlpha(alpha);
        if (floatToast != null) floatToast.setWindowAlpha(alpha);
        if (shortcutBar != null) shortcutBar.updateAlpha(alpha);
    }

    @Override
    public void onFeatureListFontSizeChanged(int sizePx) {
        persistFeatureListFontSize(sizePx);
        FloatingWindowService.applyFeatureListDisplayStyle();
    }

    @Override
    public void onFeatureListRightOffsetChanged(int offsetPx) {
        persistFeatureListRightOffset(offsetPx);
        FloatingWindowService.applyFeatureListDisplayStyle();
    }

    @Override
    public void onFeatureListGlowChanged(boolean enabled) {
        persistFeatureListGlow(enabled);
        FloatingWindowService.applyFeatureListDisplayStyle();
    }

    @Override
    public void onFeatureListGlowStrengthChanged(int strength) {
        persistFeatureListGlowStrength(strength);
        FloatingWindowService.applyFeatureListDisplayStyle();
    }

    @Override
    public void onFeatureListWatermarkChanged(boolean enabled) {
        persistFeatureListWatermark(enabled);
        FloatingWindowService.applyFeatureListDisplayStyle();
    }

    @Override
    public void onNewToastFontSizeChanged(int sizePx) {
        persistNewToastFontSize(sizePx);
        FloatingWindowService.applyNotifyStyle();
    }

    @Override
    public void onNewToastGradientChanged(boolean enabled) {
        persistNewToastGradient(enabled);
        FloatingWindowService.applyNotifyStyle();
    }

    @Override
    public void onSwitchIme() {
        ImeAutoSwitchHelper.showPicker(this);
    }

    private void autoSwitchImeIfNeeded() {
        try {
            int saved = readSavedInputModel();
            if (saved == InputModelManager.MODEL_IME) {
                ImeAutoSwitchHelper.switchToMioIme(this);
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "autoSwitchImeIfNeeded failed: " + e.getMessage());
        }
    }

    @Override
    public void showToast(String text) {
        if (allHidden) {
            return;
        }
        if (isNewToastEnabled() && FloatingFeatureList.savedUseNewFeatureList()
                && featureList != null && featureList.isAttached()) {
            featureList.showNotification(text, 0, 2.0f);
            return;
        }
        if (floatToast != null) {
            floatToast.show(text);
        }
    }

    private void showActionToast(String text, io.takamiya.mio.config.ToastSettings settings) {
        if (allHidden) {
            return;
        }
        if (isNewToastEnabled() && FloatingFeatureList.savedUseNewFeatureList()
                && featureList != null && featureList.isAttached()) {
            float duration = 2.0f;
            if (settings != null && settings.getDurationMs() != null && settings.getDurationMs() >= 0) {
                duration = settings.getDurationMs() / 1000f;
            }
            featureList.showNotification(text, 0, duration);
            return;
        }
        if (floatToast != null) {
            floatToast.show(text, settings);
        }
    }

    public static void applyFeatureListDisplayStyle() {
        if (sInstance != null && FloatingFeatureList.savedUseNewFeatureList()
                && sInstance.featureList != null) {
            sInstance.featureList.setDisplayStyle(FloatingFeatureList.savedDisplayStyle());
            sInstance.featureList.applySavedFeatureListStyle();
        }
    }

    public static void applyFeatureListEngine() {
        if (sInstance != null) {
            sInstance.rebuildFeatureList();
        }
    }

    private void rebuildFeatureList() {
        if (featureList != null && featureList.isAttached()) {
            featureList.detach();
        }
        if (legacyFeatureList != null && legacyFeatureList.isAttached()) {
            legacyFeatureList.detach();
        }
        boolean useNew = FloatingFeatureList.savedUseNewFeatureList();
        if (!useNew && legacyFeatureList == null) {
            legacyFeatureList = new FloatingFeatureListView(themedContext());
        }
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        if (!landscape || !isFeatureListEnabled()) {
            return;
        }
        if (useNew) {
            if (featureList == null) {
                featureList = new FloatingFeatureList(themedContext());
            }
            featureList.attach();
        } else if (legacyFeatureList != null) {
            legacyFeatureList.attach();
        }
        syncFeatureListActions();
    }

    public static void applyNotifyStyle() {
        if (sInstance != null && sInstance.featureList != null) {
            sInstance.featureList.applySavedNotifyStyle();
        }
    }

    public static void applyNewToastLayout() {
        if (sInstance != null && sInstance.featureList != null) {
            sInstance.featureList.applyWindowSize();
        }
    }

    public static void applyNotifyColor() {
        if (sInstance != null && sInstance.featureList != null) {
            FloatingFeatureList.applySavedNotifyColor(sInstance);
        }
    }

    public static void applyShortcutSound() {
        if (sInstance != null && sInstance.shortcutBar != null) {
            sInstance.shortcutBar.reloadSounds();
        }
    }

    public static void applyActionToast(boolean enabled) {
        if (sInstance != null) {
            sInstance.actionToastEnabled = enabled;
        }
    }

    private boolean isNewToastEnabled() {
        return FloatingFeatureList.savedNewToastEnabled();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (ball != null) {
            ball.adaptToOrientation();
        }
        if (shortcutBar != null) {
            shortcutBar.reClampAll();
        }
        if (allHidden) {
            return;
        }
        boolean landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (!landscape) {
            if (controlPanel != null && controlPanel.isAttached()) {
                controlPanel.close();
            }
            if (commandPanel != null && commandPanel.isAttached()) {
                commandPanel.close();
            }
            if (toolboxPanel != null && toolboxPanel.isAttached()) {
                toolboxPanel.close();
            }
            if (toolboxDetailPanel != null && toolboxDetailPanel.isAttached()) {
                toolboxDetailPanel.close();
            }
            if (panel != null && panel.isAttached()) {
                panel.close();
            }
            if (statusBar != null && statusBar.isAttached()) {
                statusBar.detach();
            }
            if (featureList != null && featureList.isAttached()) {
                featureList.detach();
            }
            if (legacyFeatureList != null && legacyFeatureList.isAttached()) {
                legacyFeatureList.detach();
            }
        } else {
            if ((isStatusBarEnabled() || isNewStatusBarEnabled())
                    && statusBar != null && !statusBar.isAttached()) {
                statusBar.attach();
            }
            if (isNewStatusBarEnabled() && shortcutDispatcher != null) {
                shortcutDispatcher.setStatusBar(statusBar);
            }
            if (!isFeatureListEnabled()) {
                return;
            }
            if (FloatingFeatureList.savedUseNewFeatureList()) {
                if (featureList != null && !featureList.isAttached()) {
                    featureList.attach();
                }
            } else if (legacyFeatureList != null && !legacyFeatureList.isAttached()) {
                legacyFeatureList.attach();
            }
            syncFeatureListActions();
        }
    }

    @Override
    public void onDestroy() {
        // 先停引擎（触发 onStateChanged → releaseAllPhysicalKeys 补发松开），再注销监听
        io.takamiya.mio.monitor.InputMonitorEngine.getInstance().stop();
        if (monitorKeyListener != null) {
            InputMonitorEngine.getInstance().removeKeyListener(monitorKeyListener);
            monitorKeyListener = null;
        }
        MioAccessibilityService.setOverlayTokenListener(null);
        if (floatToast != null) {
            floatToast.dismiss();
            floatToast = null;
        }
        if (controlPanel != null) {
            controlPanel.detach();
            controlPanel = null;
        }
        if (commandPanel != null) {
            commandPanel.detach();
            commandPanel = null;
        }
        if (toolboxPanel != null) {
            toolboxPanel.detach();
            toolboxPanel = null;
        }
        if (toolboxDetailPanel != null) {
            toolboxDetailPanel.detach();
            toolboxDetailPanel = null;
        }
        if (wsPanel != null) {
            wsPanel.detach();
            wsPanel = null;
        }
        if (importBuildingPanel != null) {
            importBuildingPanel.detach();
            importBuildingPanel = null;
        }
        if (buildingCommandPanel != null) {
            buildingCommandPanel.detach();
            buildingCommandPanel = null;
        }
        if (actionChipRunner != null) {
            actionChipRunner.cancel();
            actionChipRunner = null;
        }
        if (actionChip != null) {
            actionChip.detach();
            actionChip = null;
        }
        if (shortcutBar != null) {
            if (wasShortcutBarVisible && shortcutDispatcher != null) {
                shortcutDispatcher.fireGlobalEvent(ShortcutEventType.SHORTCUT_BAR_HIDE);
            }
            shortcutBar.detach();
            shortcutBar = null;
        }
        if (shortcutDispatcher != null) {
            shortcutDispatcher.stopAll();
            shortcutDispatcher = null;
        }
        if (panel != null) {
            panel.detach();
            panel = null;
        }
        if (ball != null) {
            ball.detach();
            ball = null;
        }
        if (watermark != null) {
            watermark.detach();
            watermark = null;
        }
        if (statusBar != null) {
            statusBar.detach();
            statusBar = null;
        }
        if (featureList != null) {
            featureList.detach();
            featureList = null;
        }
        if (legacyFeatureList != null) {
            legacyFeatureList.detach();
            legacyFeatureList = null;
        }
        if (cpsIndicator != null) {
            cpsIndicator.detach();
            cpsIndicator = null;
        }
        if (crosshair != null) {
            crosshair.detach();
            crosshair = null;
        }
        // DSL 插件系统清理
        if (dslPluginManager != null) {
            dslPluginManager.detachWsEventListener();
            dslPluginManager.closeAll();
            dslPluginManager = null;
        }
        dslEventDispatcher = null;
        try {
            SdlEngine.flushAllHostData();
        } catch (Exception e) {
            AppLogger.e(TAG, "flushAllHostData failed: " + e.getMessage(), e);
        }
        if (gyroController != null) {
            gyroController.stop();
            gyroController = null;
        }
        running = false;
        restoreImeIfNeeded();
        HomeFragment.notifyServiceStateChanged();
        if (mViewModelStore != null) {
            mViewModelStore.clear();
            mViewModelStore = null;
        }
        mLifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
        if (sInstance == this) {
            sInstance = null;
        }
        super.onDestroy();
        AppLogger.i(TAG, "FloatingWindowService destroyed");
    }

    /**
     * 加载已安装的 DSL 插件
     */
    private void loadDslPlugins() {
        if (dslPluginManager == null) return;
        java.util.List<String> packages = dslPluginManager.getInstalledPackages();
        for (String pkg : packages) {
            try {
                if (!SdlEngine.isEnabled(pkg)) {
                    AppLogger.i(TAG, "loading DSL plugin skipped (disabled): " + pkg);
                    continue;
                }
                AppLogger.i(TAG, "loading DSL plugin: " + pkg);
                loadDslPlugin(pkg);
            } catch (Exception e) {
                AppLogger.e(TAG, "loadDslPlugins: failed to load plugin " + pkg + ", skipping: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 加载单个 DSL 插件（open + 启动 MAINSERVICE 与所有 SHORTCUT_KEY / WS_SERVICE 服务）。 */
    private void loadDslPlugin(String pkg) {
        if (dslPluginManager == null) return;
        try {
            AppLogger.i(TAG, "loading DSL plugin: " + pkg);
            DslPluginHandle handle = dslPluginManager.open(pkg);
            if (handle != null) {
                // 启动 MAINSERVICE 入口
                handle.startMainService();
                // 启动所有 SHORTCUT_KEY / WS_SERVICE 服务
                for (DslPluginHandle.ServiceEntry entry : handle.getServiceEntries()) {
                    if (entry.isShortcutKey() || entry.isWs()) {
                        handle.startService(entry.name);
                    }
                }
                // 生命周期钩子：插件启动（未导出时静默跳过）
                handle.callIfExported("onStart");
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "loadDslPlugin: failed to load plugin " + pkg + ": " + e.getMessage(), e);
        }
    }

    /**
     * 保存配置后重启单个插件：关闭 → 重新加载（仅当插件已启用）。 */
    public void reloadDslPlugin(String packageName) {
        if (dslPluginManager == null || packageName == null) return;
        dslPluginManager.close(packageName);
        try {
            if (SdlEngine.isEnabled(packageName)) {
                loadDslPlugin(packageName);
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "reloadDslPlugin failed: " + packageName + " - " + e.getMessage(), e);
        }
    }

    /**
     * 关闭已加载的插件句柄（禁用插件时调用，同步停止其运行服务）。 */
    public void closeDslPlugin(String packageName) {
        if (dslPluginManager != null) {
            dslPluginManager.close(packageName);
        }
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifecycleRegistry;
    }

    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        if (mViewModelStore == null) {
            mViewModelStore = new ViewModelStore();
        }
        return mViewModelStore;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isWatermarkEnabled() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return true;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_WATERMARK_ENABLED.getValue())) {
                return reg.getBoolean(DataManage.Tag.APP_CONFIG_FLOAT_WATERMARK_ENABLED.getValue());
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load watermark enabled: " + e.getMessage());
        }
        return true;
    }

    private void persistWatermarkEnabled(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_WATERMARK_ENABLED.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save watermark enabled failed: " + e.getMessage(), e);
        }
    }

    private boolean isStatusBarEnabled() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return true;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR.getValue())) {
                return reg.getBoolean(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR.getValue());
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load status bar enabled: " + e.getMessage());
        }
        return true;
    }

    private void persistStatusBarEnabled(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save status bar enabled failed: " + e.getMessage(), e);
        }
    }

    private boolean isNewStatusBarEnabled() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return false;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_NEW_STATUS_BAR.getValue())) {
                return reg.getBoolean(DataManage.Tag.APP_CONFIG_FLOAT_NEW_STATUS_BAR.getValue());
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load new status bar enabled: " + e.getMessage());
        }
        return false;
    }

    private void persistNewStatusBarEnabled(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_NEW_STATUS_BAR.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save new status bar enabled failed: " + e.getMessage(), e);
        }
    }

    private boolean isFeatureListEnabled() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return true;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_FEATURE_LIST.getValue())) {
                return reg.getBoolean(DataManage.Tag.APP_CONFIG_FLOAT_FEATURE_LIST.getValue());
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load feature list enabled: " + e.getMessage());
        }
        return true;
    }

    private void persistFeatureListEnabled(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_FEATURE_LIST.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save feature list enabled failed: " + e.getMessage(), e);
        }
    }

    private boolean isCpsIndicatorEnabled() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return false;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_CPS_INDICATOR.getValue())) {
                return reg.getBoolean(DataManage.Tag.APP_CONFIG_FLOAT_CPS_INDICATOR.getValue());
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load cps indicator enabled: " + e.getMessage());
        }
        return false;
    }

    private void persistCpsIndicatorEnabled(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_CPS_INDICATOR.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save cps indicator enabled failed: " + e.getMessage(), e);
        }
    }

    private boolean isCrosshairEnabled() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return false;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_ENABLED.getValue())) {
                return reg.getBoolean(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_ENABLED.getValue());
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load crosshair enabled: " + e.getMessage());
        }
        return false;
    }

    private void persistCrosshairEnabled(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_ENABLED.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save crosshair enabled failed: " + e.getMessage(), e);
        }
    }

    private boolean loadGyroAimEnabled() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return false;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_GYRO_AIM_ENABLED.getValue())) {
                return reg.getBoolean(DataManage.Tag.APP_CONFIG_FLOAT_GYRO_AIM_ENABLED.getValue());
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load gyro aim enabled: " + e.getMessage());
        }
        return false;
    }

    private void persistGyroAimEnabled(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_GYRO_AIM_ENABLED.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save gyro aim enabled failed: " + e.getMessage(), e);
        }
    }

    private void persistCrosshairSize(int sizeDp) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_SIZE.getValue(), sizeDp);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save crosshair size failed: " + e.getMessage(), e);
        }
    }

    private void persistCrosshairAlpha(int alphaPercent) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_ALPHA.getValue(), alphaPercent);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save crosshair alpha failed: " + e.getMessage(), e);
        }
    }

    public static void applyCrosshairIcon() {
        if (sInstance != null && sInstance.crosshair != null) {
            sInstance.crosshair.loadCustomIcon();
        }
    }

    private boolean loadActionToastEnabled() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return true;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_ACTION_TOAST_ENABLED.getValue())) {
                return reg.getBoolean(DataManage.Tag.APP_CONFIG_ACTION_TOAST_ENABLED.getValue());
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load action toast enabled: " + e.getMessage());
        }
        return true;
    }

    @Override
    public void onActionToastChanged(boolean enabled) {
        persistActionToastEnabled(enabled);
        actionToastEnabled = enabled;
    }

    @Override
    public void onToggleCpsIndicator(boolean enabled) {
        persistCpsIndicatorEnabled(enabled);
        if (enabled) {
            if (allHidden) return;
            if (cpsIndicator == null) {
                cpsIndicator = new FloatingCpsIndicator(themedContext());
            }
            if (!cpsIndicator.isAttached()) cpsIndicator.attach();
            shortcutDispatcher.setCpsIndicator(cpsIndicator);
        } else {
            shortcutDispatcher.setCpsIndicator(null);
            if (cpsIndicator != null && cpsIndicator.isAttached()) {
                cpsIndicator.detach();
            }
        }
    }

    @Override
    public void onToggleCrosshair(boolean enabled) {
        persistCrosshairEnabled(enabled);
        if (enabled) {
            if (allHidden) return;
            if (crosshair == null) {
                crosshair = new FloatingCrosshair(themedContext());
            }
            if (!crosshair.isAttached()) crosshair.attach();
        } else {
            if (crosshair != null && crosshair.isAttached()) {
                crosshair.detach();
            }
        }
    }

    @Override
    public void onToggleGyroAim(boolean enabled) {
        persistGyroAimEnabled(enabled);
        if (enabled) {
            startGyroAim();
        } else {
            if (gyroController != null) gyroController.stop();
        }
    }

    private void startGyroAim() {
        if (gyroController == null) {
            gyroController = new GyroController(this);
            gyroController.setListener(reason -> {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    showToast(reason);
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> showToast(reason));
                }
            });
        }
        gyroController.start();
    }

    @Override
    public void onCrosshairCenter() {
        if (crosshair != null && crosshair.isAttached()) {
            crosshair.center();
            crosshair.saveCurrentPosition();
        }
    }

    @Override
    public void onCrosshairSizeChanged(int sizeDp) {
        persistCrosshairSize(sizeDp);
        if (crosshair != null) {
            crosshair.setCrosshairSize(sizeDp);
            crosshair.saveCurrentPosition();
        }
    }

    @Override
    public void onCrosshairAlphaChanged(int alphaPercent) {
        persistCrosshairAlpha(alphaPercent);
        if (crosshair != null) {
            crosshair.setCrosshairAlpha(alphaPercent / 100f);
        }
    }

    private void persistActionToastEnabled(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setBoolean(DataManage.Tag.APP_CONFIG_ACTION_TOAST_ENABLED.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save action toast enabled failed: " + e.getMessage(), e);
        }
    }

    private void persistStatusBarAlpha(int alphaPercent) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_ALPHA.getValue(), alphaPercent);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save status bar alpha failed: " + e.getMessage(), e);
        }
    }

    private void persistFeatureListFontSize(int sizePx) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setInt(DataManage.Tag.APP_CONFIG_FEATURE_LIST_FONT_SIZE.getValue(), sizePx);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save feature list font size failed: " + e.getMessage(), e);
        }
    }

    private void persistFeatureListRightOffset(int offsetPx) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setInt(DataManage.Tag.APP_CONFIG_FEATURE_LIST_RIGHT_OFFSET.getValue(), offsetPx);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save feature list right offset failed: " + e.getMessage(), e);
        }
    }

    private void persistFeatureListGlow(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setBoolean(DataManage.Tag.APP_CONFIG_FEATURE_LIST_GLOW.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save feature list glow failed: " + e.getMessage(), e);
        }
    }

    private void persistFeatureListGlowStrength(int strength) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setInt(DataManage.Tag.APP_CONFIG_FEATURE_LIST_GLOW_STRENGTH.getValue(), strength);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save feature list glow strength failed: " + e.getMessage(), e);
        }
    }

    private void persistFeatureListWatermark(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setBoolean(DataManage.Tag.APP_CONFIG_FEATURE_LIST_WATERMARK.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save feature list watermark failed: " + e.getMessage(), e);
        }
    }

    private void persistNewToastFontSize(int sizePx) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setInt(DataManage.Tag.APP_CONFIG_NEW_TOAST_FONT_SIZE.getValue(), sizePx);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save new toast font size failed: " + e.getMessage(), e);
        }
    }

    private void persistNewToastGradient(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setBoolean(DataManage.Tag.APP_CONFIG_NEW_TOAST_GRADIENT.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save new toast gradient failed: " + e.getMessage(), e);
        }
    }

    private void maybeAutoLaunchGame() {
        if (isAutoOpenGameEnabled()) {
            launchGame(this);
        }
    }

    public static void launchGame(Context context) {
        String pkg = loadAutoOpenGamePackage(context);
        if (pkg == null || pkg.isEmpty()) {
            AppLogger.w(TAG, "auto launch skipped, no package configured");
            return;
        }
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launch);
                AppLogger.i(TAG, "auto launch game: " + pkg);
            } else {
                AppLogger.w(TAG, "auto launch failed, package not installed: " + pkg);
        Toast.makeText(context, "自动打开游戏失败：未找到 " + pkg, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "auto launch game failed: " + e.getMessage(), e);
        Toast.makeText(context, "自动打开游戏失败", Toast.LENGTH_SHORT).show();
        }
    }

    private static String loadAutoOpenGamePackage(Context context) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return "";
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_AUTO_OPEN_GAME_PACKAGE.getValue())) {
                return reg.getString(DataManage.Tag.APP_CONFIG_AUTO_OPEN_GAME_PACKAGE.getValue());
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load auto open game package: " + e.getMessage());
        }
        return "";
    }

    private boolean isAutoOpenGameEnabled() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return false;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_AUTO_OPEN_GAME.getValue())) {
                return reg.getBoolean(DataManage.Tag.APP_CONFIG_AUTO_OPEN_GAME.getValue());
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load auto open game: " + e.getMessage());
        }
        return false;
    }

    private Context themedContext() {
        int themeRes = getApplicationInfo().theme;
        return themeRes != 0
                ? new ContextThemeWrapper(this, themeRes)
                : this;
    }

    private boolean isLandscape() {
        return getApplicationContext().getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean isPanelPortraitEnabled() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return false;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_PANEL_PORTRAIT.getValue())) {
                return reg.getBoolean(DataManage.Tag.APP_CONFIG_PANEL_PORTRAIT.getValue());
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load panel portrait: " + e.getMessage());
        }
        return false;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.floating_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.floating_channel_desc));
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.mio)
                .setContentTitle(getString(R.string.floating_notification_title))
                .setContentText(getString(R.string.floating_notification_content))
                .setOngoing(true)
                .build();
    }
}
