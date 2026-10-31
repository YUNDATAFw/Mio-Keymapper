package io.takamiya.mio.shizuku;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.List;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.BuildConfig;
import io.takamiya.mio.gamepad.GamepadDaemon;
import io.takamiya.mio.gamepad.IGamepadService;
import rikka.shizuku.Shizuku;
import rikka.shizuku.Shizuku.UserServiceArgs;

public class ShizukuHelper {

    private static final String TAG = "ShizukuHelper";
    private static final int REQUEST_CODE = 2001;

    private static volatile ShizukuHelper instance;

    private WeakReference<ShizukuCallback> callbackRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = this::onBinderReceived;
    private final Shizuku.OnBinderDeadListener binderDeadListener = this::onBinderDead;
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = this::onPermissionResult;

    private boolean initialized = false;

    private volatile IShellService shellService;
    private volatile boolean serviceBound = false;

    private volatile IGamepadService gamepadService;
    private volatile boolean gamepadBound = false;

    private volatile io.takamiya.mio.monitor.IMonitorService monitorService;
    private volatile boolean monitorBound = false;
    private volatile MonitorConnectionListener monitorConnectionListener;

    /** 外接键鼠监听服务的连接状态回调（onServiceConnected/Disconnected 转发）。 */
    public interface MonitorConnectionListener {
        void onMonitorConnected();

        void onMonitorDisconnected();
    }

