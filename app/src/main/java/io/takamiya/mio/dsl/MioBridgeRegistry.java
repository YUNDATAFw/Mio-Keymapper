package io.takamiya.mio.dsl;

import android.content.Context;

import cn.fsy.plugin.kore.library.BridgeHandler;
import cn.fsy.plugin.kore.library.KoreEngine;
import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.dsl.bridge.FloatingBridge;
import io.takamiya.mio.dsl.bridge.GamepadBridge;
import io.takamiya.mio.dsl.bridge.JsonBridge;
import io.takamiya.mio.dsl.bridge.MioConfigBridge;
import io.takamiya.mio.dsl.bridge.MioEngFunBridge;
import io.takamiya.mio.dsl.bridge.SensorBridge;
import io.takamiya.mio.dsl.bridge.WsBridge;

/**
 * Mio 专属桥接注册中心。
 * 在 KoreEngine.init() 之后调用 register()，将所有 Mio 桥接函数注册到 Kore 引擎。
 */
public class MioBridgeRegistry {

    private static final String TAG = "MioBridgeRegistry";

    private static MioEngFunBridge mioEngFunBridge;
    private static GamepadBridge gamepadBridge;
    private static SensorBridge sensorBridge;
    private static FloatingBridge floatingBridge;
    private static JsonBridge jsonBridge;
    private static WsBridge wsBridge;
    private static MioConfigBridge mioConfigBridge;
    private static io.takamiya.mio.dsl.bridge.PermissionBridge permissionBridge;

    private MioBridgeRegistry() {}

    /**
     * 注册所有 Mio 桥接到 KoreEngine。
     * 必须在 KoreEngine.init(context) 之后调用。
     */
    public static void register(Context context) {
        AppLogger.i(TAG, "register: registering Mio bridges");

        mioEngFunBridge = new MioEngFunBridge(context);
        gamepadBridge = new GamepadBridge(context);
        sensorBridge = new SensorBridge(context);
        floatingBridge = new FloatingBridge(context);
        jsonBridge = new JsonBridge();
        wsBridge = new WsBridge();
        mioConfigBridge = new MioConfigBridge();
        permissionBridge = new io.takamiya.mio.dsl.bridge.PermissionBridge(context);

        registerMioEngFun();
        registerGamepad();
        registerSensor();
        registerFloating();
        registerJson();
        registerWs();
        registerMioConfig();
        registerPermission();
        registerSystemVersion(context);
        registerHostData(context);

        AppLogger.i(TAG, "register: all Mio bridges registered");
    }

    // ==================== HostData 自愈 ====================

    private static boolean hostDataWrapped = false;

    /**
     * 包裹引擎内置的 {@code HostData.get}：存储文件损坏（NegativeArraySize/BufferUnderflow 等）
     * 时自动备份隔离损坏文件并重建空存储后重试一次（见 {@link HostDataRepair}）。
     */
    private static void registerHostData(Context context) {
        if (hostDataWrapped) return;
        BridgeHandler original;
        try {
            original = KoreEngine.findBridge("HostData.get");
        } catch (Exception e) {
            original = null;
        }
        if (original == null) {
            AppLogger.w(TAG, "registerHostData: HostData.get not found, skip wrap");
            return;
        }
        hostDataWrapped = true;
        bridge("HostData.get", HostDataRepair.wrap(context, original));
    }

    // ==================== MioEngFun.* ====================

