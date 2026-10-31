package io.takamiya.mio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.databinding.ActivityShizukuManageBinding;
import io.takamiya.mio.floating.FloatingWindowService;
import io.takamiya.mio.gamepad.GamepadButton;
import io.takamiya.mio.gamepad.GamepadEngine;
import io.takamiya.mio.gamepad.GamepadState;
import io.takamiya.mio.shizuku.IShellService;
import io.takamiya.mio.shizuku.ShizukuHelper;
import io.takamiya.mio.utils.SecureStrings;
import rikka.shizuku.Shizuku;

public class ShizukuManageActivity extends AppCompatActivity {

    private static final String TAG = "ShizukuManage";
    private ActivityShizukuManageBinding binding;

    private volatile boolean keyMapTestActive = false;
    private volatile Thread gamepadTestThread;
    private final Queue<int[]> keyMapReceived = new ConcurrentLinkedQueue<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.d(TAG, "onCreate");
        binding = ActivityShizukuManageBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        initShizuku();
        initButtons();
        updateInitialStatus();
    }

    private void initShizuku() {
        AppLogger.d(TAG, "Initializing ShizukuHelper");
        ShizukuHelper.getInstance().init(new ShizukuHelper.ShizukuCallback() {
            @Override
            public void onShizukuReady() {
                AppLogger.d(TAG, "Shizuku permission granted, binding ShellService");
                updateStatus("已授权");
                binding.btnGrantPermission.setText("已授权");
                binding.btnGrantPermission.setEnabled(false);
                binding.cardInstall.setVisibility(android.view.View.GONE);
                updateTestButton(true);
                ShizukuHelper.getInstance().bindShellService();
            }

            @Override
            public void onShizukuDenied() {
                AppLogger.e(TAG, "Shizuku permission denied");
                updateStatus("权限被拒绝");
                updateTestButton(false);
            }

            @Override
            public void onShizukuNotAvailable() {
                AppLogger.e(TAG, "Shizuku not available");
                updateStatus("未运行");
                binding.cardInstall.setVisibility(android.view.View.VISIBLE);
                updateTestButton(false);
            }

            @Override
            public void onBinderDead() {
                AppLogger.e(TAG, "Shizuku binder dead");
                updateStatus("连接断开");
                updateTestButton(false);
            }
        });
    }

    private void initButtons() {
        binding.btnInstallShizuku.setOnClickListener(v -> {
            AppLogger.d(TAG, "Opening Shizuku download page");
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(SecureStrings.shizukuDownloadUrl()));
            startActivity(intent);
        });

        binding.btnGrantPermission.setOnClickListener(v -> {
            AppLogger.d(TAG, "Requesting Shizuku permission");
            ShizukuHelper.getInstance().requestPermission();
        });

        binding.btnTestConnection.setOnClickListener(v -> {
            AppLogger.d(TAG, "Testing Shizuku connection");
            testConnection();
        });

        binding.btnTestKeyMapping.setOnClickListener(v -> {
            AppLogger.d(TAG, "Testing key mapping");
            testKeyMapping();
        });

        binding.btnTestGamepad.setOnClickListener(v -> {
            AppLogger.d(TAG, "Testing gamepad");
            testGamepad();
        });
    }

    private void updateInitialStatus() {
        boolean running = ShizukuHelper.getInstance().isRunning();
        boolean granted = ShizukuHelper.getInstance().isGranted();
        boolean bound = ShizukuHelper.getInstance().isServiceBound();
        AppLogger.d(TAG, "updateInitialStatus: running=" + running + " granted=" + granted + " serviceBound=" + bound);

        if (running) {
            updateStatus("运行中");
            binding.cardInstall.setVisibility(android.view.View.GONE);
            if (granted) {
                binding.btnGrantPermission.setText("已授权");
                binding.btnGrantPermission.setEnabled(false);
                updateTestButton(true);
            } else {
                updateTestButton(false);
            }
        } else {
            updateStatus("未运行");
            binding.cardInstall.setVisibility(android.view.View.VISIBLE);
            updateTestButton(false);
        }

        try {
            String version = "版本:" + Shizuku.getVersion();
            binding.tvShizukuVersion.setText(version);
            AppLogger.d(TAG, version);
        } catch (Exception e) {
            binding.tvShizukuVersion.setText("版本:未知");
            AppLogger.e(TAG, "Failed to get Shizuku version: " + e.getMessage());
        }
    }

    private void updateStatus(String status) {
        binding.tvShizukuStatus.setText(status);
    }

    private void updateTestButton(boolean enabled) {
        binding.btnTestConnection.setEnabled(enabled);
        binding.btnTestConnection.setText(enabled ? "测试" : "未授权");
        binding.btnTestKeyMapping.setEnabled(enabled);
        binding.btnTestGamepad.setEnabled(enabled);
    }

    private void testConnection() {
        binding.tvLogContent.setText("测试中...");

        new Thread(() -> {
            try {
                IShellService service = ShizukuHelper.getInstance().getShellService();
                boolean initialBound = ShizukuHelper.getInstance().isServiceBound();
                AppLogger.d(TAG, "testConnection: service=" + service + " serviceBound=" + initialBound);

                if (service == null) {
                    AppLogger.d(TAG, "ShellService not connected, attempting to bind");
                    ShizukuHelper.getInstance().bindShellService();
                    Thread.sleep(2000);
                    service = ShizukuHelper.getInstance().getShellService();
                    AppLogger.d(TAG, "After retry: service=" + service + " serviceBound=" + ShizukuHelper.getInstance().isServiceBound());
                }

                if (service != null) {
                    AppLogger.d(TAG, "ShellService connected, executing test commands");
                    String whoami = service.execCommand("whoami");
                    String id = service.execCommand("id");
                    AppLogger.d(TAG, "whoami: " + whoami);
                    AppLogger.d(TAG, "id: " + id);
                    AppLogger.d(TAG, "Test connection successful");
                    String log = "whoami: " + whoami + "\nid: " + id;
                    runOnUiThread(() -> {
                        binding.tvLogContent.setText(log);
                        showTestResultDialog(true, log);
                    });
                } else {
                    AppLogger.e(TAG, "ShellService still not connected after retry");
                    final boolean finalBound = ShizukuHelper.getInstance().isServiceBound();
                    runOnUiThread(() -> {
                        String log = "serviceBound=" + finalBound;
                        binding.tvLogContent.setText("Service未连接，请先授权\n" + log);
                        showTestResultDialog(false, log);
                    });
                }
            } catch (Exception e) {
                AppLogger.e(TAG, "Test connection failed: " + e.getMessage());
                runOnUiThread(() -> {
                    binding.tvLogContent.setText("错误: " + e.getMessage());
                    showTestResultDialog(false, e.getMessage());
                });
            }
        }).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (keyMapTestActive) {
            int code = event.getKeyCode();
            int action = event.getAction();
            int meta = event.getMetaState();
            AppLogger.d(TAG, "dispatchKeyEvent: " + keyName(code)
                    + (action == KeyEvent.ACTION_DOWN ? "↓" : "↑")
                    + " meta=0x" + Integer.toHexString(meta));
            keyMapReceived.add(new int[]{code, action, meta});
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void testKeyMapping() {
        binding.tvKeyMapResult.setText("正在检测,请保持本页面在前台...");
        binding.btnTestKeyMapping.setEnabled(false);
        binding.btnTestConnection.setEnabled(false);

        new Thread(() -> {
            try {
                IShellService service = ShizukuHelper.getInstance().getShellService();
                if (service == null) {
                    throw new IllegalStateException("ShellService未连接,请先测试连接");
                }
                keyMapTestActive = true;
                keyMapReceived.clear();
                StringBuilder sb = new StringBuilder();
                int pass = 0;
                int fail = 0;

                int[] keys = buildKeyList();
                sb.append("--- 单键检测 (").append(keys.length).append(" 个) ---\n");
                for (int kc : keys) {
                    keyMapReceived.clear();
                    boolean injected = service.injectKeyEvent(kc, 0);
                    Thread.sleep(60);
                    boolean downOk = containsAction(kc, KeyEvent.ACTION_DOWN);
                    boolean upOk = containsAction(kc, KeyEvent.ACTION_UP);
                    boolean ok = injected && downOk && upOk;
                    if (ok) pass++; else fail++;
                    sb.append(keyName(kc)).append(" : ")
                            .append(!injected ? "注入失败"
                                    : ok ? "OK"
                                    : "未收到(" + receivedCodes() + ")")
                            .append("\n");
                }

                sb.append("\n--- 组合键检测 (修饰键+X) ---\n");
                int[] mods = {KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
                        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
                        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT};
                for (int mod : mods) {
                    keyMapReceived.clear();
                    service.injectCombo(KeyEvent.KEYCODE_X, new int[]{mod});
                    Thread.sleep(100);
                    int xDownMeta = findMeta(KeyEvent.KEYCODE_X, KeyEvent.ACTION_DOWN);
                    boolean modOk = modifierBitPresent(mod, xDownMeta);
                    sb.append(keyName(mod)).append("+X : ")
                            .append(modOk ? "OK" : "X事件缺少修饰位")
                            .append("  X meta=0x").append(Integer.toHexString(xDownMeta))
                            .append("  序列[").append(formatReceived()).append("]")
                            .append("\n");
                }

                keyMapTestActive = false;
                String result = "单键: 通过 " + pass + " / " + (pass + fail) + "\n" + sb;
                runOnUiThread(() -> {
                    binding.tvKeyMapResult.setText(result);
                    binding.btnTestKeyMapping.setEnabled(true);
                    binding.btnTestConnection.setEnabled(true);
                });
            } catch (Exception e) {
                keyMapTestActive = false;
                AppLogger.e(TAG, "testKeyMapping failed: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    binding.tvKeyMapResult.setText("错误: " + e.getMessage());
                    binding.btnTestKeyMapping.setEnabled(true);
                    binding.btnTestConnection.setEnabled(true);
                });
            }
        }).start();
    }

    private void testGamepad() {
        binding.tvGamepadResult.setText("正在初始化虚拟手柄...");
        binding.btnTestGamepad.setEnabled(false);

        Thread thread = new Thread(() -> {
            try {
                GamepadEngine engine = GamepadEngine.get(this);
                engine.init();
                StringBuilder sb = new StringBuilder();

                switch (engine.getState()) {
                    case OPEN:
                        sb.append("虚拟手柄已创建: ").append(engine.getDeviceName())
                                .append(" (VID 045e / PID 028e)\n");
                        GamepadButton[] seq = GamepadButton.values();
                        for (GamepadButton btn : seq) {
                            if (Thread.currentThread().isInterrupted()) return;
                            if (btn == GamepadButton.B) return;
                            engine.pressButton(btn);
                            Thread.sleep(40);
                            engine.releaseButton(btn);
                            Thread.sleep(30);
                            sb.append(btn.getDisplayName()).append("✓ ");
                        }
                        sb.append("\n");
                        sb.append("左摇杆: 上/下/左/右/对角 全幅扫掠\n");
                        stickSweep(engine, true);
                        sb.append("左摇杆✓ ");
                        sb.append("右摇杆: 上/下/左/右/对角 全幅扫掠\n");
                        stickSweep(engine, false);
                        sb.append("右摇杆✓\n");
                        engine.setTrigger(255, 255);
                        Thread.sleep(40);
                        engine.setTrigger(0, 0);
                        engine.setDpad(1, 0);
                        Thread.sleep(40);
                        engine.setDpad(-1, 0);
                        Thread.sleep(40);
                        engine.setDpad(0, 1);
                        Thread.sleep(40);
                        engine.setDpad(0, -1);
                        Thread.sleep(40);
                        engine.setDpad(0, 0);
                        sb.append("摇杆/扳机/十字 轴测试 OK\n");
                        sb.append("可用 adb shell getevent -lt 观察事件\n");
                        break;
                    case FALLBACK_KEY_INJECTION:
                        sb.append("/dev/uinput 不可用 (errno=").append(engine.getLastError())
                                .append("), 已降级为 SOURCE_GAMEPAD 注入\n");
                        engine.pressButton(GamepadButton.A);
                        Thread.sleep(50);
                        engine.releaseButton(GamepadButton.A);
                        sb.append("A键 降级注入测试完成\n");
                        break;
                    case UINPUT_UNAVAILABLE:
                        sb.append("GamepadDaemon 未连接, 请先授权 Shizuku 后重试\n");
                        break;
                    case UNINITIALIZED:
                    default:
                        sb.append("未初始化 (Shizuku 状态: ")
                                .append(ShizukuHelper.getInstance().isGranted() ? "已授权" : "未授权")
                                .append(")\n");
                        break;
                }

                if (!Thread.currentThread().isInterrupted() && !isFinishing()) {
                    runOnUiThread(() -> binding.tvGamepadResult.setText(sb.toString()));
                }
            } catch (InterruptedException e) {
                AppLogger.w(TAG, "testGamepad interrupted, stopping");
            } catch (Exception e) {
                AppLogger.e(TAG, "testGamepad failed", e);
                if (!isFinishing()) {
                    runOnUiThread(() -> binding.tvGamepadResult.setText("错误: " + e.getMessage()));
                }
            } finally {
                GamepadEngine.get(this).destroy();
                if (!isFinishing()) {
                    runOnUiThread(() -> binding.btnTestGamepad.setEnabled(true));
                }
            }
        }, "gamepad-test");
        gamepadTestThread = thread;
        thread.start();
    }

    private void stickSweep(GamepadEngine engine, boolean left) throws InterruptedException {
        sendStick(engine, left, 0, -32767);    // 全上
        sendStick(engine, left, 0, 32767);     // 全下
        sendStick(engine, left, 0, 0);         // 回中
        sendStick(engine, left, -32767, 0);    // 全左
        sendStick(engine, left, 32767, 0);     // 全右
        sendStick(engine, left, 0, 0);         // 回中
        sendStick(engine, left, -32767, -32767); // 左上
        sendStick(engine, left, 32767, 32767);   // 右下
        sendStick(engine, left, 0, 0);         // 回中
        sendStick(engine, left, 32767, -32767);  // 右上
        sendStick(engine, left, -32767, 32767);  // 左下
        sendStick(engine, left, 0, 0);         // 回中
    }

    private void sendStick(GamepadEngine engine, boolean left, int x, int y) throws InterruptedException {
        if (left) engine.setLeftStick(x, y);
        else engine.setRightStick(x, y);
        Thread.sleep(50);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    private int[] buildKeyList() {
        int[] list = new int[26 + 10 + 12 + 15];
        int i = 0;
        for (int k = KeyEvent.KEYCODE_A; k <= KeyEvent.KEYCODE_Z; k++) list[i++] = k;
        for (int k = KeyEvent.KEYCODE_0; k <= KeyEvent.KEYCODE_9; k++) list[i++] = k;
        for (int k = KeyEvent.KEYCODE_F1; k <= KeyEvent.KEYCODE_F12; k++) list[i++] = k;
        int[] extra = {KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB,
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
                KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
                KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT};
        for (int k : extra) list[i++] = k;
        return list;
    }

    private boolean containsAction(int keyCode, int action) {
        for (int[] e : keyMapReceived) {
            if (e[0] == keyCode && e[1] == action) return true;
        }
        return false;
    }

    private int findMeta(int keyCode, int action) {
        for (int[] e : keyMapReceived) {
            if (e[0] == keyCode && e[1] == action) return e[2];
        }
        return 0;
    }

    private boolean modifierBitPresent(int modKeyCode, int xDownMeta) {
        switch (modKeyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT: return (xDownMeta & KeyEvent.META_SHIFT_LEFT_ON) != 0;
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return (xDownMeta & KeyEvent.META_SHIFT_RIGHT_ON) != 0;
            case KeyEvent.KEYCODE_CTRL_LEFT: return (xDownMeta & KeyEvent.META_CTRL_LEFT_ON) != 0;
            case KeyEvent.KEYCODE_CTRL_RIGHT: return (xDownMeta & KeyEvent.META_CTRL_RIGHT_ON) != 0;
            case KeyEvent.KEYCODE_ALT_LEFT: return (xDownMeta & KeyEvent.META_ALT_LEFT_ON) != 0;
            case KeyEvent.KEYCODE_ALT_RIGHT: return (xDownMeta & KeyEvent.META_ALT_RIGHT_ON) != 0;
            default: return false;
        }
    }

    private String receivedCodes() {
        StringBuilder s = new StringBuilder();
        for (int[] e : keyMapReceived) {
            s.append(keyName(e[0])).append(e[1] == KeyEvent.ACTION_DOWN ? "↓" : "↑").append(" ");
        }
        return s.toString().trim();
    }

    private String formatReceived() {
        if (keyMapReceived.isEmpty()) return "无事件收到";
        StringBuilder s = new StringBuilder();
        for (int[] e : keyMapReceived) {
            s.append(keyName(e[0])).append(e[1] == KeyEvent.ACTION_DOWN ? "↓" : "↑")
                    .append("[").append(Integer.toHexString(e[2])).append("] ");
        }
        return s.toString().trim();
    }

    private String keyName(int keyCode) {
        try {
            return KeyEvent.keyCodeToString(keyCode);
        } catch (Exception e) {
            return String.valueOf(keyCode);
        }
    }

    private void showTestResultDialog(boolean success, String log) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(success ? "测试成功" : "测试失败")
                .setMessage(log != null ? log : "未知错误")
                .setPositiveButton("确定", null)
                .show();
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isGamepadTesting() && isBackLikeKey(keyCode)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isGamepadTesting() {
        Thread t = gamepadTestThread;
        return t != null && t.isAlive();
    }

    private boolean isBackLikeKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_BUTTON_B
                || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT;
    }

    @Override
    public void onBackPressed() {
        if (isGamepadTesting()) return;
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppLogger.d(TAG, "onDestroy");
        Thread t = gamepadTestThread;
        if (t != null) t.interrupt();
        if (!FloatingWindowService.isRunning()) {
            GamepadEngine.get(this).destroy();
            ShizukuHelper.getInstance().destroy();
        }
    }
}
