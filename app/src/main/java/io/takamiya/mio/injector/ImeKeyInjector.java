package io.takamiya.mio.injector;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.view.KeyEvent;

import java.util.List;

import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.utils.PDebug;

public class ImeKeyInjector implements KeyInjector {

    private static final String TAG = "ImeKeyInjector";

    private final Context context;
    private volatile boolean repeatRunning = false;
    private Thread repeatThread;

    public ImeKeyInjector(Context context) {
        this.context = context.getApplicationContext();
        PDebug.d(TAG, "constructor: context=" + this.context.getPackageName());
    }

    private static String imeId(Context context) {
        String id = new ComponentName(context, MioImeService.class).flattenToShortString();
        PDebug.d(TAG, "imeId: " + id);
        return id;
    }

    public static String getMioImeId(Context context) {
        return imeId(context);
    }

    public static boolean isImeEnabled(Context context) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            AppLogger.w(TAG, "isImeEnabled: InputMethodManager is null");
            return false;
        }
        String targetId = imeId(context);
        List<InputMethodInfo> list = imm.getEnabledInputMethodList();
        for (InputMethodInfo info : list) {
            if (targetId.equals(info.getId())) {
                PDebug.d(TAG, "isImeEnabled: enabled, target=" + targetId);
                return true;
            }
        }
        AppLogger.w(TAG, "isImeEnabled: NOT enabled, target=" + targetId
                + ", enabledImeCount=" + (list != null ? list.size() : -1));
        return false;
    }

    public static boolean isImeCurrent(Context context) {
        String current = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        String target = imeId(context);
        boolean result = target.equals(current);
        PDebug.d(TAG, "isImeCurrent: current=" + current + " target=" + target + " -> " + result);
        return result;
    }

    private MioImeService getService() throws InjectionException {
        MioImeService ime = MioImeService.getInstance();
        if (ime == null) {
            AppLogger.e(TAG, "getService: MioImeService not running");
            throw new InjectionException("MioImeService not running");
        }
        return ime;
    }

    @Override
    public void pressKey(int keyCode) throws InjectionException {
        PDebug.d(TAG, "pressKey: " + keyName(keyCode) + " (" + keyCode + ")");
        getService().sendKeyCode(keyCode);
        PDebug.d(TAG, "pressKey: sent " + keyName(keyCode));
    }

    @Override
    public void pressKey(int keyCode, long holdDurationMs) throws InjectionException {
        PDebug.d(TAG, "pressKey: " + keyName(keyCode) + " (" + keyCode + ")"
                + " holdDuration=" + holdDurationMs + "ms");
        if (holdDurationMs > 0) {
            keyDown(keyCode);
            try {
                Thread.sleep(holdDurationMs);
            } catch (InterruptedException e) {
                AppLogger.w(TAG, "pressKey: sleep interrupted", e);
                Thread.currentThread().interrupt();
            }
            keyUp(keyCode);
        } else {
            getService().sendKeyCode(keyCode);
        }
        PDebug.d(TAG, "pressKey: sent " + keyName(keyCode));
    }

    @Override
    public void pressKey(int... keyCodes) throws InjectionException {
        if (keyCodes == null || keyCodes.length == 0) {
            PDebug.d(TAG, "pressKey: empty keyCodes, ignored");
            return;
        }
        PDebug.d(TAG, "pressKey: combo=" + keyCodesName(keyCodes));
        MioImeService ime = getService();
        for (int key : keyCodes) ime.keyDown(key);
        for (int i = keyCodes.length - 1; i >= 0; i--) ime.keyUp(keyCodes[i]);
        PDebug.d(TAG, "pressKey: combo sent");
    }

    @Override
    public void pressKeyCombo(int keyCode, int... modifierKeyCodes) throws InjectionException {
        PDebug.d(TAG, "pressKeyCombo: key=" + keyName(keyCode)
                + " modifiers=" + keyCodesName(modifierKeyCodes));
        getService().sendKeyCombo(keyCode, modifierKeyCodes);
        PDebug.d(TAG, "pressKeyCombo: sent");
    }

    @Override
    public void pressKeyCombo(int keyCode, long holdDurationMs, int... modifierKeyCodes) throws InjectionException {
        PDebug.d(TAG, "pressKeyCombo: key=" + keyName(keyCode)
                + " modifiers=" + keyCodesName(modifierKeyCodes)
                + " holdDuration=" + holdDurationMs + "ms");
        MioImeService ime = getService();
        for (int mod : modifierKeyCodes) {
            if (mod != 0 && mod != keyCode) {
                ime.keyDown(mod);
            }
        }
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            AppLogger.w(TAG, "pressKeyCombo: modifier sleep interrupted", e);
            Thread.currentThread().interrupt();
        }
        ime.keyDown(keyCode);
        if (holdDurationMs > 0) {
            try {
                Thread.sleep(holdDurationMs);
            } catch (InterruptedException e) {
                AppLogger.w(TAG, "pressKeyCombo: hold sleep interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
        ime.keyUp(keyCode);
        for (int i = modifierKeyCodes.length - 1; i >= 0; i--) {
            if (modifierKeyCodes[i] != 0 && modifierKeyCodes[i] != keyCode) {
                ime.keyUp(modifierKeyCodes[i]);
            }
        }
        PDebug.d(TAG, "pressKeyCombo: sent");
    }

    @Override
    public void inputText(String text) throws InjectionException {
        if (text == null || text.isEmpty()) {
            PDebug.d(TAG, "inputText: empty text, ignored");
            return;
        }
        PDebug.d(TAG, "inputText: text=" + text + " (len=" + text.length() + ")");
        getService().commitText(text);
        PDebug.d(TAG, "inputText: committed");
    }

    @Override
    public void keyDown(int keyCode) throws InjectionException {
        PDebug.d(TAG, "keyDown: " + keyName(keyCode) + " (" + keyCode + ")");
        getService().keyDown(keyCode);
    }

    @Override
    public void keyUp(int keyCode) throws InjectionException {
        PDebug.d(TAG, "keyUp: " + keyName(keyCode) + " (" + keyCode + ")");
        getService().keyUp(keyCode);
    }

    @Override
    public void longPress(int keyCode, long durationMs) throws InjectionException {
        PDebug.d(TAG, "longPress: key=" + keyName(keyCode) + " duration=" + durationMs + "ms");
        keyDown(keyCode);
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            AppLogger.w(TAG, "longPress: sleep interrupted", e);
            Thread.currentThread().interrupt();
        }
        keyUp(keyCode);
        PDebug.d(TAG, "longPress: done");
    }

    @Override
    public void delay(long ms) throws InjectionException {
        PDebug.d(TAG, "delay: " + ms + "ms");
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            AppLogger.w(TAG, "delay: interrupted", e);
            Thread.currentThread().interrupt();
            throw new InjectionException("delay interrupted");
        }
    }

    @Override
    public void repeatKey(long intervalMs, int keyCode) throws InjectionException {
        PDebug.d(TAG, "repeatKey: key=" + keyName(keyCode)
                + " interval=" + intervalMs + "ms (stopping any previous repeat)");
        stopRepeat();
        repeatRunning = true;
        repeatThread = new Thread(() -> {
            PDebug.d(TAG, "repeatKey: loop started, keyCode=" + keyCode);
            while (repeatRunning) {
                try {
                    pressKey(keyCode);
                    Thread.sleep(intervalMs);
                } catch (InjectionException e) {
                    AppLogger.e(TAG, "repeatKey inject failed", e);
                    break;
                } catch (InterruptedException e) {
                    AppLogger.w(TAG, "repeatKey: sleep interrupted");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            PDebug.d(TAG, "repeatKey loop ended, keyCode=" + keyCode);
        }, "repeatKey-" + keyCode);
        repeatThread.start();
    }

    public void stopRepeat() {
        PDebug.d(TAG, "stopRepeat: repeatRunning=" + repeatRunning
                + " thread=" + (repeatThread != null ? repeatThread.getName() : "null"));
        repeatRunning = false;
        if (repeatThread != null) {
            repeatThread.interrupt();
            repeatThread = null;
        }
    }

    @Override
    public boolean isAvailable() {
        boolean enabled = isImeEnabled(context);
        boolean current = isImeCurrent(context);
        MioImeService ime = MioImeService.getInstance();
        boolean running = ime != null;
        boolean connected = ime != null && ime.hasInputConnection();
        boolean result = enabled && current && running && connected;
        PDebug.d(TAG, "isAvailable: enabled=" + enabled + " current=" + current
                + " imeRunning=" + running + " inputConnected=" + connected + " -> " + result);
        return result;
    }

    @Override
    public ServiceState getServiceState() {
        MioImeService ime = MioImeService.getInstance();
        if (ime == null) {
            PDebug.d(TAG, "getServiceState: NO_SERVICE (IME service not running)");
            return ServiceState.NO_SERVICE;
        }
        boolean enabled = isImeEnabled(context);
        boolean current = isImeCurrent(context);
        boolean connected = ime.hasInputConnection();
        if (!enabled || !current || !connected) {
            PDebug.d(TAG, "getServiceState: NOT_READY (enabled=" + enabled
                    + " current=" + current + " inputConnected=" + connected + ")");
            return ServiceState.NOT_READY;
        }
        PDebug.d(TAG, "getServiceState: READY");
        return ServiceState.READY;
    }

    @Override
    public String getName() {
        return "输入法";
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
