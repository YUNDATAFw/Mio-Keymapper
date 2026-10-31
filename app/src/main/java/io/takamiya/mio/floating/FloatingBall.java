package io.takamiya.mio.floating;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.exceptions.RegistryException;
import io.takamiya.mio.databinding.FloatingWindowBallBinding;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class FloatingBall {

    public interface OnBallClickListener {
        void onBallClick();
    }

    private static final String TAG = "FloatingBall";

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowBallBinding binding;
    private final View view;

    private OnBallClickListener clickListener;

    private final int touchSlop;
    private float downRawX;
    private float downRawY;
    private int startX;
    private int startY;
    private boolean dragging;
    private boolean clickCandidate;

    private int lastOrientation;

    private boolean hasPortrait;
    private boolean hasLandscape;
    private int savedPortraitX;
    private int savedPortraitY;
    private int savedLandscapeX;
    private int savedLandscapeY;

    private float currentScale = 1.0f;
    private int baseWidth;
    private int baseHeight;
    private int baseIconPx;
    private boolean awaitingBaseLayout;

    public FloatingBall(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        this.binding = FloatingWindowBallBinding.inflate(LayoutInflater.from(context));
        this.view = binding.getRoot();

        int type = FloatingWindowType.resolve(context);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        FloatingWindowType.applyToken(params);
        params.gravity = Gravity.TOP | Gravity.START;

        loadSavedPositions();
        loadScale();
        loadAlpha();
        applyInitialPosition();

        view.setOnTouchListener(this::onTouch);
    }

    public void setWindowAlpha(float alpha) {
        params.alpha = alpha;
        if (isAttached()) {
            windowManager.updateViewLayout(view, params);
        }
    }

    public void setOnBallClickListener(OnBallClickListener listener) {
        this.clickListener = listener;
    }

    public void updateScale(float scale) {
        int prevRight = params.x + ballWidth();
        int prevBottom = params.y + ballHeight();
        currentScale = Math.max(0.8f, Math.min(1.2f, scale));
        applyScale();
        if (isAttached()) {
            reClampPreservingEdges(prevRight, prevBottom);
        }
        persistScale((int) (currentScale * 100));
    }

    public float getCurrentScale() {
        return currentScale;
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
        if (baseWidth > 0) {
            params.width = (int) (baseWidth * currentScale);
            params.height = (int) (baseHeight * currentScale);
            if (isAttached()) {
                windowManager.updateViewLayout(view, params);
                reClampToSafe();
            }
        } else if (!awaitingBaseLayout) {
            awaitingBaseLayout = true;
            view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    awaitingBaseLayout = false;
                    if (view.getWidth() <= 0) return;
                    baseWidth = view.getWidth();
                    baseHeight = view.getHeight();
                    params.width = (int) (baseWidth * currentScale);
                    params.height = (int) (baseHeight * currentScale);
                    if (isAttached()) {
                        windowManager.updateViewLayout(view, params);
                        reClampToSafe();
                    }
                }
            });
        }
        scaleIcon();
    }

    private void scaleIcon() {
        if (baseIconPx <= 0) {
            int measured = binding.ivBallIcon.getWidth();
            baseIconPx = measured > 0
                    ? measured
                    : (int) (42 * context.getResources().getDisplayMetrics().density);
        }
        ViewGroup.LayoutParams lp = binding.ivBallIcon.getLayoutParams();
        if (lp != null) {
            int size = Math.max((int) (baseIconPx * currentScale), 1);
            lp.width = size;
            lp.height = size;
            binding.ivBallIcon.setLayoutParams(lp);
        }
    }

    private void persistScale(int scalePercent) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_SIZE.getValue(), scalePercent);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save float size failed: " + e.getMessage(), e);
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

    private void reClampPreservingEdges(int prevRight, int prevBottom) {
        Rect safe = WindowBounds.safeRect(context, view);
        int maxX = Math.max(safe.left, safe.right - ballWidth());
        int maxY = Math.max(safe.top, safe.bottom - ballHeight());
        int threshold = Math.max(ballWidth() / 2, safe.top);
        if (prevRight >= maxX - threshold) {
            params.x = maxX;
        } else {
            params.x = clamp(params.x, safe.left, maxX);
        }
        if (prevBottom >= maxY - threshold) {
            params.y = maxY;
        } else {
            params.y = clamp(params.y, safe.top, maxY);
        }
        windowManager.updateViewLayout(view, params);
    }

    private void reClampToSafe() {
        if (!isAttached()) return;
        Rect safe = WindowBounds.safeRect(context, view);
        params.x = WindowBounds.clampX(safe, params.x, ballWidth());
        params.y = WindowBounds.clampY(safe, params.y, ballHeight());
        windowManager.updateViewLayout(view, params);
    }

    private void snapToEdge() {
        Rect safe = WindowBounds.safeRect(context, view);
        int ballCenterX = params.x + ballWidth() / 2;
        int snapThreshold = Math.max(ballWidth() * 2, (safe.right - safe.left) / 6);
        int targetX;
        if (ballCenterX < safe.left + snapThreshold) {
            targetX = safe.left;
        } else if (ballCenterX > safe.right - snapThreshold) {
            targetX = safe.right - ballWidth();
        } else {
            saveCurrentPosition();
            return;
        }
        if (targetX == params.x) {
            saveCurrentPosition();
            return;
        }
        final int startX = params.x;
        final int deltaX = targetX - startX;
        final long duration = 150;
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            params.x = startX + (int) (deltaX * fraction);
            windowManager.updateViewLayout(view, params);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                params.x = targetX;
                windowManager.updateViewLayout(view, params);
                saveCurrentPosition();
            }
        });
        animator.start();
    }

    public void attach() {
        if (isAttached()) return;
        try {
            windowManager.addView(view, params);
            applyScale();
            view.post(this::reClampToSafe);
            AppLogger.d(TAG, "ball attached");
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        try {
            windowManager.removeView(view);
            AppLogger.d(TAG, "ball detached");
        } catch (Exception e) {
            AppLogger.e(TAG, "detach failed: " + e.getMessage(), e);
        }
    }

    public boolean isAttached() {
        return view.getParent() != null;
    }

    public void adaptToOrientation() {
        if (!isAttached()) return;
        int newOrientation = getOrientation();
        if (newOrientation != lastOrientation) {
            Point screen = getRealScreenSize();
            int portraitWidth = Math.min(screen.x, screen.y);
            int portraitHeight = Math.max(screen.x, screen.y);
            int landscapeWidth = Math.max(screen.x, screen.y);
            int landscapeHeight = Math.min(screen.x, screen.y);

            if (lastOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                params.x = (int) ((long) params.x * portraitWidth / landscapeWidth);
                params.y = (int) ((long) params.y * portraitHeight / landscapeHeight);
            } else {
                params.x = (int) ((long) params.x * landscapeWidth / portraitWidth);
                params.y = (int) ((long) params.y * landscapeHeight / portraitHeight);
            }
            lastOrientation = newOrientation;
            AppLogger.d(TAG, "ball adapted to orientation " + newOrientation);
        }
        reClampToSafe();
    }

    private boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = params.x;
                startY = params.y;
                dragging = false;
                clickCandidate = true;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    dragging = true;
                    clickCandidate = false;
                }
                if (dragging) {
                    Rect safe = WindowBounds.safeRect(context, view);
                    params.x = clamp(startX + (int) dx, safe.left, Math.max(safe.left, safe.right - ballWidth()));
                    params.y = clamp(startY + (int) dy, safe.top, Math.max(safe.top, safe.bottom - ballHeight()));
                    windowManager.updateViewLayout(view, params);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (clickCandidate) {
                    if (clickListener != null) clickListener.onBallClick();
                } else if (dragging) {
                    if (isSnapToEdgeEnabled()) {
                        snapToEdge();
                    } else {
                        saveCurrentPosition();
                    }
                }
                dragging = false;
                clickCandidate = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                clickCandidate = false;
                return true;
            default:
                return false;
        }
    }

    private void applyInitialPosition() {
        Point screen = getRealScreenSize();
        int orientation = getOrientation();
        int x;
        int y;
        boolean derived = false;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (hasLandscape) {
                x = savedLandscapeX;
                y = savedLandscapeY;
            } else if (hasPortrait) {
                x = (int) ((long) savedPortraitX * screen.x / Math.min(screen.x, screen.y));
                y = (int) ((long) savedPortraitY * screen.y / Math.max(screen.x, screen.y));
                derived = true;
            } else {
                x = 0;
                y = WindowBounds.topInset(context, view);
            }
        } else {
            if (hasPortrait) {
                x = savedPortraitX;
                y = savedPortraitY;
            } else if (hasLandscape) {
                x = (int) ((long) savedLandscapeX * Math.min(screen.x, screen.y) / Math.max(screen.x, screen.y));
                y = (int) ((long) savedLandscapeY * Math.max(screen.x, screen.y) / Math.min(screen.x, screen.y));
                derived = true;
            } else {
                x = 0;
                y = WindowBounds.topInset(context, view);
            }
        }

        Rect safe = WindowBounds.safeRect(context, view);
        params.x = clamp(x, safe.left, Math.max(safe.left, safe.right - ballWidth()));
        params.y = clamp(y, safe.top, Math.max(safe.top, safe.bottom - ballHeight()));
        lastOrientation = orientation;
        AppLogger.d(TAG, "ball initial pos=(" + params.x + "," + params.y + "), derived=" + derived);
    }

    private void saveCurrentPosition() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            if (getOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
                reg.set(DataManage.Tag.APP_CONFIG_FLOAT_BALL_X_LANDSCAPE.getValue(), params.x);
                reg.set(DataManage.Tag.APP_CONFIG_FLOAT_BALL_Y_LANDSCAPE.getValue(), params.y);
            } else {
                reg.set(DataManage.Tag.APP_CONFIG_FLOAT_BALL_X_PORTRAIT.getValue(), params.x);
                reg.set(DataManage.Tag.APP_CONFIG_FLOAT_BALL_Y_PORTRAIT.getValue(), params.y);
            }
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_ORIENTATION.getValue(), getOrientation());
            reg.saveAsync();
            AppLogger.d(TAG, "ball position saved: " + params.x + "," + params.y);
        } catch (Exception e) {
            AppLogger.e(TAG, "save position failed: " + e.getMessage(), e);
        }
    }

    // 实时读取贴边开关（拖动松手时判定，设置改动即时生效）
    private boolean isSnapToEdgeEnabled() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return true;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_BALL_SNAP_TO_EDGE.getValue())) {
                return reg.getBoolean(DataManage.Tag.APP_CONFIG_FLOAT_BALL_SNAP_TO_EDGE.getValue());
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load snap to edge: " + e.getMessage());
        }
        return true;
    }

    private void loadSavedPositions() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            hasPortrait = reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_BALL_X_PORTRAIT.getValue())
                    && reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_BALL_Y_PORTRAIT.getValue());
            if (hasPortrait) {
                savedPortraitX = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_BALL_X_PORTRAIT.getValue());
                savedPortraitY = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_BALL_Y_PORTRAIT.getValue());
            }
        } catch (RegistryException e) {
            hasPortrait = false;
            AppLogger.w(TAG, "failed to load portrait position: " + e.getMessage());
        }
        try {
            hasLandscape = reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_BALL_X_LANDSCAPE.getValue())
                    && reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_BALL_Y_LANDSCAPE.getValue());
            if (hasLandscape) {
                savedLandscapeX = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_BALL_X_LANDSCAPE.getValue());
                savedLandscapeY = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_BALL_Y_LANDSCAPE.getValue());
            }
        } catch (RegistryException e) {
            hasLandscape = false;
            AppLogger.w(TAG, "failed to load landscape position: " + e.getMessage());
        }
    }

    private int getOrientation() {
        return context.getResources().getConfiguration().orientation;
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

    private int ballWidth() {
        return params.width > 0 ? params.width : Math.max(view.getWidth(), 1);
    }

    private int ballHeight() {
        return params.height > 0 ? params.height : Math.max(view.getHeight(), 1);
    }

    private int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(value, max));
    }
}
