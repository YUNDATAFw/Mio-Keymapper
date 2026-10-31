package io.takamiya.mio;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.databinding.ActivityGyroTestBinding;

/**
 * 陀螺仪测试页：
 * 读取方向传感器(Rotation Vector)得到绝对欧拉角，
 * 读取陀螺仪(Gyroscope)得到三轴角速度并做积分累积，
 * 支持「校准初始位置」(以当前姿态为基准)与「重置初始位置」(恢复页面打开时的初始姿态)。
 */
public class GyroTestActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "GyroTest";

    private ActivityGyroTestBinding binding;

    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private Sensor rotationSensor;

    // 取向传感器原始姿态(latest)
    private double curPitch;
    private double curRoll;
    private double curYaw;
    private boolean hasRotation = false;

    // 陀螺仪 latest 角速度(°/s)
    private double gx;
    private double gy;
    private double gz;
    private boolean hasGyro = false;
    private long lastGyroTs = 0;

    // 陀螺仪积分累积角度
    private double accX;
    private double accY;
    private double accZ;

    // 基准:页面打开时的初始姿态 与 当前基准(可被校准覆盖)
    private double startPitch;
    private double startRoll;
    private double startYaw;
    private double basePitch;
    private double baseRoll;
    private double baseYaw;
    private boolean baseReady = false;

    private long pageOpenTime = System.currentTimeMillis();
    private long lastCalibrateTime = 0;
    private long lastUiUpdateMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.d(TAG, "onCreate");

        binding = ActivityGyroTestBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnCalibrate.setOnClickListener(v -> calibrateNow());
        binding.btnReset.setOnClickListener(v -> resetNow());

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationSensor == null) {
                rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            }
        }
        refreshAvailability();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager == null) return;
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
        if (gyroSensor != null) {
            sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void refreshAvailability() {
        boolean gyroOk = gyroSensor != null;
        boolean rotationOk = rotationSensor != null;
        AppLogger.d(TAG, "refreshAvailability: gyro=" + gyroOk + " rotation=" + rotationOk);

        if (!gyroOk && !rotationOk) {
            binding.tvGyroDetail.setText("本设备无可用陀螺仪/方向传感器");
            binding.tvGyroStatus.setText("不可用");
            binding.tvOrientation.setText("无传感器");
            binding.tvRelative.setText("无传感器");
            binding.tvGyro.setText("无传感器");
            binding.btnCalibrate.setEnabled(false);
            binding.btnReset.setEnabled(false);
            return;
        }
        String detail = (gyroOk ? "陀螺仪:可用 " : "陀螺仪:无 ")
                + (rotationOk ? "方向:可用" : "方向:无");
        binding.tvGyroDetail.setText(detail);
        binding.tvGyroStatus.setText("就绪");
    }

    private void calibrateNow() {
        if (!hasRotation) {
            binding.tvCalStatus.setText("暂无姿态数据,请稍候再试");
            return;
        }
        basePitch = curPitch;
        baseRoll = curRoll;
        baseYaw = curYaw;
        baseReady = true;
        lastCalibrateTime = System.currentTimeMillis();
        accX = 0;
        accY = 0;
        accZ = 0;
        binding.tvCalStatus.setText("已校准:当前姿态已设为初始位置 @ " + timeText(lastCalibrateTime));
        AppLogger.d(TAG, "calibrateNow -> pitch=" + curPitch + " roll=" + curRoll + " yaw=" + curYaw);
    }

    private void resetNow() {
        if (!baseReady) {
            binding.tvCalStatus.setText("尚未采集到初始姿态,请稍候再试");
            return;
        }
        basePitch = startPitch;
        baseRoll = startRoll;
        baseYaw = startYaw;
        accX = 0;
        accY = 0;
        accZ = 0;
        binding.tvCalStatus.setText("已重置:恢复为页面打开时的初始姿态 @ " + timeText(pageOpenTime));
        AppLogger.d(TAG, "resetNow -> 恢复初始 pitch=" + startPitch + " roll=" + startRoll + " yaw=" + startYaw);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gx = Math.toDegrees(event.values[0]);
            gy = Math.toDegrees(event.values[1]);
            gz = Math.toDegrees(event.values[2]);
            hasGyro = true;
            if (lastGyroTs != 0) {
                double dt = (event.timestamp - lastGyroTs) / 1e9;
                if (dt > 0 && dt < 1.0) {
                    accX += gx * dt;
                    accY += gy * dt;
                    accZ += gz * dt;
                }
            }
            lastGyroTs = event.timestamp;
        } else {
            // Rotation Vector / Game Rotation Vector
            float[] rotationMatrix = new float[9];
            float[] orientation = new float[3];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
            double pitch = Math.toDegrees(orientation[1]);
            double roll = Math.toDegrees(orientation[2]);
            double yaw = Math.toDegrees(orientation[0]);

            boolean first = !baseReady;
            if (first) {
                // 页面首次拿到姿态,记录为初始位置
                startPitch = pitch;
                startRoll = roll;
                startYaw = yaw;
                basePitch = startPitch;
                baseRoll = startRoll;
                baseYaw = startYaw;
                baseReady = true;
                AppLogger.d(TAG, "initial pose captured: pitch=" + pitch + " roll=" + roll + " yaw=" + yaw);
            }
            curPitch = pitch;
            curRoll = roll;
            curYaw = yaw;
            hasRotation = true;
        }
        refreshDisplay();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        AppLogger.d(TAG, "onAccuracyChanged: " + sensor.getName() + " accuracy=" + accuracy);
    }

    private void refreshDisplay() {
        long now = System.currentTimeMillis();
        if (now - lastUiUpdateMs < 66) {
            return;
        }
        lastUiUpdateMs = now;
        if (binding == null || isFinishing() || isDestroyed()) return;
        if (hasRotation) {
            binding.tvOrientation.setText(String.format(java.util.Locale.CHINA,
                    "俯仰 Pitch: %7.1f°\n横滚 Roll: %7.1f°\n航向 Yaw: %7.1f°",
                    curPitch, curRoll, curYaw));
            binding.tvRelative.setText(String.format(java.util.Locale.CHINA,
                    "ΔPitch: %7.1f°\nΔRoll:  %7.1f°\nΔYaw:   %7.1f°",
                    normalize180(curPitch - basePitch),
                    normalize180(curRoll - baseRoll),
                    normalize180(curYaw - baseYaw)));
        }
        if (hasGyro) {
            binding.tvGyro.setText(String.format(java.util.Locale.CHINA,
                    "角速度 X: %7.2f  Y: %7.2f  Z: %7.2f (°/s)\n累积角度 X: %7.1f°  Y: %7.1f°  Z: %7.1f°",
                    gx, gy, gz, accX, accY, accZ));
        }
    }

    private static double normalize180(double deg) {
        while (deg > 180) deg -= 360;
        while (deg < -180) deg += 360;
        return deg;
    }

    private static String timeText(long time) {
        return new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(new java.util.Date(time));
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    bars.top,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());
            binding.scrollView.setPadding(
                    binding.scrollView.getPaddingLeft(),
                    binding.scrollView.getPaddingTop(),
                    binding.scrollView.getPaddingRight(),
                    bars.bottom);
            return insets;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
}