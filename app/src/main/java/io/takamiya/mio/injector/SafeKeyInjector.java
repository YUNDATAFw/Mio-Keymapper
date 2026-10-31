package io.takamiya.mio.injector;

import android.view.KeyEvent;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.utils.PDebug;

public class SafeKeyInjector implements KeyInjector {

    private static final String TAG = "SafeKeyInjector";
    private final KeyInjector delegate;

    public SafeKeyInjector(KeyInjector delegate) {
        this.delegate = delegate;
        PDebug.d(TAG, "constructor: delegate="
                + (delegate != null ? delegate.getName() : "null"));
    }

    @Override
    public void pressKey(int keyCode) {
        PDebug.d(TAG, "pressKey: " + keyName(keyCode));
        try {
            delegate.pressKey(keyCode);
            PDebug.d(TAG, "pressKey: ok");
        } catch (InjectionException e) {
            AppLogger.e(TAG, "pressKey(" + keyName(keyCode) + ") failed: " + e.getMessage());
        }
    }

    @Override
    public void pressKey(int keyCode, long holdDurationMs) {
        PDebug.d(TAG, "pressKey: " + keyName(keyCode) + " holdDuration=" + holdDurationMs + "ms");
        try {
            delegate.pressKey(keyCode, holdDurationMs);
            PDebug.d(TAG, "pressKey: ok");
        } catch (InjectionException e) {
            AppLogger.e(TAG, "pressKey(" + keyName(keyCode) + ") failed: " + e.getMessage());
        }
    }

    @Override
    public void pressKey(int... keyCodes) {
        PDebug.d(TAG, "pressKey: combo=" + keyCodesName(keyCodes));
        try {
            delegate.pressKey(keyCodes);
            PDebug.d(TAG, "pressKey: combo ok");
        } catch (InjectionException e) {
            AppLogger.e(TAG, "pressKey combo failed: " + e.getMessage());
        }
    }

    @Override
    public void pressKeyCombo(int keyCode, int... modifierKeyCodes) {
        PDebug.d(TAG, "pressKeyCombo: key=" + keyName(keyCode)
                + " modifiers=" + keyCodesName(modifierKeyCodes));
        try {
            delegate.pressKeyCombo(keyCode, modifierKeyCodes);
            PDebug.d(TAG, "pressKeyCombo: ok");
        } catch (InjectionException e) {
            AppLogger.e(TAG, "pressKeyCombo(" + keyName(keyCode) + ") failed: " + e.getMessage());
        }
    }

    @Override
    public void pressKeyCombo(int keyCode, long holdDurationMs, int... modifierKeyCodes) {
        PDebug.d(TAG, "pressKeyCombo: key=" + keyName(keyCode)
                + " modifiers=" + keyCodesName(modifierKeyCodes)
                + " holdDuration=" + holdDurationMs + "ms");
        try {
            delegate.pressKeyCombo(keyCode, holdDurationMs, modifierKeyCodes);
            PDebug.d(TAG, "pressKeyCombo: ok");
        } catch (InjectionException e) {
            AppLogger.e(TAG, "pressKeyCombo(" + keyName(keyCode) + ") failed: " + e.getMessage());
        }
    }

    @Override
    public void inputText(String text) {
        PDebug.d(TAG, "inputText: text=" + text);
        try {
            delegate.inputText(text);
            PDebug.d(TAG, "inputText: ok");
        } catch (InjectionException e) {
            AppLogger.e(TAG, "inputText failed: " + e.getMessage());
        }
    }

    @Override
    public void keyDown(int keyCode) {
        PDebug.d(TAG, "keyDown: " + keyName(keyCode));
        try {
            delegate.keyDown(keyCode);
        } catch (InjectionException e) {
            AppLogger.e(TAG, "keyDown(" + keyName(keyCode) + ") failed: " + e.getMessage());
        }
    }

    @Override
    public void keyUp(int keyCode) {
        PDebug.d(TAG, "keyUp: " + keyName(keyCode));
        try {
            delegate.keyUp(keyCode);
        } catch (InjectionException e) {
            AppLogger.e(TAG, "keyUp(" + keyName(keyCode) + ") failed: " + e.getMessage());
        }
    }

    @Override
    public void longPress(int keyCode, long durationMs) {
        PDebug.d(TAG, "longPress: key=" + keyName(keyCode) + " duration=" + durationMs + "ms");
        try {
            delegate.longPress(keyCode, durationMs);
            PDebug.d(TAG, "longPress: ok");
        } catch (InjectionException e) {
            AppLogger.e(TAG, "longPress(" + keyName(keyCode) + ") failed: " + e.getMessage());
        }
    }

    @Override
    public void delay(long ms) {
        PDebug.d(TAG, "delay: " + ms + "ms");
        try {
            delegate.delay(ms);
        } catch (InjectionException e) {
            AppLogger.e(TAG, "delay(" + ms + ") failed: " + e.getMessage());
        }
    }

    @Override
    public void repeatKey(long intervalMs, int keyCode) {
        PDebug.d(TAG, "repeatKey: key=" + keyName(keyCode) + " interval=" + intervalMs + "ms");
        try {
            delegate.repeatKey(intervalMs, keyCode);
        } catch (InjectionException e) {
            AppLogger.e(TAG, "repeatKey(" + intervalMs + ", " + keyName(keyCode)
                    + ") failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        boolean result = delegate.isAvailable();
        PDebug.d(TAG, "isAvailable -> " + result);
        return result;
    }

    @Override
    public ServiceState getServiceState() {
        ServiceState result = delegate.getServiceState();
        PDebug.d(TAG, "getServiceState -> " + result);
        return result;
    }

    @Override
    public String getName() {
        return delegate.getName() + "(Safe)";
    }

    @Override
    public void restoreSessionIme() {
        delegate.restoreSessionIme();
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
