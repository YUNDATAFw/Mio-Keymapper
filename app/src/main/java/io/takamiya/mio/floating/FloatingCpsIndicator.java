package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.WindowManager;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.databinding.FloatingWindowCpsIndicatorBinding;

public class FloatingCpsIndicator {

    private static final String TAG = "FloatingCpsIndicator";

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowCpsIndicatorBinding binding;

    public FloatingCpsIndicator(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.binding = FloatingWindowCpsIndicatorBinding.inflate(LayoutInflater.from(context));

        int type = FloatingWindowType.resolve(context);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        FloatingWindowType.applyToken(params);
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.x = 16;
        params.y = 100;

        AppLogger.d(TAG, "constructor done");
    }

    public void updateCps(float cps) {
        String text = String.format("CPS: %.1f", cps);
        binding.tvCps.setText(text);
        AppLogger.d(TAG, "updateCps: " + text);
    }

    public void setWindowAlpha(float alpha) {
        params.alpha = alpha;
        FloatingWindowType.applyPassThroughAlphaClamp(context, params);
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
        PassThroughOpacityCoordinator.notifyTargetChanged(
                PassThroughOpacityCoordinator.ID_CPS, params.alpha);
    }

    public void attach() {
        if (isAttached()) return;
        try {
            FloatingWindowType.applyPassThroughAlphaClamp(context, params);
            windowManager.addView(binding.getRoot(), params);
            PassThroughOpacityCoordinator.notifyAttached(
                    PassThroughOpacityCoordinator.ID_CPS,
                    () -> PassThroughOpacityCoordinator.frameOf(context, params),
                    a -> {
                        params.alpha = a;
                        if (isAttached()) {
                            windowManager.updateViewLayout(binding.getRoot(), params);
                        }
                    },
                    false,
                    params.alpha);
            AppLogger.d(TAG, "attached");
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        PassThroughOpacityCoordinator.notifyDetached(PassThroughOpacityCoordinator.ID_CPS);
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "detached");
        } catch (Exception e) {
            AppLogger.e(TAG, "detach failed: " + e.getMessage(), e);
        }
    }

    public boolean isAttached() {
        return binding.getRoot().getParent() != null;
    }
}
