package io.takamiya.mio.dsl.bridge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.fsy.plugin.sdl.library.BridgeHandler;
import cn.fsy.plugin.sdl.library.SdlException;

/**
 * Json 命名空间桥接实现。
 * 把 JSON 文本按点路径（如 "playerName"、"sender"、"block.coordinate.x"）取值暴露给 DSL：
 * 通用能力，与任何具体业务（如人机校验）无关。
 */
public class JsonBridge {

    private static final String TAG = "JsonBridge";

    /** Json.parse(raw) - 解析 JSON 文本，返回解析后的对象（JSONObject/JSONArray/基础值）。 */
    public BridgeHandler parse() {
        return (args) -> {
            String raw = str(args, 0, "parse");
            if (raw == null || raw.trim().isEmpty()) return null;
            String s = raw.trim();
            try {
                if (s.startsWith("{")) return new JSONObject(s);
                if (s.startsWith("[")) return new JSONArray(s);
                return raw;
            } catch (Exception e) {
                throw new SdlException(SdlException.ErrorCode.INTERNAL_ERROR,
                        "parse: JSON 解析失败 - " + e.getMessage());
            }
        };
    }

    /** Json.getString(raw, path) - 取字符串字段，不存在返回空串。 */
    public BridgeHandler getString() {
        return (args) -> {
            Object v = resolve(at(args, "getString"), path(args, 1));
            return v == null ? "" : v.toString();
        };
    }

    /** Json.getInt(raw, path) - 取整数字段，不存在返回 0。 */
    public BridgeHandler getInt() {
        return (args) -> resolve(at(args, "getInt"), path(args, 1)) instanceof Number
                ? ((Number) resolve(at(args, "getInt"), path(args, 1))).intValue() : 0;
    }

    /** Json.getBool(raw, path) - 取布尔字段，不存在返回 false。 */
    public BridgeHandler getBool() {
        return (args) -> resolve(at(args, "getBool"), path(args, 1)) instanceof Boolean
                && ((Boolean) resolve(at(args, "getBool"), path(args, 1)));
    }

    /** Json.getStringArray(raw, path) - 取字符串数组字段，不存在/非数组返回空列表。 */
    public BridgeHandler getStringArray() {
        return (args) -> {
            JSONObject root = at(args, "getStringArray");
            String p = path(args, 1);
            JSONArray arr = resolveArray(root, p);
            List<String> list = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    Object o = arr.opt(i);
                    list.add(o == null ? "" : o.toString());
                }
            }
            return list;
        };
    }

    // ==================== 取值工具 ====================

    private static JSONObject at(Object[] args, String fn) {
        String raw = str(args, 0, fn);
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            throw new SdlException(SdlException.ErrorCode.INTERNAL_ERROR,
                    fn + ": 首参不是合法 JSON 对象 - " + e.getMessage());
        }
    }

    private static String path(Object[] args, int index) {
        String p = str(args, index, "get");
        return p == null ? "" : p.trim();
    }

    /** 按点路径逐层下钻，返回叶子变量值（无则返回 null 由 opt 兜底）。 */
    private static Object resolve(JSONObject root, String path) {
        Object cur = root;
        for (String seg : path.split("\\.")) {
            if (cur instanceof JSONObject) {
                cur = ((JSONObject) cur).opt(seg);
            } else {
                return null;
            }
        }
        return cur;
    }

    private static JSONArray resolveArray(JSONObject root, String path) {
        Object v = resolve(root, path);
        return v instanceof JSONArray ? (JSONArray) v : null;
    }

    private static String str(Object[] args, int index, String fn) {
        if (args == null || index >= args.length || args[index] == null) {
            throw new SdlException(SdlException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 缺少第 " + (index + 1) + " 个参数");
        }
        return args[index].toString();
    }
}