package io.takamiya.mio.floating;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Build;
import android.view.WindowManager;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.injector.MioAccessibilityService;

public final class FloatingWindowType {

    private static final String TAG = "FloatingWindowType";

    private FloatingWindowType() {
    }

    public static int resolve(Context context) {
        if (MioAccessibilityService.isTrustedOverlayAvailable(context)) {
            return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }

    public static void applyToken(WindowManager.LayoutParams params) {
        if (params.type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
            params.token = MioAccessibilityService.getOverlayWindowToken();
        }
    }

    public static void applyPassThroughAlphaClamp(Context context, WindowManager.LayoutParams params) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        if (params.type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
            return;
        }
        InputManager im = context.getSystemService(InputManager.class);
        float max = im != null ? im.getMaximumObscuringOpacityForTouch() : 0.8f;
        if (params.alpha > max) {
            AppLogger.d(TAG, "clamp pass-through window alpha: " + params.alpha + " -> " + max
                    + " (untrusted overlay would block touches on Android "
                    + Build.VERSION.SDK_INT + ")");
            params.alpha = max;
        }
    }
}
