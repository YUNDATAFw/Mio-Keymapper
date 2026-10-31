package io.takamiya.mio.monitor;

import android.os.Handler;
import android.os.Looper;
import android.util.SparseIntArray;
import android.view.KeyEvent;

import java.util.concurrent.CopyOnWriteArrayList;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.shizuku.ShizukuHelper;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

/**
 * 外接键鼠监听引擎（宿主侧入口，内部 API）。
 * <p>
 * 职责：绑定 {@link MonitorDaemon}（Shizuku UserService）、注册回调、
 * evdev 码 → Android keycode 映射、状态机与重连、偏好开关。
 * 事件经 {@link KeyListener} 分发（主线程），本期只做引擎与内部 API，
 * 接 DSL（onKeyOn/onKeyOff）与悬浮窗展示留待后续。
 */
public final class InputMonitorEngine {

    public static final int STATE_IDLE = 0;
    public static final int STATE_STARTING = 1;
    public static final int STATE_RUNNING = 2;
    public static final int STATE_FAILED = 3;

    private static final int MAX_REBIND_ATTEMPTS = 3;
    private static final long REBIND_DELAY_MS = 2000L;

    private static final String TAG = "InputMonitor";

    private static volatile InputMonitorEngine instance;

    public static InputMonitorEngine getInstance() {
        if (instance == null) {
            synchronized (InputMonitorEngine.class) {
                if (instance == null) {
                    instance = new InputMonitorEngine();
                }
            }
        }
        return instance;
    }

    /** 监听事件回调；所有方法在主线程调用。 */
    public interface KeyListener {
        /** 一条按键事件；androidKeyCode=0 表示该 evdev 码无 Android 对应键，用 evdevCode 判定。 */
        void onKeyChanged(int androidKeyCode, int evdevCode, boolean down);

        void onStateChanged(int state, String detail);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<KeyListener> listeners = new CopyOnWriteArrayList<>();
    private final SparseIntArray keycodeByEvdev = buildKeycodeTable();

    private volatile int state = STATE_IDLE;
    private volatile String lastDetail = "";
    private volatile boolean wantRunning = false;
    private int rebindAttempts = 0;

    private final IMonitorCallback callback = new IMonitorCallback.Stub() {
        @Override
        public void onKeyEvent(final int evdevCode, final int value) {
            // binder 线程 → 主线程分发
            mainHandler.post(() -> dispatchKey(evdevCode, value == 1));
        }

        @Override
        public void onMonitorState(final int newState, final String detail) {
            mainHandler.post(() -> setState(newState, detail));
        }
    };

    private final ShizukuHelper.MonitorConnectionListener connectionListener =
            new ShizukuHelper.MonitorConnectionListener() {
                @Override
                public void onMonitorConnected() {
                    mainHandler.post(() -> {
                        if (wantRunning) {
                            activate();
                        }
                    });
                }

                @Override
                public void onMonitorDisconnected() {
                    mainHandler.post(() -> {
                        if (wantRunning) {
                            setState(STATE_FAILED, "监听服务断开");
                            scheduleRebind();
                        }
                    });
                }
            };

    private InputMonitorEngine() {
    }

    /** 随悬浮窗服务启动监听；幂等。未授权 Shizuku 时置 FAILED，不反复重试。 */
    public void start() {
        wantRunning = true;
        rebindAttempts = 0;
        if (state == STATE_RUNNING || state == STATE_STARTING) {
            return;
        }
        ShizukuHelper helper = ShizukuHelper.getInstance();
        helper.setMonitorConnectionListener(connectionListener);
        if (!helper.isGranted()) {
            setState(STATE_FAILED, "Shizuku 未授权，无法启动外接键鼠监听");
            return;
        }
        setState(STATE_STARTING, "连接 Shizuku 监听服务…");
        helper.bindMonitorService();
        if (helper.getMonitorService() != null) {
            activate();
        }
    }

    /** 停止监听并解绑（daemon 连同 getevent 子进程一起销毁）；幂等。 */
    public void stop() {
        wantRunning = false;
        rebindAttempts = 0;
        ShizukuHelper helper = ShizukuHelper.getInstance();
        try {
            IMonitorService svc = helper.getMonitorService();
            if (svc != null) {
                svc.stopMonitor();
            }
        } catch (Throwable t) {
            AppLogger.w(TAG, "stopMonitor failed: " + t);
        }
        helper.unbindMonitorService();
        setState(STATE_IDLE, "已停止");
    }

    private void activate() {
        if (!wantRunning) {
            return;
        }
        IMonitorService svc = ShizukuHelper.getInstance().getMonitorService();
        if (svc == null) {
            return;
        }
        try {
            svc.registerCallback(callback);
            boolean accepted = svc.startMonitor(); // 异步，状态经 onMonitorState 回报
            AppLogger.i(TAG, "startMonitor accepted=" + accepted
                    + " daemonState=" + svc.getState());
            if (!accepted) {
                setState(STATE_FAILED, "监听服务拒绝启动");
            }
        } catch (Throwable t) {
            AppLogger.e(TAG, "activate failed", t);
            setState(STATE_FAILED, "启动监听失败：" + t.getMessage());
            scheduleRebind();
        }
    }

