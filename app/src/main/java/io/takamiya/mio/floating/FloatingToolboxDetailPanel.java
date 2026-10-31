package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.function.Consumer;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.databinding.FloatingWindowToolboxDetailPanelBinding;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxBaseFragment;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxTab;

public class FloatingToolboxDetailPanel {

    private static final String TAG = "FloatingToolboxDetailPanel";

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowToolboxDetailPanelBinding binding;

    private ToolboxBaseFragment currentFragment;
    private Consumer<String> showToastFn;
    private boolean closing;

    public FloatingToolboxDetailPanel(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.binding = FloatingWindowToolboxDetailPanelBinding.inflate(LayoutInflater.from(context));

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

        binding.toolboxDetailRoot.setOnClickListener(v -> close());
        binding.toolboxDetailCard.setOnClickListener(v -> {
        });
        binding.btnClose.setOnClickListener(v -> close());

        applySystemBarInsets(binding.toolboxDetailCard);
    }

    public void setShowToast(Consumer<String> fn) {
        this.showToastFn = fn;
    }

    public void loadTool(ToolboxTab tab) {
        try {
            if (currentFragment != null) {
                detachCurrentFragment();
            }

            currentFragment = tab.getFragmentClass().newInstance();
            currentFragment.onAttach(context);
            if (showToastFn != null) currentFragment.setShowToast(showToastFn);

            LayoutInflater inflater = LayoutInflater.from(context);
            ViewGroup container = binding.fragmentContainer;
            currentFragment.create(inflater, container);

            binding.tvToolTitle.setText(tab.getName());

            if (currentFragment.getView() != null) {
                container.removeAllViews();
                container.addView(currentFragment.getView());
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "load tool failed: " + e.getMessage(), e);
        }
    }

    private void detachCurrentFragment() {
        if (currentFragment != null) {
            binding.fragmentContainer.removeAllViews();
            currentFragment.onDetach();
            currentFragment = null;
        }
    }

    public void attach() {
        if (isAttached()) return;
        closing = false;
        try {
            windowManager.addView(binding.getRoot(), params);
            AppLogger.d(TAG, "toolbox detail panel attached");
            binding.getRoot().post(this::animateOpen);
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "toolbox detail panel detached");
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
                binding.toolboxDetailCard, slideDistance(), FloatingAnimator.NORMAL_MS, null);
    }

    private void animateClose() {
        FloatingAnimator.slideOutToStart(
                binding.toolboxDetailCard, slideDistance(), FloatingAnimator.NORMAL_MS, this::detach);
        FloatingAnimator.fadeOut(binding.getRoot(), FloatingAnimator.NORMAL_MS, null);
    }

    private float slideDistance() {
        int width = binding.toolboxDetailCard.getWidth();
        if (width <= 0) {
            width = context.getResources().getDisplayMetrics().widthPixels;
        }
        return -width;
    }

    private void resetAnimState() {
        closing = false;
        binding.getRoot().setAlpha(1f);
        binding.toolboxDetailCard.setAlpha(1f);
        binding.toolboxDetailCard.setTranslationX(0f);
    }

    private void applySystemBarInsets(View card) {
        ViewCompat.setOnApplyWindowInsetsListener(card, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