    private final ServiceConnection monitorServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AppLogger.d(TAG, "monitor onServiceConnected: " + name);
            monitorService = io.takamiya.mio.monitor.IMonitorService.Stub.asInterface(service);
            monitorBound = true;
            MonitorConnectionListener l = monitorConnectionListener;
            if (l != null) {
                l.onMonitorConnected();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            AppLogger.w(TAG, "monitor onServiceDisconnected: " + name);
            monitorService = null;
            monitorBound = false;
            MonitorConnectionListener l = monitorConnectionListener;
            if (l != null) {
                l.onMonitorDisconnected();
            }
        }
    };

    private final UserServiceArgs monitorUserServiceArgs = new UserServiceArgs(
            new ComponentName(BuildConfig.APPLICATION_ID,
                    io.takamiya.mio.monitor.MonitorDaemon.class.getName())
    ).tag("monitor_service")
            .processNameSuffix("monitor")
            .version(1)
            .daemon(false)
            .debuggable(false);

    private final ServiceConnection gamepadServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AppLogger.d(TAG, "gamepad onServiceConnected: " + name + " binder=" + service);
            gamepadService = IGamepadService.Stub.asInterface(service);
            gamepadBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            AppLogger.w(TAG, "gamepad onServiceDisconnected: " + name);
            gamepadService = null;
            gamepadBound = false;
        }
    };

    private final UserServiceArgs gamepadUserServiceArgs = new UserServiceArgs(
            new ComponentName(BuildConfig.APPLICATION_ID, GamepadDaemon.class.getName())
    ).tag("gamepaddaemon")
            .processNameSuffix("gamepadpad")
            .version(1)
            .daemon(false)
            .debuggable(false);

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AppLogger.d(TAG, "onServiceConnected: name=" + name + " binder=" + service);
            shellService = IShellService.Stub.asInterface(service);
            serviceBound = true;
            AppLogger.d(TAG, "onServiceConnected: serviceBound=true, service=" + shellService);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            AppLogger.w(TAG, "onServiceDisconnected: name=" + name
                    + ", pid=" + android.os.Process.myPid());
            shellService = null;
            serviceBound = false;
            io.takamiya.mio.injector.ShizukuKeyInjector.invalidateServiceCache();
            AppLogger.w(TAG, "onServiceDisconnected: state reset, rebinding immediately");
            logShellExitReasons();
            bindShellService();
        }
    };

    private final UserServiceArgs userServiceArgs = new UserServiceArgs(
            new ComponentName(BuildConfig.APPLICATION_ID, ShellService.class.getName())
    ).tag("shell_service")
            .processNameSuffix("shell")
            .version(4)
            .daemon(true)
            .debuggable(false);

    public interface ShizukuCallback {
        void onShizukuReady();

        void onShizukuDenied();

        void onShizukuNotAvailable();

        default void onBinderDead() {}
    }

    private ShizukuHelper() {}

    public static ShizukuHelper getInstance() {
        if (instance == null) {
            synchronized (ShizukuHelper.class) {
                if (instance == null) {
                    instance = new ShizukuHelper();
                    AppLogger.d(TAG, "getInstance: created singleton");
                }
            }
        }
        return instance;
    }

    public void init(@Nullable ShizukuCallback callback) {
        this.callbackRef = new WeakReference<>(callback);
        AppLogger.d(TAG, "init: pid=" + android.os.Process.myPid()
                + " initialized=" + initialized
                + " callback=" + (callback != null ? callback.getClass().getSimpleName() : "null"));

        if (initialized) {
            AppLogger.d(TAG, "init: already initialized, skipping");
            return;
        }
        initialized = true;

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
            Shizuku.addBinderDeadListener(binderDeadListener);
            Shizuku.addRequestPermissionResultListener(permissionResultListener);
            AppLogger.d(TAG, "init: Shizuku listeners registered");
        } catch (Exception e) {
            AppLogger.e(TAG, "init: Shizuku not available", e);
            notifyNotAvailable();
        }
    }

    public void requestPermission() {
        try {
            if (Shizuku.isPreV11()) {
                AppLogger.w(TAG, "requestPermission: Shizuku pre-v11, not supported");
                notifyNotAvailable();
                return;
            }

            int checkResult = Shizuku.checkSelfPermission();
            AppLogger.d(TAG, "requestPermission: checkSelfPermission=" + checkResult
                    + " GRANTED=" + PackageManager.PERMISSION_GRANTED);

            if (checkResult == PackageManager.PERMISSION_GRANTED) {
                AppLogger.d(TAG, "requestPermission: already granted, notifying ready");
                notifyReady();
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                AppLogger.w(TAG, "requestPermission: permission denied by user");
                notifyDenied();
            } else {
                AppLogger.d(TAG, "requestPermission: requesting permission, requestCode=" + REQUEST_CODE);
                Shizuku.requestPermission(REQUEST_CODE);
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "requestPermission: failed", e);
            notifyNotAvailable();
        }
    }

    public boolean isGranted() {
        try {
            boolean result = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            AppLogger.d(TAG, "isGranted -> " + result);
            return result;
        } catch (Exception e) {
            AppLogger.w(TAG, "isGranted: exception", e);
            return false;
        }
    }

    public boolean isRunning() {
        try {
            boolean result = Shizuku.pingBinder();
            AppLogger.d(TAG, "isRunning -> " + result);
            return result;
        } catch (Exception e) {
            AppLogger.w(TAG, "isRunning: exception", e);
            return false;
        }
    }

    public void bindShellService() {
        if (serviceBound) {
            AppLogger.d(TAG, "bindShellService: already bound, skipping");
            return;
        }
        try {
            AppLogger.d(TAG, "bindShellService: binding user service, args=" + userServiceArgs
                    + ", pid=" + android.os.Process.myPid());
            Shizuku.bindUserService(userServiceArgs, serviceConnection);
            AppLogger.d(TAG, "bindShellService: bindUserService() invoked");
        } catch (Exception e) {
            AppLogger.e(TAG, "bindShellService: failed to bind", e);
        }
    }

    public void ensureBound() {
        if (shellService != null && isShellAlive()) {
            AppLogger.d(TAG, "ensureBound: already alive, nothing to do");
            return;
        }
        if (shellService != null || serviceBound) {
            AppLogger.w(TAG, "ensureBound: binder dead, resetting state (shellService="
                    + (shellService != null) + " serviceBound=" + serviceBound + ")");
            shellService = null;
            serviceBound = false;
            logShellExitReasons();
        }
        bindShellService();
    }

    private boolean isShellAlive() {
        try {
            boolean alive = shellService.asBinder().pingBinder();
            AppLogger.d(TAG, "isShellAlive -> " + alive);
            return alive;
        } catch (Exception e) {
            AppLogger.w(TAG, "isShellAlive: exception", e);
            return false;
        }
    }

    public void unbindShellService() {
        if (!serviceBound) {
            AppLogger.w(TAG, "unbindShellService: called but not bound");
            return;
        }
        AppLogger.w(TAG, "unbindShellService: destroying shell process");
        try {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true);
        } catch (Exception e) {
            AppLogger.e(TAG, "unbindShellService: failed", e);
        }
        shellService = null;
        serviceBound = false;
        io.takamiya.mio.injector.ShizukuKeyInjector.invalidateServiceCache();
        AppLogger.d(TAG, "unbindShellService: state reset");
    }

    public void resetShellState() {
        AppLogger.w(TAG, "resetShellState: shellService=" + (shellService != null)
                + " serviceBound=" + serviceBound + " -> clearing");
        shellService = null;
        serviceBound = false;
        io.takamiya.mio.injector.ShizukuKeyInjector.invalidateServiceCache();
    }

    private void logShellExitReasons() {
        if (Build.VERSION.SDK_INT < 30) {
            AppLogger.w(TAG, "Shell exit reason unavailable on API " + Build.VERSION.SDK_INT);
            return;
        }
        try {
            io.takamiya.mio.app.Mio app = io.takamiya.mio.app.Mio.getInstance();
            if (app == null) {
                AppLogger.w(TAG, "Shell exit reason unavailable: app context not ready");
                return;
            }
            ActivityManager am = (ActivityManager) app.getSystemService(android.content.Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> infos = am.getHistoricalProcessExitReasons(null, 0, 20);
            if (infos == null || infos.isEmpty()) {
                AppLogger.w(TAG, "Shell exit reason unavailable: no exit info recorded");
                return;
            }
            int shellCount = 0;
            for (ApplicationExitInfo info : infos) {
                String processName = info.getProcessName();
                if (processName == null || !processName.contains(":shell")) {
                    continue;
                }
                shellCount++;
                AppLogger.w(TAG, "ShellExitReason pid=" + info.getPid()
                        + " reason=" + reasonName(info.getReason())
                        + " status=" + info.getStatus()
                        + " processName=" + processName
                        + " timestamp=" + info.getTimestamp()
                        + " description=" + info.getDescription());
                if (info.getReason() == ApplicationExitInfo.REASON_SIGNALED) {
                    AppLogger.w(TAG, "ShellExitReason signal=" + signalName(info.getStatus())
                            + " (9=SIGKILL system kill, 6=SIGABRT, 11=SIGSEGV crash)");
                }
                if (info.getReason() == ApplicationExitInfo.REASON_CRASH
                        || info.getReason() == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE) {
                    logExitTrace(info);
                }
            }
            if (shellCount == 0) {
                AppLogger.w(TAG, "Shell exit reason: no :shell process records in recent "
                        + infos.size() + " exits");
            }
        } catch (Throwable t) {
            AppLogger.e(TAG, "Failed to query shell exit reasons", t);
        }
    }

    private void logExitTrace(ApplicationExitInfo info) {
        try {
            java.io.InputStream trace = info.getTraceInputStream();
            if (trace == null) {
                AppLogger.w(TAG, "ShellExitReason no trace stream for pid=" + info.getPid());
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(trace));
            StringBuilder head = new StringBuilder();
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                if (lineCount < 5) {
                    head.append(line).append('\n');
                }
                lineCount++;
            }
            AppLogger.w(TAG, "ShellExitReason trace pid=" + info.getPid()
                    + " lines=" + lineCount + " head:\n" + head);
        } catch (Throwable t) {
            AppLogger.e(TAG, "Failed to read exit trace pid=" + info.getPid(), t);
        }
    }

    private static String signalName(int signal) {
        switch (signal) {
            case 1: return "SIGHUP";
            case 2: return "SIGINT";
            case 3: return "SIGQUIT";
            case 4: return "SIGILL";
            case 6: return "SIGABRT";
            case 7: return "SIGBUS";
            case 8: return "SIGFPE";
            case 9: return "SIGKILL";
            case 11: return "SIGSEGV";
            case 13: return "SIGPIPE";
            case 14: return "SIGALRM";
            case 15: return "SIGTERM";
            default: return "SIG_" + signal;
        }
    }

    private static String reasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_UNKNOWN: return "UNKNOWN";
            case ApplicationExitInfo.REASON_EXIT_SELF: return "EXIT_SELF";
            case ApplicationExitInfo.REASON_SIGNALED: return "SIGNALED";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "LOW_MEMORY";
            case ApplicationExitInfo.REASON_CRASH: return "CRASH";
            case ApplicationExitInfo.REASON_ANR: return "ANR";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "EXCESSIVE_RESOURCE_USAGE";
            case ApplicationExitInfo.REASON_FREEZER: return "FREEZER";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "INITIALIZATION_FAILURE";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE: return "PERMISSION_CHANGE";
            case ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE: return "PACKAGE_STATE_CHANGE";
            case ApplicationExitInfo.REASON_PACKAGE_UPDATED: return "PACKAGE_UPDATED";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED: return "DEPENDENCY_DIED";
            case ApplicationExitInfo.REASON_USER_STOPPED: return "USER_STOPPED";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "USER_REQUESTED";
            default: return "REASON_" + reason;
        }
    }

    public IShellService getShellService() {
        return shellService;
    }

    public boolean isServiceBound() {
        return serviceBound;
    }

    public void bindGamepadDaemon() {
        if (gamepadBound) {
            AppLogger.d(TAG, "bindGamepadDaemon: already bound, skipping");
            return;
        }
        try {
            AppLogger.d(TAG, "bindGamepadDaemon: binding user service, args=" + gamepadUserServiceArgs);
            Shizuku.bindUserService(gamepadUserServiceArgs, gamepadServiceConnection);
        } catch (Exception e) {
            AppLogger.e(TAG, "bindGamepadDaemon: failed to bind", e);
        }
    }

    public void unbindGamepadDaemon() {
        if (!gamepadBound && gamepadService == null) {
            AppLogger.w(TAG, "unbindGamepadDaemon: not bound");
            return;
        }
        AppLogger.w(TAG, "unbindGamepadDaemon: destroying gamepad process");
        try {
            Shizuku.unbindUserService(gamepadUserServiceArgs, gamepadServiceConnection, true);
        } catch (Exception e) {
            AppLogger.e(TAG, "unbindGamepadDaemon: failed", e);
        }
        gamepadService = null;
        gamepadBound = false;
    }

    public IGamepadService getGamepadService() {
        return gamepadService;
    }

    public boolean isGamepadBound() {
        return gamepadBound;
    }

    public void resetGamepadState() {
        AppLogger.w(TAG, "resetGamepadState");
        gamepadService = null;
        gamepadBound = false;
    }

    public void setMonitorConnectionListener(MonitorConnectionListener listener) {
        this.monitorConnectionListener = listener;
    }

    public void bindMonitorService() {
        if (monitorBound) {
            AppLogger.d(TAG, "bindMonitorService: already bound, skipping");
            return;
        }
        try {
            AppLogger.d(TAG, "bindMonitorService: binding user service, args=" + monitorUserServiceArgs);
            Shizuku.bindUserService(monitorUserServiceArgs, monitorServiceConnection);
        } catch (Exception e) {
            AppLogger.e(TAG, "bindMonitorService: failed to bind", e);
        }
    }

    public void unbindMonitorService() {
        if (!monitorBound && monitorService == null) {
            AppLogger.w(TAG, "unbindMonitorService: not bound");
            return;
        }
        AppLogger.d(TAG, "unbindMonitorService: destroying monitor process");
        try {
            Shizuku.unbindUserService(monitorUserServiceArgs, monitorServiceConnection, true);
        } catch (Exception e) {
            AppLogger.e(TAG, "unbindMonitorService: failed to unbind", e);
        }
        monitorService = null;
        monitorBound = false;
    }

    public io.takamiya.mio.monitor.IMonitorService getMonitorService() {
        return monitorService;
    }

    public boolean isMonitorBound() {
        return monitorBound;
    }

    private void onBinderReceived() {
        AppLogger.d(TAG, "onBinderReceived: Shizuku binder received, requesting permission");
        requestPermission();
    }

    private void onBinderDead() {
        AppLogger.w(TAG, "onBinderDead: Shizuku binder dead, pid=" + android.os.Process.myPid());
        ShizukuCallback cb = callbackRef != null ? callbackRef.get() : null;
        if (cb != null) {
            mainHandler.post(() -> cb.onBinderDead());
        }
    }

    private void onPermissionResult(int requestCode, int grantResult) {
        AppLogger.d(TAG, "onPermissionResult: requestCode=" + requestCode
                + " grantResult=" + grantResult + " GRANTED=" + PackageManager.PERMISSION_GRANTED);
        if (requestCode != REQUEST_CODE) {
            AppLogger.w(TAG, "onPermissionResult: unexpected requestCode, ignored");
            return;
        }

        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            AppLogger.d(TAG, "onPermissionResult: granted, notifying ready");
            notifyReady();
        } else {
            AppLogger.w(TAG, "onPermissionResult: denied, grantResult=" + grantResult);
            notifyDenied();
        }
    }

    private void notifyReady() {
        AppLogger.d(TAG, "notifyReady: binding shell service");
        bindShellService();
        ShizukuCallback cb = callbackRef != null ? callbackRef.get() : null;
        AppLogger.d(TAG, "notifyReady: callback=" + (cb != null ? cb.getClass().getSimpleName() : "null"));
        if (cb != null) {
            mainHandler.post(cb::onShizukuReady);
        }
    }

    private void notifyDenied() {
        AppLogger.w(TAG, "notifyDenied");
        ShizukuCallback cb = callbackRef != null ? callbackRef.get() : null;
        if (cb != null) {
            mainHandler.post(cb::onShizukuDenied);
        }
    }

    private void notifyNotAvailable() {
        AppLogger.w(TAG, "notifyNotAvailable");
        ShizukuCallback cb = callbackRef != null ? callbackRef.get() : null;
        if (cb != null) {
            mainHandler.post(cb::onShizukuNotAvailable);
        }
    }

    public void destroy() {
        AppLogger.w(TAG, "destroy() called, pid=" + android.os.Process.myPid(),
                new Throwable("destroy() caller"));
        try {
            unbindShellService();
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            Shizuku.removeBinderDeadListener(binderDeadListener);
            Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        } catch (Exception e) {
            AppLogger.e(TAG, "destroy: failed to remove listeners", e);
        }
        callbackRef = null;
        initialized = false;
        AppLogger.d(TAG, "destroy: done");
    }
}
