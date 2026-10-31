package io.takamiya.mio.monitor;

/**
 * 监听事件回调（宿主 → App 进程单向推送）。
 * oneway：daemon 的 getevent 读线程不能被 binder 调用阻塞。
 */
oneway interface IMonitorCallback {
    /** 一条过滤后的按键事件（仅 EV_KEY，value 0=抬起 / 1=按下）。 */
    void onKeyEvent(int evdevCode, int value);

    /** 监听状态变化：0=IDLE 1=STARTING 2=RUNNING 3=FAILED，detail 为可读说明。 */
    void onMonitorState(int state, String detail);
}
