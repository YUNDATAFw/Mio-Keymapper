package io.takamiya.mio.gamepad;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;

import cn.panda.utils.library.AppLogger;

/**
 * 陀螺仪瞄准控制器（姿态偏移模型）。
 * 开启后持续把手机姿态相对基准的偏移映射为虚拟手柄右摇杆输出（视角控制）。
 * - 传感器: GAME_ROTATION_VECTOR 优先, ROTATION_VECTOR 回退, SENSOR_DELAY_GAME
 * - 映射: 绕屏幕法线(Z) 偏转 -> 右摇杆 X; 绕屏幕短边(X) 俯仰 -> 右摇杆 Y
 * - 自动回中: 输出持续处于死区内一段时间后, 将基准吸附到当前姿态, 防累积漂移
 * - 轴仅 uinput 通道(OPEN)有效, 降级/IME 模式不产生任何输出
 */
public class GyroController implements SensorEventListener {

    private static final String TAG = "GyroController";

    private static final float DEADBAND_DEG = 1.5f;
    private static final float MAX_DEG = 90.0f;
    private static final float AUTO_CENTER_HOLD_MS = 600f;
    private static final float SCALE = 32767f / MAX_DEG;
    private static final float EMA_ALPHA = 0.4f;
    private static final float DRIFT_RATE_THRESHOLD = 0.3f;

    public interface Listener {
        void onGyroUnavailable(String reason);
    }

    private final Object lock = new Object();
    private final Context appContext;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private HandlerThread sensorThread;
    private Handler sensorHandler;
    private boolean running;
    private boolean started;

    private Listener listener;

    // 校准状态
    private boolean baseReady;
    private float baseYaw;
    private float basePitch;
    private long stableSinceMs;
    private float emaRx;
    private float emaRy;
    private float prevDYaw;
    private float prevDPitch;

    // 缓存的传感器原始值（供 DSL 桥接读取）
    private volatile float cachedYaw;
    private volatile float cachedPitch;
    private volatile float cachedRoll;
    private volatile float cachedDYaw;
    private volatile float cachedDPitch;

