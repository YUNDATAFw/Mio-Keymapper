package io.takamiya.mio.floating;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.NinePatch;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.NinePatchDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.exceptions.RegistryException;
import io.takamiya.mio.R;
import io.takamiya.mio.config.TriggerModel;
import io.takamiya.mio.config.ShortcutEventType;
import io.takamiya.mio.databinding.FloatingWindowShortcutKeyBinding;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class FloatingShortcutKey {

    public interface OnKeyActionListener {
        void onKeyAction(FloatingShortcutKey key);
        void onKeyEvent(FloatingShortcutKey key, ShortcutEventType eventType);
    }

    private static final String TAG = "FloatingShortcutKey";
    private static final String SHORTCUT_POS_PREFIX = "AppConfig.floatShortcut";

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowShortcutKeyBinding binding;
    private final View view;
    private final String key;

    private final int dragSlop;
    private int activePointerId = -1;
    private float downRawX;
    private float downRawY;
    private int startX;
    private int startY;
    private boolean dragging;
    private boolean clickCandidate;

    private TriggerModel triggerModel = TriggerModel.BUTTON;
    private boolean active;
    private boolean editMode;
    private OnKeyActionListener actionListener;
    private long holdThresholdMs;
    private long releaseDelayMs;
    private boolean thresholdActivated;
    private final Runnable thresholdRunnable = this::fireThresholdActivated;
    private final Runnable releaseRunnable = this::fireReleaseDelayed;
    private float currentScale = 1.0f;
    private int baseWidth;
    private int baseHeight;
    private boolean awaitingBaseLayout;
    private float baseTextPx;

    /** 插件 onKeyInit 指定的卡片尺寸（px，null=默认 wrap_content）与背景（null=默认渐变） */
    private Integer customWidthPx;
    private Integer customHeightPx;
    private Drawable cardBgNormal;
    private Drawable cardBgActive;

    private SoundPool soundPool;
    private int soundDown;
    private int soundUp;
    private boolean soundDownSilent;
    private boolean soundUpSilent;
    private AssetFileDescriptor customSoundDownAfd;
    private AssetFileDescriptor customSoundUpAfd;

    public FloatingShortcutKey(Context context, String key) {
        this.context = context;
        this.key = key;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.dragSlop = Math.max((int) (ViewConfiguration.get(context).getScaledTouchSlop() * 0.5f), 4);
        this.binding = FloatingWindowShortcutKeyBinding.inflate(LayoutInflater.from(context));
        this.view = binding.getRoot();

        int type = FloatingWindowType.resolve(context);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        FloatingWindowType.applyToken(params);
        params.gravity = Gravity.START | Gravity.TOP;
        params.x = (int) (30 * context.getResources().getDisplayMetrics().density);
        params.y = (int) (30 * context.getResources().getDisplayMetrics().density);

        loadSavedPosition();
        loadScale();
        loadAlpha();
        initSoundPool();
        view.setOnTouchListener(this::onTouch);
    }

    private void initSoundPool() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(attrs)
                .build();
        soundDownSilent = false;
        soundUpSilent = false;
        soundDown = loadCustomSound(savedCustomSoundUri(true), R.raw.click_down, true);
        soundUp = loadCustomSound(savedCustomSoundUri(false), R.raw.click_up, false);
    }

    private int loadCustomSound(String uri, int defaultRes, boolean isDown) {
        if (uri != null) {
            if (DataManage.SOUND_NONE.equals(uri)) {
                if (isDown) {
                    soundDownSilent = true;
                } else {
                    soundUpSilent = true;
                }
                AppLogger.d(TAG, "shortcut key sound "
                        + (isDown ? "down" : "up") + " disabled");
                return 0;
            }
            try {
                AssetFileDescriptor afd = context.getContentResolver()
                        .openAssetFileDescriptor(Uri.parse(uri), "r");
                if (afd != null) {
                    if (isDown) {
                        customSoundDownAfd = afd;
                    } else {
                        customSoundUpAfd = afd;
                    }
                    int id = soundPool.load(afd, 1);
                    AppLogger.d(TAG, "shortcut key custom sound "
                            + (isDown ? "down" : "up") + " loaded: " + uri);
                    return id;
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load custom shortcut sound: " + e.getMessage());
            }
        }
        return soundPool.load(context, defaultRes, 1);
    }

    private String savedCustomSoundUri(boolean isDown) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return null;
        DataManage.Tag tag = isDown
                ? DataManage.Tag.APP_CONFIG_SHORTCUT_SOUND_ON_URI
                : DataManage.Tag.APP_CONFIG_SHORTCUT_SOUND_OFF_URI;
        try {
            if (reg.exists(tag.getValue())) {
                String s = reg.getString(tag.getValue());
                if (s != null && !s.isEmpty()) return s;
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load custom shortcut sound uri: " + e.getMessage());
        }
        return null;
    }

    public void reloadSound() {
        releaseSound();
        initSoundPool();
    }

    private void releaseSound() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        if (customSoundDownAfd != null) {
            try {
                customSoundDownAfd.close();
            } catch (Exception ignored) {
            }
            customSoundDownAfd = null;
        }
        if (customSoundUpAfd != null) {
            try {
                customSoundUpAfd.close();
            } catch (Exception ignored) {
            }
            customSoundUpAfd = null;
        }
    }

    private void playSoundDown() {
        if (soundDownSilent) return;
        if (soundPool != null && soundDown > 0) soundPool.play(soundDown, 1f, 1f, 1, 0, 1f);
    }

    private void playSoundUp() {
        if (soundUpSilent) return;
        if (soundPool != null && soundUp > 0) soundPool.play(soundUp, 1f, 1f, 1, 0, 1f);
    }

    public void updateScale(float scale) {
        currentScale = Math.max(0.8f, Math.min(1.2f, scale));
        applyScale();
        reClampPosition();
    }

    public void updateAlpha(float alpha) {
        params.alpha = alpha;
        if (isAttached()) {
            windowManager.updateViewLayout(view, params);
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

    void reClampPosition() {
        if (!isAttached()) return;
        Rect safe = WindowBounds.safeRect(context, view);
        params.x = clamp(params.x, safe.left, Math.max(safe.left, safe.right - getWidth()));
        params.y = clamp(params.y, safe.top, Math.max(safe.top, safe.bottom - getHeight()));
        windowManager.updateViewLayout(view, params);
    }

    private void applyScale() {
        if (baseWidth > 0) {
            params.width = (int) (baseWidth * currentScale);
            params.height = (int) (baseHeight * currentScale);
            if (isAttached()) {
                windowManager.updateViewLayout(view, params);
            }
            applyTextScale();
        } else {
            if (awaitingBaseLayout) return;
            awaitingBaseLayout = true;
            view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    awaitingBaseLayout = false;
                    baseWidth = view.getWidth();
                    baseHeight = view.getHeight();
                    params.width = (int) (baseWidth * currentScale);
                    params.height = (int) (baseHeight * currentScale);
                    if (isAttached()) {
                        windowManager.updateViewLayout(view, params);
                    }
                    applyTextScale();
                }
            });
        }
    }

    private void applyTextScale() {
        if (baseTextPx <= 0f) {
            baseTextPx = binding.shortcutKeyText.getTextSize();
        }
        binding.shortcutKeyText.setTextSize(
                TypedValue.COMPLEX_UNIT_PX, baseTextPx * currentScale);
    }

    private void loadAlpha() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_ALPHA.getValue())) {
                int saved = reg.getInt(DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_ALPHA.getValue());
                params.alpha = saved / 100f;
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load float alpha: " + e.getMessage());
        }
    }

    // ── 位置持久化（每个快捷键按 key + 横竖屏分别保存，与悬浮球一致） ──

    private String posTagX() {
        return SHORTCUT_POS_PREFIX + "." + key + "." + orientationSuffix() + ".X";
    }

    private String posTagY() {
        return SHORTCUT_POS_PREFIX + "." + key + "." + orientationSuffix() + ".Y";
    }

    private String orientationSuffix() {
        return getOrientation() == Configuration.ORIENTATION_LANDSCAPE ? "L" : "P";
    }

    private int getOrientation() {
        return context.getResources().getConfiguration().orientation;
    }

    private void loadSavedPosition() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            if (reg.exists(posTagX()) && reg.exists(posTagY())) {
                int savedX = reg.getInt(posTagX());
                int savedY = reg.getInt(posTagY());
                Rect safe = WindowBounds.safeRect(context, view);
                params.x = clamp(savedX, safe.left, Math.max(safe.left, safe.right - getWidth()));
                params.y = clamp(savedY, safe.top, Math.max(safe.top, safe.bottom - getHeight()));
                AppLogger.d(TAG, "shortcut key " + key + " loaded pos=(" + params.x + "," + params.y + ")");
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load shortcut position: " + e.getMessage());
        }
    }

    private void savePosition() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(posTagX(), params.x);
            reg.set(posTagY(), params.y);
            reg.saveAsync();
            AppLogger.d(TAG, "shortcut key " + key + " position saved: " + params.x + "," + params.y);
        } catch (Exception e) {
            AppLogger.e(TAG, "save shortcut position failed: " + e.getMessage(), e);
        }
    }

    public String getKey() {
        return key;
    }

    public View getView() {
        return view;
    }

    public TriggerModel getTriggerModel() {
        return triggerModel;
    }

    public boolean isActive() {
        return active;
    }

    public void setTitle(String title) {
        binding.shortcutKeyText.setText(title);
    }

    public void setTriggerModel(TriggerModel triggerModel) {
        this.triggerModel = triggerModel != null ? triggerModel : TriggerModel.BUTTON;
        applyActiveStyle();
    }

    public void setHoldThresholdMs(long ms) {
        this.holdThresholdMs = ms;
    }

    public void setReleaseDelayMs(long ms) {
        this.releaseDelayMs = ms;
    }

    /** 插件 onKeyInit 指定的卡片样式：尺寸(dp，null/<=0=默认)与背景文件（可空=默认渐变）。 */
    public void setCardStyle(Integer widthDp, Integer heightDp, String normalPath, String activePath) {
        if (widthDp != null && widthDp > 0) customWidthPx = dp(widthDp);
        if (heightDp != null && heightDp > 0) customHeightPx = dp(heightDp);
        if (normalPath != null) cardBgNormal = loadCardDrawable(normalPath);
        if (activePath != null) cardBgActive = loadCardDrawable(activePath);
        applyCardSize();
        applyActiveStyle();
    }

    private void applyCardSize() {
        if (customWidthPx == null && customHeightPx == null) return;
        ViewGroup.LayoutParams lp = binding.shortcutKeyCard.getLayoutParams();
        if (lp == null) return;
        Point screen = getRealScreenSize();
        if (customWidthPx != null) lp.width = clamp(customWidthPx, dp(16), screen.x);
        if (customHeightPx != null) lp.height = clamp(customHeightPx, dp(16), screen.y);
        binding.shortcutKeyCard.setLayoutParams(lp);
    }

    /**
     * 读插件 res 背景图：.9 图 → NinePatchDrawable，普通图 → BitmapDrawable(FILL 拉伸)；
     * 解码失败返回 null（回落宿主默认背景）。
     */
    private Drawable loadCardDrawable(String path) {
        try {
            Bitmap bmp = BitmapFactory.decodeFile(path);
            if (bmp == null) {
                AppLogger.w(TAG, "card bg decode failed: " + path);
                return null;
            }
            byte[] chunk = bmp.getNinePatchChunk();
            if (chunk != null && NinePatch.isNinePatchChunk(chunk)) {
                return new NinePatchDrawable(bmp, chunk, null, path);
            }
            AppLogger.w(TAG, "card bg 不是点九图，按位图拉伸: " + path);
            BitmapDrawable d = new BitmapDrawable(context.getResources(), bmp);
            d.setGravity(Gravity.FILL);
            return d;
        } catch (Exception e) {
            AppLogger.w(TAG, "load card bg failed: " + path + ": " + e.getMessage());
            return null;
        }
    }

    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        if (active) {
            playSoundDown();
        } else {
            playSoundUp();
        }
        applyActiveStyle();
    }

    public void setOnKeyActionListener(OnKeyActionListener listener) {
        this.actionListener = listener;
    }

    public void setEditMode(boolean enabled) {
        this.editMode = enabled;
        applyEditModeBorder();
    }

    private void applyEditModeBorder() {
        if (editMode) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(color(R.color.colorSurfaceContainerHighest));
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(2), Color.parseColor("#FF6D00"),
                    dp(4), dp(4));
            binding.shortcutKeyCard.setBackground(bg);
            binding.shortcutKeyText.setTextColor(color(R.color.colorSecondary));
        } else {
            applyCardBackground();
            applyActiveStyle();
        }
    }

    private void applyCardBackground() {
        if (cardBgNormal != null) {
            cardBgNormal.clearColorFilter();
            binding.shortcutKeyCard.setBackground(cardBgNormal);
            return;
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(R.color.colorSurfaceContainerHighest));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), color(R.color.colorOutline));
        binding.shortcutKeyCard.setBackground(bg);
    }

    public void setPosition(int x, int y) {
        params.x = x;
        params.y = y;
        if (isAttached()) {
            try {
                windowManager.updateViewLayout(view, params);
            } catch (Exception e) {
                AppLogger.e(TAG, "update position failed: " + e.getMessage(), e);
            }
        }
    }

    public void attach() {
        if (isAttached()) return;
        try {
            windowManager.addView(view, params);
            applyScale();
            view.post(this::reClampPosition);
            AppLogger.d(TAG, "shortcut key attached: " + key + " at (" + params.x + "," + params.y + ")");
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        try {
            windowManager.removeView(view);
            releaseSound();
            AppLogger.d(TAG, "shortcut key detached: " + key);
        } catch (Exception e) {
            AppLogger.e(TAG, "detach failed: " + e.getMessage(), e);
        }
    }

    public boolean isAttached() {
        return view.getParent() != null;
    }

    public void bringToFront() {
        if (!isAttached()) return;
        try {
            windowManager.removeView(view);
            windowManager.addView(view, params);
            AppLogger.d(TAG, "shortcut key brought to front: " + key);
        } catch (Exception e) {
            AppLogger.e(TAG, "bringToFront failed: " + e.getMessage(), e);
        }
    }

    public int getWidth() {
        return params.width > 0 ? params.width : Math.max(view.getWidth(), 1);
    }

    public int getHeight() {
        return params.height > 0 ? params.height : Math.max(view.getHeight(), 1);
    }

    public int getRight() {
        return params.x + getWidth();
    }

    private boolean onTouch(View v, MotionEvent event) {
        if (editMode) {
            return onTouchEditMode(v, event);
        }
        return onTouchNormal(v, event);
    }

    private boolean onTouchEditMode(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = params.x;
                startY = params.y;
                dragging = false;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                return true;
            case MotionEvent.ACTION_POINTER_UP: {
                int index = event.getActionIndex();
                if (event.getPointerId(index) == activePointerId) {
                    dragging = false;
                    activePointerId = -1;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int index = event.findPointerIndex(activePointerId);
                if (index < 0) return true;
                float rawX = event.getRawX() + (event.getX(index) - event.getX(0));
                float rawY = event.getRawY() + (event.getY(index) - event.getY(0));
                float dx = rawX - downRawX;
                float dy = rawY - downRawY;
                dragging = true;
                Rect safe = WindowBounds.safeRect(context, view);
                params.x = clamp(startX + (int) dx, safe.left, Math.max(safe.left, safe.right - getWidth()));
                params.y = clamp(startY + (int) dy, safe.top, Math.max(safe.top, safe.bottom - getHeight()));
                windowManager.updateViewLayout(view, params);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) savePosition();
                dragging = false;
                activePointerId = -1;
                return true;
            default:
                return false;
        }
    }

    private boolean onTouchNormal(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = params.x;
                startY = params.y;
                dragging = false;
                clickCandidate = true;
                if (triggerModel == TriggerModel.BUTTON) {
                    setActive(true);
                    fireKeyEvent(ShortcutEventType.KEY_PRESS);
                    fireKeyEvent(ShortcutEventType.BUTTON_PRESS);
                } else if (triggerModel == TriggerModel.PRESSURE_PLATE) {
                    fireKeyEvent(ShortcutEventType.KEY_PRESS);
                    thresholdActivated = false;
                    view.removeCallbacks(thresholdRunnable);
                    view.removeCallbacks(releaseRunnable);
                    if (holdThresholdMs > 0) {
                        view.postDelayed(thresholdRunnable, holdThresholdMs);
                    } else {
                        thresholdActivated = true;
                        setActive(true);
                        fireKeyEvent(ShortcutEventType.PRESSURE_PLATE_PRESS);
                        if (actionListener != null) actionListener.onKeyAction(this);
                    }
                } else if (triggerModel == TriggerModel.SWITCH) {
                    fireKeyEvent(ShortcutEventType.KEY_PRESS);
                }
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                clickCandidate = false;
                return true;
            case MotionEvent.ACTION_POINTER_UP: {
                int index = event.getActionIndex();
                if (event.getPointerId(index) == activePointerId) {
                    if (triggerModel == TriggerModel.BUTTON) {
                        setActive(false);
                        fireKeyEvent(ShortcutEventType.KEY_RELEASE);
                        fireKeyEvent(ShortcutEventType.BUTTON_RELEASE);
                    }
                    if (triggerModel == TriggerModel.PRESSURE_PLATE) {
                        view.removeCallbacks(thresholdRunnable);
                        if (thresholdActivated) {
                            setActive(false);
                            fireKeyEvent(ShortcutEventType.KEY_RELEASE);
                            fireKeyEvent(ShortcutEventType.PRESSURE_PLATE_RELEASE);
                            if (actionListener != null) actionListener.onKeyAction(this);
                        }
                    }
                    dragging = false;
                    clickCandidate = false;
                    activePointerId = -1;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int index = event.findPointerIndex(activePointerId);
                if (index < 0) {
                    dragging = false;
                    clickCandidate = false;
                    return true;
                }
                float rawX = event.getRawX() + (event.getX(index) - event.getX(0));
                float rawY = event.getRawY() + (event.getY(index) - event.getY(0));
                float dx = rawX - downRawX;
                float dy = rawY - downRawY;
                if (!dragging && (Math.abs(dx) > dragSlop || Math.abs(dy) > dragSlop)) {
                    dragging = true;
                    clickCandidate = false;
                    if (triggerModel == TriggerModel.BUTTON) {
                        setActive(false);
                        fireKeyEvent(ShortcutEventType.KEY_RELEASE);
                        fireKeyEvent(ShortcutEventType.BUTTON_RELEASE);
                    }
                    if (triggerModel == TriggerModel.PRESSURE_PLATE) {
                        view.removeCallbacks(thresholdRunnable);
                        if (thresholdActivated) {
                            view.removeCallbacks(releaseRunnable);
                            setActive(false);
                            fireKeyEvent(ShortcutEventType.KEY_RELEASE);
                            fireKeyEvent(ShortcutEventType.PRESSURE_PLATE_RELEASE);
                            if (actionListener != null) actionListener.onKeyAction(this);
                        }
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (triggerModel == TriggerModel.BUTTON) {
                    setActive(false);
                    fireKeyEvent(ShortcutEventType.KEY_RELEASE);
                    fireKeyEvent(ShortcutEventType.BUTTON_RELEASE);
                }
                if (triggerModel == TriggerModel.PRESSURE_PLATE) {
                    view.removeCallbacks(thresholdRunnable);
                    if (thresholdActivated) {
                        if (releaseDelayMs > 0) {
                            view.removeCallbacks(releaseRunnable);
                            view.postDelayed(releaseRunnable, releaseDelayMs);
                        } else {
                            setActive(false);
                            fireKeyEvent(ShortcutEventType.KEY_RELEASE);
                            fireKeyEvent(ShortcutEventType.PRESSURE_PLATE_RELEASE);
                            if (actionListener != null) actionListener.onKeyAction(this);
                        }
                    } else {
                        fireKeyEvent(ShortcutEventType.KEY_RELEASE);
                    }
                }
                if (clickCandidate) {
                    if (triggerModel == TriggerModel.SWITCH) {
                        boolean wasActive = active;
                        setActive(!active);
                        fireKeyEvent(ShortcutEventType.KEY_RELEASE);
                        if (active && !wasActive) {
                            fireKeyEvent(ShortcutEventType.KEY_ON);
                        } else if (!active && wasActive) {
                            fireKeyEvent(ShortcutEventType.KEY_OFF);
                        }
                    }
                    if (triggerModel == TriggerModel.BUTTON || triggerModel == TriggerModel.SWITCH) {
                        if (actionListener != null) actionListener.onKeyAction(this);
                    }
                }
                dragging = false;
                clickCandidate = false;
                activePointerId = -1;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (triggerModel == TriggerModel.BUTTON) {
                    setActive(false);
                    fireKeyEvent(ShortcutEventType.KEY_RELEASE);
                    fireKeyEvent(ShortcutEventType.BUTTON_RELEASE);
                }
                if (triggerModel == TriggerModel.PRESSURE_PLATE) {
                    view.removeCallbacks(thresholdRunnable);
                    view.removeCallbacks(releaseRunnable);
                    if (thresholdActivated) {
                        thresholdActivated = false;
                        setActive(false);
                        fireKeyEvent(ShortcutEventType.KEY_RELEASE);
                        fireKeyEvent(ShortcutEventType.PRESSURE_PLATE_RELEASE);
                        if (actionListener != null) actionListener.onKeyAction(this);
                    } else {
                        fireKeyEvent(ShortcutEventType.KEY_RELEASE);
                    }
                }
                if (triggerModel == TriggerModel.SWITCH) {
                    fireKeyEvent(ShortcutEventType.KEY_RELEASE);
                }
                dragging = false;
                clickCandidate = false;
                activePointerId = -1;
                return true;
            default:
                return false;
        }
    }

    private void fireKeyEvent(ShortcutEventType eventType) {
        if (actionListener != null) {
            actionListener.onKeyEvent(this, eventType);
        }
    }

    private void fireThresholdActivated() {
        thresholdActivated = true;
        setActive(true);
        fireKeyEvent(ShortcutEventType.PRESSURE_PLATE_PRESS);
        if (actionListener != null) actionListener.onKeyAction(this);
    }

    private void fireReleaseDelayed() {
        thresholdActivated = false;
        setActive(false);
        fireKeyEvent(ShortcutEventType.KEY_RELEASE);
        fireKeyEvent(ShortcutEventType.PRESSURE_PLATE_RELEASE);
        if (actionListener != null) actionListener.onKeyAction(this);
    }

    private Point getRealScreenSize() {
        Point p = new Point();
        try {
            windowManager.getDefaultDisplay().getRealSize(p);
        } catch (Exception e) {
            AppLogger.w(TAG, "getRealScreenSize failed: " + e.getMessage());
        }
        if (p.x <= 0 || p.y <= 0) {
            p.set(1080, 2340);
        }
        return p;
    }

    private int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(value, max));
    }

    private void applyActiveStyle() {
        if (active) {
            if (cardBgActive != null) {
                cardBgActive.clearColorFilter();
                binding.shortcutKeyCard.setBackground(cardBgActive);
                binding.shortcutKeyText.setTextColor(color(R.color.colorOnPrimary));
                return;
            }
            if (cardBgNormal != null) {
                // 只给了常态图：按下态叠主题色滤镜做反馈
                cardBgNormal.setColorFilter(color(R.color.colorPrimary), PorterDuff.Mode.SRC_ATOP);
                binding.shortcutKeyCard.setBackground(cardBgNormal);
                binding.shortcutKeyText.setTextColor(color(R.color.colorOnPrimary));
                return;
            }
        } else if (cardBgNormal != null) {
            cardBgNormal.clearColorFilter();
            binding.shortcutKeyCard.setBackground(cardBgNormal);
            binding.shortcutKeyText.setTextColor(color(R.color.colorSecondary));
            return;
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        if (active) {
            bg.setColor(color(R.color.colorPrimary));
            bg.setStroke(dp(1), color(R.color.colorPrimary));
            binding.shortcutKeyText.setTextColor(color(R.color.colorOnPrimary));
        } else {
            bg.setColor(color(R.color.colorSurfaceContainerHighest));
            bg.setStroke(dp(1), color(R.color.colorOutline));
            binding.shortcutKeyText.setTextColor(color(R.color.colorSecondary));
        }
        binding.shortcutKeyCard.setBackground(bg);
    }

    private int color(int res) {
        return ContextCompat.getColor(context, res);
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
