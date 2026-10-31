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

import com.google.android.material.slider.Slider;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.databinding.FloatingWindowSettingsSubpanelBinding;
import io.takamiya.mio.databinding.LayoutSubpanelCrosshairBinding;
import io.takamiya.mio.databinding.LayoutSubpanelFeatureListBinding;
import io.takamiya.mio.databinding.LayoutSubpanelStatusBarBinding;

/**
 * 设置面板二级窗口：复用工具箱详情面板的骨架,
 * 在同一个窗口内按页面（状态栏/功能列表/准星）注入内容布局。
 * 主设置面板保持在下层不关闭,关闭本窗口后回到主面板。
 */
public class FloatingSettingsSubPanel {

    public enum Page {
        STATUS_BAR,
        FEATURE_LIST,
        CROSSHAIR
    }

    private static final String TAG = "FloatingSettingsSubPanel";

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowSettingsSubpanelBinding binding;

    private FloatingSettingsPanel.Listener listener;
    private boolean closing;

    public FloatingSettingsSubPanel(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.binding = FloatingWindowSettingsSubpanelBinding.inflate(LayoutInflater.from(context));

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

        binding.subPanelRoot.setOnClickListener(v -> close());
        binding.subPanelCard.setOnClickListener(v -> {
        });
        binding.btnClose.setOnClickListener(v -> close());

        applySystemBarInsets(binding.subPanelCard);
    }

    public void setListener(FloatingSettingsPanel.Listener listener) {
        this.listener = listener;
    }

    public void load(Page page) {
        AppLogger.d(TAG, "load page: " + page);
        ViewGroup container = binding.contentContainer;
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        SettingsValues values = SettingsValues.instance();
        values.reload();

        switch (page) {
            case STATUS_BAR:
                binding.tvSubTitle.setText("状态栏设置");
                bindStatusBarPage(values, LayoutSubpanelStatusBarBinding.inflate(inflater, container, false));
                break;
            case FEATURE_LIST:
                binding.tvSubTitle.setText("功能列表设置");
                bindFeatureListPage(values, LayoutSubpanelFeatureListBinding.inflate(inflater, container, false));
                break;
            case CROSSHAIR:
                binding.tvSubTitle.setText("准星设置");
                bindCrosshairPage(values, LayoutSubpanelCrosshairBinding.inflate(inflater, container, false));
                break;
        }
    }

