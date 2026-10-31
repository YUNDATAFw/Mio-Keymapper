package io.takamiya.mio.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WS 功能注册表（宿主侧）。
 *
 * <p>记录插件通过 {@code Ws.registerFeature} 动态注册的功能条目，供悬浮窗
 * WsFeatureFragment 合并展示。后注册同名 key 覆盖旧条目。</p>
 */
public class WsFeatureRegistry {

    private static final String TAG = "WsFeatureRegistry";

    /** key → 功能条目 */
    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private WsFeatureRegistry() {}

    public static class Entry {
        public final String key;
        public final String packageName;
        public final String serviceName;
        public final String name;
        public final String desc;

        Entry(String key, String packageName, String serviceName, String name, String desc) {
            this.key = key;
            this.packageName = packageName;
            this.serviceName = serviceName;
            this.name = name;
            this.desc = desc;
        }
    }

    /** 注册功能条目（后注册覆盖）. */
    public static void register(String key, String packageName, String serviceName, String name, String desc) {
        if (key == null || key.isEmpty()) return;
        ENTRIES.put(key, new Entry(key, packageName, serviceName, name, desc));
    }

    /** 注销指定插件包的全部功能条目（插件关闭时调用）。 */
    public static void unregisterPackage(String packageName) {
        if (packageName == null) return;
        ENTRIES.entrySet().removeIf(e -> packageName.equals(e.getValue().packageName));
    }

    /** 注销指定包+服务的功能条目（服务停止时调用）。 */
    public static void unregisterService(String packageName, String serviceName) {
        if (serviceName == null) return;
        ENTRIES.entrySet().removeIf(e -> {
            Entry v = e.getValue();
            return (packageName == null || packageName.equals(v.packageName))
                    && serviceName.equals(v.serviceName);
        });
    }

    /** 按 key 查询功能条目（不存在返回 null）。 */
    public static Entry get(String key) {
        return key == null ? null : ENTRIES.get(key);
    }

    /** 全部功能条目。 */
    public static List<Entry> all() {
        return new ArrayList<>(ENTRIES.values());
    }
}