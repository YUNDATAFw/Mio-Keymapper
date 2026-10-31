package io.takamiya.mio.monitor;

import io.takamiya.mio.monitor.IMonitorCallback;

/**
 * 外接键鼠监听服务（Shizuku UserService，shell 权限跑 getevent，只读不注入）。
 */
interface IMonitorService {
    /** 注册事件回调；传 null 反注册。注册后若有旧回调会被替换。 */
    void registerCallback(IMonitorCallback callback);

    /** 异步启动监听（内部扫描设备 + 起 getevent 子进程），状态经回调回报。 */
    boolean startMonitor();

    /** 停止监听并清理全部 getevent 子进程；幂等。 */
    void stopMonitor();

    /** 0=IDLE 1=STARTING 2=RUNNING 3=FAILED */
    int getState();

    /** 当前成功读取的设备数 */
    int getDeviceCount();

    /** 最近一次失败原因（可为空串） */
    String getLastError();

    /** 停止监听并释放服务进程 */
    void destroy();
}
