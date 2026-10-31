package io.takamiya.mio.terminal.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommandParser {

    public static class ParsedCommand {
        public final String name;
        public final Map<String, String> namedArgs;
        public final List<String> positionalArgs;

        public ParsedCommand(String name, Map<String, String> namedArgs, List<String> positionalArgs) {
            this.name = name;
            this.namedArgs = namedArgs;
            this.positionalArgs = positionalArgs;
        }
    }

    public static ParsedCommand parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String[] parts = input.trim().split("\\s+");
        if (parts.length == 0) {
            return null;
        }

        String name = parts[0].toLowerCase();
        Map<String, String> namedArgs = new LinkedHashMap<>();
        List<String> positionalArgs = new ArrayList<>();

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int colonIndex = part.indexOf(':');
            if (colonIndex > 0) {
                String key = part.substring(0, colonIndex);
                String value = part.substring(colonIndex + 1);
                namedArgs.put(key, value);
            } else {
                positionalArgs.add(part);
            }
        }

        return new ParsedCommand(name, namedArgs, positionalArgs);
    }

    public static Map<String, Object> matchParams(ParsedCommand parsed, List<ParamDef> paramDefs) {
        Map<String, Object> result = new LinkedHashMap<>();
        int positionalIndex = 0;

        for (ParamDef def : paramDefs) {
            Object value = null;

            if (parsed.namedArgs.containsKey(def.getName())) {
                value = parsed.namedArgs.get(def.getName());
            } else if (positionalIndex < parsed.positionalArgs.size()) {
                value = parsed.positionalArgs.get(positionalIndex);
                positionalIndex++;
            }

            if (value == null) {
                if (def.isRequired()) {
                    throw new IllegalArgumentException("缺少必填参数: " + def.getName());
                }
                continue;
            }

            result.put(def.getName(), convertValue(value.toString(), def.getType(), def.getName()));
        }

        return result;
    }

    private static Object convertValue(String value, ParamType type, String paramName) {
        try {
            switch (type) {
                case STRING:
                    return value;
                case INTEGER:
                    if (value.startsWith("0x") || value.startsWith("0X")) {
                        return Integer.parseInt(value.substring(2), 16);
                    }
                    return Integer.parseInt(value);
                case FLOAT:
                    return Float.parseFloat(value);
                case BOOLEAN:
                    return Boolean.parseBoolean(value);
                case COLOR:
                    return parseColor(value);
                default:
                    return value;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("参数类型错误: " + paramName
                    + " 期望 " + type + ", 实际 '" + value + "'");
        }
    }

    private static int parseColor(String value) {
        String hex = value;
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (hex.length() == 6) {
            hex = "FF" + hex;
        }
        return (int) Long.parseLong(hex, 16);
    }
}
