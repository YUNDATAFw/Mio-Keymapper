package io.takamiya.mio.dsl.bridge;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import java.util.HashMap;
import java.util.Map;

import cn.fsy.plugin.kore.library.BridgeHandler;
import io.takamiya.mio.gamepad.GyroController;

/**
 * Sensor 命名空间桥接实现。
 * 提供传感器数据读取：姿态、相对偏移、可用性查询。
 */
public class SensorBridge {

    private final Context context;
    private GyroController gyroController;

    public SensorBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 设置 GyroController 引用（由 MioBridgeRegistry 在引擎启动时注入） */
    public void setGyroController(GyroController controller) {
        this.gyroController = controller;
    }

    /**
     * getOrientation - 获取当前绝对姿态
     * 返回: Map { "pitch": Float, "roll": Float, "yaw": Float }
     */
    public BridgeHandler getOrientation() {
        return (args) -> {
            Map<String, Object> result = new HashMap<>();
            if (gyroController != null && gyroController.isRunning()) {
                result.put("pitch", gyroController.getCachedPitch());
                result.put("roll", gyroController.getCachedRoll());
                result.put("yaw", gyroController.getCachedYaw());
            } else {
                result.put("pitch", 0f);
                result.put("roll", 0f);
                result.put("yaw", 0f);
            }
            return result;
        };
    }

    /** getPitch - 当前绝对姿态 pitch（弧度，未运行时为 0） */
    public BridgeHandler getPitch() {
        return (args) -> gyroController != null && gyroController.isRunning()
                ? gyroController.getCachedPitch() : 0f;
    }

    /** getRoll - 当前绝对姿态 roll（弧度，未运行时为 0） */
    public BridgeHandler getRoll() {
        return (args) -> gyroController != null && gyroController.isRunning()
                ? gyroController.getCachedRoll() : 0f;
    }

    /** getYaw - 当前绝对姿态 yaw（弧度，未运行时为 0） */
    public BridgeHandler getYaw() {
        return (args) -> gyroController != null && gyroController.isRunning()
                ? gyroController.getCachedYaw() : 0f;
    }

    /** getDpitch - 相对基准的 pitch 偏移（弧度，未运行时为 0） */
    public BridgeHandler getDpitch() {
        return (args) -> gyroController != null && gyroController.isRunning()
                ? gyroController.getCachedDPitch() : 0f;
    }

    /** getDyaw - 相对基准的 yaw 偏移（弧度，未运行时为 0） */
    public BridgeHandler getDyaw() {
        return (args) -> gyroController != null && gyroController.isRunning()
                ? gyroController.getCachedDYaw() : 0f;
    }

    /**
     * getRelativeOrientation - 获取相对基准偏移
     * 返回: Map { "dPitch": Float, "dRoll": Float, "dYaw": Float }
     */
    public BridgeHandler getRelativeOrientation() {
        return (args) -> {
            Map<String, Object> result = new HashMap<>();
            if (gyroController != null && gyroController.isRunning()) {
                result.put("dPitch", gyroController.getCachedDPitch());
                result.put("dRoll", 0f);
                result.put("dYaw", gyroController.getCachedDYaw());
            } else {
                result.put("dPitch", 0f);
                result.put("dRoll", 0f);
                result.put("dYaw", 0f);
            }
            return result;
        };
    }

    /**
     * isSensorAvailable - 旋转传感器是否可用
     */
    public BridgeHandler isSensorAvailable() {
        return (args) -> {
            SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            if (sm == null) return false;
            Sensor s = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (s == null) s = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            return s != null;
        };
    }
}