    private static void registerMioEngFun() {
        bridge("MioEngFun.onKeyInit", mioEngFunBridge.onKeyInit());
        bridge("MioEngFun.onPhysicalKeyInit", mioEngFunBridge.onPhysicalKeyInit());
        bridge("MioEngFun.getEngineVersion", mioEngFunBridge.getEngineVersion());
        bridge("MioEngFun.isImeReady", mioEngFunBridge.isImeReady());
        bridge("MioEngFun.isShizukuReady", mioEngFunBridge.isShizukuReady());
        bridge("MioEngFun.isMapReady", mioEngFunBridge.isMapReady());
        bridge("MioEngFun.isGamepadReady", mioEngFunBridge.isGamepadReady());
        bridge("MioEngFun.isSensorReady", mioEngFunBridge.isSensorReady());
        bridge("MioEngFun.isEngineReady", mioEngFunBridge.isEngineReady());
        bridge("MioEngFun.getInputModel", mioEngFunBridge.getInputModel());
        bridge("MioEngFun.isKeyValid", mioEngFunBridge.isKeyValid());
        bridge("MioEngFun.keyDown", mioEngFunBridge.keyDown());
        bridge("MioEngFun.keyUp", mioEngFunBridge.keyUp());
        bridge("MioEngFun.tap", mioEngFunBridge.tap());
        bridge("MioEngFun.tapGroup", mioEngFunBridge.tapGroup());
        bridge("MioEngFun.tapCombo", mioEngFunBridge.tapCombo());
        bridge("MioEngFun.inputText", mioEngFunBridge.inputText());
        bridge("MioEngFun.isTriggerDown", mioEngFunBridge.isTriggerDown());
        bridge("MioEngFun.isTriggerUp", mioEngFunBridge.isTriggerUp());
        bridge("MioEngFun.sleep", mioEngFunBridge.sleep());
        bridge("MioEngFun.showToast", mioEngFunBridge.showToast());
    }

    // ==================== Gamepad.* ====================

    private static void registerGamepad() {
        bridge("Gamepad.setLeftStick", gamepadBridge.setLeftStick());
        bridge("Gamepad.setRightStick", gamepadBridge.setRightStick());
        bridge("Gamepad.setTrigger", gamepadBridge.setTrigger());
        bridge("Gamepad.setDpad", gamepadBridge.setDpad());
        bridge("Gamepad.pressButton", gamepadBridge.pressButton());
        bridge("Gamepad.releaseButton", gamepadBridge.releaseButton());
    }

    // ==================== Sensor.* ====================

    private static void registerSensor() {
        bridge("Sensor.isSensorAvailable", sensorBridge.isSensorAvailable());
        bridge("Sensor.getPitch", sensorBridge.getPitch());
        bridge("Sensor.getRoll", sensorBridge.getRoll());
        bridge("Sensor.getYaw", sensorBridge.getYaw());
        bridge("Sensor.getDpitch", sensorBridge.getDpitch());
        bridge("Sensor.getDyaw", sensorBridge.getDyaw());
        bridge("Sensor.getOrientation", sensorBridge.getOrientation());
        bridge("Sensor.getRelativeOrientation", sensorBridge.getRelativeOrientation());
    }

    // ==================== Floating.* ====================

    private static void registerFloating() {
        bridge("Floating.showToast", floatingBridge.showToast());
        bridge("Floating.setCrosshairVisible", floatingBridge.setCrosshairVisible());
        bridge("Floating.setCrosshairPosition", floatingBridge.setCrosshairPosition());
        bridge("Floating.updateStatusBar", floatingBridge.updateStatusBar());
    }

    // ==================== Json.* ====================

    private static void registerJson() {
        bridge("Json.parse", jsonBridge.parse());
        bridge("Json.getString", jsonBridge.getString());
        bridge("Json.getInt", jsonBridge.getInt());
        bridge("Json.getBool", jsonBridge.getBool());
        bridge("Json.getStringArray", jsonBridge.getStringArray());
    }

    // ==================== Ws.* ====================

