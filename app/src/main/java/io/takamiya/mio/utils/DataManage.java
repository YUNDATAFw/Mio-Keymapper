package io.takamiya.mio.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataManage {
    public enum TagType { STRING, INTEGER, BOOLEAN }

    /** 音效槽位"无"（静音）哨兵值：存于 SHORTCUT_SOUND_*_URI，表示不播放任何音效。 */
    public static final String SOUND_NONE = "none";

    /** 建筑导入执行时序默认值（导入设置页可编辑，Registry 无值时回退）。 */
    public static final long DEFAULT_DELAY_STEP_MS = 400;
    public static final long DEFAULT_DELAY_PANEL_OPEN_MS = 800;
    public static final long DEFAULT_DELAY_LIST_OPEN_MS = 700;
    public static final long DEFAULT_DELAY_EDIT_FOCUS_MS = 900;
    public static final long DEFAULT_PRESS_DURATION_MS = 300;

    public enum Tag {
        USERDATA("", TagType.STRING, false),

        TOKEN_TOKEN(SecureStrings.dataManageValue(0), TagType.STRING, true),
        TOKEN_SUB(SecureStrings.dataManageValue(1), TagType.INTEGER, false),
        TOKEN_USERNAME(SecureStrings.dataManageValue(2), TagType.STRING, false),
        TOKEN_EMAIL(SecureStrings.dataManageValue(3), TagType.STRING, false),
        TOKEN_IAT(SecureStrings.dataManageValue(4), TagType.INTEGER, false),
        TOKEN_EXP(SecureStrings.dataManageValue(5), TagType.INTEGER, false),

        APP_TOKEN_TOKEN(SecureStrings.dataManageValue(6), TagType.STRING, true),
        APP_TOKEN_SUB(SecureStrings.dataManageValue(7), TagType.INTEGER, false),
        APP_TOKEN_APP_ID(SecureStrings.dataManageValue(8), TagType.STRING, false),
        APP_TOKEN_APP_NAME(SecureStrings.dataManageValue(9), TagType.STRING, false),
        APP_TOKEN_IAT(SecureStrings.dataManageValue(10), TagType.INTEGER, false),
        APP_TOKEN_EXP(SecureStrings.dataManageValue(11), TagType.INTEGER, false),

        USER_ACCOUNT(SecureStrings.dataManageValue(12), TagType.STRING, false),
        USER_PASSWORD(SecureStrings.dataManageValue(13), TagType.STRING, true),

        APP_CONFIG_INPUT_MODEL(SecureStrings.dataManageValue(14), TagType.INTEGER, false),
        APP_CONFIG_DEVELOPER_MODE(SecureStrings.dataManageValue(15), TagType.BOOLEAN, false),
        APP_CONFIG_DOWNGRADE_INSTALLATION(SecureStrings.dataManageValue(16), TagType.BOOLEAN, false),

        APP_CONFIG_FLOAT_BALL_X_PORTRAIT(SecureStrings.dataManageValue(17), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_BALL_Y_PORTRAIT(SecureStrings.dataManageValue(18), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_BALL_X_LANDSCAPE(SecureStrings.dataManageValue(19), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_BALL_Y_LANDSCAPE(SecureStrings.dataManageValue(20), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_ORIENTATION(SecureStrings.dataManageValue(21), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_TOAST_X(SecureStrings.dataManageValue(22), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_TOAST_Y(SecureStrings.dataManageValue(23), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_TOAST_BOLD(SecureStrings.dataManageValue(24), TagType.BOOLEAN, false),
        APP_CONFIG_FLOAT_TOAST_COLOR(SecureStrings.dataManageValue(25), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_WATERMARK_ENABLED(SecureStrings.dataManageValue(26), TagType.BOOLEAN, false),
        APP_CONFIG_FLOAT_STATUS_BAR(SecureStrings.dataManageValue(27), TagType.BOOLEAN, false),
        APP_CONFIG_FLOAT_FEATURE_LIST(SecureStrings.dataManageValue(28), TagType.BOOLEAN, false),
        APP_CONFIG_USER_AGREEMENT_ACCEPTED(SecureStrings.dataManageValue(29), TagType.BOOLEAN, false),
        APP_CONFIG_FLOAT_SIZE(SecureStrings.path(62), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_STATUS_BAR_Y(SecureStrings.path(63), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_STATUS_BAR_ALPHA(SecureStrings.path(64), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_BALL_SNAP_TO_EDGE(SecureStrings.path(65), TagType.BOOLEAN, false),
        APP_CONFIG_THEME_MODE(SecureStrings.path(66), TagType.INTEGER, false),
        APP_CONFIG_AUTO_OPEN_GAME(SecureStrings.path(67), TagType.BOOLEAN, false),
        APP_CONFIG_AUTO_OPEN_GAME_PACKAGE(SecureStrings.path(68), TagType.STRING, false),
        APP_CONFIG_PANEL_PORTRAIT(SecureStrings.path(69), TagType.BOOLEAN, false),
        APP_CONFIG_FLOAT_FEATURE_LIST_STYLE(SecureStrings.path(70), TagType.INTEGER, false),
        APP_CONFIG_USE_NEW_TOAST(SecureStrings.path(71), TagType.BOOLEAN, false),
        APP_CONFIG_USE_NEW_FEATURE_LIST(SecureStrings.path(72), TagType.BOOLEAN, false),
        APP_CONFIG_NEW_TOAST_COLOR(SecureStrings.path(73), TagType.INTEGER, false),
        APP_CONFIG_FEATURE_LIST_FONT_SIZE(SecureStrings.path(74), TagType.INTEGER, false),
        APP_CONFIG_FEATURE_LIST_RIGHT_OFFSET(SecureStrings.path(75), TagType.INTEGER, false),
        APP_CONFIG_FEATURE_LIST_GLOW(SecureStrings.path(76), TagType.BOOLEAN, false),
        APP_CONFIG_FEATURE_LIST_WATERMARK(SecureStrings.path(77), TagType.BOOLEAN, false),
        APP_CONFIG_FEATURE_LIST_WATERMARK_TEXT(SecureStrings.path(78), TagType.STRING, false),
        APP_CONFIG_FEATURE_LIST_GLOW_STRENGTH(SecureStrings.path(79), TagType.INTEGER, false),
        APP_CONFIG_NEW_TOAST_FONT_SIZE(SecureStrings.path(80), TagType.INTEGER, false),
        APP_CONFIG_NEW_TOAST_POSITION(SecureStrings.path(81), TagType.INTEGER, false),
        APP_CONFIG_NEW_TOAST_GRADIENT(SecureStrings.path(82), TagType.BOOLEAN, false),
        APP_CONFIG_ACTION_TOAST_ENABLED(SecureStrings.path(83), TagType.BOOLEAN, false),
        APP_CONFIG_SHORTCUT_SOUND_ON_URI(SecureStrings.path(84), TagType.STRING, false),
        APP_CONFIG_SHORTCUT_SOUND_OFF_URI(SecureStrings.path(85), TagType.STRING, false),
        APP_CONFIG_DEBUG_FULL_INIT(SecureStrings.path(86), TagType.BOOLEAN, false),
        APP_CONFIG_FLOAT_CPS_INDICATOR(SecureStrings.path(87), TagType.BOOLEAN, false),
        APP_CONFIG_FLOAT_CROSSHAIR_ENABLED(SecureStrings.path(88), TagType.BOOLEAN, false),
        APP_CONFIG_FLOAT_CROSSHAIR_X(SecureStrings.path(89), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_CROSSHAIR_Y(SecureStrings.path(90), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_CROSSHAIR_SIZE(SecureStrings.path(91), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_CROSSHAIR_ALPHA(SecureStrings.path(92), TagType.INTEGER, false),
        APP_CONFIG_FLOAT_CROSSHAIR_ICON_URI(SecureStrings.path(93), TagType.STRING, false),
        APP_CONFIG_FLOAT_NEW_STATUS_BAR(SecureStrings.path(94), TagType.BOOLEAN, false),
        APP_CONFIG_FLOAT_GYRO_AIM_ENABLED(SecureStrings.path(95), TagType.BOOLEAN, false),
        APP_CONFIG_WS_PORT(SecureStrings.path(96), TagType.INTEGER, false),
        APP_CONFIG_WS_BLACKLIST(SecureStrings.path(97), TagType.STRING, false),
        APP_CONFIG_WS_CAPTCHA(SecureStrings.path(98), TagType.BOOLEAN, false),

        // 自定义音效库：换行分隔的 URI 列表（明文 key，非敏感，不占用 SecureStrings 表）
        APP_CONFIG_SOUND_LIBRARY("app_config_sound_library", TagType.STRING, false),

        // 自定义准星图标库：换行分隔的 URI 列表（明文 key，非敏感，不占用 SecureStrings 表）
        APP_CONFIG_CROSSHAIR_LIBRARY("app_config_crosshair_library", TagType.STRING, false),

        // 导入设置：各菜单点击坐标（明文 key，不占用 SecureStrings 表）
        IMPORT_CLICK_BLOCK_TYPE_X("import_click_block_type_x", TagType.STRING, false),
        IMPORT_CLICK_BLOCK_TYPE_Y("import_click_block_type_y", TagType.STRING, false),
        IMPORT_CLICK_REPEAT_X("import_click_repeat_x", TagType.STRING, false),
        IMPORT_CLICK_REPEAT_Y("import_click_repeat_y", TagType.STRING, false),
        IMPORT_CLICK_CHAIN_X("import_click_chain_x", TagType.STRING, false),
        IMPORT_CLICK_CHAIN_Y("import_click_chain_y", TagType.STRING, false),
        IMPORT_CLICK_ACTIVATION_X("import_click_activation_x", TagType.STRING, false),
        IMPORT_CLICK_ACTIVATION_Y("import_click_activation_y", TagType.STRING, false),
        IMPORT_CLICK_KEEP_ON_X("import_click_keep_on_x", TagType.STRING, false),
        IMPORT_CLICK_KEEP_ON_Y("import_click_keep_on_y", TagType.STRING, false),
        IMPORT_CLICK_COMMAND_EDIT_X("import_click_command_edit_x", TagType.STRING, false),
        IMPORT_CLICK_COMMAND_EDIT_Y("import_click_command_edit_y", TagType.STRING, false),
        IMPORT_CLICK_CLOSE_PANEL_X("import_click_close_panel_x", TagType.STRING, false),

        IMPORT_CLICK_CLOSE_PANEL_Y("import_click_close_panel_y", TagType.STRING, false),

        IMPORT_DELAY_STEP("import_delay_step_ms", TagType.STRING, false),

        IMPORT_DELAY_PANEL_OPEN("import_delay_panel_open_ms", TagType.STRING, false),

        IMPORT_DELAY_LIST_OPEN("import_delay_list_open_ms", TagType.STRING, false),

        IMPORT_DELAY_EDIT_FOCUS("import_delay_edit_focus_ms", TagType.STRING, false),

        // 外接键鼠监听：是否随悬浮窗服务启动 getevent 监听（明文 key，非敏感）
        APP_CONFIG_INPUT_MONITOR_ENABLED("app_config_input_monitor_enabled", TagType.BOOLEAN, false),

        IMPORT_PRESS_DURATION("import_press_duration_ms", TagType.STRING, false);
        private static final Map<String, Tag> MAP = Arrays.stream(values())
                .collect(Collectors.toMap(s -> s.value, s -> s));

        private final String value;
        private final TagType type;
        private final boolean sensitive;

        Tag(String value, TagType type, boolean sensitive) {
            this.value = value;
            this.type = type;
            this.sensitive = sensitive;
        }

        public static Tag fromValue(String value) {
            Tag tag = MAP.get(value);
            if (tag == null) {
                throw new IllegalArgumentException("Unknown:" + value);
            }
            return tag;
        }

        public static List<Tag> getAllTags() {
            return Collections.unmodifiableList(Arrays.asList(values()));
        }

        public String getValue() {
            return value;
        }

        public TagType getType() {
            return type;
        }

        public boolean isSensitive() {
            return sensitive;
        }
    }
}
