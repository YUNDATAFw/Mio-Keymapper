package io.takamiya.mio.building.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON parser producing Map<String,Object>/List/String/Long/Double/Boolean/null. */
public final class Json {

    public static Object parse(String text) {
        return new Parser(text).parseValue();
    }

    static final class Parser {
        final String s;
        int i;

        Parser(String s) { this.s = s; }

        Object parseValue() {
            skipWs();
            char c = s.charAt(i);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return parseNumber();
            }
        }

        Map<String, Object> parseObject() {
            i++; skipWs();
            Map<String, Object> map = new LinkedHashMap<>();
            if (peek() == '}') { i++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                if (s.charAt(i) != ':') throw error("expected ':'");
                i++;
                map.put(key, parseValue());
                skipWs();
                char ch = s.charAt(i++);
                if (ch == '}') break;
                if (ch != ',') throw error("expected ',' or '}'");
            }
            return map;
        }

        List<Object> parseArray() {
            i++; skipWs();
            List<Object> list = new ArrayList<>();
            if (peek() == ']') { i++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                char ch = s.charAt(i++);
                if (ch == ']') break;
                if (ch != ',') throw error("expected ',' or ']'");
            }
            return list;
        }

        String parseString() {
            if (s.charAt(i) != '"') throw error("expected string");
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u': {
                            int cp = Integer.parseInt(s.substring(i, i + 4), 16);
                            i += 4;
                            sb.append((char) cp);
                            break;
                        }
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object parseNumber() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || (c >= '0' && c <= '9')) {
                    i++;
                } else break;
            }
            String num = s.substring(start, i);
            if (num.isEmpty()) throw error("invalid number");
            try {
                if (num.indexOf('.') < 0 && num.indexOf('e') < 0 && num.indexOf('E') < 0) {
                    return Long.parseLong(num);
                }
                return Double.parseDouble(num);
            } catch (NumberFormatException e) {
                throw error("invalid number: " + num);
            }
        }

        void expect(String word) {
            if (!s.startsWith(word, i)) throw error("expected " + word);
            i += word.length();
        }

        char peek() { skipWs(); return s.charAt(i); }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else break;
            }
        }

        RuntimeException error(String msg) {
            throw new RuntimeException("JSON error at " + i + ": " + msg);
        }
    }
}