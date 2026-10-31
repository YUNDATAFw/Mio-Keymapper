package io.takamiya.mio.gamepad;

import android.content.Context;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.shizuku.IShellService;
import io.takamiya.mio.shizuku.ShizukuHelper;

/**
 * 虚拟手柄引擎门面（App 进程）。
 * uinput 通道可用时直接写入虚拟 Xbox 手柄；不可用时自动降级为
 * SOURCE_GAMEPAD 按键注入（走 ShellService）。
 */
public final class GamepadEngine {

    private static final String TAG = "GamepadEngine";

    public static final int ABS_X = 0;
    public static final int ABS_Y = 1;
    public static final int ABS_Z = 2;
    public static final int ABS_RX = 3;
    public static final int ABS_RY = 4;
    public static final int ABS_RZ = 5;
    public static final int ABS_HAT0X = 16;
    public static final int ABS_HAT0Y = 17;

    private static final String DEFAULT_DEVICE_NAME = "Virtual Xbox Gamepad";

    private static volatile GamepadEngine instance;

    private final Context appContext;
    private final Object lock = new Object();

    private IGamepadService service;
    private GamepadState state = GamepadState.UNINITIALIZED;
    private String deviceName = DEFAULT_DEVICE_NAME;
    private int lastError = 0;

    private GamepadEngine(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static GamepadEngine get(Context context) {
        if (instance == null) {
            synchronized (GamepadEngine.class) {
                if (instance == null) {
                    instance = new GamepadEngine(context);
                }
            }
        }
        return instance;
    }

    /**
     * 绑定 GamepadDaemon 并尝试打开 /dev/uinput。同步方法，应在后台线程调用。
     *
     * @return true = OPEN，false = 不可用（看 getState()）
     */
    public synchronized boolean init() {
        synchronized (lock) {
            if (state == GamepadState.OPEN) return true;
            if (state == GamepadState.FALLBACK_KEY_INJECTION && service != null) return true;
            if (!ShizukuHelper.getInstance().isGranted()) {
                state = GamepadState.UNINITIALIZED;
                AppLogger.w(TAG, "init: shizuku not granted");
                return false;
            }

            ShizukuHelper.getInstance().bindGamepadDaemon();
            IGamepadService svc = null;
            for (int i = 0; i < 15; i++) {
                svc = ShizukuHelper.getInstance().getGamepadService();
                if (svc != null) break;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (svc == null) {
                state = GamepadState.UINPUT_UNAVAILABLE;
                lastError = 0;
                AppLogger.e(TAG, "init: gamepad daemon not bound");
                return false;
            }

            service = svc;
            int rc;
            try {
                rc = svc.openDevice(deviceName);
            } catch (Exception e) {
                AppLogger.e(TAG, "init: openDevice exception", e);
                state = GamepadState.FALLBACK_KEY_INJECTION;
                lastError = 0;
                AppLogger.i(TAG, "init: daemon bound, uinput unavailable, fallback key injection ready");
                return true;
            }
            if (rc == 0) {
                state = GamepadState.OPEN;
                lastError = 0;
                AppLogger.i(TAG, "init: uinput gamepad opened: " + deviceName);
                return true;
            }
            try {
                lastError = svc.getLastError();
            } catch (Exception e) {
                AppLogger.w(TAG, "init: getLastError exception", e);
            }
            AppLogger.e(TAG, "init: openDevice failed rc=" + rc + " errno=" + lastError);
            state = GamepadState.FALLBACK_KEY_INJECTION;
            AppLogger.i(TAG, "init: daemon bound, uinput failed, fallback key injection ready");
            return true;
        }
    }

    public void destroy() {
        synchronized (lock) {
            if (service != null) {
                try {
                    service.destroy();
                } catch (Exception e) {
                    AppLogger.w(TAG, "destroy: daemon destroy failed", e);
                }
            }
            ShizukuHelper.getInstance().unbindGamepadDaemon();
            service = null;
            state = GamepadState.UNINITIALIZED;
            lastError = 0;
        }
    }

    public GamepadState getState() {
        return state;
    }

    public boolean isOpen() {
        return state == GamepadState.OPEN;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String name) {
        this.deviceName = name != null && !name.trim().isEmpty() ? name.trim() : DEFAULT_DEVICE_NAME;
    }

    public int getLastError() {
        return lastError;
    }

    // ---- 按键 ----

    public void pressButton(GamepadButton button) {
        if (button == null) return;
        sendKey(button, 1);
    }

    public void releaseButton(GamepadButton button) {
        if (button == null) return;
        sendKey(button, 0);
    }

    private void sendKey(GamepadButton button, int value) {
        synchronized (lock) {
            if (state == GamepadState.OPEN && service != null) {
                try {
                    service.sendKey(button.getLinuxCode(), value);
                } catch (Exception e) {
                    AppLogger.e(TAG, "sendKey remote failed", e);
                }
            } else {
                fallbackInjectKey(button.getAndroidKeyCode(), button.getLinuxCode(), value == 1);
            }
        }
    }

    private void fallbackInjectKey(int keyCode, int scancode, boolean pressed) {
        try {
            IShellService shell = ShizukuHelper.getInstance().getShellService();
            if (shell == null) {
                AppLogger.w(TAG, "fallbackInjectKey: shell service unavailable");
                return;
            }
            shell.injectGamepadKey(keyCode, scancode, pressed);
        } catch (Exception e) {
            AppLogger.e(TAG, "fallbackInjectKey failed", e);
        }
    }

    // ---- 轴 ----

    public void setLeftStick(int lx, int ly) {
        synchronized (lock) {
            if (service == null) return;
            try {
                service.sendAxis(ABS_X, lx);
                service.sendAxis(ABS_Y, ly);
            } catch (Exception e) {
                AppLogger.e(TAG, "setLeftStick remote failed", e);
            }
        }
    }

    public void setRightStick(int rx, int ry) {
        synchronized (lock) {
            if (service == null) return;
            try {
                service.sendAxis(ABS_RX, rx);
                service.sendAxis(ABS_RY, ry);
            } catch (Exception e) {
                AppLogger.e(TAG, "setRightStick remote failed", e);
            }
        }
    }

    public void setStick(int lx, int ly, int rx, int ry) {
        synchronized (lock) {
            if (service == null) return;
            try {
                service.sendStick(lx, ly, rx, ry);
            } catch (Exception e) {
                AppLogger.e(TAG, "setStick remote failed", e);
            }
        }
    }

    public void setTrigger(int lt, int rt) {
        synchronized (lock) {
            if (service == null) return;
            try {
                service.sendTrigger(lt, rt);
            } catch (Exception e) {
                AppLogger.e(TAG, "setTrigger remote failed", e);
            }
        }
    }

    public void setDpad(int x, int y) {
        synchronized (lock) {
            if (service == null) return;
            try {
                service.sendDpad(x, y);
            } catch (Exception e) {
                AppLogger.e(TAG, "setDpad remote failed", e);
            }
        }
    }
}