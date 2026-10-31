package io.takamiya.mio.pluginconfig;

import android.content.Context;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.fsy.plugin.sdl.library.internal.manage.open.OpenManager;
import cn.fsy.plugin.sdl.library.internal.registry.PandaRegistryFactory;
import cn.fsy.plugin.sdl.library.internal.registry.RegistryManagerHolder;
import cn.fsy.plugin.sdl.library.internal.registry.RegistryStore;
import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.app.Mio;
import io.takamiya.mio.dsl.DslPluginHandle;
import io.takamiya.mio.dsl.DslPluginManager;
import io.takamiya.mio.dsl.HostDataRepair;
import io.takamiya.mio.dsl.MioBridgeRegistry;
import io.takamiya.mio.dsl.bridge.MioConfigBridge;

/**
 * 将插件 config.dsl 动态声明的配置方案解析为 App 内的 {@link PluginConfigScheme}。
 *
 * <p>两种声明方式（推荐前者，一行一项、参数校验抛错，风格对齐快捷键 onKeyInit）：</p>
 * <ol>
 *   <li><b>MioConfig 桥</b>：schema() 内逐行调用 {@code MioConfig.onSwitch(...)} 等，
 *       宿主开窗收集（见 {@link io.takamiya.mio.dsl.bridge.MioConfigBridge}）。</li>
 *   <li><b>旧式返回值</b>：schema() 返回嵌套 {@code List<Map>}，由本类解析（兼容保留）。</li>
 * </ol>
 *
 * <p>模块名由 config.json 的 {@code plugin.service[]} 中 {@code tag=CONFIG} 的服务声明
 * （见 {@link #configModule(String)}）；未声明 CONFIG 视为开发者不需要配置页，
 * 宿主不调用 schema()/values()（也就不会产生「函数不存在」错误）。</p>
 */
public final class PluginConfigDeclaration {

    private static final String TAG = "PluginConfigDeclaration";
    private static final String CONFIG_TAG = "CONFIG";

    private PluginConfigDeclaration() {}

    /**
     * 读取 config.json 中 {@code tag=CONFIG} 的服务声明，返回 config.dsl 的模块名（叶文件名）。
     *
     * <p>未声明 CONFIG（开发者不需要配置页）或读取失败返回 null。</p>
     */
    @Nullable
    public static String configModule(@Nullable String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        try {
            Context context = Mio.getAppContext();
            if (context == null) return null;
            File pluginDir = OpenManager.pluginDir(context.getFilesDir(), packageName);
            File configFile = new File(pluginDir, "config.json");
            if (!configFile.isFile()) return null;
            String json = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(json);
            JSONObject plugin = root.optJSONObject("plugin");
            JSONArray services = plugin == null ? null : plugin.optJSONArray("service");
            if (services == null) return null;
            for (int i = 0; i < services.length(); i++) {
                JSONObject svc = services.optJSONObject(i);
                if (svc == null || !CONFIG_TAG.equals(svc.optString("tag", ""))) continue;
                String name = svc.optString("name", "").trim();
                if (name.isEmpty()) continue;
                int dot = name.lastIndexOf('.');
                return dot >= 0 ? name.substring(dot + 1) : name;
            }
        } catch (Throwable t) {
            AppLogger.w(TAG, "configModule failed: " + packageName + " - " + t.getMessage());
        }
        return null;
    }

    /**
     * 解析插件声明并注册 scheme。
     *
     * <p>先开桥收集窗调用 schema()（MioConfig 一行式声明），收不到项再解析旧式返回值。</p>
     *
     * @return 解析成功的 scheme；未声明 CONFIG / 无 schema() / 解析失败时返回 null
     */
    @Nullable
    public static PluginConfigScheme resolve(DslPluginManager manager, String packageName) {
        if (manager == null || packageName == null) return null;
        String module = configModule(packageName);
        if (module == null) {
            AppLogger.d(TAG, "resolve: no CONFIG service declared for " + packageName);
            return null;
        }
        DslPluginHandle handle = ensureHandle(manager, packageName);
        if (handle == null) {
            AppLogger.w(TAG, "resolve: no handle for " + packageName);
            return null;
        }

        MioConfigBridge bridge = MioBridgeRegistry.getMioConfigBridge();
        Object data;
        PluginConfigScheme scheme = null;
        if (bridge != null) {
            bridge.begin(packageName);
            try {
                data = handle.callFunction(module + ".schema");
            } finally {
                scheme = bridge.end();
            }
        } else {
            data = handle.callFunction(module + ".schema");
        }
        if (scheme == null) {
            scheme = parseScheme(data);
        }
        if (scheme == null) {
            AppLogger.w(TAG, "resolve: schema() not parseable for " + packageName);
            return null;
        }
        PluginConfigScheme.register(packageName, scheme);
        return scheme;
    }

