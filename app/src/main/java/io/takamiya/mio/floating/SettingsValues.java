package io.takamiya.mio.floating;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.exceptions.RegistryException;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

/**
 * 设置面板共享配置状态。
 * 主设置面板与二级面板共用同一份值；load() 从 Registry 读取,
 * 读取后由各自的 UI 绑定,值变更继续通过 Listener 回调由 FloatingWindowService 持久化。
 */
public class SettingsValues {

    private static final String TAG = "SettingsValues";

    private static volatile SettingsValues instance;

    public boolean watermarkEnabled = true;
    public boolean statusBarEnabled = true;
    public boolean featureListEnabled = true;
    public int floatSizePercent = 100;
    public int statusBarY = 30;
    public int statusBarAlpha = 45;
    // 新增设置项
    public int featureListFontSize = 24;
    public int featureListRightOffset = 10;
    public boolean featureListGlow = true;
    public int featureListGlowStrength = 24;
    public boolean featureListWatermark = false;
    public int newToastFontSize = 20;
    public boolean newToastGradient = false;
    public boolean actionToastEnabled = true;
    public boolean cpsIndicatorEnabled = false;
    public boolean crosshairEnabled = false;
    public boolean newStatusBarEnabled = false;
    public int crosshairSizeDp = 40;
    public int crosshairAlpha = 100;
    public boolean allHidden = false;
    public boolean gyroAimEnabled = false;

    public static SettingsValues instance() {
        if (instance == null) {
            synchronized (SettingsValues.class) {
                if (instance == null) {
                    instance = new SettingsValues();
                }
            }
        }
        return instance;
    }

    public void load(Registry reg) {
        if (reg == null) return;
        watermarkEnabled = loadBoolean(
                reg, DataManage.Tag.APP_CONFIG_FLOAT_WATERMARK_ENABLED.getValue(), true, "watermark enabled");
        statusBarEnabled = loadBoolean(
                reg, DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR.getValue(), true, "status bar enabled");
        featureListEnabled = loadBoolean(
                reg, DataManage.Tag.APP_CONFIG_FLOAT_FEATURE_LIST.getValue(), true, "feature list enabled");
        floatSizePercent = loadInt(
                reg, DataManage.Tag.APP_CONFIG_FLOAT_SIZE.getValue(), 100, "float size");
        statusBarY = loadInt(
                reg, DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_Y.getValue(), 30, "status bar y");
        statusBarAlpha = loadInt(
                reg, DataManage.Tag.APP_CONFIG_FLOAT_STATUS_BAR_ALPHA.getValue(), 45, "status bar alpha");
        featureListFontSize = loadInt(
                reg, DataManage.Tag.APP_CONFIG_FEATURE_LIST_FONT_SIZE.getValue(), 24, "feature list font size");
        featureListRightOffset = loadInt(
                reg, DataManage.Tag.APP_CONFIG_FEATURE_LIST_RIGHT_OFFSET.getValue(), 10, "feature list right offset");
        featureListGlow = loadBoolean(
                reg, DataManage.Tag.APP_CONFIG_FEATURE_LIST_GLOW.getValue(), true, "feature list glow");
        featureListGlowStrength = loadInt(
                reg, DataManage.Tag.APP_CONFIG_FEATURE_LIST_GLOW_STRENGTH.getValue(), 24, "feature list glow strength");
        featureListWatermark = loadBoolean(
                reg, DataManage.Tag.APP_CONFIG_FEATURE_LIST_WATERMARK.getValue(), false, "feature list watermark");
        newToastFontSize = loadInt(
                reg, DataManage.Tag.APP_CONFIG_NEW_TOAST_FONT_SIZE.getValue(), 20, "new toast font size");
        newToastGradient = loadBoolean(
                reg, DataManage.Tag.APP_CONFIG_NEW_TOAST_GRADIENT.getValue(), false, "new toast gradient");
        actionToastEnabled = loadBoolean(
                reg, DataManage.Tag.APP_CONFIG_ACTION_TOAST_ENABLED.getValue(), true, "action toast enabled");
        cpsIndicatorEnabled = loadBoolean(
                reg, DataManage.Tag.APP_CONFIG_FLOAT_CPS_INDICATOR.getValue(), false, "cps indicator enabled");
        crosshairEnabled = loadBoolean(
                reg, DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_ENABLED.getValue(), false, "crosshair enabled");
        crosshairSizeDp = loadInt(
                reg, DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_SIZE.getValue(), 40, "crosshair size");
        crosshairAlpha = loadInt(
                reg, DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_ALPHA.getValue(), 100, "crosshair alpha");
        newStatusBarEnabled = loadBoolean(
                reg, DataManage.Tag.APP_CONFIG_FLOAT_NEW_STATUS_BAR.getValue(), false, "new status bar enabled");
        gyroAimEnabled = loadBoolean(
                reg, DataManage.Tag.APP_CONFIG_FLOAT_GYRO_AIM_ENABLED.getValue(), false, "gyro aim enabled");
        allHidden = false;
    }

    private static boolean loadBoolean(Registry reg, String tag, boolean def, String name) {
        try {
            if (reg.exists(tag)) return reg.getBoolean(tag);
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load " + name + ": " + e.getMessage());
        }
        return def;
    }

    private static int loadInt(Registry reg, String tag, int def, String name) {
        try {
            if (reg.exists(tag)) return reg.getInt(tag);
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load " + name + ": " + e.getMessage());
        }
        return def;
    }

    public void reload() {
        load(RegistryUtils.get());
    }
}