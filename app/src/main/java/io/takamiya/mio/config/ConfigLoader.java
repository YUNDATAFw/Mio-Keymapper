package io.takamiya.mio.config;

import android.content.Context;
import cn.panda.utils.library.AppLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigLoader {
    private static final String TAG = "ConfigLoader";

    public static Plugin loadFromAssets(Context context, String fileName) {
        try (InputStream is = context.getAssets().open(fileName)) {
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            String json = new String(buffer, StandardCharsets.UTF_8);
            return parse(json);
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to load config from assets: " + fileName, e);
            return null;
        }
    }

    public static Plugin loadFromString(String json) {
        try {
            return parse(json);
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to parse config", e);
            return null;
        }
    }

    public static JSONObject parseStrict(String json) throws JSONException {
        String cleaned = json.replaceAll("[\\s\\uFEFF\\u00A0\\u00AD\\u200B-\\u200F\\u2028-\\u202F\\u2060-\\u2064\\u180E\\u034F\\u061C]+$", "");
        return new JSONObject(cleaned);
    }

    public static Plugin parse(String json) throws JSONException {
        JSONObject root = parseStrict(json);
        JSONObject pluginObj = root.getJSONObject("plugin");

        Plugin plugin = new Plugin();
        plugin.setConfig(parseConfig(pluginObj.getJSONObject("config")));
        plugin.setActionList(parseActionList(pluginObj.getJSONArray("actionList")));
        return plugin;
    }

    public static Plugin parseWithValidation(String json) throws ConfigParseException {
        try {
            JSONObject root = parseStrict(json);
            JSONObject pluginObj;
            try {
                pluginObj = root.getJSONObject("plugin");
            } catch (JSONException e) {
                throw new ConfigParseException("plugin", "object", "缺少plugin对象");
            }

            Plugin plugin = new Plugin();
            try {
                plugin.setConfig(parseConfigValidated(pluginObj.getJSONObject("config")));
            } catch (JSONException e) {
                throw new ConfigParseException("config", "object", "缺少config对象");
            }

            try {
                JSONArray actionListArr = pluginObj.getJSONArray("actionList");
                List<ActionItem> items = new ArrayList<>();
                for (int i = 0; i < actionListArr.length(); i++) {
                    items.add(parseActionItemValidated(actionListArr.getJSONObject(i), i));
                }
                plugin.setActionList(items);
            } catch (JSONException e) {
                throw new ConfigParseException("actionList", "array", "缺少actionList数组");
            } catch (ConfigParseException e) {
                throw e;
            }
            return plugin;
        } catch (ConfigParseException e) {
            throw e;
        } catch (JSONException e) {
            throw new ConfigParseException("root", "object", "JSON格式错误: " + e.getMessage());
        }
    }

    private static void validateInt(JSONObject obj, String field, String context,
                                     Integer min, Integer max) throws ConfigParseException {
        if (!obj.has(field)) return;
        try {
            int value = obj.getInt(field);
            if (min != null && value < min) {
                throw new ConfigParseException(context + "." + field, "int",
                        "值 " + value + " 小于最小值 " + min);
            }
            if (max != null && value > max) {
                throw new ConfigParseException(context + "." + field, "int",
                        "值 " + value + " 大于最大值 " + max);
            }
        } catch (JSONException e) {
            throw new ConfigParseException(context + "." + field, "int",
                    "期望int类型，实际为: " + e.getMessage());
        } catch (ConfigParseException e) {
            throw e;
        }
    }

    private static void validateLong(JSONObject obj, String field, String context,
                                      Long min, Long max) throws ConfigParseException {
        if (!obj.has(field)) return;
        try {
            long value = obj.getLong(field);
            if (min != null && value < min) {
                throw new ConfigParseException(context + "." + field, "long",
                        "值 " + value + " 小于最小值 " + min);
            }
            if (max != null && value > max) {
                throw new ConfigParseException(context + "." + field, "long",
                        "值 " + value + " 大于最大值 " + max);
            }
        } catch (JSONException e) {
            throw new ConfigParseException(context + "." + field, "long",
                    "期望long类型，实际为: " + e.getMessage());
        } catch (ConfigParseException e) {
            throw e;
        }
    }

    private static void validateBoolean(JSONObject obj, String field, String context) throws ConfigParseException {
        if (!obj.has(field)) return;
        try {
            if (!obj.isNull(field) && !(obj.get(field) instanceof Boolean)) {
                throw new ConfigParseException(context + "." + field, "boolean", "期望boolean类型");
            }
        } catch (JSONException e) {
            throw new ConfigParseException(context + "." + field, "boolean",
                    "期望boolean类型，实际为: " + e.getMessage());
        } catch (ConfigParseException e) {
            throw e;
        }
    }

    private static void validateString(JSONObject obj, String field, String context) throws ConfigParseException {
        if (!obj.has(field)) return;
        try {
            if (!obj.isNull(field) && !(obj.get(field) instanceof String)) {
                throw new ConfigParseException(context + "." + field, "string", "期望string类型");
            }
        } catch (JSONException e) {
            throw new ConfigParseException(context + "." + field, "string",
                    "期望string类型，实际为: " + e.getMessage());
        } catch (ConfigParseException e) {
            throw e;
        }
    }

    private static void validateArray(JSONObject obj, String field, String context) throws ConfigParseException {
        if (!obj.has(field)) return;
        try {
            if (!obj.isNull(field) && !(obj.get(field) instanceof JSONArray)) {
                throw new ConfigParseException(context + "." + field, "array", "期望array类型");
            }
        } catch (JSONException e) {
            throw new ConfigParseException(context + "." + field, "array",
                    "期望array类型，实际为: " + e.getMessage());
        } catch (ConfigParseException e) {
            throw e;
        }
    }

    private static PluginConfig parseConfig(JSONObject obj) throws JSONException {
        PluginConfig config = new PluginConfig();
        config.setName(obj.optString("name", ""));
        config.setPackageName(obj.optString("package", ""));
        config.setDescription(obj.optString("description", ""));
        config.setAuthor(obj.optString("author", ""));
        config.setUpdateUrl(obj.optString("updateUrl", ""));
        config.setVersionName(obj.optString("versionName", ""));
        if (obj.has("versionCode")) config.setVersionCode(obj.optInt("versionCode", 0));
        if (obj.has("minEngine")) config.setMinEngine(obj.optInt("minEngine", 1));
        config.setEnable(obj.optBoolean("enable", true));
        if (obj.has("keyHoldDuration")) config.setKeyHoldDuration(obj.optLong("keyHoldDuration", 0));
        return config;
    }

    private static PluginConfig parseConfigValidated(JSONObject obj) throws ConfigParseException {
        validateString(obj, "name", "config");
        validateString(obj, "package", "config");
        validateString(obj, "description", "config");
        validateString(obj, "author", "config");
        validateString(obj, "updateUrl", "config");
        validateString(obj, "versionName", "config");
        validateInt(obj, "versionCode", "config", 0, null);
        validateInt(obj, "minEngine", "config", 1, null);
        validateBoolean(obj, "enable", "config");
        validateLong(obj, "keyHoldDuration", "config", 0L, null);

        try {
            return parseConfig(obj);
        } catch (JSONException e) {
            throw new ConfigParseException("config", "object", "config解析失败: " + e.getMessage());
        }
    }

    private static List<ActionItem> parseActionList(JSONArray arr) throws JSONException {
        List<ActionItem> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(parseActionItem(arr.getJSONObject(i)));
        }
        return list;
    }

    private static ActionItem parseActionItem(JSONObject obj) throws JSONException {
        ActionItem item = new ActionItem();
        item.setTitle(obj.optString("title", ""));
        item.setDescription(obj.optString("description", ""));
        item.setTriggerModel(TriggerModel.fromString(obj.optString("triggerModel", "BUTTON")));
        item.setId(obj.optString("id", ""));
        if (obj.has("group")) {
            item.setGroup(obj.optString("group", ""));
        }
        int holdThreshold = obj.optInt("holdThresholdMs", 0);
        if (holdThreshold > 0) item.setHoldThresholdMs(holdThreshold);
        int releaseDelay = obj.optInt("releaseDelayMs", 0);
        if (releaseDelay > 0) item.setReleaseDelayMs(releaseDelay);

        if (obj.has("action")) {
            item.setAction(parseActions(obj.getJSONArray("action")));
        }
        if (obj.has("groups")) {
            item.setGroups(parseGroups(obj.getJSONArray("groups")));
        }

        if (obj.has("events")) {
            JSONObject eventsObj = obj.getJSONObject("events");
            Map<ShortcutEventType, List<ActionGroup>> events = new HashMap<>();
            for (java.util.Iterator<String> it = eventsObj.keys(); it.hasNext(); ) {
                String key = it.next();
                ShortcutEventType type = ShortcutEventType.fromString(key);
                if (type != null) {
                    events.put(type, parseGroups(eventsObj.getJSONArray(key)));
                }
            }
            item.setEvents(events);
        }

        return item;
    }

    private static ActionItem parseActionItemValidated(JSONObject obj, int index) throws ConfigParseException {
        String ctx = "actionList[" + index + "]";
        validateString(obj, "title", ctx);
        validateString(obj, "description", ctx);
        validateString(obj, "id", ctx);
        validateString(obj, "group", ctx);
        validateString(obj, "triggerModel", ctx);
        validateInt(obj, "holdThresholdMs", ctx, 0, null);
        validateInt(obj, "releaseDelayMs", ctx, 0, null);

        try {
            ActionItem item = parseActionItem(obj);
            if (obj.has("action")) {
                validateArray(obj, "action", ctx);
                JSONArray arr = obj.getJSONArray("action");
                List<Action> actions = new ArrayList<>();
                for (int j = 0; j < arr.length(); j++) {
                    actions.add(parseActionValidated(arr.getJSONObject(j), j));
                }
                item.setAction(actions);
            }
            if (obj.has("groups")) {
                validateArray(obj, "groups", ctx);
                JSONArray arr = obj.getJSONArray("groups");
                List<ActionGroup> groups = new ArrayList<>();
                for (int j = 0; j < arr.length(); j++) {
                    groups.add(parseGroupValidated(arr.getJSONObject(j), ctx + ".groups[" + j + "]"));
                }
                item.setGroups(groups);
            }
            if (obj.has("events")) {
                JSONObject eventsObj = obj.getJSONObject("events");
                Map<ShortcutEventType, List<ActionGroup>> events = new HashMap<>();
                for (java.util.Iterator<String> it = eventsObj.keys(); it.hasNext(); ) {
                    String key = it.next();
                    ShortcutEventType type = ShortcutEventType.fromString(key);
                    if (type == null) {
                        throw new ConfigParseException(ctx + ".events." + key, "ShortcutEventType",
                                "未知的事件类型: " + key);
                    }
                    JSONArray typeArr = eventsObj.getJSONArray(key);
                    if (!(eventsObj.get(key) instanceof JSONArray)) {
                        throw new ConfigParseException(ctx + ".events." + key, "array", "期望array类型");
                    }
                    List<ActionGroup> groups = new ArrayList<>();
                    for (int j = 0; j < typeArr.length(); j++) {
                        groups.add(parseGroupValidated(typeArr.getJSONObject(j),
                                ctx + ".events." + key + "[" + j + "]"));
                    }
                    events.put(type, groups);
                }
                item.setEvents(events);
            }
            return item;
        } catch (ConfigParseException e) {
            throw e;
        } catch (JSONException e) {
            throw new ConfigParseException(ctx, "object", "actionItem解析失败: " + e.getMessage());
        }
    }

    private static List<ActionGroup> parseGroups(JSONArray arr) throws JSONException {
        List<ActionGroup> groups = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            groups.add(parseGroup(arr.getJSONObject(i)));
        }
        return groups;
    }

    private static ActionGroup parseGroup(JSONObject obj) throws JSONException {
        ActionGroup group = new ActionGroup();
        if (obj.has("name")) group.setName(obj.getString("name"));
        if (obj.has("actions")) {
            group.setActions(parseActions(obj.getJSONArray("actions")));
        }
        return group;
    }

    private static ActionGroup parseGroupValidated(JSONObject obj, String ctx) throws ConfigParseException {
        try {
            ActionGroup group = new ActionGroup();
            if (obj.has("name")) group.setName(obj.getString("name"));
            if (obj.has("actions")) {
                validateArray(obj, "actions", ctx);
                JSONArray arr = obj.getJSONArray("actions");
                List<Action> actions = new ArrayList<>();
                for (int j = 0; j < arr.length(); j++) {
                    actions.add(parseActionValidated(arr.getJSONObject(j), j));
                }
                group.setActions(actions);
            }
            return group;
        } catch (ConfigParseException e) {
            throw e;
        } catch (JSONException e) {
            throw new ConfigParseException(ctx, "object", "group解析失败: " + e.getMessage());
        }
    }

    private static List<Action> parseActions(JSONArray arr) throws JSONException {
        List<Action> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(parseAction(arr.getJSONObject(i)));
        }
        return list;
    }

    private static Action parseAction(JSONObject obj) throws JSONException {
        Action action = new Action();
        action.setType(ActionType.fromString(obj.optString("type", "INPUT_KEY")));
        action.setTarget(Action.ActionTarget.fromString(obj.optString("target", "KEYBOARD")));
        action.setKey(obj.optString("key", ""));
        action.setText(obj.optString("text", ""));
        action.setHold(obj.optBoolean("hold", false));
        if (obj.has("keyHoldDuration")) {
            action.setKeyHoldDuration(obj.optLong("keyHoldDuration", 0));
        }
        if (obj.has("delayMs")) action.setBeforeDelayMs(obj.optLong("delayMs", 0));
        if (obj.has("beforeDelayMs")) action.setBeforeDelayMs(obj.optLong("beforeDelayMs", 0));
        if (obj.has("afterDelayMs")) action.setAfterDelayMs(obj.optLong("afterDelayMs", 0));
        if (obj.has("repeatBeforeDelayMs")) action.setRepeatBeforeDelayMs(obj.optLong("repeatBeforeDelayMs", 0));
        if (obj.has("repeatAfterDelayMs")) action.setRepeatAfterDelayMs(obj.optLong("repeatAfterDelayMs", 0));
        if (obj.has("charDelayMs")) action.setCharDelayMs(obj.optLong("charDelayMs", 0));
        if (obj.has("toastSettings")) {
            action.setToastSettings(parseToastSettings(obj.getJSONObject("toastSettings")));
        }
        if (obj.has("combinationKeys")) {
            JSONArray arr = obj.getJSONArray("combinationKeys");
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                keys.add(arr.getString(i));
            }
            action.setCombinationKeys(keys);
        }

        action.setRepeat(obj.optBoolean("repeat", false));
        if (obj.has("duplicateConfiguration")) {
            action.setDuplicateConfiguration(parseDuplicateConfiguration(obj.getJSONObject("duplicateConfiguration")));
        } else if (action.isRepeat()) {
            action.ensureDuplicateConfiguration();
        }

        if (action.getType() == ActionType.INPUT_KEY || action.getType() == ActionType.INPUT_KEY_GROUP) {
            action.setText(null);
        } else {
            action.setKey(null);
            action.setCombinationKeys(null);
        }

        return action;
    }

    private static DuplicateConfiguration parseDuplicateConfiguration(JSONObject obj) throws JSONException {
        DuplicateConfiguration dc = new DuplicateConfiguration();
        dc.setIntervalDuration(obj.optLong("intervalDuration", 40));
        dc.setNumberOfRepetitions(obj.optInt("numberOfRepetitions", 1));
        dc.setRepeatTime(obj.optInt("repeatTime", 0));
        dc.setStopRepeatingConditions(obj.optInt("stopRepeatingConditions", 0));
        return dc;
    }

    private static Action parseActionValidated(JSONObject obj, int index)
            throws ConfigParseException {
        String ctx = "action[" + index + "]";
        validateString(obj, "type", ctx);
        validateString(obj, "key", ctx);
        validateString(obj, "text", ctx);
        validateBoolean(obj, "hold", ctx);
        validateLong(obj, "keyHoldDuration", ctx, 0L, null);
        validateLong(obj, "beforeDelayMs", ctx, 0L, null);
        validateLong(obj, "afterDelayMs", ctx, 0L, null);
        validateLong(obj, "repeatBeforeDelayMs", ctx, 0L, null);
        validateLong(obj, "repeatAfterDelayMs", ctx, 0L, null);
        validateLong(obj, "charDelayMs", ctx, 0L, null);
        validateBoolean(obj, "repeat", ctx);

        if (obj.has("type")) {
            String type = obj.optString("type", "");
            if (!type.isEmpty() && ActionType.fromString(type) == null) {
                throw new ConfigParseException(ctx + ".type", "ActionType",
                        "未知的类型: " + type + "，有效值: INPUT_KEY, INPUT_KEY_GROUP, INPUT_TEXT, TOAST");
            }
        }
        if (obj.has("combinationKeys")) {
            validateArray(obj, "combinationKeys", ctx);
        }
        if (obj.has("duplicateConfiguration")) {
            // duplicateConfiguration is a JSON object, not validated as array
        }
        try {
            return parseAction(obj);
        } catch (JSONException e) {
            throw new ConfigParseException(ctx, "object", "解析action失败: " + e.getMessage());
        }
    }

    private static ToastSettings parseToastSettings(JSONObject obj) {
        ToastSettings ts = new ToastSettings();
        if (obj.has("durationMs")) ts.setDurationMs(obj.optInt("durationMs", 0));
        if (obj.has("fontSize")) ts.setFontSize(obj.optInt("fontSize", 0));
        if (obj.has("textColor")) ts.setTextColor(obj.optInt("textColor", 0));
        if (obj.has("bold")) ts.setBold(obj.optBoolean("bold", false));
        if (obj.has("backgroundColor")) ts.setBackgroundColor(obj.optInt("backgroundColor", 0));
        return ts;
    }

    // ── 序列化 ──

    public static String toJson(Plugin plugin) {
        try {
            JSONObject root = new JSONObject();
            JSONObject pluginObj = new JSONObject();

            if (plugin.getConfig() != null) {
                pluginObj.put("config", configToJson(plugin.getConfig()));
            }
            if (plugin.getActionList() != null) {
                pluginObj.put("actionList", actionListToJson(plugin.getActionList()));
            }

            root.put("plugin", pluginObj);
            return root.toString(2);
        } catch (JSONException e) {
            AppLogger.e(TAG, "toJson failed", e);
            return null;
        }
    }

    private static JSONObject configToJson(PluginConfig config) throws JSONException {
        JSONObject obj = new JSONObject();
        if (config.getName() != null) obj.put("name", config.getName());
        if (config.getPackageName() != null) obj.put("package", config.getPackageName());
        if (config.getDescription() != null) obj.put("description", config.getDescription());
        if (config.getAuthor() != null) obj.put("author", config.getAuthor());
        if (config.getUpdateUrl() != null) obj.put("updateUrl", config.getUpdateUrl());
        if (config.getVersionName() != null) obj.put("versionName", config.getVersionName());
        if (config.getVersionCode() != null) obj.put("versionCode", config.getVersionCode());
        if (config.getMinEngine() != null) obj.put("minEngine", config.getMinEngine());
        obj.put("enable", config.isEnable());
        if (config.getKeyHoldDuration() != null) obj.put("keyHoldDuration", config.getKeyHoldDuration());
        return obj;
    }

    private static JSONArray actionListToJson(List<ActionItem> actionList) throws JSONException {
        JSONArray arr = new JSONArray();
        if (actionList == null) return arr;
        for (ActionItem item : actionList) {
            arr.put(actionItemToJson(item));
        }
        return arr;
    }

    private static JSONObject actionItemToJson(ActionItem item) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("title", item.getTitle());
        obj.put("description", item.getDescription());
        obj.put("triggerModel", item.getTriggerModel() != null ? item.getTriggerModel().name() : "BUTTON");
        obj.put("id", item.getId());
        if (item.getGroup() != null && !item.getGroup().trim().isEmpty()) {
            obj.put("group", item.getGroup().trim());
        }
        if (item.getTriggerModel() == TriggerModel.PRESSURE_PLATE) {
            if (item.getHoldThresholdMs() != null && item.getHoldThresholdMs() > 0) {
                obj.put("holdThresholdMs", item.getHoldThresholdMs());
            }
            if (item.getReleaseDelayMs() != null && item.getReleaseDelayMs() > 0) {
                obj.put("releaseDelayMs", item.getReleaseDelayMs());
            }
        }
        if (item.getAction() != null && !item.getAction().isEmpty()) {
            obj.put("action", actionsToJson(item.getAction()));
        } else if (item.getGroups() != null && !item.getGroups().isEmpty()) {
            obj.put("groups", groupsToJson(item.getGroups()));
        }
        if (item.getEvents() != null && !item.getEvents().isEmpty()) {
            JSONObject eventsObj = new JSONObject();
            for (Map.Entry<ShortcutEventType, List<ActionGroup>> entry : item.getEvents().entrySet()) {
                eventsObj.put(entry.getKey().name(), groupsToJson(entry.getValue()));
            }
            obj.put("events", eventsObj);
        }
        return obj;
    }

    private static JSONArray groupsToJson(List<ActionGroup> groups) throws JSONException {
        JSONArray arr = new JSONArray();
        if (groups == null) return arr;
        for (ActionGroup group : groups) {
            arr.put(groupToJson(group));
        }
        return arr;
    }

    private static JSONObject groupToJson(ActionGroup group) throws JSONException {
        JSONObject obj = new JSONObject();
        if (group.getName() != null) obj.put("name", group.getName());
        if (group.getActions() != null) {
            obj.put("actions", actionsToJson(group.getActions()));
        }
        return obj;
    }

    private static JSONArray actionsToJson(List<Action> actions) throws JSONException {
        JSONArray arr = new JSONArray();
        if (actions == null) return arr;
        for (Action action : actions) {
            arr.put(actionToJson(action));
        }
        return arr;
    }

    private static JSONObject actionToJson(Action action) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("type", action.getType() != null ? action.getType().name() : "INPUT_KEY");
        if (action.getTarget() != Action.ActionTarget.KEYBOARD) {
            obj.put("target", action.getTarget().name());
        }
        boolean keyType = action.getType() == ActionType.INPUT_KEY
                || action.getType() == ActionType.INPUT_KEY_GROUP;
        boolean textType = action.getType() == ActionType.INPUT_TEXT
                || action.getType() == ActionType.TOAST;
        if (keyType && action.getKey() != null) obj.put("key", action.getKey());
        if (textType && action.getText() != null) obj.put("text", action.getText());
        obj.put("hold", action.isHold());
        if (action.getKeyHoldDuration() != null) {
            obj.put("keyHoldDuration", action.getKeyHoldDuration());
        }
        if (action.getBeforeDelayMs() != null && action.getBeforeDelayMs() > 0) obj.put("beforeDelayMs", action.getBeforeDelayMs());
        if (action.getAfterDelayMs() != null && action.getAfterDelayMs() > 0) obj.put("afterDelayMs", action.getAfterDelayMs());
        if (action.getRepeatBeforeDelayMs() != null && action.getRepeatBeforeDelayMs() > 0) obj.put("repeatBeforeDelayMs", action.getRepeatBeforeDelayMs());
        if (action.getRepeatAfterDelayMs() != null && action.getRepeatAfterDelayMs() > 0) obj.put("repeatAfterDelayMs", action.getRepeatAfterDelayMs());
        if (action.getCharDelayMs() != null) obj.put("charDelayMs", action.getCharDelayMs());
        if (keyType && action.getCombinationKeys() != null && !action.getCombinationKeys().isEmpty()) {
            JSONArray keysArr = new JSONArray();
            for (String k : action.getCombinationKeys()) {
                keysArr.put(k);
            }
            obj.put("combinationKeys", keysArr);
        }
        if (action.isRepeat()) {
            obj.put("repeat", true);
            if (action.getDuplicateConfiguration() != null) {
                DuplicateConfiguration dc = action.getDuplicateConfiguration();
                JSONObject dcObj = new JSONObject();
                dcObj.put("intervalDuration", dc.getIntervalDuration());
                dcObj.put("numberOfRepetitions", dc.getNumberOfRepetitions());
                dcObj.put("repeatTime", dc.getRepeatTime());
                dcObj.put("stopRepeatingConditions", dc.getStopRepeatingConditions());
                obj.put("duplicateConfiguration", dcObj);
            }
        }
        if (action.getType() == ActionType.TOAST && action.getToastSettings() != null) {
            obj.put("toastSettings", toastSettingsToJson(action.getToastSettings()));
        }
        return obj;
    }

    private static JSONObject toastSettingsToJson(ToastSettings ts) throws JSONException {
        JSONObject obj = new JSONObject();
        if (ts.getDurationMs() != null) obj.put("durationMs", ts.getDurationMs());
        if (ts.getFontSize() != null) obj.put("fontSize", ts.getFontSize());
        if (ts.getTextColor() != null) obj.put("textColor", ts.getTextColor());
        if (ts.getBold() != null) obj.put("bold", ts.getBold());
        if (ts.getBackgroundColor() != null) obj.put("backgroundColor", ts.getBackgroundColor());
        return obj;
    }
}
