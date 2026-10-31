package io.takamiya.mio.injector;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.SoftKeyboardController;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.RequiresApi;

import cn.panda.utils.library.AppLogger;

public class MioAccessibilityService extends AccessibilityService {

    private static final String TAG = "MioAccessibilityService";
    private static final String OWN_PACKAGE = "io.takamiya.mio";
    private static final long DEFAULT_CLICK_TIMEOUT_MS = 3000L;
    /** clickAt(x,y) 未指定时长时的按压时长回退值。 */
    private static final long DEFAULT_PRESS_DURATION_MS = 300L;

    private static volatile MioAccessibilityService instance;
    private static volatile String currentPackage;
    private static volatile IBinder overlayWindowToken;
    private static volatile OverlayTokenListener overlayTokenListener;

    public interface OverlayTokenListener {
        void onOverlayTokenReady();

        void onOverlayTokenInvalidated();
    }

    public static void setOverlayTokenListener(OverlayTokenListener listener) {
        overlayTokenListener = listener;
    }

    public static boolean isTrustedOverlayAvailable(Context context) {
        return isServiceEnabled(context) && getOverlayWindowToken() != null;
    }

    public static MioAccessibilityService getInstance() {
        return instance;
    }

    public static String getCurrentPackage() {
        AppLogger.d(TAG, "getCurrentPackage -> " + currentPackage);
        return currentPackage;
    }

    public static IBinder getOverlayWindowToken() {
        return overlayWindowToken;
    }

    /**
     * 屏幕坐标点击（同步等待结果）。服务未连接/派发失败返回 false。
     *
     * @param x 屏幕原始像素 X
     * @param y 屏幕原始像素 Y
     */
    public static boolean clickAt(float x, float y) {
        return clickAt(x, y, DEFAULT_PRESS_DURATION_MS);
    }

    /**
     * 屏幕坐标点击，可指定按压时长。
     *
     * @param strokeDurationMs 手势按压时长（毫秒），限制在 50~3000
     */
    public static boolean clickAt(float x, float y, long strokeDurationMs) {
        MioAccessibilityService svc = instance;
        if (svc == null) {
            AppLogger.w(TAG, "clickAt: accessibility service not connected");
            return false;
        }
        long duration = Math.max(50L, Math.min(3000L, strokeDurationMs));
        return svc.dispatchClick(x, y, duration, DEFAULT_CLICK_TIMEOUT_MS);
    }

