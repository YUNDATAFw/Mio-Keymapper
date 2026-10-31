package io.takamiya.mio.gamepad;

import cn.panda.utils.library.AppLogger;

/**
 * Shizuku UserService：以 shell 权限运行，通过 libgamepad-uinput 打开 /dev/uinput
 * 创建虚拟 Xbox 360 手柄，并把按键/轴事件写入内核输入子系统。
 * <p>
 * 注意：不能继承 android.app.Service。Shizuku 的 ServiceStarter 会把实例强转为
 * IBinder（与 ShellService 一致，直接继承 AIDL Stub）。
 */
public class GamepadDaemon extends IGamepadService.Stub {

    private static final String TAG = "GamepadDaemon";

    private static boolean nativeLoaded = false;

    static {
        try {
            System.loadLibrary("gamepad-uinput");
            nativeLoaded = true;
        } catch (Throwable t) {
            nativeLoaded = false;
        }
    }

    public static native int nativeOpen(String name, boolean isXbox);

    public static native void nativeSendKey(int linuxCode, int value);

    public static native void nativeSendAxis(int axis, int value);

    public static native void nativeSendStick(int lx, int ly, int rx, int ry);

    public static native void nativeSendTrigger(int lt, int rt);

    public static native void nativeSendDpad(int x, int y);

    public static native boolean nativeIsAvailable();

    public static native void nativeDestroy();

    public static native int nativeGetLastError();

    public GamepadDaemon() {
        AppLogger.i(TAG, "GamepadDaemon started, pid=" + android.os.Process.myPid()
                + " nativeLoaded=" + nativeLoaded);
        if (nativeLoaded) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                AppLogger.w(TAG, "GamepadDaemon shutdown hook: destroy native gamepad");
                nativeDestroy();
            }));
        }
    }

    @Override
    public int openDevice(String name) {
        AppLogger.i(TAG, "openDevice: " + name + " nativeLoaded=" + nativeLoaded);
        if (!nativeLoaded) {
            AppLogger.e(TAG, "openDevice: native library not loaded");
            return -1;
        }
        return nativeOpen(name, true);
    }

    @Override
    public void sendKey(int linuxCode, int value) {
        nativeSendKey(linuxCode, value);
    }

    @Override
    public void sendAxis(int axis, int value) {
        nativeSendAxis(axis, value);
    }

    @Override
    public void sendStick(int lx, int ly, int rx, int ry) {
        nativeSendStick(lx, ly, rx, ry);
    }

    @Override
    public void sendTrigger(int lt, int rt) {
        nativeSendTrigger(lt, rt);
    }

    @Override
    public void sendDpad(int x, int y) {
        nativeSendDpad(x, y);
    }

    @Override
    public void destroy() {
        AppLogger.i(TAG, "destroy");
        nativeDestroy();
    }

    @Override
    public int getLastError() {
        return nativeGetLastError();
    }
}