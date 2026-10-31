package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.DeviceInfoUtils;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.exceptions.RegistryException;
import io.takamiya.mio.databinding.FloatingWindowStatusBarBinding;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.InputModelManager;
import io.takamiya.mio.utils.RegistryUtils;

/**
 * 悬浮状态栏（旧版/新版二合一）：
 * 「新版状态栏」开关在本类内直接切换渲染样式，不再独立成类。
 * 旧版显示 图标+标题/版本+模式/FPS，新版显示 版本/账号/模式/CPS。
 * 仅横屏时挂载。
 */
public class FloatingStatusBar {

    private static final String TAG = "FloatingStatusBar";
    private static final String[] INPUT_MODEL_NAMES = {"IME", "Shizuku"};
    private static final long FPS_UPDATE_INTERVAL_MS = 100L;
    private static final int MSG_UPDATE_FPS = 1001;

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowStatusBarBinding binding;

    private final Choreographer choreographer = Choreographer.getInstance();
    private HandlerThread fpsThread;
    private Handler fpsHandler;

    private long lastFrameTimeNanos;
    private int frameCount;
    private String lastFpsText;
    private String lastCpsText = "";
    private float currentScale = 1.0f;
    private int currentY;
    private float currentAlpha;
    private boolean useNewStyle;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (lastFrameTimeNanos == 0) {
                lastFrameTimeNanos = frameTimeNanos;
            }
            frameCount++;
            choreographer.postFrameCallback(this);
        }
    };

    private final Handler.Callback fpsCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == MSG_UPDATE_FPS) {
                float fps = 0f;
                if (lastFrameTimeNanos > 0) {
                    float deltaSeconds = (System.nanoTime() - lastFrameTimeNanos) / 1_000_000_000f;
                    if (deltaSeconds > 0) {
                        fps = frameCount / deltaSeconds;
                    }
                }
                frameCount = 0;
                lastFrameTimeNanos = 0;

                final String fpsText = String.format("FPS:%.2f", fps);
                if (!fpsText.equals(lastFpsText)) {
                    lastFpsText = fpsText;
                    binding.tvStatusBarFps.post(() -> {
                        binding.tvStatusBarFps.setText(fpsText);
                        refreshScaleIfNeeded();
                    });
                }

                // stopFps() 可能在本方法执行期间把 fpsHandler 置 null，先取局部引用防空指针
                Handler handler = fpsHandler;
                if (handler != null) {
                    handler.sendEmptyMessageDelayed(MSG_UPDATE_FPS, FPS_UPDATE_INTERVAL_MS);
                }
                return true;
            }
            return false;
        }
    };

    public FloatingStatusBar(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        this.binding = FloatingWindowStatusBarBinding.inflate(LayoutInflater.from(context));

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
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.x = 0;

        loadConfig();
        loadScale();
        applyStyle(true);
        initData();
    }

    public void updateScale(float scale) {
        currentScale = Math.max(0.8f, Math.min(1.2f, scale));
        applyScale();
    }

    public void updateY(int yDp) {
        params.y = clampY(yDp);
        currentY = (int) (params.y / context.getResources().getDisplayMetrics().density);
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
        saveY(currentY);
    }

    public void updateAlpha(int alphaPercent) {
        currentAlpha = alphaPercent / 100f;
        params.alpha = currentAlpha;
        FloatingWindowType.applyPassThroughAlphaClamp(context, params);
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
        saveAlpha(alphaPercent);
        PassThroughOpacityCoordinator.notifyTargetChanged(
                PassThroughOpacityCoordinator.ID_STATUS_BAR, params.alpha);
    }

    public void setUseNewStyle(boolean enabled) {
        if (useNewStyle == enabled) return;
        useNewStyle = enabled;
        persistNewStyle(enabled);
        applyStyle(false);
    }

    public void setCpsText(String cpsText) {
        final String text = cpsText != null ? "CPS:" + cpsText : "CPS:0";
        if (!text.equals(lastCpsText)) {
            lastCpsText = text;
            binding.tvNewStatusCps.post(() -> binding.tvNewStatusCps.setText(text));
        }
    }

    public void setWindowAlpha(float alpha) {
        params.alpha = alpha;
        FloatingWindowType.applyPassThroughAlphaClamp(context, params);
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
        PassThroughOpacityCoordinator.notifyTargetChanged(
                PassThroughOpacityCoordinator.ID_STATUS_BAR, params.alpha);
    }

    private void applyStyle(boolean force) {
        binding.scalableContent.setVisibility(useNewStyle ? View.GONE : View.VISIBLE);
        binding.scalableContentNew.setVisibility(useNewStyle ? View.VISIBLE : View.GONE);
        if (force || isAttached()) {
            applyScale();
        }
    }

    private void persistNewStyle(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_NEW_STATUS_BAR.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save new status bar style failed: " + e.getMessage(), e);
        }
    }

    private View activeContainer() {
        return useNewStyle ? binding.scalableContentNew : binding.scalableContent;
    }

    private void loadConfig() {
        Registry reg = RegistryUtils.get();
        if (reg == null) {
            currentY = 30;
            currentAlpha = 0.45f;
            useNewStyle = false;
            return;
        }
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_Y.getValue())) {
                currentY = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_Y.getValue());
            } else {
                currentY = 30;
            }
        } catch (Exception e) {
            currentY = 30;
        }
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_ALPHA.getValue())) {
                int alphaInt = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_ALPHA.getValue());
                currentAlpha = alphaInt / 100f;
            } else {
                currentAlpha = 0.45f;
            }
        } catch (Exception e) {
            currentAlpha = 0.45f;
        }
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_NEW_STATUS_BAR.getValue())) {
                useNewStyle = reg.getBoolean(DataManage.Tag.APP_CONFIG_FLOAT_NEW_STATUS_BAR.getValue());
            } else {
                useNewStyle = false;
            }
        } catch (Exception e) {
            useNewStyle = false;
        }
        params.y = clampY(currentY);
        params.alpha = currentAlpha;
    }

        // 把y(dp) 夹到可见区域内，防止 FPS 条跑到导航栏下方
    private int clampY(int yDp) {
        float density = context.getResources().getDisplayMetrics().density;
        int yPx = (int) (yDp * density);
        int height = params.height > 0
                ? params.height
                : Math.max(binding.getRoot().getHeight(), (int) (30 * density));
        Rect safe = WindowBounds.safeRect(context, binding.getRoot());
        int maxY = Math.max(0, safe.bottom - height);
        return Math.max(0, Math.min(yPx, maxY));
    }

    private void saveY(int yDp) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_Y.getValue(), yDp);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save status bar Y failed: " + e.getMessage(), e);
        }
    }

    private void saveAlpha(int alphaPercent) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_ALPHA.getValue(), alphaPercent);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save status bar alpha failed: " + e.getMessage(), e);
        }
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
        View container = activeContainer();
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
        container.setPivotX(0f);
        container.setPivotY(0f);
        container.setScaleX(currentScale);
        container.setScaleY(currentScale);
        if (isAttached()) {
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
    }

    private void refreshScaleIfNeeded() {
        if (!isAttached()) return;
        View container = activeContainer();
        container.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int w = Math.max(container.getMeasuredWidth(), 1);
        int h = Math.max(container.getMeasuredHeight(), 1);
        ViewGroup.LayoutParams lp = container.getLayoutParams();
        if (lp != null && lp.width == w && lp.height == h) return;
        applyScale();
    }

    private void initData() {
        String version = DeviceInfoUtils.getAppVersionName(context);
        binding.tvStatusVersion.setText(version);
        binding.tvNewStatusVersion.setText(version);

        int model = loadInputModel();
        String modelName = (model >= 0 && model < INPUT_MODEL_NAMES.length)
                ? INPUT_MODEL_NAMES[model]
                : "--";
        binding.tvStatusMode.setText(modelName);
        binding.tvNewStatusModel.setText(modelName);

        String account = loadUserAccount();
        binding.tvNewStatusNickname.setText(account.isEmpty() ? "--" : account);
    }

    private String loadUserAccount() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return "";
        try {
            if (reg.exists(DataManage.Tag.USER_ACCOUNT.getValue())) {
                return reg.getString(DataManage.Tag.USER_ACCOUNT.getValue());
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load user account: " + e.getMessage());
        }
        return "";
    }

    private int loadInputModel() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return InputModelManager.MODEL_NONE;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue())) {
                return reg.getInt(DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue());
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load input model: " + e.getMessage());
        }
        return InputModelManager.MODEL_NONE;
    }

    public void attach() {
        if (isAttached()) return;
        try {
            FloatingWindowType.applyPassThroughAlphaClamp(context, params);
            windowManager.addView(binding.getRoot(), params);
            applyScale();
            startFps();
            PassThroughOpacityCoordinator.notifyAttached(
                    PassThroughOpacityCoordinator.ID_STATUS_BAR,
                    () -> PassThroughOpacityCoordinator.frameOf(context, params),
                    a -> {
                        params.alpha = a;
                        if (isAttached()) {
                            windowManager.updateViewLayout(binding.getRoot(), params);
                        }
                    },
                    false,
                    params.alpha);
            AppLogger.d(TAG, "status bar attached, newStyle=" + useNewStyle);
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        stopFps();
        PassThroughOpacityCoordinator.notifyDetached(PassThroughOpacityCoordinator.ID_STATUS_BAR);
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "status bar detached");
        } catch (Exception e) {
            AppLogger.e(TAG, "detach failed: " + e.getMessage(), e);
        }
    }

    public boolean isAttached() {
        return binding.getRoot().getParent() != null;
    }

    public void show() {
        if (!isAttached()) {
            attach();
        }
    }

    public void hide() {
        detach();
    }

    private void startFps() {
        lastFrameTimeNanos = 0;
        frameCount = 0;
        choreographer.postFrameCallback(frameCallback);

        fpsThread = new HandlerThread("FpsMonitorThread");
        fpsThread.start();
        fpsHandler = new Handler(fpsThread.getLooper(), fpsCallback);
        fpsHandler.sendEmptyMessageDelayed(MSG_UPDATE_FPS, FPS_UPDATE_INTERVAL_MS);
    }

    private void stopFps() {
        choreographer.removeFrameCallback(frameCallback);
        if (fpsHandler != null) {
            fpsHandler.removeCallbacksAndMessages(null);
            fpsHandler = null;
        }
        if (fpsThread != null) {
            fpsThread.quitSafely();
            fpsThread = null;
        }
        lastFrameTimeNanos = 0;
        frameCount = 0;
        lastFpsText = null;
    }
}
