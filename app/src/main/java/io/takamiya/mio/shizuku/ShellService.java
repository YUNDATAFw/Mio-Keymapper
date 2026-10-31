package io.takamiya.mio.shizuku;

import android.os.SystemClock;
import cn.panda.utils.library.AppLogger;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

public class ShellService extends IShellService.Stub {

    private static final String TAG = "ShellService";

    public ShellService() {
        AppLogger.i(TAG, "ShellService process started, pid=" + android.os.Process.myPid());
        try {
            Thread.UncaughtExceptionHandler existing = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                AppLogger.e(TAG, "ShellService UNCAUGHT exception on " + thread
                        + ", pid=" + android.os.Process.myPid(), throwable);
                if (existing != null) {
                    existing.uncaughtException(thread, throwable);
                }
            });
        } catch (Throwable t) {
            AppLogger.e(TAG, "failed to install uncaught exception handler", t);
        }
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() ->
                    AppLogger.w(TAG, "ShellService shutdown hook fired, pid=" + android.os.Process.myPid()
                            + " (graceful exit), stack:",
                            new Throwable("shutdown hook"))));
        } catch (Throwable t) {
            AppLogger.e(TAG, "failed to register shutdown hook", t);
        }
    }

    @Override
    public String execCommand(String command) {
        AppLogger.d(TAG, "execCommand: command=" + command);
        long start = SystemClock.uptimeMillis();
        try {
            Process process = new ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exit = process.waitFor();
            String result = output.toString().trim();
            AppLogger.d(TAG, "execCommand: exit=" + exit
                    + " elapsed=" + (SystemClock.uptimeMillis() - start) + "ms"
                    + " output(len=" + result.length() + ")");
            return result;
        } catch (Exception e) {
            AppLogger.e(TAG, "execCommand failed: command=" + command, e);
            return "error: " + e.getMessage();
        }
    }

    @Override
    public String execCommandWithArgs(String[] args) {
        AppLogger.d(TAG, "execCommandWithArgs: args=" + java.util.Arrays.toString(args));
        long start = SystemClock.uptimeMillis();
        try {
            Process process = new ProcessBuilder(args)
                    .redirectErrorStream(true)
                    .start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exit = process.waitFor();
            String result = output.toString().trim();
            AppLogger.d(TAG, "execCommandWithArgs: exit=" + exit
                    + " elapsed=" + (SystemClock.uptimeMillis() - start) + "ms"
                    + " output(len=" + result.length() + ")");
            return result;
        } catch (Exception e) {
            AppLogger.e(TAG, "execCommandWithArgs failed: args=" + java.util.Arrays.toString(args), e);
            return "error: " + e.getMessage();
        }
    }

    @Override
    public boolean injectKeyEvent(int keyCode, int flags) {
        AppLogger.d(TAG, "injectKeyEvent: key=" + keyName(keyCode)
                + " flags=0x" + Integer.toHexString(flags));
        try {
            long now = SystemClock.uptimeMillis();
            int deviceId = keyboardDeviceId();
            int scanCode = scanCodeFor(keyCode);
            int eventFlags = adjustFlags(keyCode, flags);
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                    deviceId, scanCode,
                    eventFlags,
                    InputDevice.SOURCE_KEYBOARD);
            KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
                    deviceId, scanCode,
                    eventFlags,
                    InputDevice.SOURCE_KEYBOARD);

            Object im = getInputManager();
            if (im == null) {
                AppLogger.e(TAG, "injectKeyEvent: InputManager is null");
                return false;
            }
            Method inject = getInputManagerClass().getMethod("injectInputEvent", InputEvent.class, int.class);
            inject.invoke(im, down, 0);
            AppLogger.d(TAG, "injectKeyEvent: ACTION_DOWN injected, key=" + keyName(keyCode));
            inject.invoke(im, up, 0);
            AppLogger.d(TAG, "injectKeyEvent: ACTION_UP injected, key=" + keyName(keyCode));
            return true;
        } catch (Exception e) {
            AppLogger.e(TAG, "injectKeyEvent failed: key=" + keyName(keyCode), e);
            return false;
        }
    }

    @Override
    public boolean injectKeyDown(int keyCode) {
        AppLogger.d(TAG, "injectKeyDown: key=" + keyName(keyCode));
        boolean ok = injectKeyAction(keyCode, KeyEvent.ACTION_DOWN, 0);
        AppLogger.d(TAG, "injectKeyDown: -> " + ok);
        return ok;
    }

    @Override
    public boolean injectKeyUp(int keyCode) {
        AppLogger.d(TAG, "injectKeyUp: key=" + keyName(keyCode));
        boolean ok = injectKeyAction(keyCode, KeyEvent.ACTION_UP, 0);
        AppLogger.d(TAG, "injectKeyUp: -> " + ok);
        return ok;
    }

    @Override
    public boolean injectGamepadKey(int keyCode, int scancode, boolean pressed) {
        AppLogger.d(TAG, "injectGamepadKey: key=" + keyName(keyCode)
                + " scancode=" + scancode + " pressed=" + pressed);
        try {
            long now = SystemClock.uptimeMillis();
            int action = pressed ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
            KeyEvent event = new KeyEvent(now, now, action, keyCode, 0, 0,
                    gamepadDeviceId(), scancode,
                    0,
                    InputDevice.SOURCE_GAMEPAD);

            Object im = getInputManager();
            if (im == null) {
                AppLogger.e(TAG, "injectGamepadKey: InputManager is null");
                return false;
            }
            Method inject = getInputManagerClass().getMethod("injectInputEvent", InputEvent.class, int.class);
            inject.invoke(im, event, 0);
            AppLogger.d(TAG, "injectGamepadKey: injected " + (pressed ? "DOWN" : "UP") + " "
                    + keyName(keyCode));
            return true;
        } catch (Exception e) {
            AppLogger.e(TAG, "injectGamepadKey failed: key=" + keyName(keyCode), e);
            return false;
        }
    }

    @Override
    public boolean injectCombo(int keyCode, int[] modifierKeyCodes) {
        AppLogger.d(TAG, "injectCombo: key=" + keyName(keyCode)
                + " modifiers=" + keyCodesName(modifierKeyCodes)
                + " pid=" + android.os.Process.myPid());
        long comboStart = SystemClock.uptimeMillis();
        boolean ok = false;
        try {
            int meta = io.takamiya.mio.config.KeyMapping.metaStateFor(modifierKeyCodes);
            AppLogger.d(TAG, "injectCombo: meta=0x" + Integer.toHexString(meta));
            if (modifierKeyCodes != null) {
                for (int mod : modifierKeyCodes) {
                    if (mod != 0 && mod != keyCode) {
                        boolean r = injectKeyAction(mod, KeyEvent.ACTION_DOWN, meta);
                        AppLogger.d(TAG, "injectCombo: modifier DOWN " + keyName(mod) + " -> " + r);
                    }
                }
                if (modifierKeyCodes.length > 0) {
                    long sleepStart = SystemClock.uptimeMillis();
                    Thread.sleep(30);
                    AppLogger.d(TAG, "injectCombo: sleep done, elapsed="
                            + (SystemClock.uptimeMillis() - sleepStart) + "ms");
                }
            }
            long t = SystemClock.uptimeMillis();
            boolean r1 = injectKeyAction(keyCode, KeyEvent.ACTION_DOWN, meta);
            AppLogger.d(TAG, "injectCombo: main key DOWN " + keyName(keyCode) + " -> " + r1
                    + " elapsed=" + (SystemClock.uptimeMillis() - t) + "ms");
            t = SystemClock.uptimeMillis();
            boolean r2 = injectKeyAction(keyCode, KeyEvent.ACTION_UP, meta);
            AppLogger.d(TAG, "injectCombo: main key UP " + keyName(keyCode) + " -> " + r2
                    + " elapsed=" + (SystemClock.uptimeMillis() - t) + "ms");
            if (modifierKeyCodes != null) {
                for (int i = modifierKeyCodes.length - 1; i >= 0; i--) {
                    if (modifierKeyCodes[i] != 0 && modifierKeyCodes[i] != keyCode) {
                        int upMeta = metaWithout(modifierKeyCodes, i);
                        boolean r3 = injectKeyAction(modifierKeyCodes[i], KeyEvent.ACTION_UP, upMeta);
                        AppLogger.d(TAG, "injectCombo: modifier UP " + keyName(modifierKeyCodes[i]) + " -> " + r3);
                    }
                }
            }
            ok = true;
            return true;
        } catch (Exception e) {
            AppLogger.e(TAG, "injectCombo failed: key=" + keyName(keyCode), e);
            return false;
        } finally {
            AppLogger.d(TAG, "injectCombo: exit ok=" + ok + " totalElapsed="
                    + (SystemClock.uptimeMillis() - comboStart) + "ms, pid="
                    + android.os.Process.myPid());
        }
    }

    @Override
    public boolean injectKeyEventWithDuration(int keyCode, int flags, int holdDurationMs) {
        AppLogger.d(TAG, "injectKeyEventWithDuration: key=" + keyName(keyCode)
                + " flags=0x" + Integer.toHexString(flags)
                + " holdDuration=" + holdDurationMs + "ms");
        try {
            long now = SystemClock.uptimeMillis();
            int deviceId = keyboardDeviceId();
            int scanCode = scanCodeFor(keyCode);
            int eventFlags = adjustFlags(keyCode, flags);
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                    deviceId, scanCode, eventFlags, InputDevice.SOURCE_KEYBOARD);
            Object im = getInputManager();
            if (im == null) {
                AppLogger.e(TAG, "injectKeyEventWithDuration: InputManager is null");
                return false;
            }
            Method inject = getInputManagerClass().getMethod("injectInputEvent", InputEvent.class, int.class);
            inject.invoke(im, down, 0);
            AppLogger.d(TAG, "injectKeyEventWithDuration: ACTION_DOWN injected, key=" + keyName(keyCode));
            if (holdDurationMs > 0) {
                Thread.sleep(holdDurationMs);
            }
            long upTime = SystemClock.uptimeMillis();
            KeyEvent up = new KeyEvent(upTime, upTime, KeyEvent.ACTION_UP, keyCode, 0, 0,
                    deviceId, scanCode, eventFlags, InputDevice.SOURCE_KEYBOARD);
            inject.invoke(im, up, 0);
            AppLogger.d(TAG, "injectKeyEventWithDuration: ACTION_UP injected, key=" + keyName(keyCode)
                    + " actualHold=" + (SystemClock.uptimeMillis() - now) + "ms");
            return true;
        } catch (Exception e) {
            AppLogger.e(TAG, "injectKeyEventWithDuration failed: key=" + keyName(keyCode), e);
            return false;
        }
    }

    @Override
    public boolean injectComboWithDuration(int keyCode, int[] modifierKeyCodes, int holdDurationMs) {
        AppLogger.d(TAG, "injectComboWithDuration: key=" + keyName(keyCode)
                + " modifiers=" + keyCodesName(modifierKeyCodes)
                + " holdDuration=" + holdDurationMs + "ms"
                + " pid=" + android.os.Process.myPid());
        long comboStart = SystemClock.uptimeMillis();
        boolean ok = false;
        try {
            int meta = io.takamiya.mio.config.KeyMapping.metaStateFor(modifierKeyCodes);
            AppLogger.d(TAG, "injectComboWithDuration: meta=0x" + Integer.toHexString(meta));
            if (modifierKeyCodes != null) {
                for (int mod : modifierKeyCodes) {
                    if (mod != 0 && mod != keyCode) {
                        boolean r = injectKeyAction(mod, KeyEvent.ACTION_DOWN, meta);
                        AppLogger.d(TAG, "injectComboWithDuration: modifier DOWN " + keyName(mod) + " -> " + r);
                    }
                }
                if (modifierKeyCodes.length > 0) {
                    long sleepStart = SystemClock.uptimeMillis();
                    Thread.sleep(30);
                    AppLogger.d(TAG, "injectComboWithDuration: modifier sleep done, elapsed="
                            + (SystemClock.uptimeMillis() - sleepStart) + "ms");
                }
            }
            long t = SystemClock.uptimeMillis();
            boolean r1 = injectKeyAction(keyCode, KeyEvent.ACTION_DOWN, meta);
            AppLogger.d(TAG, "injectComboWithDuration: main key DOWN " + keyName(keyCode) + " -> " + r1
                    + " elapsed=" + (SystemClock.uptimeMillis() - t) + "ms");
            if (holdDurationMs > 0) {
                Thread.sleep(holdDurationMs);
            }
            t = SystemClock.uptimeMillis();
            boolean r2 = injectKeyAction(keyCode, KeyEvent.ACTION_UP, meta);
            AppLogger.d(TAG, "injectComboWithDuration: main key UP " + keyName(keyCode) + " -> " + r2
                    + " elapsed=" + (SystemClock.uptimeMillis() - t) + "ms");
            if (modifierKeyCodes != null) {
                for (int i = modifierKeyCodes.length - 1; i >= 0; i--) {
                    if (modifierKeyCodes[i] != 0 && modifierKeyCodes[i] != keyCode) {
                        int upMeta = metaWithout(modifierKeyCodes, i);
                        boolean r3 = injectKeyAction(modifierKeyCodes[i], KeyEvent.ACTION_UP, upMeta);
                        AppLogger.d(TAG, "injectComboWithDuration: modifier UP " + keyName(modifierKeyCodes[i]) + " -> " + r3);
                    }
                }
            }
            ok = true;
            return true;
        } catch (Exception e) {
            AppLogger.e(TAG, "injectComboWithDuration failed: key=" + keyName(keyCode), e);
            return false;
        } finally {
            AppLogger.d(TAG, "injectComboWithDuration: exit ok=" + ok + " totalElapsed="
                    + (SystemClock.uptimeMillis() - comboStart) + "ms, pid="
                    + android.os.Process.myPid());
        }
    }

    private boolean injectKeyAction(int keyCode, int action, int metaState) {
        AppLogger.d(TAG, "injectKeyAction: key=" + keyName(keyCode)
                + " action=" + actionName(action)
                + " meta=0x" + Integer.toHexString(metaState));
        try {
            long now = SystemClock.uptimeMillis();
            KeyEvent event = new KeyEvent(now, now, action, keyCode, 0, metaState,
                    keyboardDeviceId(), scanCodeFor(keyCode),
                    adjustFlags(keyCode, 0),
                    InputDevice.SOURCE_KEYBOARD);

            Object im = getInputManager();
            if (im == null) {
                AppLogger.e(TAG, "injectKeyAction: InputManager is null, key=" + keyName(keyCode));
                return false;
            }
            Method inject = getInputManagerClass().getMethod("injectInputEvent", InputEvent.class, int.class);
            inject.invoke(im, event, 0);
            AppLogger.d(TAG, "injectKeyAction: injected " + actionName(action) + " " + keyName(keyCode));
            return true;
        } catch (Exception e) {
            AppLogger.e(TAG, "injectKeyAction failed: key=" + keyName(keyCode)
                    + " action=" + actionName(action), e);
            return false;
        }
    }

    private static int cachedDeviceId = Integer.MIN_VALUE;

    private static int keyboardDeviceId() {
        if (cachedDeviceId != Integer.MIN_VALUE) return cachedDeviceId;
        int id = KeyCharacterMap.VIRTUAL_KEYBOARD;
        try {
            for (int did : InputDevice.getDeviceIds()) {
                InputDevice dev = InputDevice.getDevice(did);
                if (dev != null && (dev.getSources() & InputDevice.SOURCE_KEYBOARD) != 0) {
                    id = did;
                    break;
                }
            }
        } catch (Throwable t) {
            AppLogger.e(TAG, "keyboardDeviceId failed", t);
        }
        cachedDeviceId = id;
        AppLogger.d(TAG, "keyboardDeviceId -> " + id);
        return id;
    }

    private static int gamepadDeviceId() {
        try {
            for (int did : InputDevice.getDeviceIds()) {
                InputDevice dev = InputDevice.getDevice(did);
                if (dev != null && (dev.getSources() & InputDevice.SOURCE_GAMEPAD) != 0) {
                    return did;
                }
            }
        } catch (Throwable t) {
            AppLogger.e(TAG, "gamepadDeviceId failed", t);
        }
        return -1;
    }

    private static boolean isDpadKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private static int adjustFlags(int keyCode, int flags) {
        if (isDpadKey(keyCode)) {
            return flags | KeyEvent.FLAG_SOFT_KEYBOARD;
        }
        return flags;
    }

    private static int scanCodeFor(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ESCAPE: return 1;
            case KeyEvent.KEYCODE_1: return 2;
            case KeyEvent.KEYCODE_2: return 3;
            case KeyEvent.KEYCODE_3: return 4;
            case KeyEvent.KEYCODE_4: return 5;
            case KeyEvent.KEYCODE_5: return 6;
            case KeyEvent.KEYCODE_6: return 7;
            case KeyEvent.KEYCODE_7: return 8;
            case KeyEvent.KEYCODE_8: return 9;
            case KeyEvent.KEYCODE_9: return 10;
            case KeyEvent.KEYCODE_0: return 11;
            case KeyEvent.KEYCODE_MINUS: return 12;
            case KeyEvent.KEYCODE_EQUALS: return 13;
            case KeyEvent.KEYCODE_DEL: return 14;
            case KeyEvent.KEYCODE_TAB: return 15;
            case KeyEvent.KEYCODE_Q: return 16;
            case KeyEvent.KEYCODE_W: return 17;
            case KeyEvent.KEYCODE_E: return 18;
            case KeyEvent.KEYCODE_R: return 19;
            case KeyEvent.KEYCODE_T: return 20;
            case KeyEvent.KEYCODE_Y: return 21;
            case KeyEvent.KEYCODE_U: return 22;
            case KeyEvent.KEYCODE_I: return 23;
            case KeyEvent.KEYCODE_O: return 24;
            case KeyEvent.KEYCODE_P: return 25;
            case KeyEvent.KEYCODE_LEFT_BRACKET: return 26;
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return 27;
            case KeyEvent.KEYCODE_ENTER: return 28;
            case KeyEvent.KEYCODE_CTRL_LEFT: return 29;
            case KeyEvent.KEYCODE_A: return 30;
            case KeyEvent.KEYCODE_S: return 31;
            case KeyEvent.KEYCODE_D: return 32;
            case KeyEvent.KEYCODE_F: return 33;
            case KeyEvent.KEYCODE_G: return 34;
            case KeyEvent.KEYCODE_H: return 35;
            case KeyEvent.KEYCODE_J: return 36;
            case KeyEvent.KEYCODE_K: return 37;
            case KeyEvent.KEYCODE_L: return 38;
            case KeyEvent.KEYCODE_SEMICOLON: return 39;
            case KeyEvent.KEYCODE_APOSTROPHE: return 40;
            case KeyEvent.KEYCODE_GRAVE: return 41;
            case KeyEvent.KEYCODE_SHIFT_LEFT: return 42;
            case KeyEvent.KEYCODE_BACKSLASH: return 43;
            case KeyEvent.KEYCODE_Z: return 44;
            case KeyEvent.KEYCODE_X: return 45;
            case KeyEvent.KEYCODE_C: return 46;
            case KeyEvent.KEYCODE_V: return 47;
            case KeyEvent.KEYCODE_B: return 48;
            case KeyEvent.KEYCODE_N: return 49;
            case KeyEvent.KEYCODE_M: return 50;
            case KeyEvent.KEYCODE_COMMA: return 51;
            case KeyEvent.KEYCODE_PERIOD: return 52;
            case KeyEvent.KEYCODE_SLASH: return 53;
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return 54;
            case KeyEvent.KEYCODE_ALT_LEFT: return 56;
            case KeyEvent.KEYCODE_SPACE: return 57;
            case KeyEvent.KEYCODE_CAPS_LOCK: return 58;
            case KeyEvent.KEYCODE_F1: return 59;
            case KeyEvent.KEYCODE_F2: return 60;
            case KeyEvent.KEYCODE_F3: return 61;
            case KeyEvent.KEYCODE_F4: return 62;
            case KeyEvent.KEYCODE_F5: return 63;
            case KeyEvent.KEYCODE_F6: return 64;
            case KeyEvent.KEYCODE_F7: return 65;
            case KeyEvent.KEYCODE_F8: return 66;
            case KeyEvent.KEYCODE_F9: return 67;
            case KeyEvent.KEYCODE_F10: return 68;
            case KeyEvent.KEYCODE_F11: return 87;
            case KeyEvent.KEYCODE_F12: return 88;
            case KeyEvent.KEYCODE_DPAD_UP: return 103;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 108;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 105;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 106;
            case KeyEvent.KEYCODE_CTRL_RIGHT: return 97;
            case KeyEvent.KEYCODE_ALT_RIGHT: return 100;
            case KeyEvent.KEYCODE_META_LEFT: return 125;
            case KeyEvent.KEYCODE_META_RIGHT: return 126;
            default: return 0;
        }
    }

    private static int metaWithout(int[] modifierKeyCodes, int skipIndex) {
        int meta = io.takamiya.mio.config.KeyMapping.metaStateFor(modifierKeyCodes);
        meta &= ~io.takamiya.mio.config.KeyMapping.metaStateFor(new int[]{modifierKeyCodes[skipIndex]});
        return meta;
    }

    private static Class<?> getInputManagerClass() throws ClassNotFoundException {
        return Class.forName("android.hardware.input.InputManager");
    }

    private static Object getInputManager() {
        try {
            Class<?> imClass = getInputManagerClass();
            Method getInstance = imClass.getMethod("getInstance");
            return getInstance.invoke(null);
        } catch (Exception e) {
            AppLogger.e(TAG, "getInputManager: failed", e);
            return null;
        }
    }

    @Override
    public void destroy() {
        AppLogger.w(TAG, "ShellService.destroy() called, exiting, pid=" + android.os.Process.myPid(),
                new Throwable("destroy() caller"));
        System.exit(0);
    }

    private static String actionName(int action) {
        switch (action) {
            case KeyEvent.ACTION_DOWN: return "DOWN";
            case KeyEvent.ACTION_UP: return "UP";
            case KeyEvent.ACTION_MULTIPLE: return "MULTIPLE";
            default: return "ACTION_" + action;
        }
    }

    private static String keyName(int keyCode) {
        try {
            return KeyEvent.keyCodeToString(keyCode);
        } catch (Exception e) {
            return String.valueOf(keyCode);
        }
    }

    private static String keyCodesName(int[] keyCodes) {
        if (keyCodes == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < keyCodes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(keyName(keyCodes[i]));
        }
        return sb.append("]").toString();
    }
}