    public GyroController(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isRunning() {
        synchronized (lock) {
            return running;
        }
    }

    /**
     * 启动：后台线程拉起虚拟手柄（可能阻塞约 1s），成功后注册传感器。
     * 重复调用安全。
     */
    public void start() {
        synchronized (lock) {
            if (started) return;
            started = true;
        }
        AppLogger.i(TAG, "start: initiating gyro aim");
        Thread worker = new Thread(() -> {
            boolean ready = GamepadEngine.get(appContext).init();
            if (!ready) {
                synchronized (lock) {
                    started = false;
                }
                AppLogger.w(TAG, "start: gamepad engine not ready, state=" + GamepadEngine.get(appContext).getState());
                if (listener != null) {
                    listener.onGyroUnavailable("虚拟手柄未就绪，陀螺仪控制不生效（需 Shizuku + uinput）");
                }
                return;
            }
            synchronized (lock) {
                if (!started) return; // stop() 已在 init 期间被调用
                if (setupSensors()) {
                    running = true;
                    AppLogger.i(TAG, "start: gyro aim running");
                } else {
                    started = false;
                }
            }
        });
        worker.start();
    }

    /**
     * 停止采样, 保留虚拟手柄引擎（不 destroy, 频繁开关不必重开 uinput）。
     */
    public void stop() {
        synchronized (lock) {
            started = false;
            running = false;
            if (sensorManager != null && rotationSensor != null && sensorHandler != null) {
                sensorManager.unregisterListener(this, rotationSensor);
            }
            if (sensorThread != null) {
                sensorThread.quitSafely();
                sensorThread = null;
            }
            sensorHandler = null;
            baseReady = false;
        }
        AppLogger.i(TAG, "stop: gyro aim stopped");
    }

    private boolean setupSensors() {
        try {
            sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager == null) {
                reportUnavailable("系统无传感器服务");
                return false;
            }
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (rotationSensor == null) {
                rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            }
            if (rotationSensor == null) {
                reportUnavailable("本设备无陀螺仪/方向传感器");
                return false;
            }
            sensorThread = new HandlerThread("gyro-aim");
            sensorThread.start();
            sensorHandler = new Handler(sensorThread.getLooper());
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME, sensorHandler);
            return true;
        } catch (Exception e) {
            AppLogger.e(TAG, "setupSensors failed: " + e.getMessage(), e);
            reportUnavailable("陀螺仪初始化失败");
            return false;
        }
    }

    private void reportUnavailable(String reason) {
        AppLogger.w(TAG, "unavailable: " + reason);
        if (listener != null) {
            try {
                listener.onGyroUnavailable(reason);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] matrix = new float[9];
        float[] orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(matrix, event.values);
        SensorManager.getOrientation(matrix, orientation);
        float yaw = (float) Math.toDegrees(orientation[0]);
        float pitch = (float) Math.toDegrees(orientation[1]);
        float roll = (float) Math.toDegrees(orientation[2]);

        // 缓存原始值
        cachedYaw = yaw;
        cachedPitch = pitch;
        cachedRoll = roll;

        if (!baseReady) {
            baseYaw = yaw;
            basePitch = pitch;
            baseReady = true;
            stableSinceMs = System.currentTimeMillis();
            emaRx = 0;
            emaRy = 0;
            prevDYaw = 0;
            prevDPitch = 0;
            return;
        }

        float dYaw = normalizeDeg(yaw - baseYaw);
        float dPitch = normalizeDeg(pitch - basePitch);
        if (Math.abs(dYaw) > MAX_DEG) dYaw = Math.signum(dYaw) * MAX_DEG;
        if (Math.abs(dPitch) > MAX_DEG) dPitch = Math.signum(dPitch) * MAX_DEG;

        // 缓存相对偏移值
        cachedDYaw = dYaw;
        cachedDPitch = dPitch;

        float rx = -dYaw * SCALE;
        float ry = -dPitch * SCALE;
        if (Math.abs(rx) < DEADBAND_DEG * SCALE) rx = 0;
        if (Math.abs(ry) < DEADBAND_DEG * SCALE) ry = 0;

        emaRx = emaRx + EMA_ALPHA * (rx - emaRx);
        emaRy = emaRy + EMA_ALPHA * (ry - emaRy);
        int outRx = Math.round(emaRx);
        int outRy = Math.round(emaRy);

        float driftYaw = Math.abs(dYaw - prevDYaw);
        float driftPitch = Math.abs(dPitch - prevDPitch);
        prevDYaw = dYaw;
        prevDPitch = dPitch;

        synchronized (lock) {
            if (!running) return;
            boolean yawStable = Math.abs(dYaw) < DEADBAND_DEG && driftYaw < DRIFT_RATE_THRESHOLD;
            boolean pitchStable = Math.abs(dPitch) < DEADBAND_DEG && driftPitch < DRIFT_RATE_THRESHOLD;
            if (yawStable && pitchStable) {
                long now = System.currentTimeMillis();
                if (stableSinceMs == 0) stableSinceMs = now;
                if (now - stableSinceMs >= AUTO_CENTER_HOLD_MS) {
                    baseYaw = yaw;
                    basePitch = pitch;
                    outRx = 0;
                    outRy = 0;
                    emaRx = 0;
                    emaRy = 0;
                    prevDYaw = 0;
                    prevDPitch = 0;
                }
            } else {
                stableSinceMs = 0;
            }
            GamepadEngine.get(appContext).setRightStick(outRx, outRy);
        }
    }

    private static float normalizeDeg(float deg) {
        while (deg > 180) deg -= 360;
        while (deg < -180) deg += 360;
        return deg;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // ==================== 缓存数据访问（供 DSL 桥接） ====================

    public float getCachedYaw() { return cachedYaw; }
    public float getCachedPitch() { return cachedPitch; }
    public float getCachedRoll() { return cachedRoll; }
    public float getCachedDYaw() { return cachedDYaw; }
    public float getCachedDPitch() { return cachedDPitch; }
}