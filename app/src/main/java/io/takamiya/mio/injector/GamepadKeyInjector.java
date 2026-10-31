package io.takamiya.mio.injector;

import android.content.Context;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.gamepad.GamepadButton;
import io.takamiya.mio.gamepad.GamepadEngine;
import io.takamiya.mio.gamepad.GamepadMapping;

/**
 * 手柄按键注入适配器：把 Android KeyEvent 键码映射为虚拟手柄键。
 * uinput 可用时走虚拟手柄，否则自动降级为 SOURCE_GAMEPAD 注入。
 */
public class GamepadKeyInjector implements KeyInjector {

    private static final String TAG = "GamepadKeyInjector";

    private final GamepadEngine engine;

    public GamepadKeyInjector(Context context) {
        this.engine = GamepadEngine.get(context);
    }

    @Override
    public void pressKey(int keyCode) throws InjectionException {
        GamepadButton btn = GamepadMapping.getButtonByAndroidKeyCode(keyCode);
        if (btn == null) {
            AppLogger.w(TAG, "pressKey: no gamepad button for keyCode " + keyCode);
            return;
        }
        engine.pressButton(btn);
        engine.releaseButton(btn);
    }

    @Override
    public void pressKey(int keyCode, long holdDurationMs) throws InjectionException {
        GamepadButton btn = GamepadMapping.getButtonByAndroidKeyCode(keyCode);
        if (btn == null) return;
        engine.pressButton(btn);
        try {
            Thread.sleep(Math.max(0, holdDurationMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        engine.releaseButton(btn);
    }

    @Override
    public void pressKey(int... keyCodes) throws InjectionException {
        for (int kc : keyCodes) {
            GamepadButton btn = GamepadMapping.getButtonByAndroidKeyCode(kc);
            if (btn != null) engine.pressButton(btn);
        }
        for (int kc : keyCodes) {
            GamepadButton btn = GamepadMapping.getButtonByAndroidKeyCode(kc);
            if (btn != null) engine.releaseButton(btn);
        }
    }

    @Override
    public void pressKeyCombo(int keyCode, int... modifierKeyCodes) throws InjectionException {
        for (int mod : modifierKeyCodes) {
            if (mod == keyCode) continue;
            GamepadButton btn = GamepadMapping.getButtonByAndroidKeyCode(mod);
            if (btn != null) engine.pressButton(btn);
        }
        pressKey(keyCode);
        for (int mod : modifierKeyCodes) {
            if (mod == keyCode) continue;
            GamepadButton btn = GamepadMapping.getButtonByAndroidKeyCode(mod);
            if (btn != null) engine.releaseButton(btn);
        }
    }

    @Override
    public void pressKeyCombo(int keyCode, long holdDurationMs, int... modifierKeyCodes)
            throws InjectionException {
        for (int mod : modifierKeyCodes) {
            if (mod == keyCode) continue;
            GamepadButton btn = GamepadMapping.getButtonByAndroidKeyCode(mod);
            if (btn != null) engine.pressButton(btn);
        }
        pressKey(keyCode, holdDurationMs);
        for (int mod : modifierKeyCodes) {
            if (mod == keyCode) continue;
            GamepadButton btn = GamepadMapping.getButtonByAndroidKeyCode(mod);
            if (btn != null) engine.releaseButton(btn);
        }
    }

    @Override
    public void inputText(String text) throws InjectionException {
        AppLogger.w(TAG, "inputText not supported for gamepad");
    }

    @Override
    public void keyDown(int keyCode) throws InjectionException {
        GamepadButton btn = GamepadMapping.getButtonByAndroidKeyCode(keyCode);
        if (btn != null) engine.pressButton(btn);
    }

    @Override
    public void keyUp(int keyCode) throws InjectionException {
        GamepadButton btn = GamepadMapping.getButtonByAndroidKeyCode(keyCode);
        if (btn != null) engine.releaseButton(btn);
    }

    @Override
    public void longPress(int keyCode, long durationMs) throws InjectionException {
        pressKey(keyCode, durationMs);
    }

    @Override
    public void delay(long ms) throws InjectionException {
        try {
            Thread.sleep(Math.max(0, ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InjectionException("delay interrupted", e);
        }
    }

    private static final long MAX_REPEAT_RUN_MS = 30 * 60 * 1000L;

    @Override
    public void repeatKey(long intervalMs, int keyCode) throws InjectionException {
        long deadline = System.currentTimeMillis() + MAX_REPEAT_RUN_MS;
        while (System.currentTimeMillis() < deadline) {
            pressKey(keyCode);
            if (Thread.interrupted()) return;
            delay(intervalMs);
            if (Thread.interrupted()) return;
        }
    }

    @Override
    public boolean isAvailable() {
        return engine.isOpen()
                || (io.takamiya.mio.shizuku.ShizukuHelper.getInstance().isGranted());
    }

    @Override
    public ServiceState getServiceState() {
        if (engine.isOpen() || engine.getState() == io.takamiya.mio.gamepad.GamepadState.FALLBACK_KEY_INJECTION) {
            return ServiceState.READY;
        }
        return ServiceState.NOT_READY;
    }

    @Override
    public String getName() {
        return "GAMEPAD";
    }
}