    private void scheduleRebind() {
        if (rebindAttempts >= MAX_REBIND_ATTEMPTS) {
            AppLogger.w(TAG, "rebind abandoned after " + rebindAttempts + " attempts");
            return;
        }
        rebindAttempts++;
        AppLogger.i(TAG, "rebind in " + REBIND_DELAY_MS + "ms (attempt " + rebindAttempts + ")");
        mainHandler.postDelayed(() -> {
            if (wantRunning) {
                ShizukuHelper.getInstance().bindMonitorService();
            }
        }, REBIND_DELAY_MS);
    }

    private void dispatchKey(int evdevCode, boolean down) {
        int androidKeyCode = keycodeByEvdev.get(evdevCode, 0);
        for (KeyListener l : listeners) {
            l.onKeyChanged(androidKeyCode, evdevCode, down);
        }
    }

    private void setState(int newState, String detail) {
        boolean changed = state != newState;
        state = newState;
        lastDetail = detail == null ? "" : detail;
        if (newState == STATE_RUNNING) {
            rebindAttempts = 0;
        }
        if (changed) {
            AppLogger.i(TAG, "state=" + newState + " detail=" + lastDetail);
        }
        for (KeyListener l : listeners) {
            l.onStateChanged(newState, lastDetail);
        }
    }