    private void bindStatusBarPage(SettingsValues s, LayoutSubpanelStatusBarBinding b) {
        containerAdd(b.getRoot());
        b.sliderStatusBarY.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int v = (int) value;
                b.tvStatusBarYValue.setText(v + "dp");
                if (listener != null) listener.onStatusBarYChanged(v);
            }
        });
        s.statusBarY = syncStatusBarY(b, s.statusBarY);
        bindStatusBarToggle(b, s);
    }

    private int syncStatusBarY(LayoutSubpanelStatusBarBinding b, int y) {
        int snapped = (int) snapToSlider(b.sliderStatusBarY, y);
        b.sliderStatusBarY.setValue(snapped);
        b.tvStatusBarYValue.setText(snapped + "dp");
        return snapped;
    }

    private void bindStatusBarToggle(LayoutSubpanelStatusBarBinding b, SettingsValues s) {
        b.switchStatusBar.setOnCheckedChangeListener(null);
        b.switchStatusBar.setChecked(s.statusBarEnabled);
        b.switchStatusBar.setOnCheckedChangeListener((buttonView, isChecked) -> {
            s.statusBarEnabled = isChecked;
            if (isChecked && s.newStatusBarEnabled) {
                s.newStatusBarEnabled = false;
                b.switchNewStatusBar.setOnCheckedChangeListener(null);
                b.switchNewStatusBar.setChecked(false);
                restoreNewStatusBarListener(b, s);
            }
            if (listener != null) listener.onToggleStatusBar(isChecked);
        });
        bindNewStatusBarToggle(b, s);
    }

    private void bindNewStatusBarToggle(LayoutSubpanelStatusBarBinding b, SettingsValues s) {
        b.switchNewStatusBar.setOnCheckedChangeListener(null);
        b.switchNewStatusBar.setChecked(s.newStatusBarEnabled);
        b.switchNewStatusBar.setOnCheckedChangeListener((buttonView, isChecked) -> {
            s.newStatusBarEnabled = isChecked;
            if (isChecked && s.statusBarEnabled) {
                s.statusBarEnabled = false;
                b.switchStatusBar.setOnCheckedChangeListener(null);
                b.switchStatusBar.setChecked(false);
                bindStatusBarToggle(b, s);
            }
            if (listener != null) listener.onToggleNewStatusBar(isChecked);
        });
    }

    private void restoreNewStatusBarListener(LayoutSubpanelStatusBarBinding b, SettingsValues s) {
        b.switchNewStatusBar.setOnCheckedChangeListener((buttonView, isChecked) -> {
            s.newStatusBarEnabled = isChecked;
            if (isChecked && s.statusBarEnabled) {
                s.statusBarEnabled = false;
                b.switchStatusBar.setOnCheckedChangeListener(null);
                b.switchStatusBar.setChecked(false);
                bindStatusBarToggle(b, s);
            }
            if (listener != null) listener.onToggleNewStatusBar(isChecked);
        });
    }

    private void bindFeatureListPage(SettingsValues s, LayoutSubpanelFeatureListBinding b) {
        containerAdd(b.getRoot());

        boolean useNew = FloatingFeatureList.savedUseNewFeatureList();
        int engineVisibility = useNew ? View.VISIBLE : View.GONE;
        b.cardFeatureListFontSize.setVisibility(engineVisibility);
        b.cardFeatureListRightOffset.setVisibility(engineVisibility);
        b.cardFeatureListGlow.setVisibility(engineVisibility);
        b.cardFeatureListGlowStrength.setVisibility(engineVisibility);
        b.cardFeatureListWatermark.setVisibility(engineVisibility);

        b.sliderFeatureListFontSize.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int v = (int) value;
                b.tvFeatureListFontSizeValue.setText(v + "px");
                if (listener != null) listener.onFeatureListFontSizeChanged(v);
            }
        });
        b.sliderFeatureListRightOffset.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int v = (int) value;
                b.tvFeatureListRightOffsetValue.setText(v + "px");
                if (listener != null) listener.onFeatureListRightOffsetChanged(v);
            }
        });
        b.sliderFeatureListGlowStrength.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int v = (int) value;
                b.tvFeatureListGlowStrengthValue.setText(v + "%");
                if (listener != null) listener.onFeatureListGlowStrengthChanged(v);
            }
        });

        b.sliderFeatureListFontSize.setValue(snapToSlider(b.sliderFeatureListFontSize, s.featureListFontSize));
        b.tvFeatureListFontSizeValue.setText(s.featureListFontSize + "px");
        b.sliderFeatureListRightOffset.setValue(snapToSlider(b.sliderFeatureListRightOffset, s.featureListRightOffset));
        b.tvFeatureListRightOffsetValue.setText(s.featureListRightOffset + "px");
        b.sliderFeatureListGlowStrength.setValue(snapToSlider(b.sliderFeatureListGlowStrength, s.featureListGlowStrength));
        b.tvFeatureListGlowStrengthValue.setText(s.featureListGlowStrength + "%");

        bindChecked(b.switchFeatureList, s.featureListEnabled, checked -> {
            if (listener != null) listener.onToggleFeatureList(checked);
        });
        bindChecked(b.switchFeatureListGlow, s.featureListGlow, checked -> {
            if (listener != null) listener.onFeatureListGlowChanged(checked);
        });
        bindChecked(b.switchFeatureListWatermark, s.featureListWatermark, checked -> {
            if (listener != null) listener.onFeatureListWatermarkChanged(checked);
        });
    }

    private void bindCrosshairPage(SettingsValues s, LayoutSubpanelCrosshairBinding b) {
        containerAdd(b.getRoot());

        b.btnCrosshairCenter.setOnClickListener(v -> {
            if (listener != null) listener.onCrosshairCenter();
        });
        b.sliderCrosshairSize.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int v = (int) value;
                b.tvCrosshairSizeValue.setText(v + "dp");
                if (listener != null) listener.onCrosshairSizeChanged(v);
            }
        });
        b.sliderCrosshairAlpha.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int v = (int) value;
                b.tvCrosshairAlphaValue.setText(v + "%");
                if (listener != null) listener.onCrosshairAlphaChanged(v);
            }
        });

        b.sliderCrosshairSize.setValue(snapToSlider(b.sliderCrosshairSize, s.crosshairSizeDp));
        b.tvCrosshairSizeValue.setText(s.crosshairSizeDp + "dp");
        b.sliderCrosshairAlpha.setValue(snapToSlider(b.sliderCrosshairAlpha, s.crosshairAlpha));
        b.tvCrosshairAlphaValue.setText(s.crosshairAlpha + "%");

        bindChecked(b.switchCrosshair, s.crosshairEnabled, checked -> {
            updateCrosshairVisibility(b, checked);
            if (listener != null) listener.onToggleCrosshair(checked);
        });
        updateCrosshairVisibility(b, s.crosshairEnabled);
    }

    private void updateCrosshairVisibility(LayoutSubpanelCrosshairBinding b, boolean enabled) {
        int visibility = enabled ? View.VISIBLE : View.GONE;
        b.btnCrosshairCenter.setVisibility(visibility);
        b.sliderCrosshairSize.setVisibility(visibility);
        b.sliderCrosshairAlpha.setVisibility(visibility);
    }

    private void bindChecked(com.google.android.material.materialswitch.MaterialSwitch sw,
                             boolean checked,
                             java.util.function.Consumer<Boolean> onChanged) {
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> onChanged.accept(isChecked));
    }

    private void containerAdd(View view) {
        binding.contentContainer.addView(view);
    }

    private static float snapToSlider(Slider slider, float value) {
        float from = slider.getValueFrom();
        float to = slider.getValueTo();
        float step = slider.getStepSize();
        if (step <= 0f) {
            return Math.max(from, Math.min(to, value));
        }
        float snapped = from + Math.round((value - from) / step) * step;
        return Math.max(from, Math.min(to, snapped));
    }

    public void attach() {
        if (isAttached()) return;
        closing = false;
        try {
            windowManager.addView(binding.getRoot(), params);
            AppLogger.d(TAG, "settings sub panel attached");
            binding.getRoot().post(this::animateOpen);
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "settings sub panel detached");
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
                binding.subPanelCard, slideDistance(), FloatingAnimator.NORMAL_MS, null);
    }

    private void animateClose() {
        FloatingAnimator.slideOutToStart(
                binding.subPanelCard, slideDistance(), FloatingAnimator.NORMAL_MS, this::detach);
        FloatingAnimator.fadeOut(binding.getRoot(), FloatingAnimator.NORMAL_MS, null);
    }

    private float slideDistance() {
        int width = binding.subPanelCard.getWidth();
        if (width <= 0) {
            width = context.getResources().getDisplayMetrics().widthPixels;
        }
        return -width;
    }

    private void resetAnimState() {
        closing = false;
        binding.getRoot().setAlpha(1f);
        binding.subPanelCard.setAlpha(1f);
        binding.subPanelCard.setTranslationX(0f);
    }

    private void applySystemBarInsets(View card) {
        ViewCompat.setOnApplyWindowInsetsListener(card, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}