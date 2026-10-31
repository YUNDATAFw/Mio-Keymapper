package io.takamiya.mio.dsl.bridge;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cn.fsy.plugin.sdl.library.BridgeHandler;
import cn.fsy.plugin.sdl.library.SdlException;
import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.pluginconfig.PluginConfigScheme;

/**
 * MioConfig 命名空间桥接实现：插件配置方案声明（一行一项，风格对齐 onKeyInit）。
 *
 * <p>插件在 config 模块导出的 {@code schema()} 内逐行调用 {@code MioConfig.onSwitch(...)}
 * 等函数声明配置项；宿主在 {@link #begin(String)} / {@link #end()} 窗口内收集为
 * {@link PluginConfigScheme}。窗口外调用（如 loading/keyInit 里）抛 INVALID_ARGUMENT。</p>
 *
 * <p>参数约定与其它桥一致：缺参按 null 传递（尾部参数可省略），必填项缺失/为空抛
 * INVALID_ARGUMENT，key 重复返回 false 并记日志。</p>
 */
public class MioConfigBridge {

    private static final String TAG = "MioConfigBridge";

    /** 未调用 onSection 时首个配置项落入的默认分组。 */
    private static final String DEFAULT_SECTION = "配置";

    /** text 字段允许的 inputType 取值（与 PluginConfigAdapter 的分发一致）。 */
    private static boolean isInputType(@Nullable String t) {
        return t == null || t.isEmpty() || "int".equals(t)
                || "decimal".equals(t) || "digits".equals(t) || "english".equals(t);
    }

    private static final class Declaration {
        final String packageName;
        final PluginConfigScheme.Builder builder = PluginConfigScheme.builder();
        final Set<String> keys = new LinkedHashSet<>();
        PluginConfigScheme.SectionBuilder current;
        int count;

        Declaration(@Nullable String packageName) {
            this.packageName = packageName;
        }
    }

    /** 当前声明窗口（仅 resolve 所在线程可见）。 */
    private final ThreadLocal<Declaration> current = new ThreadLocal<>();

    // ==================== 声明窗口（由 PluginConfigDeclaration 调用） ====================

    /** 打开声明窗口：调用插件 config.schema 前调用。 */
    public void begin(@Nullable String packageName) {
        current.set(new Declaration(packageName));
    }

    /**
     * 关闭声明窗口并返回收集到的方案。
     *
     * @return 插件未声明任何配置项（或未开窗）时返回 null，由调用方回退旧式 schema 返回值
     */
    @Nullable
    public PluginConfigScheme end() {
        Declaration d = current.get();
        current.remove();
        if (d == null || d.count == 0) return null;
        return d.builder.build();
    }

    // ==================== 声明函数 ====================

    /** onSection(title) - 开始新分组；首个配置项前不调用则落入默认分组。 */
    public BridgeHandler onSection() {
        return args -> {
            Declaration d = require("onSection");
            String title = str(args, 0, "onSection");
            d.current = d.builder.section(title);
            return true;
        };
    }

    /** onSwitch(key, title, subtitle, value) - 开关。 */
    public BridgeHandler onSwitch() {
        return args -> {
            Declaration d = require("onSwitch");
            String key = str(args, 0, "onSwitch");
            String title = str(args, 1, "onSwitch");
            PluginConfigScheme.SectionBuilder s = open(d, "onSwitch", key, title);
            if (s == null) return false;
            s.addSwitch(key, title, optStr(args, 2), bool(args, 3, false));
            d.count++;
            return true;
        };
    }

    /** onCheckbox(key, title, subtitle, value) - 复选框。 */
    public BridgeHandler onCheckbox() {
        return args -> {
            Declaration d = require("onCheckbox");
            String key = str(args, 0, "onCheckbox");
            String title = str(args, 1, "onCheckbox");
            PluginConfigScheme.SectionBuilder s = open(d, "onCheckbox", key, title);
            if (s == null) return false;
            s.addCheckbox(key, title, optStr(args, 2), bool(args, 3, false));
            d.count++;
            return true;
        };
    }