    private static void registerWs() {
        bridge("Ws.isRunning", wsBridge.isRunning());
        bridge("Ws.isConnected", wsBridge.isConnected());
        bridge("Ws.getPort", wsBridge.getPort());
        bridge("Ws.getConnectCommand", wsBridge.getConnectCommand());
        bridge("Ws.getPlayerNames", wsBridge.getPlayerNames());
        bridge("Ws.getPlayers", wsBridge.getPlayers());
        bridge("Ws.start", wsBridge.start());
        bridge("Ws.startWithPort", wsBridge.startWithPort());
        bridge("Ws.stop", wsBridge.stop());
        bridge("Ws.sendCommand", wsBridge.sendCommand());
        bridge("Ws.sendChat", wsBridge.sendChat());
        bridge("Ws.sendTell", wsBridge.sendTell());
        bridge("Ws.sendToAll", wsBridge.sendToAll());
        bridge("Ws.sendTitle", wsBridge.sendTitle());
        bridge("Ws.sendActionbar", wsBridge.sendActionbar());
        bridge("Ws.sendTellraw", wsBridge.sendTellraw());
        bridge("Ws.sendChatAs", wsBridge.sendChatAs());
        bridge("Ws.sendMe", wsBridge.sendMe());
        bridge("Ws.playSound", wsBridge.playSound());
        bridge("Ws.stopSound", wsBridge.stopSound());
        bridge("Ws.sendTitleTimes", wsBridge.sendTitleTimes());
        bridge("Ws.clearTitle", wsBridge.clearTitle());
        bridge("Ws.registerFeature", wsBridge.registerFeature());
    }

    // ==================== MioConfig.* ====================

    private static void registerMioConfig() {
        bridge("MioConfig.onSection", mioConfigBridge.onSection());
        bridge("MioConfig.onSwitch", mioConfigBridge.onSwitch());
        bridge("MioConfig.onCheckbox", mioConfigBridge.onCheckbox());
        bridge("MioConfig.onText", mioConfigBridge.onText());
        bridge("MioConfig.onRadio", mioConfigBridge.onRadio());
        bridge("MioConfig.onChip", mioConfigBridge.onChip());
        bridge("MioConfig.onProgress", mioConfigBridge.onProgress());
        bridge("MioConfig.onTextView", mioConfigBridge.onTextView());
        bridge("MioConfig.onButton", mioConfigBridge.onButton());
    }

    // ==================== Permission.* ====================

    /**
     * 注册权限状态查询，并覆盖引擎内置的 Permission.request / Permission.requestMultiple
     * （内置实现默认抛 UNSUPPORTED；同名注册将替换为 Mio 的真实授权交互）。
     */
    private static void registerPermission() {
        bridge("Permission.granted", permissionBridge.granted());
        bridge("Permission.declared", permissionBridge.declared());
        bridge("Permission.status", permissionBridge.status());
        bridge("Permission.request", permissionBridge.request());
        bridge("Permission.requestMultiple", permissionBridge.requestMultiple());
    }

    // ==================== System 版本 ====================

    private static void registerSystemVersion(Context context) {
        final android.content.Context appCtx = context.getApplicationContext();
        bridge("System.getAppVersion", args -> {
            try {
                android.content.pm.PackageInfo pi = appCtx.getPackageManager()
                        .getPackageInfo(appCtx.getPackageName(), 0);
                return pi.versionName != null ? pi.versionName : "";
            } catch (Exception e) {
                return "";
            }
        });
        bridge("System.getAppVersionCode", args -> {
            try {
                android.content.pm.PackageInfo pi = appCtx.getPackageManager()
                        .getPackageInfo(appCtx.getPackageName(), 0);
                return (int) pi.getLongVersionCode();
            } catch (Exception e) {
                return 0;
            }
        });
    }

    // ==================== 工具 ====================

    private static void bridge(String hostId, BridgeHandler handler) {
        try {
            KoreEngine.bridge(hostId, handler);
        } catch (Exception e) {
            AppLogger.e(TAG, "register bridge failed: " + hostId + " - " + e.getMessage());
        }
    }

    // ==================== Getter ====================

    public static MioEngFunBridge getMioEngFunBridge() { return mioEngFunBridge; }
    public static GamepadBridge getGamepadBridge() { return gamepadBridge; }
    public static SensorBridge getSensorBridge() { return sensorBridge; }
    public static FloatingBridge getFloatingBridge() { return floatingBridge; }
    public static JsonBridge getJsonBridge() { return jsonBridge; }
    public static WsBridge getWsBridge() { return wsBridge; }
    public static MioConfigBridge getMioConfigBridge() { return mioConfigBridge; }
    public static io.takamiya.mio.dsl.bridge.PermissionBridge getPermissionBridge() { return permissionBridge; }
}