    /**
     * 读取插件已保存的配置值（尚未保存的 key 不出现）。
     *
     * <p>插件可选导出 {@code config.values()}（旧式写法）；未导出时宿主直读插件 HostData
     * （与引擎 {@code HostData.get} 同一存储），因此新插件无需写 values()。</p>
     */
    @Nullable
    public static Map<String, Object> values(DslPluginManager manager, String packageName) {
        if (packageName == null) return null;
        String module = configModule(packageName);
        if (module == null) return null;
        if (manager != null) {
            DslPluginHandle handle = ensureHandle(manager, packageName);
            if (handle != null) {
                Object data = handle.callIfExported(module + ".values");
                if (data instanceof Map) {
                    Map<?, ?> raw = (Map<?, ?>) data;
                    if (!raw.isEmpty()) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> e : raw.entrySet()) {
                            if (e.getKey() != null) {
                                result.put(String.valueOf(e.getKey()), e.getValue());
                            }
                        }
                        return result;
                    }
                }
            }
        }
        return readHostData(packageName);
    }

    /**
     * 宿主直读插件 HostData：按已注册 scheme 的 key 逐个取值。
     *
     * <p>复用引擎的 host store（同一缓存实例），故与插件侧 {@code HostData.get} 完全一致。</p>
     */
    @Nullable
    private static Map<String, Object> readHostData(@Nullable String packageName) {
        PluginConfigScheme scheme = PluginConfigScheme.registeredFor(packageName);
        if (packageName == null || scheme == null) return null;
        try {
            Context context = Mio.getAppContext();
            if (context == null) return null;
            File pluginDir = OpenManager.pluginDir(context.getFilesDir(), packageName);
            if (!new File(pluginDir, "config.json").exists()) return null;
            try {
                return readHostDataOnce(pluginDir, scheme);
            } catch (RuntimeException first) {
                // 存储损坏：隔离备份后重试一次（与 HostData.get 桥同一自愈路径）
                if (!HostDataRepair.looksCorrupt(first)) throw first;
                if (!HostDataRepair.heal(pluginDir, first)) throw first;
                return readHostDataOnce(pluginDir, scheme);
            }
        } catch (Throwable t) {
            AppLogger.w(TAG, "readHostData failed: " + packageName + " - " + t.getMessage());
            return null;
        }
    }

    private static Map<String, Object> readHostDataOnce(File pluginDir, PluginConfigScheme scheme) {
        RegistryStore store = RegistryManagerHolder.hostStore(
                pluginDir, new PandaRegistryFactory(Mio.getAppContext()));
        if (store == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        for (PluginConfigScheme.Category category : scheme.getCategories()) {
            for (PluginConfigScheme.Item item : category.items) {
                if (item.key == null || item.key.isEmpty()) continue;
                Object v = store.get(item.key);
                if (v != null) result.put(item.key, v);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 触发插件按钮动作：调用 config 模块导出的 onButton(key)。
     */
    public static void callButton(DslPluginManager manager, String packageName, String key) {
        if (manager == null || packageName == null || key == null) return;
        String module = configModule(packageName);
        if (module == null) return;
        DslPluginHandle handle = ensureHandle(manager, packageName);
        if (handle == null) return;
        handle.callFunction(module + ".onButton", key);
    }

    private static DslPluginHandle ensureHandle(DslPluginManager manager, String packageName) {
        DslPluginHandle handle = manager.getHandle(packageName);
        if (handle != null && handle.isEngineLoaded()) {
            return handle;
        }
        if (handle != null) {
            // 引擎侧已 close/unload（如禁用插件），宿主句柄失效：丢弃后重新打开
            AppLogger.w(TAG, "ensureHandle: stale handle, reopening " + packageName);
            manager.close(packageName);
        }
        return manager.open(packageName);
    }

    // ==================== schema 解析 ====================

    @Nullable
    private static PluginConfigScheme parseScheme(@Nullable Object data) {
        if (!(data instanceof List)) return null;
        try {
            PluginConfigScheme.Builder builder = PluginConfigScheme.builder();
            boolean any = false;
            for (Object catObj : (List<?>) data) {
                if (!(catObj instanceof Map)) continue;
                Map<?, ?> cat = (Map<?, ?>) catObj;
                String catTitle = str(cat.get("category"), "配置");
                PluginConfigScheme.SectionBuilder section = builder.section(catTitle);
                Object itemsObj = cat.get("items");
                if (itemsObj instanceof List) {
                    for (Object itemObj : (List<?>) itemsObj) {
                        if (!(itemObj instanceof Map)) continue;
                        if (parseItem(section, (Map<?, ?>) itemObj)) {
                            any = true;
                        }
                    }
                }
                section.and();
            }
            return any ? builder.build() : null;
        } catch (RuntimeException e) {
            AppLogger.e(TAG, "parseScheme failed: " + e.getMessage(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean parseItem(@Nullable PluginConfigScheme.SectionBuilder section, Map<?, ?> m) {
        if (section == null) return false;
        String type = str(m.get("type"), "");
        String key = str(m.get("key"), "");
        String title = str(m.get("title"), "");
        String subtitle = asString(m.get("subtitle"));
        switch (type) {
            case "switch": {
                section.addSwitch(key, title, subtitle, bool(m.get("value"), false));
                return true;
            }
            case "checkbox": {
                section.addCheckbox(key, title, subtitle, bool(m.get("value"), false));
                return true;
            }
            case "text": {
                section.addTextField(key, title, subtitle,
                        str(m.get("value"), ""), asString(m.get("hint")));
                PluginConfigScheme.Item item = section.lastAdded();
                if (item != null) {
                    applyInput(item, m);
                }
                return true;
            }
            case "radio": {
                String selected = str(m.get("value"), null);
                List<String> labels = splitOptions(m.get("options"));
                if (labels.isEmpty()) return true;
                int defIndex = labels.indexOf(selected);
                section.addRadio(key, title, subtitle, defIndex, labels.toArray(new String[0]));
                return true;
            }
            case "chip": {
                List<String> labels = splitOptions(m.get("options"));
                if (labels.isEmpty()) return true;
                String[] defSelected = splitOptions(m.get("value")).toArray(new String[0]);
                section.addChip(key, title, subtitle, labels.toArray(new String[0]), defSelected);
                return true;
            }
            case "textView": {
                section.addTextView(key, title, subtitle, str(m.get("body"), ""));
                return true;
            }
            case "button": {
                section.addButton(key, title, subtitle, str(m.get("label"), ""), null);
                return true;
            }
            case "progress": {
                section.addProgress(key, title, subtitle,
                        intValue(m.get("value"), 0), intValue(m.get("max"), 100),
                        str(m.get("text"), ""));
                return true;
            }
            default:
                return false;
        }
    }

    private static void applyInput(PluginConfigScheme.Item item, Map<?, ?> m) {
        item.inputType = str(m.get("inputType"), "");
        if (m.containsKey("min")) item.min = intValue(m.get("min"), Integer.MIN_VALUE);
        else item.min = Integer.MIN_VALUE;
        if (m.containsKey("max")) item.max = intValue(m.get("max"), Integer.MAX_VALUE);
        else item.max = Integer.MAX_VALUE;
    }

    private static String asString(@Nullable Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String str(@Nullable Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static boolean bool(@Nullable Object o, boolean def) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).intValue() != 0;
        if (o instanceof String) return Boolean.parseBoolean((String) o);
        return def;
    }

    private static int intValue(@Nullable Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try {
                return Integer.parseInt(((String) o).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static List<String> splitOptions(@Nullable Object o) {
        List<String> result = new ArrayList<>();
        if (o == null) return result;
        if (o instanceof List) {
            for (Object item : (List<?>) o) result.add(String.valueOf(item));
            return result;
        }
        for (String part : String.valueOf(o).split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) result.add(t);
        }
        return result;
    }
}