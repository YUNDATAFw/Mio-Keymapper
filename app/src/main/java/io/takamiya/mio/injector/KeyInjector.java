package io.takamiya.mio.injector;

public interface KeyInjector {

    void pressKey(int keyCode) throws InjectionException;

    void pressKey(int keyCode, long holdDurationMs) throws InjectionException;

    void pressKey(int... keyCodes) throws InjectionException;

    void pressKeyCombo(int keyCode, int... modifierKeyCodes) throws InjectionException;

    void pressKeyCombo(int keyCode, long holdDurationMs, int... modifierKeyCodes) throws InjectionException;

    void inputText(String text) throws InjectionException;

    default void inputText(String text, long keyHoldDurationMs) throws InjectionException {
        inputText(text);
    }

    void keyDown(int keyCode) throws InjectionException;

    void keyUp(int keyCode) throws InjectionException;

    void longPress(int keyCode, long durationMs) throws InjectionException;

    void delay(long ms) throws InjectionException;

    void repeatKey(long intervalMs, int keyCode) throws InjectionException;

    boolean isAvailable();

    ServiceState getServiceState();

    String getName();

    default void restoreSessionIme() {}
}
