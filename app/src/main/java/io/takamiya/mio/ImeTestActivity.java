package io.takamiya.mio;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.databinding.ActivityImeTestBinding;
import io.takamiya.mio.injector.ImeAutoSwitchHelper;
import io.takamiya.mio.injector.ImeKeyInjector;
import io.takamiya.mio.injector.InjectionException;
import io.takamiya.mio.injector.MioImeService;

public class ImeTestActivity extends AppCompatActivity {

    private static final String TAG = "ImeTest";

    private ActivityImeTestBinding binding;

    private ImeKeyInjector injector;
    private volatile boolean realtimeListening = false;
    private volatile boolean keyMapTestActive = false;
    private final Queue<int[]> keyMapReceived = new ConcurrentLinkedQueue<>();
    private final StringBuilder realtimeLog = new StringBuilder();
    private static final int REALTIME_LOG_LIMIT = 200;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long STATUS_REFRESH_INTERVAL_MS = 1000L;
    private final Runnable statusRefresher = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) return;
            refreshStatus();
            handler.postDelayed(this, STATUS_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.d(TAG, "onCreate");

        binding = ActivityImeTestBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        injector = new ImeKeyInjector(this);

        binding.btnSwitchIme.setOnClickListener(v -> switchToMioIme());
        binding.btnRestoreIme.setOnClickListener(v -> {
            AppLogger.d(TAG, "restore/choose IME");
            ImeAutoSwitchHelper.restorePreviousIme(this);
        });
        binding.btnOneKeyTest.setOnClickListener(v -> oneKeyTest());
        binding.btnRealtimeTest.setOnClickListener(v -> toggleRealtime());

        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        handler.removeCallbacks(statusRefresher);
        handler.post(statusRefresher);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(statusRefresher);
    }

    private void refreshStatus() {
        boolean enabled = ImeKeyInjector.isImeEnabled(this);
        boolean current = ImeKeyInjector.isImeCurrent(this);
        boolean running = MioImeService.getInstance() != null;
        boolean connected = running && MioImeService.getInstance().hasInputConnection();
        String currentId = ImeAutoSwitchHelper.getCurrentImeId(this);
        boolean isMioCurrent = currentId != null && currentId.equals(ImeKeyInjector.getMioImeId(this));

        AppLogger.d(TAG, "refreshStatus: enabled=" + enabled + " current=" + current
                + " isMioCurrent=" + isMioCurrent + " running=" + running + " connected=" + connected);

        binding.tvStatusDetail.setText(
                "已启用: " + (enabled ? "是" : "否")
                        + "\n当前输入法: " + (isMioCurrent ? "Mio" : (currentId != null ? currentId : "-"))
                        + "\n服务运行: " + (running ? "是" : "否")
                        + "\n输入连接: " + (connected ? "已连接" : "未连接"));

        if (!enabled) {
            binding.tvImeStatus.setText("未启用");
        } else if (!isMioCurrent) {
            binding.tvImeStatus.setText("非当前");
        } else if (!connected) {
            binding.tvImeStatus.setText("未连接");
        } else {
            binding.tvImeStatus.setText("就绪");
        }
    }

    private void switchToMioIme() {
        AppLogger.d(TAG, "switchToMioIme clicked");
        binding.tvStatusDetail.setText("正在切换到Mio输入法...");
        new Thread(() -> {
            ImeAutoSwitchHelper.switchToMioIme(this);
            boolean ok = ImeAutoSwitchHelper.waitForMioImeCurrent(this, 4000);
            runOnUiThread(() -> {
                refreshStatus();
                if (ok) {
                    showSoftInput();
                }
            });
        }, "ime-switch").start();
    }

    private void showSoftInput() {
        binding.receiverView.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.receiverView, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void oneKeyTest() {
        AppLogger.d(TAG, "oneKeyTest clicked");
        binding.tvOneKeyResult.setText("正在检测,请保持本页面在前台...");
        binding.btnOneKeyTest.setEnabled(false);
        binding.btnOneKeyTest.setText("测试中...");

        new Thread(() -> {
            String initialIme = readCurrentImeId();
            try {
                if (!ImeKeyInjector.isImeCurrent(this)) {
                    AppLogger.d(TAG, "oneKeyTest: switching to Mio IME");
                    ImeAutoSwitchHelper.switchToMioIme(this);
                    if (!ImeAutoSwitchHelper.waitForMioImeCurrent(this, 4000)) {
                        throw new IllegalStateException("切换到Mio输入法超时");
                    }
                }
                if (!waitForInputConnection(6000)) {
                    throw new IllegalStateException("无法建立输入连接,请检查输入法是否已启用");
                }

                keyMapTestActive = true;
                StringBuilder sb = new StringBuilder();
                int pass = 0;
                int fail = 0;
                int[] keys = buildKeyList();

                sb.append("--- 单键检测 (").append(keys.length).append(" 个) ---\n");
                for (int kc : keys) {
                    keyMapReceived.clear();
                    boolean injected;
                    try {
                        injector.pressKey(kc);
                        injected = true;
                    } catch (InjectionException e) {
                        injected = false;
                        AppLogger.e(TAG, "inject " + keyName(kc) + " failed: " + e.getMessage());
                    }
                    Thread.sleep(100);

                    boolean downOk = containsAction(kc, KeyEvent.ACTION_DOWN);
                    boolean upOk = containsAction(kc, KeyEvent.ACTION_UP);
                    boolean ok = injected && downOk && upOk;
                    if (ok) pass++; else fail++;
                    sb.append(keyName(kc)).append(" : ")
                            .append(!injected ? "注入失败"
                                    : ok ? "OK"
                                    : "未收到("
                                    + (!downOk ? "缺DOWN" : "") + (!upOk ? "缺UP" : "")
                                    + ") 实收[" + receivedCodes() + "]")
                            .append("\n");
                }

                keyMapTestActive = false;
                String result = "单键: 通过 " + pass + " / " + (pass + fail) + "\n" + sb;
                runOnUiThread(() -> binding.tvOneKeyResult.setText(result));

                restoreIme(initialIme);
            } catch (Exception e) {
                keyMapTestActive = false;
                AppLogger.e(TAG, "oneKeyTest failed: " + e.getMessage(), e);
                runOnUiThread(() -> binding.tvOneKeyResult.setText("错误: " + e.getMessage()));
                restoreIme(initialIme);
            } finally {
                runOnUiThread(() -> {
                    binding.btnOneKeyTest.setEnabled(true);
                    binding.btnOneKeyTest.setText("一键测试全部按键");
                });
            }
        }, "ime-test").start();
    }

    private void restoreIme(String initialIme) {
        try {
            String mioId = ImeKeyInjector.getMioImeId(this);
            if (initialIme != null && !initialIme.isEmpty() && !initialIme.equals(mioId)) {
                boolean ok = ImeAutoSwitchHelper.restoreIme(initialIme);
                AppLogger.d(TAG, "restoreIme: " + initialIme + " -> " + ok);
                if (!ok) {
                    ImeAutoSwitchHelper.restorePreviousIme(this);
                }
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "restoreIme failed: " + e.getMessage());
        }
    }

    private String readCurrentImeId() {
        String id = ImeAutoSwitchHelper.getCurrentImeId(this);
        return id != null ? id : "";
    }

    private boolean waitForInputConnection(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            MioImeService ime = MioImeService.getInstance();
            if (ime != null && ime.hasInputConnection()) {
                AppLogger.d(TAG, "waitForInputConnection: ready");
                return true;
            }
            runOnUiThread(this::showSoftInput);
            try {
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        AppLogger.w(TAG, "waitForInputConnection: timeout");
        return false;
    }

    private void toggleRealtime() {
        realtimeListening = !realtimeListening;
        AppLogger.d(TAG, "toggleRealtime -> " + realtimeListening);
        if (realtimeListening) {
            realtimeLog.setLength(0);
            binding.tvRealtimeResult.setText("监听中,请在任意窗口按下按键(本页面前台时)...");
            binding.btnRealtimeTest.setText("停止实时监听");
            binding.receiverView.clearFocus();
        } else {
            binding.btnRealtimeTest.setText("开始实时监听");
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        int action = event.getAction();
        int meta = event.getMetaState();
        if (keyMapTestActive) {
            keyMapReceived.add(new int[]{code, action, meta});
        }
        if (realtimeListening) {
            String raw = keyName(code)
                    + (action == KeyEvent.ACTION_DOWN ? "↓" : action == KeyEvent.ACTION_UP ? "↑" : "?")
                    + " meta=0x" + Integer.toHexString(meta);
            AppLogger.d(TAG, "realtime: " + raw);
            appendRealtime(raw);
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean containsAction(int keyCode, int action) {
        for (int[] e : keyMapReceived) {
            if (e[0] == keyCode && e[1] == action) return true;
        }
        return false;
    }

    private String receivedCodes() {
        StringBuilder s = new StringBuilder();
        for (int[] e : keyMapReceived) {
            s.append(keyName(e[0])).append(e[1] == KeyEvent.ACTION_DOWN ? "↓" : e[1] == KeyEvent.ACTION_UP ? "↑" : "?")
                    .append(" ");
        }
        return s.toString().trim();
    }

    private void appendRealtime(String line) {
        if (realtimeLog.length() > 0) {
            realtimeLog.append('\n');
        }
        realtimeLog.append(line);
        int idx = -1;
        int count = 1;
        for (int i = realtimeLog.length() - 1; i >= 0; i--) {
            if (realtimeLog.charAt(i) == '\n') {
                if (count >= REALTIME_LOG_LIMIT) {
                    idx = i;
                    break;
                }
                count++;
            }
        }
        if (idx >= 0) {
            realtimeLog.delete(0, idx + 1);
        }
        runOnUiThread(() -> binding.tvRealtimeResult.setText(realtimeLog.toString()));
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

    private String keyName(int keyCode) {
        try {
            return KeyEvent.keyCodeToString(keyCode);
        } catch (Exception e) {
            return String.valueOf(keyCode);
        }
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
        handler.removeCallbacksAndMessages(null);
        realtimeListening = false;
        keyMapTestActive = false;
        binding = null;
        super.onDestroy();
    }
}