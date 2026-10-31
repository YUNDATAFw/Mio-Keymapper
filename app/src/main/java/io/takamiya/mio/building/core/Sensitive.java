package io.takamiya.mio.building.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 网易敏感词过滤 (port of 敏感词库lite.json handling). */
public final class Sensitive {

    public static final class Rule {
        public final int index;
        public final Pattern pattern;
        public final String raw;

        Rule(int index, Pattern pattern, String raw) {
            this.index = index;
            this.pattern = pattern;
            this.raw = raw;
        }
    }

    private static final int CACHE_CAP = 32 * 1024 * 1024;

    private final List<Rule> rules = new ArrayList<>();
    private byte[] safeCache;
    private final Map<Integer, Rule> sensitiveMap = new HashMap<>();
    private final Set<Integer> safeSet = new HashSet<>();
    private final Map<Integer, Rule> overflowSensitive = new HashMap<>();
    private final Set<Integer> overflowSafe = new HashSet<>();

    public boolean enabled() { return !rules.isEmpty(); }

    public int count() { return rules.size(); }

    public static Sensitive load(File file) {
        Sensitive s = new Sensitive();
        if (file == null || !file.exists()) return s;
        try {
            String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Object parsed = Json.parse(raw);
            if (!(parsed instanceof List)) return s;
            List<?> data = (List<?>) parsed;
            for (int i = 0; i < data.size(); i++) {
                Object item = data.get(i);
                if (!(item instanceof Map)) continue;
                Object pat = ((Map<?, ?>) item).get("pattern");
                if (!(pat instanceof String)) continue;
                String patternStr = (String) pat;
                Pattern compiled = compileJavaPattern(patternStr);
                if (compiled != null) {
                    s.rules.add(new Rule(i, compiled, patternStr));
                }
            }
        } catch (Exception e) {
            System.out.println("读取敏感词库失败: " + e.getMessage() + " 喵！将跳过敏感词过滤喵～");
        }
        return s;
    }

    /** Convert JS inline flags (?imsux) to Java flags; remove the groups. */
    static Pattern compileJavaPattern(String jsPattern) {
        String p = jsPattern;
        boolean ignoreCase = false;
        java.util.regex.Matcher flagMatcher = Pattern.compile("\\(\\?[imsux]+\\)").matcher(p);
        StringBuilder sb = new StringBuilder();
        while (flagMatcher.find()) {
            String f = flagMatcher.group().substring(2, flagMatcher.group().length() - 1);
            if (f.indexOf('i') >= 0) ignoreCase = true;
            flagMatcher.appendReplacement(sb, "");
        }
        flagMatcher.appendTail(sb);
        p = sb.toString();
        try {
            return Pattern.compile((ignoreCase ? "(?i)" : "") + p);
        } catch (Exception e) {
            return null;
        }
    }

    public void initCache(int maxExpectedScore) {
        long size = (long) maxExpectedScore + 10000;
        if (size > CACHE_CAP) size = CACHE_CAP;
        safeCache = new byte[(int) size];
    }

    /** returns matching Rule or null; caches result. score = coordinate score value. */
    public Rule checkScore(int score) {
        if (score >= 0 && score < safeCache.length) {
            byte status = safeCache[score];
            if (status == 1) return null;
            if (status == 2) return sensitiveMap.get(score);
        } else {
            if (overflowSafe.contains(score)) return null;
            if (overflowSensitive.containsKey(score)) return overflowSensitive.get(score);
        }
        String str = String.valueOf(score);
        Rule matched = null;
        for (Rule r : rules) {
            if (r.pattern.matcher(str).matches()) {
                matched = r;
                break;
            }
        }
        if (score >= 0 && score < safeCache.length) {
            if (matched != null) {
                safeCache[score] = 2;
                sensitiveMap.put(score, matched);
            } else {
                safeCache[score] = 1;
            }
        } else {
            if (matched != null) {
                overflowSensitive.put(score, matched);
            } else {
                overflowSafe.add(score);
            }
        }
        return matched;
    }
}