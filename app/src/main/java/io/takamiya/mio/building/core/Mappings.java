package io.takamiya.mio.building.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Java -> Bedrock block mapping loaded from generator_blocks.json (GeyserMC). */
public final class Mappings {

    public static final class Entry {
        public final String identifier;
        public final LinkedHashMap<String, Object> state;

        public Entry(String identifier, Map<String, Object> state) {
            this.identifier = identifier;
            this.state = state == null ? new LinkedHashMap<>() : new LinkedHashMap<>(state);
        }
    }

    private final Map<String, Entry> map = new LinkedHashMap<>();
    private final Map<String, Entry> fallback = new LinkedHashMap<>();

    public static Mappings load(File file) throws IOException {
        String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Object parsed = Json.parse(raw);
        if (!(parsed instanceof Map)) {
            throw new IOException("映射表格式不正确（应为对象）");
        }
        Object mappingsObj = ((Map<?, ?>) parsed).get("mappings");
        if (!(mappingsObj instanceof List)) {
            throw new IOException("映射表格式不正确（缺少 mappings 数组）");
        }
        Mappings m = new Mappings();
        for (Object o : (List<?>) mappingsObj) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> entry = (Map<?, ?>) o;
            Object javaState = entry.get("java_state");
            if (!(javaState instanceof Map)) continue;
            Object nameObj = ((Map<?, ?>) javaState).get("Name");
            if (!(nameObj instanceof String)) continue;
            String javaName = (String) nameObj;

            Map<String, String> props = new LinkedHashMap<>();
            Object propsObj = ((Map<?, ?>) javaState).get("Properties");
            if (propsObj instanceof Map) {
                for (Map.Entry<?, ?> kv : ((Map<?, ?>) propsObj).entrySet()) {
                    props.put(String.valueOf(kv.getKey()), String.valueOf(kv.getValue()));
                }
            }

            Object bedrockState = entry.get("bedrock_state");
            if (!(bedrockState instanceof Map)) continue;
            Object idObj = ((Map<?, ?>) bedrockState).get("bedrock_identifier");
            if (!(idObj instanceof String)) continue;
            String id = (String) idObj;

            Map<String, Object> state = new LinkedHashMap<>();
            Object sObj = ((Map<?, ?>) bedrockState).get("state");
            if (sObj instanceof Map) {
                for (Map.Entry<?, ?> kv : ((Map<?, ?>) sObj).entrySet()) {
                    state.put(String.valueOf(kv.getKey()), kv.getValue());
                }
            }

            Entry e = new Entry(id, state);
            m.map.put(makeJavaKey(javaName, props), e);
            if (!m.fallback.containsKey(javaName)) {
                m.fallback.put(javaName, e);
            }
        }
        return m;
    }

    /** sort props keys and join as k=v,k2=v2 */
    public static String makeJavaKey(String name, Map<String, String> props) {
        List<String> keys = new ArrayList<>(props.keySet());
        keys.sort(String::compareTo);
        StringBuilder sb = new StringBuilder(name).append("::");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(keys.get(i)).append('=').append(props.get(keys.get(i)));
        }
        return sb.toString();
    }

    public Entry lookup(String name, Map<String, String> props) {
        Entry e = map.get(makeJavaKey(name, props));
        if (e != null) return e;
        return fallback.get(name);
    }

    public int size() { return map.size(); }
}