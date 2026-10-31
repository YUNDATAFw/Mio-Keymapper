package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.slider.Slider;

import java.util.List;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.exceptions.RegistryException;
import io.takamiya.mio.databinding.FloatingWindowSettingsPanelBinding;
import io.takamiya.mio.injector.ImeAutoSwitchHelper;
import io.takamiya.mio.injector.ImeKeyInjector;
import io.takamiya.mio.injector.KeyInjectorFactory;
import io.takamiya.mio.injector.KeyInjectorType;
import io.takamiya.mio.injector.ShizukuKeyInjector;
import io.takamiya.mio.navigation.HomeFragment;
import io.takamiya.mio.shizuku.ShizukuHelper;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.InputModelManager;
import io.takamiya.mio.utils.RegistryUtils;

public class FloatingSettingsPanel {

    public interface Listener {
        void onOpenFunctionPanel();
        void onOpenCommandPanel();
        void onOpenToolboxPanel();
        void onOpenWsPanel();
        void onOpenImportBuildingPanel();
        void onToggleWatermark(boolean enabled);
        void onToggleStatusBar(boolean enabled);
        void onToggleFeatureList(boolean enabled);
        void onToggleEditMode(boolean enabled);
        void onExitService();
        void onSizeChanged(int scalePercent);
        void onStatusBarYChanged(int yDp);
        void onStatusBarAlphaChanged(int alphaPercent);
        void onSwitchIme();
        void showToast(String text);
        void onFeatureListFontSizeChanged(int sizePx);
        void onFeatureListRightOffsetChanged(int offsetPx);
        void onFeatureListGlowChanged(boolean enabled);
        void onFeatureListGlowStrengthChanged(int strength);
        void onFeatureListWatermarkChanged(boolean enabled);
        void onNewToastFontSizeChanged(int sizePx);
        void onNewToastGradientChanged(boolean enabled);
        void onActionToastChanged(boolean enabled);
        void onToggleCpsIndicator(boolean enabled);
        void onToggleNewStatusBar(boolean enabled);
        void onToggleCrosshair(boolean enabled);
        void onToggleGyroAim(boolean enabled);
        void onCrosshairCenter();
        void onCrosshairSizeChanged(int sizeDp);
        void onCrosshairAlphaChanged(int alphaPercent);
        void onToggleAllHidden(boolean enabled);
        void onOpenStatusBarSettings();
        void onOpenFeatureListSettings();
        void onOpenCrosshairSettings();
    }

    private static final String TAG = "FloatingSettingsPanel";

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowSettingsPanelBinding binding;

    private Listener listener;
    private boolean closing;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int currentModel = InputModelManager.MODEL_IME;
    private boolean watermarkEnabled = true;
    private int floatSizePercent = 100;
    private int statusBarAlpha = 45;
    // 新增设置项
    private int newToastFontSize = 20;
    private boolean newToastGradient = false;
    private boolean actionToastEnabled = true;
    private boolean cpsIndicatorEnabled = false;
    private boolean allHidden = false;
    private boolean gyroAimEnabled = false;