    public void addKeyListener(KeyListener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public void removeKeyListener(KeyListener listener) {
        listeners.remove(listener);
    }

    public int getState() {
        return state;
    }

    public String getLastDetail() {
        return lastDetail;
    }

    public boolean isRunning() {
        return state == STATE_RUNNING;
    }

    /** 是否随悬浮窗服务自动启动（设置页开关，默认开）。 */
    public boolean isPrefEnabled() {
        try {
            Registry reg = RegistryUtils.get();
            if (reg == null) {
                return true;
            }
            String key = DataManage.Tag.APP_CONFIG_INPUT_MONITOR_ENABLED.getValue();
            if (reg.exists(key)) {
                return reg.getBoolean(key);
            }
        } catch (Throwable t) {
            AppLogger.w(TAG, "read pref failed: " + t);
        }
        return true;
    }

    public void setPrefEnabled(boolean enabled) {
        try {
            Registry reg = RegistryUtils.get();
            if (reg == null) {
                return;
            }
            reg.setBoolean(DataManage.Tag.APP_CONFIG_INPUT_MONITOR_ENABLED.getValue(), enabled);
            reg.saveAsync();
        } catch (Throwable t) {
            AppLogger.e(TAG, "save pref failed: " + t, t);
        }
    }

    /**
     * evdev 码 → Android keycode。键鼠部分键值依据 linux/input-event-codes.h
     * 与 ShellService.scanCodeFor 的反向对应；Android 平台没有鼠标键 keycode
     * （鼠标按钮走 MotionEvent），BTN_LEFT/RIGHT/MIDDLE 借用 BUTTON_1/2/3，
     * 原始 evdev 码始终经 {@link KeyListener#onKeyChanged} 透传。
     * 未列出的码以 androidKeyCode=0 透传。
     */
    private static SparseIntArray buildKeycodeTable() {
        SparseIntArray m = new SparseIntArray();
        m.put(1, KeyEvent.KEYCODE_ESCAPE);
        m.put(2, KeyEvent.KEYCODE_1);
        m.put(3, KeyEvent.KEYCODE_2);
        m.put(4, KeyEvent.KEYCODE_3);
        m.put(5, KeyEvent.KEYCODE_4);
        m.put(6, KeyEvent.KEYCODE_5);
        m.put(7, KeyEvent.KEYCODE_6);
        m.put(8, KeyEvent.KEYCODE_7);
        m.put(9, KeyEvent.KEYCODE_8);
        m.put(10, KeyEvent.KEYCODE_9);
        m.put(11, KeyEvent.KEYCODE_0);
        m.put(12, KeyEvent.KEYCODE_MINUS);
        m.put(13, KeyEvent.KEYCODE_EQUALS);
        m.put(14, KeyEvent.KEYCODE_DEL);
        m.put(15, KeyEvent.KEYCODE_TAB);
        String qwerty = "QWERTYUIOP";
        for (int i = 0; i < qwerty.length(); i++) {
            m.put(16 + i, KeyEvent.KEYCODE_A + qwerty.charAt(i) - 'A');
        }
        m.put(26, KeyEvent.KEYCODE_LEFT_BRACKET);
        m.put(27, KeyEvent.KEYCODE_RIGHT_BRACKET);
        m.put(28, KeyEvent.KEYCODE_ENTER);
        m.put(29, KeyEvent.KEYCODE_CTRL_LEFT);
        String asdf = "ASDFGHJKL";
        for (int i = 0; i < asdf.length(); i++) {
            m.put(30 + i, KeyEvent.KEYCODE_A + asdf.charAt(i) - 'A');
        }
        m.put(39, KeyEvent.KEYCODE_SEMICOLON);
        m.put(40, KeyEvent.KEYCODE_APOSTROPHE);
        m.put(41, KeyEvent.KEYCODE_GRAVE);
        m.put(42, KeyEvent.KEYCODE_SHIFT_LEFT);
        m.put(43, KeyEvent.KEYCODE_BACKSLASH);
        String zxcv = "ZXCVBNM";
        for (int i = 0; i < zxcv.length(); i++) {
            m.put(44 + i, KeyEvent.KEYCODE_A + zxcv.charAt(i) - 'A');
        }
        m.put(51, KeyEvent.KEYCODE_COMMA);
        m.put(52, KeyEvent.KEYCODE_PERIOD);
        m.put(53, KeyEvent.KEYCODE_SLASH);
        m.put(54, KeyEvent.KEYCODE_SHIFT_RIGHT);
        m.put(55, KeyEvent.KEYCODE_NUMPAD_MULTIPLY);
        m.put(56, KeyEvent.KEYCODE_ALT_LEFT);
        m.put(57, KeyEvent.KEYCODE_SPACE);
        m.put(58, KeyEvent.KEYCODE_CAPS_LOCK);
        for (int i = 0; i < 10; i++) { // F1..F10
            m.put(59 + i, KeyEvent.KEYCODE_F1 + i);
        }
        m.put(69, KeyEvent.KEYCODE_NUM_LOCK);
        m.put(70, KeyEvent.KEYCODE_SCROLL_LOCK);
        int[] kp789 = {KeyEvent.KEYCODE_NUMPAD_7, KeyEvent.KEYCODE_NUMPAD_8, KeyEvent.KEYCODE_NUMPAD_9};
        for (int i = 0; i < 3; i++) {
            m.put(71 + i, kp789[i]);
        }
        m.put(74, KeyEvent.KEYCODE_NUMPAD_SUBTRACT);
        int[] kp456 = {KeyEvent.KEYCODE_NUMPAD_4, KeyEvent.KEYCODE_NUMPAD_5, KeyEvent.KEYCODE_NUMPAD_6};
        for (int i = 0; i < 3; i++) {
            m.put(75 + i, kp456[i]);
        }
        m.put(78, KeyEvent.KEYCODE_NUMPAD_ADD);
        int[] kp123 = {KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.KEYCODE_NUMPAD_2, KeyEvent.KEYCODE_NUMPAD_3};
        for (int i = 0; i < 3; i++) {
            m.put(79 + i, kp123[i]);
        }
        m.put(82, KeyEvent.KEYCODE_NUMPAD_0);
        m.put(83, KeyEvent.KEYCODE_NUMPAD_DOT);
        m.put(87, KeyEvent.KEYCODE_F11);
        m.put(88, KeyEvent.KEYCODE_F12);
        m.put(96, KeyEvent.KEYCODE_NUMPAD_ENTER);
        m.put(97, KeyEvent.KEYCODE_CTRL_RIGHT);
        m.put(98, KeyEvent.KEYCODE_NUMPAD_DIVIDE);
        m.put(99, KeyEvent.KEYCODE_SYSRQ);
        m.put(100, KeyEvent.KEYCODE_ALT_RIGHT);
        m.put(102, KeyEvent.KEYCODE_MOVE_HOME);
        m.put(103, KeyEvent.KEYCODE_DPAD_UP);
        m.put(104, KeyEvent.KEYCODE_PAGE_UP);
        m.put(105, KeyEvent.KEYCODE_DPAD_LEFT);
        m.put(106, KeyEvent.KEYCODE_DPAD_RIGHT);
        m.put(107, KeyEvent.KEYCODE_MOVE_END);
        m.put(108, KeyEvent.KEYCODE_DPAD_DOWN);
        m.put(109, KeyEvent.KEYCODE_PAGE_DOWN);
        m.put(110, KeyEvent.KEYCODE_INSERT);
        m.put(111, KeyEvent.KEYCODE_FORWARD_DEL);
        m.put(114, KeyEvent.KEYCODE_VOLUME_DOWN);
        m.put(115, KeyEvent.KEYCODE_VOLUME_UP);
        m.put(116, KeyEvent.KEYCODE_POWER);
        m.put(125, KeyEvent.KEYCODE_META_LEFT);
        m.put(126, KeyEvent.KEYCODE_META_RIGHT);
        m.put(139, KeyEvent.KEYCODE_MENU);
        m.put(142, KeyEvent.KEYCODE_SLEEP);
        m.put(143, KeyEvent.KEYCODE_WAKEUP);
        // 鼠标键（Android 无专用 keycode，借用 BUTTON_1/2/3，原始码透传）
        m.put(272, KeyEvent.KEYCODE_BUTTON_1);
        m.put(273, KeyEvent.KEYCODE_BUTTON_2);
        m.put(274, KeyEvent.KEYCODE_BUTTON_3);
        m.put(275, KeyEvent.KEYCODE_BUTTON_4);
        m.put(276, KeyEvent.KEYCODE_BUTTON_5);
        return m;
    }
}