    /**
     * onText(key, title, subtitle, value, hint, inputType, min, max) - 文本输入。
     * inputType 取 ""（任意）/ "int" / "decimal" / "digits" / "english"，尾部参数可省略。
     */
    public BridgeHandler onText() {
        return args -> {
            Declaration d = require("onText");
            String key = str(args, 0, "onText");
            String title = str(args, 1, "onText");
            String inputType = optStr(args, 5);
            if (inputType == null) inputType = "";
            if (!isInputType(inputType)) {
                throw new SdlException(SdlException.ErrorCode.INVALID_ARGUMENT,
                        "onText: 未知 inputType " + inputType
                                + "（可选 int/decimal/digits/english）");
            }
            PluginConfigScheme.SectionBuilder s = open(d, "onText", key, title);
            if (s == null) return false;
            String value = args.length > 3 && args[3] != null ? args[3].toString() : "";
            s.addTextField(key, title, optStr(args, 2), value, optStr(args, 4));
            PluginConfigScheme.Item item = s.lastAdded();
            if (item != null) {
                item.inputType = inputType;
                Integer min = optInt(args, 6);
                Integer max = optInt(args, 7);
                if (min != null) item.min = min;
                if (max != null) item.max = max;
            }
            d.count++;
            return true;
        };
    }

    /** onRadio(key, title, subtitle, options, value) - 单选；value 为默认选中项（可省略）。 */
    public BridgeHandler onRadio() {
        return args -> {
            Declaration d = require("onRadio");
            String key = str(args, 0, "onRadio");
            String title = str(args, 1, "onRadio");
            List<String> labels = strings(args, 3, "onRadio");
            if (labels.isEmpty()) {
                throw new SdlException(SdlException.ErrorCode.INVALID_ARGUMENT,
                        "onRadio: 选项列表不能为空");
            }
            PluginConfigScheme.SectionBuilder s = open(d, "onRadio", key, title);
            if (s == null) return false;
            String selected = optStr(args, 4);
            int defIndex = selected == null ? -1 : labels.indexOf(selected);
            s.addRadio(key, title, optStr(args, 2), defIndex, labels.toArray(new String[0]));
            d.count++;
            return true;
        };
    }

    /** onChip(key, title, subtitle, options, selected) - 多选；selected 为默认选中项列表（可省略）。 */
    public BridgeHandler onChip() {
        return args -> {
            Declaration d = require("onChip");
            String key = str(args, 0, "onChip");
            String title = str(args, 1, "onChip");
            List<String> labels = strings(args, 3, "onChip");
            if (labels.isEmpty()) {
                throw new SdlException(SdlException.ErrorCode.INVALID_ARGUMENT,
                        "onChip: 选项列表不能为空");
            }
            PluginConfigScheme.SectionBuilder s = open(d, "onChip", key, title);
            if (s == null) return false;
            List<String> selected = strings(args, 4, "onChip");
            s.addChip(key, title, optStr(args, 2),
                    labels.toArray(new String[0]), selected.toArray(new String[0]));
            d.count++;
            return true;
        };
    }

    /** onProgress(key, title, subtitle, value, max, text) - 进度环（尾部参数可省略）。 */
    public BridgeHandler onProgress() {
        return args -> {
            Declaration d = require("onProgress");
            String key = str(args, 0, "onProgress");
            String title = str(args, 1, "onProgress");
            PluginConfigScheme.SectionBuilder s = open(d, "onProgress", key, title);
            if (s == null) return false;
            int value = optIntOrDefault(args, 3, 0);
            int max = optIntOrDefault(args, 4, 100);
            s.addProgress(key, title, optStr(args, 2), value, max, optStr(args, 5));
            d.count++;
            return true;
        };
    }