    public FloatingSettingsPanel(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.binding = FloatingWindowSettingsPanelBinding.inflate(LayoutInflater.from(context));

        int type = FloatingWindowType.resolve(context);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
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

        binding.panelRoot.setOnClickListener(v -> close());
        binding.btnClose.setOnClickListener(v -> close());

        applySystemBarInsets(binding.panelCard);

        binding.btnFunctionPanel.setOnClickListener(v -> {
            if (listener != null) listener.onOpenFunctionPanel();
        });
        binding.btnCommandPanel.setOnClickListener(v -> {
            if (listener != null) listener.onOpenCommandPanel();
        });
        binding.btnToolboxPanel.setOnClickListener(v -> {
            if (listener != null) listener.onOpenToolboxPanel();
        });
        binding.btnWsPanel.setOnClickListener(v -> {
            if (listener != null) listener.onOpenWsPanel();
        });
        binding.btnImportBuilding.setOnClickListener(v -> {
            if (listener != null) listener.onOpenImportBuildingPanel();
        });
        binding.chipGroupMode.setOnCheckedStateChangeListener((group, checkedIds) -> {
            onModeChanged(checkedIds);
        });
        binding.switchWatermark.setOnCheckedChangeListener((buttonView, isChecked) -> {
            watermarkEnabled = isChecked;
            if (listener != null) listener.onToggleWatermark(isChecked);
        });
        binding.switchEditMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onToggleEditMode(isChecked);
            if (isChecked) close();
        });
        binding.btnExitService.setOnClickListener(v -> {
            if (listener != null) listener.onExitService();
        });

        binding.switchHideAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            allHidden = isChecked;
            if (listener != null) listener.onToggleAllHidden(isChecked);
            if (isChecked) close();
        });

        binding.btnSwitchIme.setOnClickListener(v -> {
            if (listener != null) listener.onSwitchIme();
        });

        binding.btnStatusBarSettings.setOnClickListener(v -> {
            if (listener != null) listener.onOpenStatusBarSettings();
        });
        binding.btnFeatureListSettings.setOnClickListener(v -> {
            if (listener != null) listener.onOpenFeatureListSettings();
        });
        binding.btnCrosshairSettings.setOnClickListener(v -> {
            if (listener != null) listener.onOpenCrosshairSettings();
        });

        binding.sliderFloatSize.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int percent = (int) value;
                floatSizePercent = percent;
                binding.tvSizeValue.setText(percent + "%");
                if (listener != null) listener.onSizeChanged(percent);
            }
        });

        binding.sliderStatusBarAlpha.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int alpha = (int) value;
                binding.tvStatusBarAlphaValue.setText(alpha + "%");
                if (listener != null) listener.onStatusBarAlphaChanged(alpha);
            }
        });

        // 新增设置项绑定
        binding.sliderNewToastFontSize.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int v = (int) value;
                newToastFontSize = v;
                binding.tvNewToastFontSizeValue.setText(v + "px");
                if (listener != null) listener.onNewToastFontSizeChanged(v);
            }
        });

        binding.switchNewToastGradient.setOnCheckedChangeListener((buttonView, isChecked) -> {
            newToastGradient = isChecked;
            if (listener != null) listener.onNewToastGradientChanged(isChecked);
        });

        binding.switchActionToast.setOnCheckedChangeListener((buttonView, isChecked) -> {
            actionToastEnabled = isChecked;
            if (listener != null) listener.onActionToastChanged(isChecked);
        });

        binding.switchCpsIndicator.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onToggleCpsIndicator(isChecked);
        });

        binding.switchGyroAim.setOnCheckedChangeListener((buttonView, isChecked) -> {
            gyroAimEnabled = isChecked;
            if (listener != null) listener.onToggleGyroAim(isChecked);
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void attach() {
        if (isAttached()) return;
        closing = false;
        loadConfig();
        syncStates();
        try {
            windowManager.addView(binding.getRoot(), params);
            AppLogger.d(TAG, "settings panel attached");
            binding.getRoot().post(this::animateOpen);
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "settings panel detached");
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

    private void onModeChanged(List<Integer> checkedIds) {
        if (checkedIds == null || checkedIds.isEmpty()) return;
        int checkedId = checkedIds.get(0);
        int newModel = checkedId == binding.chipModeShizuku.getId()
                ? InputModelManager.MODEL_SHIZUKU : InputModelManager.MODEL_IME;
        if (newModel == currentModel) return;

        if (newModel == InputModelManager.MODEL_IME) {
            switchToImeMode();
        } else {
            switchToShizukuMode();
        }
    }

    private void switchToImeMode() {
        try {
            KeyInjectorFactory.create(KeyInjectorType.IME, context);
            completeModeSwitch(InputModelManager.MODEL_IME);
        } catch (IllegalStateException e) {
            AppLogger.w(TAG, "mode switch to IME failed: " + e.getMessage());
            if (!ImeKeyInjector.isImeEnabled(context)) {
                revertChip();
                if (listener != null) listener.showToast(e.getMessage());
                return;
            }
            ImeAutoSwitchHelper.switchToMioIme(context);
            new Thread(() -> {
                boolean ready = ImeAutoSwitchHelper.waitForMioImeCurrent(context, 3000L);
                mainHandler.post(() -> {
                    if (ready) {
                        completeModeSwitch(InputModelManager.MODEL_IME);
                    } else {
                        revertChip();
                        if (listener != null) listener.showToast("输入法未切换为当前输入法");
                    }
                });
            }).start();
        }
    }

    private void switchToShizukuMode() {
        try {
            KeyInjectorFactory.create(KeyInjectorType.SHIZUKU, context);
            completeModeSwitch(InputModelManager.MODEL_SHIZUKU);
        } catch (IllegalStateException e) {
            AppLogger.w(TAG, "mode switch to Shizuku failed: " + e.getMessage());
            if (ShizukuKeyInjector.isShizukuRunning()
                    && ShizukuKeyInjector.isPermissionGranted()
                    && !ShizukuKeyInjector.isServiceBound()) {
        if (listener != null) listener.showToast("Shizuku服务未连接，正在重连");
                bindAndCompleteModeSwitch();
                return;
            }
            revertChip();
            if (listener != null) listener.showToast(e.getMessage());
        }
    }

    private void completeModeSwitch(int model) {
        boolean leavingIme = currentModel == InputModelManager.MODEL_IME;
        currentModel = model;
        saveInputModel(model);
        HomeFragment.notifyInputModelChanged();
        if (leavingIme && model != InputModelManager.MODEL_IME) {
            ImeAutoSwitchHelper.restorePreviousIme(context);
        }
    }

    private void bindAndCompleteModeSwitch() {
        ShizukuHelper.getInstance().bindShellService();
        new Thread(() -> {
            long deadline = SystemClock.uptimeMillis() + 2500;
            while (SystemClock.uptimeMillis() < deadline) {
                if (ShizukuKeyInjector.isServiceBound()) break;
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            boolean ready = false;
            if (ShizukuKeyInjector.isServiceBound()) {
                try {
                    KeyInjectorFactory.create(KeyInjectorType.SHIZUKU, context);
                    ready = true;
                } catch (IllegalStateException ignored) {
                }
            }
            final boolean modeReady = ready;
            mainHandler.post(() -> {
                if (modeReady) {
                    completeModeSwitch(InputModelManager.MODEL_SHIZUKU);
                } else {
                    revertChip();
                    if (listener != null) listener.showToast("Shizuku服务连接失败，请重试");
                }
            });
        }).start();
    }

    private void revertChip() {
        if (currentModel == InputModelManager.MODEL_NONE) {
            binding.chipModeShizuku.setChecked(false);
            binding.chipModeIme.setChecked(false);
            return;
        }
        if (currentModel == InputModelManager.MODEL_SHIZUKU) {
            binding.chipModeShizuku.setChecked(true);
        } else {
            binding.chipModeIme.setChecked(true);
        }
    }

    private void syncStates() {
        if (currentModel == InputModelManager.MODEL_NONE) {
            binding.chipModeShizuku.setChecked(false);
            binding.chipModeIme.setChecked(false);
        } else if (currentModel == InputModelManager.MODEL_SHIZUKU) {
            binding.chipModeShizuku.setChecked(true);
        } else {
            binding.chipModeIme.setChecked(true);
        }
        binding.switchWatermark.setOnCheckedChangeListener((buttonView, isChecked) -> {
            watermarkEnabled = isChecked;
            if (listener != null) listener.onToggleWatermark(isChecked);
        });

        binding.sliderFloatSize.setValue(snapToSlider(binding.sliderFloatSize, floatSizePercent));
        binding.tvSizeValue.setText(floatSizePercent + "%");

        binding.sliderStatusBarAlpha.setValue(snapToSlider(binding.sliderStatusBarAlpha, statusBarAlpha));
        binding.tvStatusBarAlphaValue.setText(statusBarAlpha + "%");

        //  新增设置项同步
        binding.sliderNewToastFontSize.setValue(snapToSlider(binding.sliderNewToastFontSize, newToastFontSize));
        binding.tvNewToastFontSizeValue.setText(newToastFontSize + "px");

        binding.switchNewToastGradient.setOnCheckedChangeListener(null);
        binding.switchNewToastGradient.setChecked(newToastGradient);
        binding.switchNewToastGradient.setOnCheckedChangeListener((buttonView, isChecked) -> {
            newToastGradient = isChecked;
            if (listener != null) listener.onNewToastGradientChanged(isChecked);
        });

        binding.switchActionToast.setOnCheckedChangeListener(null);
        binding.switchActionToast.setChecked(actionToastEnabled);
        binding.switchActionToast.setOnCheckedChangeListener((buttonView, isChecked) -> {
            actionToastEnabled = isChecked;
            if (listener != null) listener.onActionToastChanged(isChecked);
        });

        binding.switchCpsIndicator.setOnCheckedChangeListener(null);
        binding.switchCpsIndicator.setChecked(cpsIndicatorEnabled);
        binding.switchCpsIndicator.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onToggleCpsIndicator(isChecked);
        });

        binding.switchGyroAim.setOnCheckedChangeListener(null);
        binding.switchGyroAim.setChecked(gyroAimEnabled);
        binding.switchGyroAim.setOnCheckedChangeListener((buttonView, isChecked) -> {
            gyroAimEnabled = isChecked;
            if (listener != null) listener.onToggleGyroAim(isChecked);
        });

        binding.switchHideAll.setOnCheckedChangeListener(null);
        binding.switchHideAll.setChecked(allHidden);
        binding.switchHideAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            allHidden = isChecked;
            if (listener != null) listener.onToggleAllHidden(isChecked);
            if (isChecked) close();
        });
    }

        // 保存的配置值未必落在滑动stepSize 网格上（如Y=24、stepSize=5），
    // 直接 setValue 会在绘制时抛 IllegalStateException，必须先吸附到合法刻度
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

    private void loadConfig() {
        Registry reg = RegistryUtils.get();
        int saved = InputModelManager.MODEL_IME;
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue())) {
                    saved = reg.getInt(DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue());
                }
            } catch (RegistryException e) {
                saved = InputModelManager.MODEL_IME;
                AppLogger.w(TAG, "failed to load input model: " + e.getMessage());
            }
        }
        SettingsValues values = SettingsValues.instance();
        values.load(reg);
        watermarkEnabled = values.watermarkEnabled;
        floatSizePercent = values.floatSizePercent;
        statusBarAlpha = values.statusBarAlpha;
        newToastFontSize = values.newToastFontSize;
        newToastGradient = values.newToastGradient;
        actionToastEnabled = values.actionToastEnabled;
        cpsIndicatorEnabled = values.cpsIndicatorEnabled;
        allHidden = FloatingWindowService.isAllUiHidden();
        gyroAimEnabled = values.gyroAimEnabled;
        currentModel = InputModelManager.resolveModel(saved, context);
        if (currentModel != InputModelManager.MODEL_NONE
                && saved != InputModelManager.MODEL_IME
                && saved != InputModelManager.MODEL_SHIZUKU) {
            saveInputModel(currentModel);
        }
        if (saved == InputModelManager.MODEL_SHIZUKU
                && currentModel == InputModelManager.MODEL_IME
                && listener != null) {
        mainHandler.post(() -> listener.showToast("Shizuku暂不可用，已回退到IME模式"));
        }
        if (currentModel == InputModelManager.MODEL_SHIZUKU
                && ShizukuKeyInjector.isShizukuRunning()
                && ShizukuKeyInjector.isPermissionGranted()
                && !ShizukuKeyInjector.isServiceBound()) {
            ShizukuHelper.getInstance().bindShellService();
        }
    }

    private void saveInputModel(int model) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue(), model);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save input model failed: " + e.getMessage(), e);
        }
    }

    private void animateOpen() {
        FloatingAnimator.fadeIn(binding.getRoot(), FloatingAnimator.NORMAL_MS, null);
        float fromX = slideDistance();
        FloatingAnimator.slideInFromStart(
                binding.panelCard, fromX, FloatingAnimator.NORMAL_MS, null);
    }

    private void animateClose() {
        float toX = slideDistance();
        FloatingAnimator.slideOutToStart(
                binding.panelCard, toX, FloatingAnimator.NORMAL_MS, this::detach);
        FloatingAnimator.fadeOut(binding.getRoot(), FloatingAnimator.NORMAL_MS, null);
    }

    private float slideDistance() {
        int width = binding.panelCard.getWidth();
        if (width <= 0) {
            width = context.getResources().getDisplayMetrics().widthPixels;
        }
        return -width;
    }

    private void resetAnimState() {
        closing = false;
        binding.getRoot().setAlpha(1f);
        binding.panelCard.setAlpha(1f);
        binding.panelCard.setTranslationX(0f);
    }

    private void applySystemBarInsets(View card) {
        ViewCompat.setOnApplyWindowInsetsListener(card, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
