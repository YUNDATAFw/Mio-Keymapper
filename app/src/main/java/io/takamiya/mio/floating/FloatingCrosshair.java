package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.exceptions.RegistryException;
import io.takamiya.mio.R;
import io.takamiya.mio.databinding.FloatingWindowCrosshairBinding;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class FloatingCrosshair {

    private static final String TAG = "FloatingCrosshair";

    public interface OnPositionChangedListener {
        void onCrosshairPositionChanged(int x, int y);
    }

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowCrosshairBinding binding;

    private OnPositionChangedListener positionListener;

    private final int touchSlop;
    private float downRawX;
    private float downRawY;
    private int startX;
    private int startY;
    private boolean dragging;
    private boolean editMode;
    private float savedAlpha;
    private int crosshairSizeDp;

    public FloatingCrosshair(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        this.binding = FloatingWindowCrosshairBinding.inflate(LayoutInflater.from(context));

        int type = FloatingWindowType.resolve(context);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        FloatingWindowType.applyToken(params);
        params.gravity = Gravity.TOP | Gravity.START;

        loadSize();
        loadAlpha();
        loadSavedPosition();
        loadCustomIcon();
    }

    public void setOnPositionChangedListener(OnPositionChangedListener listener) {
        this.positionListener = listener;
    }

    public void setCrosshairSize(int sizeDp) {
        this.crosshairSizeDp = sizeDp;
        int sizePx = (int) (sizeDp * context.getResources().getDisplayMetrics().density);
        int oldWidth = params.width > 0 ? params.width : sizePx;
        int oldHeight = params.height > 0 ? params.height : sizePx;
        int centerX = params.x + oldWidth / 2;
        int centerY = params.y + oldHeight / 2;
        params.width = sizePx;
        params.height = sizePx;
        params.x = centerX - sizePx / 2;
        params.y = centerY - sizePx / 2;
        ImageView iv = binding.ivCrosshair;
        ViewGroup.LayoutParams lp = iv.getLayoutParams();
        if (lp != null) {
            lp.width = sizePx;
            lp.height = sizePx;
            iv.setLayoutParams(lp);
        }
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
        PassThroughOpacityCoordinator.notifyGeometryChanged();
    }

    public void setCrosshairAlpha(float alpha) {
        params.alpha = alpha;
        if (!editMode) {
            FloatingWindowType.applyPassThroughAlphaClamp(context, params);
            PassThroughOpacityCoordinator.notifyTargetChanged(
                    PassThroughOpacityCoordinator.ID_CROSSHAIR, params.alpha);
        }
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
    }

    public void setEditMode(boolean enabled) {
        this.editMode = enabled;
        if (!isAttached()) return;
        if (enabled) {
            savedAlpha = params.alpha;
            PassThroughOpacityCoordinator.notifyDetached(PassThroughOpacityCoordinator.ID_CROSSHAIR);
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            binding.ivCrosshair.setOnTouchListener(this::onTouch);
            binding.ivCrosshair.setAlpha(0.5f);
            windowManager.updateViewLayout(binding.getRoot(), params);
        } else {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            binding.ivCrosshair.setOnTouchListener(null);
            binding.ivCrosshair.setAlpha(1.0f);
            params.alpha = savedAlpha;
            FloatingWindowType.applyPassThroughAlphaClamp(context, params);
            windowManager.updateViewLayout(binding.getRoot(), params);
            PassThroughOpacityCoordinator.notifyAttached(
                    PassThroughOpacityCoordinator.ID_CROSSHAIR,
                    () -> PassThroughOpacityCoordinator.frameOf(context, params),
                    a -> {
                        params.alpha = a;
                        if (isAttached()) {
                            windowManager.updateViewLayout(binding.getRoot(), params);
                        }
                    },
                    false,
                    params.alpha);
        }
    }

    public void center() {
        Point screen = getRealScreenSize();
        int sizePx = (int) (crosshairSizeDp * context.getResources().getDisplayMetrics().density);
        params.x = (screen.x - sizePx) / 2;
        params.y = (screen.y - sizePx) / 2;
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
        PassThroughOpacityCoordinator.notifyGeometryChanged();
    }

    public void loadCustomIcon() {
        Registry reg = RegistryUtils.get();
        if (reg == null) {
            applyDefaultIcon();
            return;
        }
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_ICON_URI.getValue())) {
                String uri = reg.getString(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_ICON_URI.getValue());
                if (uri != null && !uri.isEmpty()) {
                    applyCustomIcon(uri);
                    return;
                }
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load crosshair icon uri: " + e.getMessage());
        }
        applyDefaultIcon();
    }

    private void applyDefaultIcon() {
        binding.ivCrosshair.setImageResource(R.drawable.material_symbols_add_2_rounded);
        binding.ivCrosshair.setColorFilter(
                ContextCompat.getColor(context, R.color.colorSecondary),
                android.graphics.PorterDuff.Mode.SRC_IN);
    }

    private void applyCustomIcon(String uriString) {
        try {
            Uri uri = Uri.parse(uriString);
            Drawable d = Drawable.createFromStream(
                    context.getContentResolver().openInputStream(uri), null);
            if (d != null) {
                binding.ivCrosshair.setImageDrawable(d);
                binding.ivCrosshair.clearColorFilter();
            } else {
                applyDefaultIcon();
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load crosshair icon: " + e.getMessage());
            applyDefaultIcon();
        }
    }

    private boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = params.x;
                startY = params.y;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    dragging = true;
                }
                if (dragging) {
                    Rect safe = WindowBounds.safeRect(context, binding.getRoot());
                    int sizePx = (int) (crosshairSizeDp * context.getResources().getDisplayMetrics().density);
                    params.x = clamp(startX + (int) dx, safe.left, Math.max(safe.left, safe.right - sizePx));
                    params.y = clamp(startY + (int) dy, safe.top, Math.max(safe.top, safe.bottom - sizePx));
                    windowManager.updateViewLayout(binding.getRoot(), params);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    saveCurrentPosition();
                    if (positionListener != null) {
                        positionListener.onCrosshairPositionChanged(params.x, params.y);
                    }
                }
                dragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return false;
        }
    }

    private void loadSize() {
        this.crosshairSizeDp = 40;
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_SIZE.getValue())) {
                crosshairSizeDp = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_SIZE.getValue());
            }
        } catch (RegistryException e) {
            crosshairSizeDp = 40;
            AppLogger.w(TAG, "failed to load crosshair size: " + e.getMessage());
        }
        int sizePx = (int) (crosshairSizeDp * context.getResources().getDisplayMetrics().density);
        params.width = sizePx;
        params.height = sizePx;
        ImageView iv = binding.ivCrosshair;
        ViewGroup.LayoutParams lp = iv.getLayoutParams();
        if (lp != null) {
            lp.width = sizePx;
            lp.height = sizePx;
            iv.setLayoutParams(lp);
        }
    }

    private void loadAlpha() {
        savedAlpha = 1.0f;
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_ALPHA.getValue())) {
                int saved = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_ALPHA.getValue());
                savedAlpha = saved / 100f;
            }
        } catch (RegistryException e) {
            savedAlpha = 1.0f;
            AppLogger.w(TAG, "failed to load crosshair alpha: " + e.getMessage());
        }
        params.alpha = savedAlpha;
    }

    private void loadSavedPosition() {
        Point screen = getRealScreenSize();
        int sizePx = (int) (crosshairSizeDp * context.getResources().getDisplayMetrics().density);
        int defaultX = (screen.x - sizePx) / 2;
        int defaultY = (screen.y - sizePx) / 2;

        params.x = defaultX;
        params.y = defaultY;

        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_X.getValue())
                    && reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_Y.getValue())) {
                params.x = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_X.getValue());
                params.y = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_Y.getValue());
            }
        } catch (RegistryException e) {
            params.x = defaultX;
            params.y = defaultY;
            AppLogger.w(TAG, "failed to load crosshair position: " + e.getMessage());
        }
    }

    public void saveCurrentPosition() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_X.getValue(), params.x);
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_Y.getValue(), params.y);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save crosshair position failed: " + e.getMessage(), e);
        }
    }

    public void attach() {
        if (isAttached()) return;
        try {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            FloatingWindowType.applyPassThroughAlphaClamp(context, params);
            windowManager.addView(binding.getRoot(), params);
            if (!editMode) {
                PassThroughOpacityCoordinator.notifyAttached(
                        PassThroughOpacityCoordinator.ID_CROSSHAIR,
                        () -> PassThroughOpacityCoordinator.frameOf(context, params),
                        a -> {
                            params.alpha = a;
                            if (isAttached()) {
                                windowManager.updateViewLayout(binding.getRoot(), params);
                            }
                        },
                        false,
                        params.alpha);
            }
            AppLogger.d(TAG, "crosshair attached");
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        PassThroughOpacityCoordinator.notifyDetached(PassThroughOpacityCoordinator.ID_CROSSHAIR);
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "crosshair detached");
        } catch (Exception e) {
            AppLogger.e(TAG, "detach failed: " + e.getMessage(), e);
        }
    }

    public boolean isAttached() {
        return binding.getRoot().getParent() != null;
    }

    private Point getRealScreenSize() {
        Point p = new Point();
        try {
            windowManager.getDefaultDisplay().getRealSize(p);
        } catch (Exception e) {
            AppLogger.w(TAG, "getRealScreenSize failed: " + e.getMessage());
        }
        if (p.x <= 0 || p.y <= 0) {
            p.set(1080, 2340);
        }
        return p;
    }

    private int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(value, max));
    }
}
