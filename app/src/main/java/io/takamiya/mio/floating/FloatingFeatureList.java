package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.R;
import io.takamiya.mio.bean.ControlActionItem;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class FloatingFeatureList {

    private static final String TAG = "FloatingFeatureList";

    private static final int WIDTH_DP = 260;
    private static final int HEIGHT_DP = 600;
    public static final int NEW_TOAST_COLOR_DEFAULT = -1;

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final GLSurfaceView glView;
    private final List<ControlActionItem> items = new ArrayList<>();
    private float currentScale = 1.0f;
    private float requestedAlpha = 1f;

    public FloatingFeatureList(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        int type = FloatingWindowType.resolve(context);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params = new WindowManager.LayoutParams(
                windowWidth(context),
                windowHeight(context),
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        FloatingWindowType.applyToken(params);
        params.gravity = Gravity.END | Gravity.TOP;
        params.x = 0;
        params.y = 0;

        glView = new GLSurfaceView(context);
        glView.setEGLContextClientVersion(3);
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        glView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        // 不能用 setZOrderOnTop(true)：那会把 GL 内容合成到窗口图层之上的独立
        // SurfaceFlinger 图层（alpha 恒为 1，不受窗口 params.alpha 控制），
        // 遮挡判定时组合不透明度超过上限导致穿透触摸被系统拦截。
        // 默认模式（窗口层之下打孔显示）视觉一致且不参与额外遮挡。
        glView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                SolsticeUI.Init();
                syncToNative();
            }

            @Override
            public void onSurfaceChanged(GL10 gl, int width, int height) {
                SolsticeUI.Resize(width, height);
            }

            @Override
            public void onDrawFrame(GL10 gl) {
                SolsticeUI.Render();
            }
        });
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        applyDefaultStyle();

        loadScale();
        loadAlpha();
    }

    public void showNotification(String msg, int type, float duration) {
        SolsticeUI.Notify(msg, type, duration);
    }

    private void applyDefaultStyle() {
        SolsticeUI.SetWatermarkText("");
        SolsticeUI.SetFontSize(24.0f);
        SolsticeUI.SetRightOffset(10.0f);
        SolsticeUI.SetDisplay(savedDisplayStyle());
        SolsticeUI.SetTextShadow(true);
        SolsticeUI.SetShadowOffset(1.0f);
        applySavedFeatureListStyle();
        applySavedNotifyStyle();
    }

    // ── 功能列表自定义项 ──

    public static int savedFeatureListFontSize() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_FEATURE_LIST_FONT_SIZE.getValue())) {
                    return reg.getInt(DataManage.Tag.APP_CONFIG_FEATURE_LIST_FONT_SIZE.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load feature list font size: " + e.getMessage());
            }
        }
        return 24;
    }

    public static int savedFeatureListRightOffset() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_FEATURE_LIST_RIGHT_OFFSET.getValue())) {
                    return reg.getInt(DataManage.Tag.APP_CONFIG_FEATURE_LIST_RIGHT_OFFSET.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load feature list right offset: " + e.getMessage());
            }
        }
        return 10;
    }

    public static boolean savedFeatureListGlow() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_FEATURE_LIST_GLOW.getValue())) {
                    return reg.getBoolean(DataManage.Tag.APP_CONFIG_FEATURE_LIST_GLOW.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load feature list glow: " + e.getMessage());
            }
        }
        return true;
    }

    public static int savedFeatureListGlowStrength() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_FEATURE_LIST_GLOW_STRENGTH.getValue())) {
                    return reg.getInt(DataManage.Tag.APP_CONFIG_FEATURE_LIST_GLOW_STRENGTH.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load feature list glow strength: " + e.getMessage());
            }
        }
        return 24;
    }

    public static boolean savedFeatureListWatermark() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_FEATURE_LIST_WATERMARK.getValue())) {
                    return reg.getBoolean(DataManage.Tag.APP_CONFIG_FEATURE_LIST_WATERMARK.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load feature list watermark: " + e.getMessage());
            }
        }
        return false;
    }

    public static String savedFeatureListWatermarkText() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_FEATURE_LIST_WATERMARK_TEXT.getValue())) {
                    String text = reg.getString(DataManage.Tag.APP_CONFIG_FEATURE_LIST_WATERMARK_TEXT.getValue());
                    if (!text.isEmpty() && !text.matches("[a-zA-Z0-9\\s_-]+")) {
                        // 包含非ASCII字符，替换为默认值
                        return "Mio";
                    }
                    return text;
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load feature list watermark text: " + e.getMessage());
            }
        }
        return "Mio";
    }

    public void applySavedFeatureListStyle() {
        SolsticeUI.SetFeatureListFontSize(savedFeatureListFontSize());
        SolsticeUI.SetRightOffset(savedFeatureListRightOffset());
        SolsticeUI.SetGlow(savedFeatureListGlow());
        int strength = savedFeatureListGlowStrength();
        SolsticeUI.SetGlowRadius(strength / 100.0f * 5.0f);
        int density = Math.max(1, Math.min(8,
                (int) Math.round(strength / 100.0f * 5.0f * 1.5f)));
        SolsticeUI.SetGlowDensity(density);
        SolsticeUI.SetWatermarkText(savedFeatureListWatermark() ? savedFeatureListWatermarkText() : "");
    }

    // ── 新提示气泡自定义项 ──

    public static int savedNewToastFontSize() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_NEW_TOAST_FONT_SIZE.getValue())) {
                    return reg.getInt(DataManage.Tag.APP_CONFIG_NEW_TOAST_FONT_SIZE.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load new toast font size: " + e.getMessage());
            }
        }
        return 20;
    }

    public static int savedNewToastPosition() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_NEW_TOAST_POSITION.getValue())) {
                    return reg.getInt(DataManage.Tag.APP_CONFIG_NEW_TOAST_POSITION.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load new toast position: " + e.getMessage());
            }
        }
        return 0;
    }

    public static boolean savedNewToastGradient() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_NEW_TOAST_GRADIENT.getValue())) {
                    return reg.getBoolean(DataManage.Tag.APP_CONFIG_NEW_TOAST_GRADIENT.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load new toast gradient: " + e.getMessage());
            }
        }
        return false;
    }

    public void applySavedNotifyStyle() {
        SolsticeUI.SetNotifyFontSize(savedNewToastFontSize());
        SolsticeUI.SetNotifyPosition(savedNewToastPosition());
        SolsticeUI.SetNotifyGradient(savedNewToastGradient());
        applySavedNotifyColor();
    }

    public static int savedDisplayStyle() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_FEATURE_LIST_STYLE.getValue())) {
                    return reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_FEATURE_LIST_STYLE.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load feature list style: " + e.getMessage());
            }
        }
        return 2;
    }

    public void setDisplayStyle(int style) {
        SolsticeUI.SetDisplay(style);
    }

    public static boolean savedNewToastEnabled() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_USE_NEW_TOAST.getValue())) {
                    return reg.getBoolean(DataManage.Tag.APP_CONFIG_USE_NEW_TOAST.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load new toast enabled: " + e.getMessage());
            }
        }
        return false;
    }

    public static boolean savedUseNewFeatureList() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_USE_NEW_FEATURE_LIST.getValue())) {
                    return reg.getBoolean(DataManage.Tag.APP_CONFIG_USE_NEW_FEATURE_LIST.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load new feature list enabled: " + e.getMessage());
            }
        }
        return true;
    }

    public static int savedNotifyColor(Context context) {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_NEW_TOAST_COLOR.getValue())) {
                    int saved = reg.getInt(DataManage.Tag.APP_CONFIG_NEW_TOAST_COLOR.getValue());
                    if (saved != NEW_TOAST_COLOR_DEFAULT) {
                        return saved;
                    }
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load new toast color: " + e.getMessage());
            }
        }
        return ContextCompat.getColor(context, R.color.colorPrimary);
    }

    public static void applySavedNotifyColor(Context context) {
        SolsticeUI.SetNotifyColor(savedNotifyColor(context));
    }

    private void applySavedNotifyColor() {
        if (context != null) {
            SolsticeUI.SetNotifyColor(savedNotifyColor(context));
        }
    }

    public void applyWindowSize() {
        params.width = windowWidth(context);
        params.height = windowHeight(context);
        if (isAttached()) {
            windowManager.updateViewLayout(glView, params);
        }
        PassThroughOpacityCoordinator.notifyGeometryChanged();
        applyScale();
    }

    // 顶层悬浮窗的 WRAP_CONTENT/MATCH_PARENT 都会被 WMS 当成满屏处理，
    // 导致整屏成为穿透遮挡区域（Android 12+ 会拦截穿透触摸），必须用实际像素尺寸
    private static int windowWidth(Context context) {
        return dp(context, WIDTH_DP);
    }

    private static int windowHeight(Context context) {
        return dp(context, HEIGHT_DP);
    }

    private static String keyOf(ControlActionItem item) {
        return item.getPackageName() + "." + item.getActionId();
    }

    private void syncToNative() {
        int n = items.size();
        String[] titles = new String[n];
        String[] keys = new String[n];
        boolean[] enabled = new boolean[n];
        for (int i = 0; i < n; i++) {
            ControlActionItem item = items.get(i);
            titles[i] = item.getTitle() != null ? item.getTitle() : "";
            keys[i] = keyOf(item);
            enabled[i] = true;
        }
        SolsticeUI.SetItems(titles, keys, enabled);
    }

    public void updateActions(List<ControlActionItem> actions) {
        items.clear();
        if (actions != null) {
            items.addAll(actions);
        }
        syncToNative();
    }

    public void setActiveState(String key, boolean active) {
        SolsticeUI.SetActiveState(key, active);
    }

    public void updateScale(float scale) {
        currentScale = Math.max(0.8f, Math.min(1.2f, scale));
        applyScale();
    }

    private void applyScale() {
        int width = layoutWidth();
        glView.setPivotX(width);
        glView.setPivotY(0f);
        glView.setScaleX(currentScale);
        glView.setScaleY(currentScale);
        if (isAttached()) {
            windowManager.updateViewLayout(glView, params);
        }
    }

    private int layoutWidth() {
        if (params.width > 0) {
            return params.width;
        }
        return context.getResources().getDisplayMetrics().widthPixels;
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

    public void setWindowAlpha(float alpha) {
        params.alpha = alpha;
        requestedAlpha = alpha;
        FloatingWindowType.applyPassThroughAlphaClamp(context, params);
        if (isAttached()) {
            windowManager.updateViewLayout(glView, params);
        }
        PassThroughOpacityCoordinator.notifyTargetChanged(
                PassThroughOpacityCoordinator.ID_FEATURE_LIST, requestedAlpha);
    }

    public void attach() {
        if (isAttached()) return;
        try {
            FloatingWindowType.applyPassThroughAlphaClamp(context, params);
            windowManager.addView(glView, params);
            syncToNative();
            applyScale();
            PassThroughOpacityCoordinator.notifyAttached(
                    PassThroughOpacityCoordinator.ID_FEATURE_LIST,
                    () -> PassThroughOpacityCoordinator.frameOf(context, params),
                    a -> {
                        params.alpha = a;
                        if (isAttached()) {
                            windowManager.updateViewLayout(glView, params);
                        }
                    },
                    false,
                    requestedAlpha);
            AppLogger.d(TAG, "feature list attached");
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        PassThroughOpacityCoordinator.notifyDetached(PassThroughOpacityCoordinator.ID_FEATURE_LIST);
        try {
            windowManager.removeView(glView);
            AppLogger.d(TAG, "feature list detached");
        } catch (Exception e) {
            AppLogger.e(TAG, "detach failed: " + e.getMessage(), e);
        }
    }

    public boolean isAttached() {
        return glView.getParent() != null;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
