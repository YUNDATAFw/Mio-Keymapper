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

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.databinding.FloatingWindowToolboxPanelBinding;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxBaseFragment;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxListAdapter;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxTab;
import io.takamiya.mio.floating.toolBoxFragment.tool.ToolBlockCoordinateConversionFragment;
import io.takamiya.mio.floating.toolBoxFragment.tool.ToolCoordinateConversionFragment;
import io.takamiya.mio.floating.toolBoxFragment.tool.ToolDistanceCalculationFragment;
import io.takamiya.mio.floating.toolBoxFragment.tool.ToolTimeConversionFragment;
import io.takamiya.mio.floating.toolBoxFragment.tool.ToolUpgradeExperienceCalculationFragment;
import io.takamiya.mio.floating.toolBoxFragment.tool.ToolItemStatisticsFragment;
import io.takamiya.mio.floating.toolBoxFragment.tool.ToolJumpHeightCalculationFragment;
import io.takamiya.mio.floating.toolBoxFragment.tool.ToolBeaconColorCalculationFragment;
import io.takamiya.mio.floating.toolBoxFragment.tool.ToolLeatherDyeCalculationFragment;
import io.takamiya.mio.floating.toolBoxFragment.tool.ToolArmorCalculationFragment;
import io.takamiya.mio.floating.toolBoxFragment.tool.ToolBinaryCoordinateSearchFragment;

public class FloatingToolboxPanel {

    private static final String TAG = "FloatingToolboxPanel";

    private static final List<ToolboxTab> TABS = Arrays.asList(
            new ToolboxTab("维度坐标转换", ToolCoordinateConversionFragment.class),
            new ToolboxTab("区块坐标转换", ToolBlockCoordinateConversionFragment.class),
            new ToolboxTab("两点间距离", ToolDistanceCalculationFragment.class),
            new ToolboxTab("时间转换", ToolTimeConversionFragment.class),
            new ToolboxTab("升级经验计算", ToolUpgradeExperienceCalculationFragment.class),
            new ToolboxTab("物品统计", ToolItemStatisticsFragment.class),
            new ToolboxTab("跳跃提升", ToolJumpHeightCalculationFragment.class),
            new ToolboxTab("信标颜色计算", ToolBeaconColorCalculationFragment.class),
            new ToolboxTab("皮革染色计算", ToolLeatherDyeCalculationFragment.class),
            new ToolboxTab("伤害减免计算", ToolArmorCalculationFragment.class),
            new ToolboxTab("二分查坐标", ToolBinaryCoordinateSearchFragment.class)
    );

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowToolboxPanelBinding binding;
    private final ToolboxListAdapter adapter;

    private Consumer<String> showToastFn;
    private OnToolSelectedListener toolSelectedListener;
    private boolean closing;

    public interface OnToolSelectedListener {
        void onToolSelected(ToolboxTab tab);
    }

    public FloatingToolboxPanel(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.binding = FloatingWindowToolboxPanelBinding.inflate(LayoutInflater.from(context));

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

        binding.toolboxRoot.setOnClickListener(v -> close());
        binding.toolboxCard.setOnClickListener(v -> {
        });
        binding.btnClose.setOnClickListener(v -> close());

        adapter = new ToolboxListAdapter(TABS);
        adapter.setOnItemClickListener(tab -> {
            if (toolSelectedListener != null) {
                toolSelectedListener.onToolSelected(tab);
            }
        });
        binding.recyclerTools.setLayoutManager(new LinearLayoutManager(context));
        binding.recyclerTools.setAdapter(adapter);

        applySystemBarInsets(binding.toolboxCard);
    }

    public void setOnToolSelectedListener(OnToolSelectedListener listener) {
        this.toolSelectedListener = listener;
    }

    public void setShowToast(Consumer<String> fn) {
        this.showToastFn = fn;
    }

    public void attach() {
        if (isAttached()) return;
        closing = false;
        try {
            windowManager.addView(binding.getRoot(), params);
            AppLogger.d(TAG, "toolbox panel attached");
            binding.getRoot().post(this::animateOpen);
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "toolbox panel detached");
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
                binding.toolboxCard, slideDistance(), FloatingAnimator.NORMAL_MS, null);
    }

    private void animateClose() {
        FloatingAnimator.slideOutToStart(
                binding.toolboxCard, slideDistance(), FloatingAnimator.NORMAL_MS, this::detach);
        FloatingAnimator.fadeOut(binding.getRoot(), FloatingAnimator.NORMAL_MS, null);
    }

    private float slideDistance() {
        int width = binding.toolboxCard.getWidth();
        if (width <= 0) {
            width = context.getResources().getDisplayMetrics().widthPixels;
        }
        return -width;
    }

    private void resetAnimState() {
        closing = false;
        binding.getRoot().setAlpha(1f);
        binding.toolboxCard.setAlpha(1f);
        binding.toolboxCard.setTranslationX(0f);
    }

    private void applySystemBarInsets(View card) {
        ViewCompat.setOnApplyWindowInsetsListener(card, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
