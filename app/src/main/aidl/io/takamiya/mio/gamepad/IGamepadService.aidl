package io.takamiya.mio.gamepad;

interface IGamepadService {
    int openDevice(String name);
    void sendKey(int linuxCode, int value);
    void sendAxis(int axis, int value);
    void sendStick(int lx, int ly, int rx, int ry);
    void sendTrigger(int lt, int rt);
    void sendDpad(int x, int y);
    void destroy();
    int getLastError();
}