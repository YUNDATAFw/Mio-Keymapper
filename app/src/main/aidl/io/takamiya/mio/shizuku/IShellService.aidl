package io.takamiya.mio.shizuku;

interface IShellService {
    String execCommand(String command) = 1;
    String execCommandWithArgs(in String[] args) = 2;
    boolean injectKeyEvent(int keyCode, int flags) = 3;
    boolean injectKeyDown(int keyCode) = 4;
    boolean injectKeyUp(int keyCode) = 5;
    boolean injectCombo(int keyCode, in int[] modifierKeyCodes) = 6;
    boolean injectKeyEventWithDuration(int keyCode, int flags, int holdDurationMs) = 7;
    boolean injectComboWithDuration(int keyCode, in int[] modifierKeyCodes, int holdDurationMs) = 8;
    boolean injectGamepadKey(int keyCode, int scancode, boolean pressed) = 9;
    void destroy() = 16777114;
}
