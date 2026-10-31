package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.adapter.FeatureListAdapter;
import io.takamiya.mio.bean.ControlActionItem;
import io.takamiya.mio.databinding.FloatingWindowFeatureListBinding;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

/**
 * 旧版功能列表：基于 RecyclerView 的悬浮窗渲染，兼容 GL 渲染异常的机型。
 * 缩放逻辑参考悬浮窗状态栏：窗口按自然尺寸 * 缩放比重新设定大小，内容按缩放比缩放，
 * 使列表始终贴右对齐。
 */
public class FloatingFeatureListView {

    private static final String TAG = "FloatingFeatureListView";

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowFeatureListBinding binding;
    private final FeatureListAdapter adapter;
    private float currentScale = 1.0f;
    private float requestedAlpha = 1f;
    private boolean resizeListenerAttached = false;
    private final ViewTreeObserver.OnGlobalLayoutListener resizeListener;

    private void onGlobalLayout() {
        View root = binding.getRoot();
        if (root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(resizeListener);
        }
        resizeListenerAttached = false;
        applyScale();
    }

    public FloatingFeatureListView(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

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
        params.gravity = Gravity.END | Gravity.TOP;
        params.x = 0;
        params.y = 0;

        binding = FloatingWindowFeatureListBinding.inflate(
                android.view.LayoutInflater.from(context));
        binding.recyclerView.setLayoutManager(new RightAlignLayoutManager(context));
        binding.recyclerView.setItemAnimator(null);
        adapter = new FeatureListAdapter(null);
        binding.recyclerView.setAdapter(adapter);

        resizeListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                FloatingFeatureListView.this.onGlobalLayout();
            }
        };

        loadScale();
        loadAlpha();
    }

    public void updateActions(List<ControlActionItem> actions) {
        adapter.updateItems(actions);
        scheduleResizeAndAlign();
    }

    public void setActiveState(String key, boolean active) {
        adapter.setActiveState(key, active);
    }

    public void updateScale(float scale) {
        currentScale = Math.max(0.8f, Math.min(1.2f, scale));
        scheduleResizeAndAlign();
    }

    public void setWindowAlpha(float alpha) {
        params.alpha = alpha;
        requestedAlpha = alpha;
        FloatingWindowType.applyPassThroughAlphaClamp(context, params);
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
        PassThroughOpacityCoordinator.notifyTargetChanged(
                PassThroughOpacityCoordinator.ID_LEGACY_LIST, requestedAlpha);
    }

    public void attach() {
        if (isAttached()) return;
        try {
            FloatingWindowType.applyPassThroughAlphaClamp(context, params);
            windowManager.addView(binding.getRoot(), params);
            scheduleResizeAndAlign();
            PassThroughOpacityCoordinator.notifyAttached(
                    PassThroughOpacityCoordinator.ID_LEGACY_LIST,
                    () -> PassThroughOpacityCoordinator.frameOf(context, params),
                    a -> {
                        params.alpha = a;
                        if (isAttached()) {
                            windowManager.updateViewLayout(binding.getRoot(), params);
                        }
                    },
                    false,
                    requestedAlpha);
            AppLogger.d(TAG, "legacy feature list attached");
        } catch (Exception e) {
            AppLogger.e(TAG, "legacy feature list attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        PassThroughOpacityCoordinator.notifyDetached(PassThroughOpacityCoordinator.ID_LEGACY_LIST);
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "legacy feature list detached");
        } catch (Exception e) {
            AppLogger.e(TAG, "legacy feature list detach failed: " + e.getMessage(), e);
        }
    }

    public boolean isAttached() {
        return binding.getRoot().getParent() != null;
    }

    private void scheduleResizeAndAlign() {        View root = binding.getRoot();
        if (!resizeListenerAttached) {
            resizeListenerAttached = true;
            root.getViewTreeObserver().addOnGlobalLayoutListener(resizeListener);
        }
        root.requestLayout();
    }

    private final class RightAlignLayoutManager extends LinearLayoutManager {

        RightAlignLayoutManager(Context context) {
            super(context);
        }

        @Override
        public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
            super.onLayoutChildren(recycler, state);
            int rightEdge = getWidth() - getPaddingRight();
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int delta = rightEdge - getDecoratedRight(child);
                if (delta != 0) {
                    child.offsetLeftAndRight(delta);
                }
            }
        }
    }

    private void applyScale() {
        View container = binding.scalableContent;
        int[] natural = measureNaturalSize(binding.recyclerView);
        int naturalWidth = Math.max(natural[0], 1);
        int naturalHeight = Math.max(natural[1], 1);
        ViewGroup.LayoutParams lp = container.getLayoutParams();
        if (lp != null) {
            lp.width = naturalWidth;
            lp.height = naturalHeight;
            container.setLayoutParams(lp);
        }
        params.width = (int) (naturalWidth * currentScale);
        params.height = Math.min((int) (naturalHeight * currentScale), maxWindowHeight(context));
        PassThroughOpacityCoordinator.notifyGeometryChanged();
        container.setPivotX(0f);
        container.setPivotY(0f);
        container.setScaleX(currentScale);
        container.setScaleY(currentScale);
        AppLogger.i(TAG, "applyScale natural=" + naturalWidth + "x" + naturalHeight
                + " -> win=" + params.width + "x" + params.height);
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
    }

    private int[] measureNaturalSize(RecyclerView rv) {
        int paddingH = rv.getPaddingLeft() + rv.getPaddingRight();
        int count = adapter.getItemCount();
        if (count == 0) {
            return new int[]{Math.max(paddingH, 1), rv.getPaddingTop() + rv.getPaddingBottom()};
        }
        int maxWidth = paddingH;
        int totalHeight = rv.getPaddingTop() + rv.getPaddingBottom();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(
                maxWindowWidth(context), View.MeasureSpec.AT_MOST);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < count; i++) {
            View child;
            RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(i);
            if (vh == null) {
                FeatureListAdapter.ViewHolder holder =
                        adapter.createViewHolder(rv, adapter.getItemViewType(i));
                adapter.bindViewHolder(holder, i);
                vh = holder;
                rv.getRecycledViewPool().putRecycledView(holder);
            }
            child = vh.itemView;
            child.measure(widthSpec, heightSpec);
            maxWidth = Math.max(maxWidth, child.getMeasuredWidth() + paddingH);
            totalHeight += child.getMeasuredHeight();
        }
        return new int[]{maxWidth, totalHeight};
    }

    private static int maxWindowWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    private static int maxWindowHeight(Context context) {
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        return (int) (screenHeight * 0.6f);
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

    private void loadAlpha() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_ALPHA.getValue())) {
                int saved = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_ALPHA.getValue());
                params.alpha = saved / 100f;
                requestedAlpha = params.alpha;
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load float alpha: " + e.getMessage());
        }
    }
}
