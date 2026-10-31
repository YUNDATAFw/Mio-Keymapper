package io.takamiya.mio.terminal.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.terminal.command.CommandCallback;
import io.takamiya.mio.terminal.command.CommandResult;
import io.takamiya.mio.terminal.command.CommandState;
import io.takamiya.mio.terminal.command.ICommand;
import io.takamiya.mio.terminal.command.ParamDef;
import io.takamiya.mio.terminal.command.ParamType;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class RegCommand implements ICommand {

    private static final String[] SENSITIVE_KEYS = {
            DataManage.Tag.TOKEN_TOKEN.getValue(),
            DataManage.Tag.APP_TOKEN_TOKEN.getValue(),
            DataManage.Tag.USER_PASSWORD.getValue()
    };

    @Override
    public String getName() {
        return "reg";
    }

    @Override
    public String getDescription() {
        return "增删改查注册表（敏感字段拒绝访问）";
    }

    @Override
    public String getUsage() {
        return "reg <get|set|delete|list|exists> [key] [value]";
    }

    @Override
    public List<ParamDef> getParams() {
        List<ParamDef> params = new ArrayList<>();
        params.add(new ParamDef("action", ParamType.STRING, true));
        params.add(new ParamDef("key", ParamType.STRING, false));
        params.add(new ParamDef("value", ParamType.STRING, false));
        return params;
    }

    @Override
    public void execute(Map<String, Object> params, CommandCallback callback) {
        String action = (String) params.get("action");
        if (action == null) {
            callback.onResult(CommandResult.error("缺少操作类型，可用: get, set, delete, list, exists"));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        Registry reg = RegistryUtils.get();
        if (reg == null) {
            callback.onResult(CommandResult.error("Registry 未初始化"));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        switch (action.toLowerCase()) {
            case "get":
                handleGet(reg, params, callback);
                break;
            case "set":
                handleSet(reg, params, callback);
                break;
            case "delete":
            case "del":
                handleDelete(reg, params, callback);
                break;
            case "list":
            case "ls":
                handleList(reg, callback);
                break;
            case "exists":
                handleExists(reg, params, callback);
                break;
            default:
                callback.onResult(CommandResult.error("缺少操作类型，可用: get, set, delete, list, exists"));
                callback.onStateChange(CommandState.ERROR);
                break;
        }
    }

    private void handleGet(Registry reg, Map<String, Object> params, CommandCallback callback) {
        String key = (String) params.get("key");
        if (key == null || key.isEmpty()) {
            callback.onResult(CommandResult.error("缺少 key 参数"));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        if (isSensitive(key)) {
            callback.onResult(CommandResult.error("拒绝访问敏感字段: " + key));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        if (!reg.exists(key)) {
            callback.onResult(CommandResult.text("键不存在: " + key));
            callback.onStateChange(CommandState.SUCCESS);
            return;
        }

        Object value = reg.get(key);
        String output = key + " = " + (value != null ? value.toString() : "null");
        callback.onResult(CommandResult.text(output));
        callback.onStateChange(CommandState.SUCCESS);
    }

    private void handleSet(Registry reg, Map<String, Object> params, CommandCallback callback) {
        String key = (String) params.get("key");
        String valueStr = (String) params.get("value");

        if (key == null || key.isEmpty()) {
            callback.onResult(CommandResult.error("缺少 key 参数"));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        if (valueStr == null || valueStr.isEmpty()) {
            callback.onResult(CommandResult.error("缺少 value 参数"));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        if (isSensitive(key)) {
            callback.onResult(CommandResult.error("拒绝修改敏感字段: " + key));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        Object value = parseValue(valueStr);
        reg.set(key, value);
        reg.saveAsync();

        callback.onResult(CommandResult.text("已设置: " + key + " = " + value));
        callback.onStateChange(CommandState.SUCCESS);
    }

    private void handleDelete(Registry reg, Map<String, Object> params, CommandCallback callback) {
        String key = (String) params.get("key");
        if (key == null || key.isEmpty()) {
            callback.onResult(CommandResult.error("缺少 key 参数"));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        if (isSensitive(key)) {
            callback.onResult(CommandResult.error("拒绝删除敏感字段: " + key));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        if (!reg.exists(key)) {
            callback.onResult(CommandResult.text("键不存在: " + key));
            callback.onStateChange(CommandState.SUCCESS);
            return;
        }

        reg.delete(key);
        reg.saveAsync();

        callback.onResult(CommandResult.text("已删除 " + key));
        callback.onStateChange(CommandState.SUCCESS);
    }

    private void handleList(Registry reg, CommandCallback callback) {
        List<String> keys = reg.list();
        if (keys.isEmpty()) {
            callback.onResult(CommandResult.text("注册表为空"));
            callback.onStateChange(CommandState.SUCCESS);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**注册表键值列表** (共 ").append(keys.size()).append(" 项)\n\n");
        for (String key : keys) {
            String displayKey = isSensitive(key) ? key + " [敏感]" : key;
            sb.append("- `").append(displayKey).append("`\n");
        }
        callback.onResult(CommandResult.markdown(sb.toString()));
        callback.onStateChange(CommandState.SUCCESS);
    }

    private void handleExists(Registry reg, Map<String, Object> params, CommandCallback callback) {
        String key = (String) params.get("key");
        if (key == null || key.isEmpty()) {
            callback.onResult(CommandResult.error("缺少 key 参数"));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        boolean exists = reg.exists(key);
        callback.onResult(CommandResult.text(key + (exists ? " 存在" : " 不存在")));
        callback.onStateChange(CommandState.SUCCESS);
    }

    private boolean isSensitive(String key) {
        for (String sensitive : SENSITIVE_KEYS) {
            if (sensitive.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private Object parseValue(String valueStr) {
        if (valueStr.equalsIgnoreCase("true") || valueStr.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(valueStr);
        }
        try {
            return Integer.parseInt(valueStr);
        } catch (NumberFormatException ignored) {}
        try {
            return Float.parseFloat(valueStr);
        } catch (NumberFormatException ignored) {}
        return valueStr;
    }
}
