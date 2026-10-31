package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import java.util.function.Consumer;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.building.BuildingInfo;
import io.takamiya.mio.databinding.FloatingChipBuindingImpotActionBinding;

/**
 * 建筑导入操作 chip：显示建筑名 + 执行进度，提供
 * 执行(粘贴图标)/上一步/下一步/关闭 操作。
 *
 * <p>独立小窗：尺寸与透明度<b>不</b>跟随全局悬浮窗设置（不读 FLOAT_SIZE/FLOAT_STATUS_BAR_ALPHA）；
 * 按住最左侧 filter 图标可拖动。
 */
public class FloatingBuildingActionChip {

    private static final String TAG = "BuildingActionChip";

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingChipBuindingImpotActionBinding binding;
    private final int touchSlop;

    private Consumer<String> showToastFn;
    private Runnable onPaste;
    private Runnable onPrev;
    private Runnable onNext;
    private Runnable onClose;

    private boolean closing;
    private float downRawX, downRawY;
    private int startX, startY;
    private boolean dragging;

    public FloatingBuildingActionChip(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.binding = FloatingChipBuindingImpotActionBinding.inflate(LayoutInflater.from(context));
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        int type = FloatingWindowType.resolve(context);
        // FLAG_NOT_FOCUSABLE：chip 不抢输入焦点，否则注入的按键会落到本应用窗口而不是游戏
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
        params.x = 0;
        params.y = (int) (80 * context.getResources().getDisplayMetrics().density);

        binding.ivDrag.setOnTouchListener(this::onDragTouch);
        binding.ivChipPaste.setOnClickListener(v -> {
            if (onPaste != null) onPaste.run();
        });
        binding.ivChipPrev.setOnClickListener(v -> {
            if (onPrev != null) onPrev.run();
        });
        binding.ivChipNext.setOnClickListener(v -> {
            if (onNext != null) onNext.run();
        });
        binding.ivChipClose.setOnClickListener(v -> {
            if (onClose != null) onClose.run();
            else close();
        });
    }

    // ==================== 回调注册 ====================

    public void setShowToast(Consumer<String> fn) {
        this.showToastFn = fn;
    }

    public void setOnPaste(Runnable fn) {
        this.onPaste = fn;
    }

    public void setOnPrev(Runnable fn) {
        this.onPrev = fn;
    }

    public void setOnNext(Runnable fn) {
        this.onNext = fn;
    }

    public void setOnClose(Runnable fn) {
        this.onClose = fn;
    }

    private void toast(String msg) {
        if (showToastFn != null) showToastFn.accept(msg);
    }

    // ==================== 数据 ====================

    /** 设置建筑名与进度（currentIndex 1-based）。 */
    public void load(BuildingInfo info) {
        binding.tvChipName.setText(info.name);
        setProgress(1, info.commandCount);
    }

    public void setProgress(int current, int total) {
        binding.tvChipProgress.setText(Math.max(0, current) + "/" + Math.max(0, total));
    }

    /**
     * 屏幕坐标 (x,y) 是否落在本 chip 窗口矩形内（会被 chip 挡住、点不到游戏）。
     * 未 attach 或尺寸未知时返回 false。
     */
    public boolean overlaps(int x, int y) {
        if (!isAttached()) return false;
        View root = binding.getRoot();
        int w = root.getWidth();
        int h = root.getHeight();
        if (w <= 0 || h <= 0) return false;
        return x >= params.x && x < params.x + w && y >= params.y && y < params.y + h;
    }

    // ==================== 拖动 ====================

    private boolean onDragTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = params.x;
                startY = params.y;
                dragging = false;
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    dragging = true;
                }
                if (dragging) {
                    Rect safe = WindowBounds.safeRect(context, binding.getRoot());
                    int w = binding.getRoot().getWidth();
                    int h = binding.getRoot().getHeight();
                    params.x = clamp(startX + (int) dx, safe.left,
                            Math.max(safe.left, safe.right - w));
                    params.y = clamp(startY + (int) dy, safe.top,
                            Math.max(safe.top, safe.bottom - h));
                    windowManager.updateViewLayout(binding.getRoot(), params);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                v.getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return false;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ==================== 窗口生命周期 ====================

    public void attach() {
        if (isAttached()) return;
        closing = false;
        try {
            windowManager.addView(binding.getRoot(), params);
            AppLogger.d(TAG, "chip attached");
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "chip detached");
        } catch (Exception e) {
            AppLogger.e(TAG, "detach failed: " + e.getMessage(), e);
        }
        closing = false;
    }

    public boolean isAttached() {
        return binding.getRoot().getParent() != null;
    }

    public void close() {
        if (closing) return;
        closing = true;
        detach();
    }
}