    private boolean dispatchClick(float x, float y, long strokeDurationMs, long timeoutMs) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture;
        try {
            gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, strokeDurationMs))
                    .build();
        } catch (Throwable t) {
            AppLogger.e(TAG, "dispatchClick: build gesture failed: " + t.getMessage(), t);
            return false;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        boolean dispatched;
        try {
            dispatched = dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription completedGesture) {
                    ok.set(true);
                    latch.countDown();
                }

                @Override
                public void onCancelled(GestureDescription cancelledGesture) {
                    AppLogger.w(TAG, "dispatchClick: gesture cancelled");
                    latch.countDown();
                }
            }, new Handler(Looper.getMainLooper()));
        } catch (Throwable t) {
            AppLogger.e(TAG, "dispatchClick: dispatch failed: " + t.getMessage(), t);
            return false;
        }
        if (!dispatched) {
            AppLogger.w(TAG, "dispatchClick: dispatchGesture returned false");
            return false;
        }
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                AppLogger.w(TAG, "dispatchClick: timeout after " + timeoutMs + "ms");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return ok.get();
    }

    public static boolean isServiceEnabled(Context context) {
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) {
            AppLogger.w(TAG, "isServiceEnabled: ENABLED_ACCESSIBILITY_SERVICES is null");
            return false;
        }
        String packageName = context.getPackageName();
        for (String service : enabledServices.split(":")) {
            if (service.startsWith(packageName + "/")) {
                AppLogger.d(TAG, "isServiceEnabled: enabled, service=" + service);
                return true;
            }
        }
        AppLogger.w(TAG, "isServiceEnabled: NOT enabled, package=" + packageName
                + " enabledServices=" + enabledServices);
        return false;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        AppLogger.d(TAG, "onServiceConnected: service connected, pid=" + android.os.Process.myPid());
        overlayWindowToken = readOverlayWindowToken();
        if (overlayWindowToken != null) {
            AppLogger.d(TAG, "onServiceConnected: overlay window token captured");
            OverlayTokenListener listener = overlayTokenListener;
            if (listener != null) {
                try {
                    listener.onOverlayTokenReady();
                } catch (Throwable t) {
                    AppLogger.e(TAG, "onOverlayTokenReady callback failed", t);
                }
            }
        } else {
            AppLogger.w(TAG, "onServiceConnected: failed to read overlay window token");
        }
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            AppLogger.d(TAG, "onServiceConnected: info=" + info);
        } catch (Throwable t) {
            AppLogger.e(TAG, "onServiceConnected: failed to read service info", t);
        }
    }

    private IBinder readOverlayWindowToken() {
        try {
            Field field = AccessibilityService.class.getDeclaredField("mWindowToken");
            field.setAccessible(true);
            return (IBinder) field.get(this);
        } catch (Throwable t) {
            AppLogger.w(TAG, "read overlay window token failed: " + t.getMessage());
            return null;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            AppLogger.d(TAG, "onAccessibilityEvent: null event, ignored");
            return;
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AppLogger.v(TAG, "onAccessibilityEvent: ignored eventType=" + typeName(type));
            return;
        }
        CharSequence packageSeq = event.getPackageName();
        if (packageSeq == null) {
            AppLogger.d(TAG, "onAccessibilityEvent: null package, ignored");
            return;
        }
        String pkg = packageSeq.toString();
        if (OWN_PACKAGE.equals(pkg) || pkg.isEmpty()) {
            AppLogger.d(TAG, "onAccessibilityEvent: ignoring own/empty package, pkg=" + pkg);
            return;
        }
        String previous = currentPackage;
        currentPackage = pkg;
        AppLogger.d(TAG, "onAccessibilityEvent: current package: " + pkg
                + " (was " + previous + "), className=" + event.getClassName());
    }

    @Override
    public void onInterrupt() {
        AppLogger.w(TAG, "onInterrupt: accessibility service interrupted");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        AppLogger.w(TAG, "onUnbind: service unbound, intent=" + intent);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        AppLogger.d(TAG, "onDestroy: clearing currentPackage=" + currentPackage);
        OverlayTokenListener listener = overlayTokenListener;
        if (listener != null) {
            try {
                listener.onOverlayTokenInvalidated();
            } catch (Throwable t) {
                AppLogger.e(TAG, "onOverlayTokenInvalidated callback failed", t);
            }
        }
        currentPackage = null;
        overlayWindowToken = null;
        instance = null;
        super.onDestroy();
    }

    @RequiresApi(Build.VERSION_CODES.R)
    public boolean switchIme(String imeId) {
        try {
            boolean ok = getSoftKeyboardController().switchToInputMethod(imeId);
            AppLogger.d(TAG, "switchIme: id=" + imeId + " -> " + ok);
            return ok;
        } catch (Throwable t) {
            AppLogger.e(TAG, "switchIme failed: id=" + imeId, t);
            return false;
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public boolean enableOwnIme() {
        try {
            String id = ImeKeyInjector.getMioImeId(this);
            int status = getSoftKeyboardController().setInputMethodEnabled(id, true);
            boolean ok = status == SoftKeyboardController.ENABLE_IME_SUCCESS;
            AppLogger.d(TAG, "enableOwnIme: id=" + id + " status=" + status + " -> " + ok);
            return ok;
        } catch (Throwable t) {
            AppLogger.e(TAG, "enableOwnIme failed", t);
            return false;
        }
    }

    private static String typeName(int type) {
        switch (type) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED: return "VIEW_CLICKED";
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: return "WINDOW_STATE_CHANGED";
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: return "WINDOW_CONTENT_CHANGED";
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED: return "VIEW_TEXT_CHANGED";
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED: return "NOTIFICATION_STATE_CHANGED";
            default: return "TYPE_" + type;
        }
    }
}
