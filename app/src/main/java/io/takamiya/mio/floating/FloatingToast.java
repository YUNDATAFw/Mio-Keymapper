package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.exceptions.RegistryException;
import io.takamiya.mio.R;
import io.takamiya.mio.config.ToastSettings;
import io.takamiya.mio.databinding.FloatingWindowToastBinding;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class FloatingToast {

    private static final String TAG = "FloatingToast";
    private static final long AUTO_DISMISS_MS = 2000;

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowToastBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean hasPosition;
    private boolean hasColor;
    private int savedBold;
    private int savedColor;
    private float currentScale = 1.0f;
    private final float defaultFontSizePx;
    private final int defaultCardColor;

    public FloatingToast(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.binding = FloatingWindowToastBinding.inflate(LayoutInflater.from(context));
        this.defaultFontSizePx = binding.tvToast.getTextSize();
        this.defaultCardColor = binding.scalableContent.getCardBackgroundColor().getDefaultColor();

        int type = FloatingWindowType.resolve(context);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        FloatingWindowType.applyToken(params);
        params.gravity = Gravity.CENTER;

        loadConfig();
        loadScale();
        loadAlpha();
        applyConfig();
    }

    public void updateScale(float scale) {
        currentScale = Math.max(0.8f, Math.min(1.2f, scale));
        applyScale();
    }

    private void loadScale() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_SIZE.getValue())) {
                int saved = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_SIZE.getValue());
                currentScale = Math.max(0.8f, Math.min(1.2f, saved / 100f));
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load float size: " + e.getMessage());
        }
    }

    private void applyScale() {
        View container = binding.scalableContent;
        container.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int naturalWidth = Math.max(container.getMeasuredWidth(), 1);
        int naturalHeight = Math.max(container.getMeasuredHeight(), 1);
        ViewGroup.LayoutParams lp = container.getLayoutParams();
        if (lp != null) {
            lp.width = naturalWidth;
            lp.height = naturalHeight;
            container.setLayoutParams(lp);
        }
        params.width = (int) (naturalWidth * currentScale);
        params.height = (int) (naturalHeight * currentScale);
        PassThroughOpacityCoordinator.notifyGeometryChanged();
        container.setPivotX(0f);
        container.setPivotY(0f);
        container.setScaleX(currentScale);
        container.setScaleY(currentScale);
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
    }

    private void loadAlpha() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_ALPHA.getValue())) {
                int saved = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_ALPHA.getValue());
                params.alpha = saved / 100f;
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load float alpha: " + e.getMessage());
        }
    }

    public void show(String text) {
        show(text, null);
    }

    public void show(String text, ToastSettings settings) {
        handler.post(() -> {
            applyConfig();
            long duration = AUTO_DISMISS_MS;
            if (settings != null) {
                if (settings.getDurationMs() != null && settings.getDurationMs() >= 0) {
                    duration = settings.getDurationMs();
                }
                if (settings.getFontSize() != null && settings.getFontSize() > 0) {
                    binding.tvToast.setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.getFontSize());
                }
                if (settings.getTextColor() != null) {
                    binding.tvToast.setTextColor(settings.getTextColor());
                }
                if (settings.getBold() != null) {
                    binding.tvToast.setTypeface(null, settings.getBold() ? Typeface.BOLD : Typeface.NORMAL);
                }
                if (settings.getBackgroundColor() != null) {
                    binding.scalableContent.setCardBackgroundColor(settings.getBackgroundColor());
                }
            }
            binding.tvToast.setText(text);
            handler.removeCallbacksAndMessages(null);
            if (!isAttached()) {
                attach();
            }
            applyScale();
            FloatingAnimator.fadeIn(binding.getRoot(), FloatingAnimator.FAST_MS, null);
            handler.postDelayed(this::dismiss, duration);
        });
    }

    public void dismiss() {
        handler.removeCallbacksAndMessages(null);
        if (!isAttached()) return;
        FloatingAnimator.fadeOut(binding.getRoot(), FloatingAnimator.FAST_MS, this::detach);
    }

    public void setPosition(int x, int y) {
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        reClampPosition();
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
        savePosition(params.x, params.y);
    }

    public void setBold(boolean bold) {
        binding.tvToast.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
        saveBold(bold);
    }

    public void setTextColor(int color) {
        binding.tvToast.setTextColor(color);
        saveColor(color);
    }

    public void setWindowAlpha(float alpha) {
        params.alpha = alpha;
        FloatingWindowType.applyPassThroughAlphaClamp(context, params);
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
        PassThroughOpacityCoordinator.notifyTargetChanged(
                PassThroughOpacityCoordinator.ID_TOAST, params.alpha);
    }

    private void attach() {
        if (isAttached()) return;
        try {
            FloatingWindowType.applyPassThroughAlphaClamp(context, params);
            windowManager.addView(binding.getRoot(), params);
            applyScale();
            reClampPosition();
            PassThroughOpacityCoordinator.notifyAttached(
                    PassThroughOpacityCoordinator.ID_TOAST,
                    () -> PassThroughOpacityCoordinator.frameOf(context, params),
                    a -> {
                        params.alpha = a;
                        if (isAttached()) {
                            windowManager.updateViewLayout(binding.getRoot(), params);
                        }
                    },
                    false,
                    params.alpha);
            AppLogger.d(TAG, "toast attached");
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    // 将已保存的位置夹回安全区域，防止旋转/换机后跑到屏幕外
    private void reClampPosition() {
        if (params.gravity != (Gravity.TOP | Gravity.START)) return;
        Rect safe = WindowBounds.safeRect(context, binding.getRoot());
        int w = params.width > 0 ? params.width : Math.max(binding.getRoot().getWidth(), 1);
        int h = params.height > 0 ? params.height : Math.max(binding.getRoot().getHeight(), 1);
        params.x = WindowBounds.clampX(safe, params.x, w);
        params.y = WindowBounds.clampY(safe, params.y, h);
    }

    private void detach() {
        if (!isAttached()) return;
        PassThroughOpacityCoordinator.notifyDetached(PassThroughOpacityCoordinator.ID_TOAST);
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "toast detached");
        } catch (Exception e) {
            AppLogger.e(TAG, "detach failed: " + e.getMessage(), e);
        }
    }

    public boolean isAttached() {
        return binding.getRoot().getParent() != null;
    }

    private void loadConfig() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            hasPosition = reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_TOAST_X.getValue())
                    && reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_TOAST_Y.getValue());
            if (hasPosition) {
                params.gravity = Gravity.TOP | Gravity.START;
                params.x = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_TOAST_X.getValue());
                params.y = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_TOAST_Y.getValue());
            }
        } catch (RegistryException e) {
            hasPosition = false;
            AppLogger.w(TAG, "failed to load toast position: " + e.getMessage());
        }
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_TOAST_BOLD.getValue())) {
                savedBold = reg.getBoolean(DataManage.Tag.APP_CONFIG_FLOAT_TOAST_BOLD.getValue()) ? 1 : 0;
            }
        } catch (RegistryException e) {
            savedBold = 0;
            AppLogger.w(TAG, "failed to load toast bold: " + e.getMessage());
        }
        try {
            hasColor = reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_TOAST_COLOR.getValue());
            if (hasColor) {
                savedColor = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_TOAST_COLOR.getValue());
            }
        } catch (RegistryException e) {
            hasColor = false;
            AppLogger.w(TAG, "failed to load toast color: " + e.getMessage());
        }
    }

    private void applyConfig() {
        if (hasColor) {
            binding.tvToast.setTextColor(savedColor);
        } else {
            binding.tvToast.setTextColor(ContextCompat.getColor(context, R.color.colorOnSecondaryContainer));
        }
        binding.tvToast.setTypeface(null, savedBold == 1 ? Typeface.BOLD : Typeface.NORMAL);
        binding.tvToast.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultFontSizePx);
        binding.scalableContent.setCardBackgroundColor(defaultCardColor);
    }

    private void savePosition(int x, int y) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_TOAST_X.getValue(), x);
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_TOAST_Y.getValue(), y);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save toast position failed: " + e.getMessage(), e);
        }
    }

    private void saveBold(boolean bold) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_TOAST_BOLD.getValue(), bold);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save toast bold failed: " + e.getMessage(), e);
        }
    }

    private void saveColor(int color) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_TOAST_COLOR.getValue(), color);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save toast color failed: " + e.getMessage(), e);
        }
    }
}
