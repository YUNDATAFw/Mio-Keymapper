package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.DeviceInfoUtils;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.exceptions.RegistryException;
import io.takamiya.mio.databinding.FloatingWindowWatermarkBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;
import io.takamiya.mio.utils.UserToken;

public class FloatingWatermark {

    private static final String TAG = "FloatingWatermark";

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowWatermarkBinding binding;
    private float currentScale = 1.0f;
    private float requestedAlpha = 1f;

    public FloatingWatermark(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        
        this.binding = FloatingWindowWatermarkBinding.inflate(LayoutInflater.from(context));

        String version = DeviceInfoUtils.getAppVersionName(context);
        binding.tvWatermarkVersion.setText(version);

        String sub = String.valueOf(UserToken.getSub());
        String appId = AppToken.getAppId();
        String base = sub + "." + appId;
        String raw = base + ";";
        binding.tvWatermarkUid.setText(Base64.encodeToString(
                raw.getBytes(StandardCharsets.UTF_8),
                Base64.NO_PADDING | Base64.NO_WRAP));
        

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
        params.gravity = Gravity.BOTTOM | Gravity.START;
        params.x = 0;
        params.y = 0;

        loadScale();
        loadAlpha();
    }

    public void updateScale(float scale) {
        currentScale = Math.max(0.8f, Math.min(1.2f, scale));
        applyScale();
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
        View container = binding.scalableContent;
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
            windowManager.updateViewLayout(binding.getRoot(), params);
        }
        PassThroughOpacityCoordinator.notifyTargetChanged(
                PassThroughOpacityCoordinator.ID_WATERMARK, requestedAlpha);
    }

    public void attach() {
        if (isAttached()) return;
        try {
            FloatingWindowType.applyPassThroughAlphaClamp(context, params);
            windowManager.addView(binding.getRoot(), params);
            applyScale();
            PassThroughOpacityCoordinator.notifyAttached(
                    PassThroughOpacityCoordinator.ID_WATERMARK,
                    () -> PassThroughOpacityCoordinator.frameOf(context, params),
                    a -> {
                        params.alpha = a;
                        if (isAttached()) {
                            windowManager.updateViewLayout(binding.getRoot(), params);
                        }
                    },
                    false,
                    requestedAlpha);
            AppLogger.d(TAG, "watermark attached");
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        PassThroughOpacityCoordinator.notifyDetached(PassThroughOpacityCoordinator.ID_WATERMARK);
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "watermark detached");
        } catch (Exception e) {
            AppLogger.e(TAG, "detach failed: " + e.getMessage(), e);
        }
    }

    public boolean isAttached() {
        return binding.getRoot().getParent() != null;
    }

    private static String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            AppLogger.e(TAG, "SHA-1 not available: " + e.getMessage());
            return "";
        }
    }
}