    /** onTextView(key, title, subtitle, body) - 纯文本说明（不可编辑）。 */
    public BridgeHandler onTextView() {
        return args -> {
            Declaration d = require("onTextView");
            String key = str(args, 0, "onTextView");
            String title = str(args, 1, "onTextView");
            PluginConfigScheme.SectionBuilder s = open(d, "onTextView", key, title);
            if (s == null) return false;
            s.addTextView(key, title, optStr(args, 2), optStr(args, 3));
            d.count++;
            return true;
        };
    }

    /** onButton(key, title, subtitle, label) - 按钮；点击回调插件导出的 config.onButton(key)。 */
    public BridgeHandler onButton() {
        return args -> {
            Declaration d = require("onButton");
            String key = str(args, 0, "onButton");
            String title = str(args, 1, "onButton");
            PluginConfigScheme.SectionBuilder s = open(d, "onButton", key, title);
            if (s == null) return false;
            s.addButton(key, title, optStr(args, 2), optStr(args, 3), null);
            d.count++;
            return true;
        };
    }

    // ==================== 内部工具 ====================

    private Declaration require(String fn) {
        Declaration d = current.get();
        if (d == null) {
            throw new SdlException(SdlException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 必须在插件 config 模块的 schema() 中调用");
        }
        return d;
    }

    /** 校验 key/title 并返回当前分组；key 重复返回 null（调用方返回 false）。 */
    @Nullable
    private PluginConfigScheme.SectionBuilder open(Declaration d, String fn,
                                                   String key, String title) {
        if (key.isEmpty()) {
            throw new SdlException(SdlException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": key 不能为空");
        }
        if (title.isEmpty()) {
            throw new SdlException(SdlException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": title 不能为空");
        }
        if (!d.keys.add(key)) {
            AppLogger.w(TAG, fn + ": 重复的 key=" + key + "，已忽略");
            return null;
        }
        if (d.current == null) {
            d.current = d.builder.section(DEFAULT_SECTION);
        }
        return d.current;
    }

    private static String str(Object[] args, int index, String fn) {
        if (args == null || index >= args.length || args[index] == null) {
            throw new SdlException(SdlException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 缺少第 " + (index + 1) + " 个参数");
        }
        String s = args[index].toString();
        if (s.isEmpty()) {
            throw new SdlException(SdlException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 第 " + (index + 1) + " 个参数不能为空");
        }
        return s;
    }

    /** 可选字符串参数：缺失/null/空串返回 null，去除首尾空白。 */
    @Nullable
    private static String optStr(Object[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) return null;
        String s = args[index].toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean bool(Object[] args, int index, boolean def) {
        if (args == null || index >= args.length || args[index] == null) return def;
        Object o = args[index];
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).intValue() != 0;
        if (o instanceof String) return Boolean.parseBoolean((String) o);
        return def;
    }

    @Nullable
    private static Integer optInt(Object[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) return null;
        Object o = args[index];
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            throw new SdlException(SdlException.ErrorCode.INVALID_ARGUMENT,
                    "第 " + (index + 1) + " 个参数必须为整数");
        }
    }

    private static int optIntOrDefault(Object[] args, int index, int def) {
        Integer v = optInt(args, index);
        return v == null ? def : v;
    }

    /** 列表参数：支持 [String] 字面量、逗号分隔字符串；缺失返回空列表。 */
    private static List<String> strings(Object[] args, int index, String fn) {
        List<String> result = new ArrayList<>();
        if (args == null || index >= args.length || args[index] == null) return result;
        Object o = args[index];
        if (o instanceof List) {
            for (Object item : (List<?>) o) {
                String t = String.valueOf(item).trim();
                if (!t.isEmpty()) result.add(t);
            }
            return result;
        }
        if (o instanceof Object[]) {
            for (Object item : (Object[]) o) {
                String t = String.valueOf(item).trim();
                if (!t.isEmpty()) result.add(t);
            }
            return result;
        }
        for (String part : o.toString().split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) result.add(t);
        }
        return result;
    }
}
