package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.adapter.BuildingImportAdapter;
import io.takamiya.mio.building.BuildingInfo;
import io.takamiya.mio.building.BuildingStore;
import io.takamiya.mio.databinding.FloatingWindowImportBuildingPanelBinding;

/**
 * 导入建筑侧边栏：顶部栏 + 已转化建筑列表（真实数据）。
 */
public class FloatingImportBuildingPanel {

    private static final String TAG = "FloatingImportBuildingPanel";

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowImportBuildingPanelBinding binding;
    private final BuildingStore store;
    private final BuildingImportAdapter adapter;

    private Consumer<String> showToastFn;
    private Consumer<BuildingInfo> onOpenCommandsFn;
    private Consumer<BuildingInfo> onOpenImportActionFn;
    private boolean closing;

    /** 点击整行回调（打开命令子面板）。 */
    public void setOnOpenCommands(Consumer<BuildingInfo> fn) {
        this.onOpenCommandsFn = fn;
    }

    /** 点击行右侧 add_link 回调（打开导入操作 chip）。 */
    public void setOnOpenImportAction(Consumer<BuildingInfo> fn) {
        this.onOpenImportActionFn = fn;
    }

    public FloatingImportBuildingPanel(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.binding = FloatingWindowImportBuildingPanelBinding.inflate(LayoutInflater.from(context));
        this.store = BuildingStore.get(context);

        int type = FloatingWindowType.resolve(context);
        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        FloatingWindowType.applyToken(params);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        adapter = new BuildingImportAdapter();
        binding.importBuildingList.setLayoutManager(new LinearLayoutManager(context));
        binding.importBuildingList.setAdapter(adapter);
        // 点击整行：打开该建筑的命令子面板；点 add_link：打开导入操作 chip
        adapter.setOnItemClickListener(info -> {
            if (onOpenCommandsFn != null) onOpenCommandsFn.accept(info);
        });
        adapter.setOnItemActionListener(info -> {
            if (onOpenImportActionFn != null) onOpenImportActionFn.accept(info);
        });

        binding.getRoot().setOnClickListener(v -> close());
        binding.importBuildingCard.setOnClickListener(v -> { });
        binding.btnClose.setOnClickListener(v -> close());

        applySystemBarInsets(binding.importBuildingCard);
    }

    public void setShowToast(Consumer<String> fn) {
        this.showToastFn = fn;
    }

    private void toast(String msg) {
        if (showToastFn != null) showToastFn.accept(msg);
    }

    /** 读取真实建筑列表并刷新 UI。 */
    private void loadBuildings() {
        store.io(() -> {
            List<BuildingInfo> list;
            try {
                list = store.repository().listBuildings();
            } catch (Exception e) {
                AppLogger.w(TAG, "list buildings failed: " + e.getMessage());
                list = new ArrayList<>();
            }
            List<BuildingInfo> result = list;
            store.main(() -> {
                adapter.setData(result);
                binding.tvImportEmpty.setVisibility(
                        result.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    public void attach() {
        if (isAttached()) return;
        closing = false;
        try {
            windowManager.addView(binding.getRoot(), params);
            AppLogger.d(TAG, "import building panel attached");
            binding.getRoot().post(this::animateOpen);
            loadBuildings();
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "import building panel detached");
        } catch (Exception e) {
            AppLogger.e(TAG, "detach failed: " + e.getMessage(), e);
        }
        resetAnimState();
    }

    public boolean isAttached() {
        return binding.getRoot().getParent() != null;
    }

    public void close() {
        if (closing) return;
        closing = true;
        animateClose();
    }

    private void animateOpen() {
        FloatingAnimator.fadeIn(binding.getRoot(), FloatingAnimator.NORMAL_MS, null);
        FloatingAnimator.slideInFromStart(
                binding.importBuildingCard, slideDistance(), FloatingAnimator.NORMAL_MS, null);
    }

    private void animateClose() {
        FloatingAnimator.slideOutToStart(
                binding.importBuildingCard, slideDistance(), FloatingAnimator.NORMAL_MS, this::detach);
        FloatingAnimator.fadeOut(binding.getRoot(), FloatingAnimator.NORMAL_MS, null);
    }

    private float slideDistance() {
        int width = binding.importBuildingCard.getWidth();
        if (width <= 0) {
            width = context.getResources().getDisplayMetrics().widthPixels;
        }
        return -width;
    }

    private void resetAnimState() {
        closing = false;
        binding.getRoot().setAlpha(1f);
        binding.importBuildingCard.setAlpha(1f);
        binding.importBuildingCard.setTranslationX(0f);
    }

    private void applySystemBarInsets(View card) {
        ViewCompat.setOnApplyWindowInsetsListener(card, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
