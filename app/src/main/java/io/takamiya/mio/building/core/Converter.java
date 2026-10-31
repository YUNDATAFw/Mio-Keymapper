package io.takamiya.mio.building.core;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 核心转换逻辑（setblock 模式）：把 .litematic / .schem 转换为基岩版命令。
 * 移植自 MCConverter，输出改为内存结果而非文件。
 */
public final class Converter {

    private static final int[] INT_MASKS = new int[33];
    private static final int MAX_COMMAND_LENGTH = 32500;
    private static final int CACHE_CAP = 32 * 1024 * 1024;

    /** 进度回调：percent 取 0~100。 */
    public interface ProgressListener {
        void onProgress(int percent);
    }

    /** 转换结果：方块命令 + 建筑元数据 + 敏感词手动命令。 */
    public static final class Result {
        public final int sx, sy, sz;
        public final long totalCoords;
        public final long blockCount;
        public final int skippedBlocks;
        public final int sensitiveFixCount;
        public final List<String> blockCommands;
        public final List<String> manualExtraCommands;

        Result(int sx, int sy, int sz, long totalCoords, long blockCount,
               int skippedBlocks, int sensitiveFixCount,
               List<String> blockCommands, List<String> manualExtraCommands) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.totalCoords = totalCoords;
            this.blockCount = blockCount;
            this.skippedBlocks = skippedBlocks;
            this.sensitiveFixCount = sensitiveFixCount;
            this.blockCommands = blockCommands;
            this.manualExtraCommands = manualExtraCommands;
        }
    }

    static {
        for (int i = 0; i <= 32; i++) {
            INT_MASKS[i] = i == 32 ? -1 : (1 << i) - 1;
        }
    }

    // ---------------- 区间收集器 ----------------

    public static final class Intervals {
        final int[] starts, ends;
        final int len;
        Intervals(int[] starts, int[] ends, int len) {
            this.starts = starts;
            this.ends = ends;
            this.len = len;
        }
    }

    static final class Collector {
        int[] starts = new int[1024];
        int[] ends = new int[1024];
        int ptr = 0;
        int start = -1;
        int end = -1;
        Intervals intervals;

        void add(int score) {
            if (start == -1) {
                start = score;
                end = score;
            } else if (score == end + 1) {
                end = score;
            } else {
                if (ptr >= starts.length) {
                    starts = Arrays.copyOf(starts, starts.length * 2);
                    ends = Arrays.copyOf(ends, ends.length * 2);
                }
                starts[ptr] = start;
                ends[ptr] = end;
                ptr++;
                start = score;
                end = score;
            }
        }

        Intervals finish() {
            if (intervals != null) return intervals;
            if (start != -1) {
                if (ptr >= starts.length) {
                    starts = Arrays.copyOf(starts, starts.length + 1);
                    ends = Arrays.copyOf(ends, ends.length + 1);
                }
                starts[ptr] = start;
                ends[ptr] = end;
                ptr++;
                start = -1;
                end = -1;
            }
            intervals = new Intervals(starts, ends, ptr);
            return intervals;
        }
    }

    // ---------------- 命令生成 ----------------

    static final class GenResult {
        final List<String> commands = new ArrayList<>();
        final List<String[]> manualBlocks = new ArrayList<>(); // {id, stateStr, x, y, z}
        int sensitiveFixCount = 0;
    }

    static GenResult generateCommands(String prefix, String suffix, Intervals col, Sensitive sensitive,
                                      int sx, int sz, String groupKey) {
        GenResult res = new GenResult();
        int[] stackS = new int[Math.max(col.len + 1000, 1024)];
        int[] stackE = new int[Math.max(col.len + 1000, 1024)];
        int stackPtr = 0;
        for (int i = col.len - 1; i >= 0; i--) {
            stackS[stackPtr] = col.starts[i];
            stackE[stackPtr] = col.ends[i];
            stackPtr++;
        }

        final List<String> finalCommands = res.commands;
        final List<String[]> manualBlocks = res.manualBlocks;
        final int[] fixCount = { 0 };
        final int baseLen = prefix.length() + suffix.length();

        List<String> groupStrs = new ArrayList<>();
        int currentLen = baseLen;

        while (stackPtr > 0) {
            stackPtr--;
            int s = stackS[stackPtr];
            int e = stackE[stackPtr];
            String str = s == e ? "a=!" + s : "a=!" + s + ".." + e;
            int addedLen = groupStrs.size() > 0 ? str.length() + 1 : str.length();
            if (currentLen + addedLen > MAX_COMMAND_LENGTH && !groupStrs.isEmpty()) {
                stackPtr++;
                finalCommands.add(prefix + String.join(",", groupStrs) + suffix);
                groupStrs = new ArrayList<>();
                currentLen = baseLen;
                continue;
            }

            Sensitive.Rule matchedRule = isIntervalSensitive(s, e, sensitive);
            if (matchedRule != null) {
                fixCount[0]++;
                if (s < e) {
                    Sensitive.Rule ruleS = sensitive != null ? sensitive.checkScore(s) : null;
                    Sensitive.Rule ruleE = sensitive != null ? sensitive.checkScore(e) : null;
                    if (stackPtr + 3 > stackS.length) {
                        stackS = Arrays.copyOf(stackS, stackS.length * 2);
                        stackE = Arrays.copyOf(stackE, stackE.length * 2);
                    }
                    if (ruleE != null && ruleS == null) {
                        stackS[stackPtr] = e; stackE[stackPtr] = e; stackPtr++;
                        stackS[stackPtr] = s; stackE[stackPtr] = e - 1; stackPtr++;
                    } else if (ruleS != null && ruleE == null) {
                        stackS[stackPtr] = s + 1; stackE[stackPtr] = e; stackPtr++;
                        stackS[stackPtr] = s; stackE[stackPtr] = s; stackPtr++;
                    } else {
                        stackS[stackPtr] = e; stackE[stackPtr] = e; stackPtr++;
                        if (s + 1 <= e - 1) {
                            stackS[stackPtr] = s + 1; stackE[stackPtr] = e - 1; stackPtr++;
                        }
                        stackS[stackPtr] = s; stackE[stackPtr] = s; stackPtr++;
                    }
                } else {
                    long sliceSize = (long) sx * sz;
                    int blockIdx = s - 1;
                    int y = (int) (blockIdx / sliceSize);
                    long rem = blockIdx % sliceSize;
                    int z = (int) (rem / sx);
                    int x = (int) (rem % sx);
                    int sep = groupKey.indexOf("::");
                    String id = sep >= 0 ? groupKey.substring(0, sep) : groupKey;
                    String stateStr = sep >= 0 ? groupKey.substring(sep + 2) : "";
                    manualBlocks.add(new String[]{ id, stateStr, String.valueOf(x), String.valueOf(y), String.valueOf(z) });
                    if (!groupStrs.isEmpty()) {
                        finalCommands.add(prefix + String.join(",", groupStrs) + suffix);
                        groupStrs = new ArrayList<>();
                        currentLen = baseLen;
                    }
                }
                continue;
            }
            groupStrs.add(str);
            currentLen += addedLen;
        }
        if (!groupStrs.isEmpty()) {
            finalCommands.add(prefix + String.join(",", groupStrs) + suffix);
        }
        res.sensitiveFixCount = fixCount[0];
        return res;
    }

    private static Sensitive.Rule isIntervalSensitive(int s, int e, Sensitive sensitive) {
        if (sensitive == null || !sensitive.enabled()) return null;
        Sensitive.Rule ruleS = sensitive.checkScore(s);
        if (s == e) return ruleS;
        Sensitive.Rule ruleE = sensitive.checkScore(e);
        if (ruleS == null && ruleE == null) return null;
        if (ruleS != null && ruleE == null) return ruleS;
        if (ruleS == null && ruleE != null) return ruleE;
        return ruleS.index <= ruleE.index ? ruleS : ruleE;
    }

    // ---------------- 方块状态格式化 ----------------

    static String formatBlockState(Map<String, Object> state) {
        if (state == null || state.isEmpty()) return "";
        String[] keys = state.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) sb.append(',');
            Object v = state.get(keys[i]);
            sb.append('"').append(keys[i]).append('"').append('=');
            if (v instanceof String) {
                sb.append('"').append((String) v).append('"');
            } else {
                sb.append(v);
            }
        }
        return sb.append(']').toString();
    }

    // ---------------- 位提取 (litematic BlockStates) ----------------

    static int[] extractBlockIndices(NbtReader.LongArraySlice blockStates, int totalBlocks, int bitsPerIndex) {
        int[] indices = new int[totalBlocks];
        int len = blockStates.length;
        long[] words = new long[len * 2];
        for (int i = 0; i < len; i++) {
            int high = blockStates.readUInt32(i * 8);
            int low = blockStates.readUInt32(i * 8 + 4);
            words[i * 2] = low & 0xFFFFFFFFL;
            words[i * 2 + 1] = high & 0xFFFFFFFFL;
        }
        long bitPos = 0;
        for (int i = 0; i < totalBlocks; i++) {
            int wordIdx = (int) (bitPos >> 5);
            int bitOffset = (int) (bitPos & 31);
            int bitsFirst = Math.min(bitsPerIndex, 32 - bitOffset);
            int maskFirst = INT_MASKS[bitsFirst];
            long value = (words[wordIdx] >>> bitOffset) & (maskFirst & 0xFFFFFFFFL);
            int remaining = bitsPerIndex - bitsFirst;
            if (remaining > 0) {
                long next = (wordIdx + 1 < words.length ? words[wordIdx + 1] : 0) & 0xFFFFFFFFL;
                int maskNext = INT_MASKS[remaining];
                value |= (next & (maskNext & 0xFFFFFFFFL)) << bitsFirst;
            }
            indices[i] = (int) value;
            bitPos += bitsPerIndex;
        }
        return indices;
    }

    static int bitsPerIndex(int paletteSize) {
        int bits = 2;
        while ((1L << bits) < paletteSize) bits++;
        return Math.max(2, bits);
    }

    // ---------------- 方块解析 ----------------

    static final class BlockInfo {
        boolean isUnknown;
        boolean isAir;
        Collector collector;
    }

    /** parse "minecraft:name[key=value,...]" into name + props (schem 调色板). */
    static Map.Entry<String, LinkedHashMap<String, String>> parseBlockStateString(String bs) {
        int br = bs.indexOf('[');
        String name = br < 0 ? bs : bs.substring(0, br);
        LinkedHashMap<String, String> props = new LinkedHashMap<>();
        if (br >= 0) {
            int end = bs.lastIndexOf(']');
            String inner = end > br + 1 ? bs.substring(br + 1, end) : "";
            if (!inner.isEmpty()) {
                for (String kv : inner.split(",", -1)) {
                    if (kv.isEmpty()) continue;
                    int eq = kv.indexOf('=');
                    if (eq > 0) props.put(kv.substring(0, eq), kv.substring(eq + 1));
                    else props.put(kv, "true");
                }
            }
        }
        return new AbstractMap.SimpleEntry<>(name, props);
    }

    static boolean isAir(String name) {
        return "minecraft:air".equals(name) || "minecraft:cave_air".equals(name)
                || "minecraft:void_air".equals(name);
    }

    // ---------------- 数字格式化 ----------------

    static String formatNumber4Digits(long num) {
        String str = Long.toString(num);
        StringBuilder result = new StringBuilder();
        while (str.length() > 4) {
            result.insert(0, "," + str.substring(str.length() - 4));
            str = str.substring(0, str.length() - 4);
        }
        return str + result;
    }

    // ---------------- 进度输出 ----------------

    static final class Progress {
        final long total;
        long done = 0;
        long lastPercent = -1;
        final ProgressListener listener;

        Progress(long total, ProgressListener listener) {
            this.total = total;
            this.listener = listener;
        }

        void tick() {
            done++;
            if (total > 0 && listener != null) {
                long pct = done * 100 / total;
                if (pct != lastPercent) {
                    lastPercent = pct;
                    listener.onProgress((int) pct);
                }
            }
        }

        void finish() {
            if (listener != null && total > 0) {
                listener.onProgress(100);
            }
        }
    }

    // ==================== 入口 ====================

    /**
     * 转换入口（setblock 模式）。
     *
     * @param raw      投影文件原始字节（.schem / .litematic，自动识别 gzip）
     * @param fileName 原始文件名（用于判断 schem/litematic 后缀）
     */
    public static Result convert(byte[] raw, String fileName, Mappings mapping,
                                 Sensitive sensitive, ProgressListener listener) throws IOException {
        String lower = fileName.toLowerCase();
        byte[] data = raw;
        Map<String, Object> root = NbtReader.parseFile(data);

        boolean isSchem = lower.endsWith(".schem") || lower.endsWith(".schematic");
        if (isSchem) {
            return convertSchem(root, mapping, sensitive, listener);
        }
        return convertLitematic(root, mapping, sensitive, listener);
    }

    // ==================== SCHEMATIC ====================

    static Result convertSchem(Map<String, Object> root, Mappings mapping,
                               Sensitive sensitive, ProgressListener listener) throws IOException {
        int sx = intOf(root.get("Width"));
        int sy = intOf(root.get("Height"));
        int sz = intOf(root.get("Length"));
        if (sx <= 0 || sy <= 0 || sz <= 0) {
            throw new IOException("schem 尺寸异常: " + sx + "x" + sy + "x" + sz);
        }
        Object paletteObj = root.get("Palette");
        Object blockDataObj = root.get("BlockData");
        if (!(paletteObj instanceof Map) || !(blockDataObj instanceof byte[])) {
            throw new IOException("不支持的 schem 格式（需包含 Palette + BlockData）");
        }
        Map<?, ?> palette = (Map<?, ?>) paletteObj;
        byte[] blockData = (byte[]) blockDataObj;

        long totalCoords = (long) sx * sy * sz;
        if (totalCoords > Integer.MAX_VALUE) {
            throw new IOException("方块总数过大: " + totalCoords);
        }

        BlockInfo[] paletteCache = new BlockInfo[palette.size()];
        Map<String, Collector> groupCollectors = new LinkedHashMap<>();
        sensitive.initCache((int) Math.min(totalCoords + 10000L, CACHE_CAP));
        for (Map.Entry<?, ?> kv : palette.entrySet()) {
            int idx = ((Number) kv.getValue()).intValue();
            if (idx < 0 || idx >= paletteCache.length) continue;
            paletteCache[idx] = resolveBlockState((String) kv.getKey(), mapping, groupCollectors);
        }

        long totalWritten = 0;
        int skippedBlocks = 0;
        int processedCoords = 0;
        Progress progress = new Progress(totalCoords, listener);
        int[] dataPos = { 0 };
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int pIdx = readSchemVarInt(blockData, dataPos);
                    processedCoords++;
                    if ((processedCoords & 0xFFFF) == 0) progress.tick();
                    BlockInfo info = pIdx < paletteCache.length ? paletteCache[pIdx] : null;
                    if (info == null || info.isAir) continue;
                    if (info.isUnknown) { skippedBlocks++; continue; }
                    int score = x + z * sx + y * sx * sz + 1;
                    info.collector.add(score);
                    totalWritten++;
                }
            }
        }
        progress.finish();
        return buildResult(groupCollectors, sx, sy, sz, totalCoords,
                totalWritten, skippedBlocks, sensitive);
    }

    // ==================== LITEMATIC ====================

    static Result convertLitematic(Map<String, Object> root,
                                   Mappings mapping, Sensitive sensitive,
                                   ProgressListener listener) throws IOException {
        Object regionsObj = root.get("Regions");
        if (!(regionsObj instanceof Map)) {
            throw new IOException("找不到 Regions 区域喵！");
        }
        Map<String, Object> regions = NbtReader.map(regionsObj);
        long totalCoords = 0;
        for (Map.Entry<String, Object> r : regions.entrySet()) {
            Map<String, Object> region = NbtReader.map(r.getValue());
            int[] size = readSize(region.get("Size"));
            totalCoords += Math.abs((long) size[0]) * Math.abs((long) size[1]) * Math.abs((long) size[2]);
        }
        sensitive.initCache((int) Math.min(totalCoords + 10000L, CACHE_CAP));

        Map<String, Collector> groupCollectors = new LinkedHashMap<>();
        long totalWritten = 0;
        int skippedBlocks = 0;
        int processedCoords = 0;
        int mainSx = 0, mainSy = 0, mainSz = 0;
        Progress progress = new Progress(totalCoords, listener);

        for (Map.Entry<String, Object> r : regions.entrySet()) {
            Map<String, Object> region = NbtReader.map(r.getValue());
            int[] size = readSize(region.get("Size"));
            int sx = Math.abs(size[0]), sy = Math.abs(size[1]), sz = Math.abs(size[2]);
            if (mainSx == 0) mainSx = sx;
            if (mainSy == 0) mainSy = sy;
            if (mainSz == 0) mainSz = sz;
            long totalBlocks = (long) sx * sy * sz;

            Object pRaw = region.get("BlockStatePalette");
            List<Object> palette = pRaw instanceof List ? NbtReader.list(pRaw) : null;
            NbtReader.LongArraySlice blockStates = region.get("BlockStates") instanceof NbtReader.LongArraySlice
                    ? (NbtReader.LongArraySlice) region.get("BlockStates") : null;
            if (palette == null || palette.isEmpty() || blockStates == null) continue;

            BlockInfo[] paletteCache = new BlockInfo[palette.size()];
            for (int pi = 0; pi < palette.size(); pi++) {
                Map<String, Object> node = NbtReader.map(palette.get(pi));
                paletteCache[pi] = resolvePaletteNode(node, mapping, groupCollectors);
            }

            int bits = bitsPerIndex(palette.size());
            int[] indices = extractBlockIndices(blockStates, (int) totalBlocks, bits);
            int idx = 0;
            int sliceSize = sx * sz;
            for (int y = 0; y < sy; y++) {
                int yOffset = y * sliceSize;
                for (int z = 0; z < sz; z++) {
                    int zOffset = z * sx;
                    int rowBaseScore = zOffset + yOffset + 1;
                    for (int x = 0; x < sx; x++) {
                        int pIdx = indices[idx++];
                        processedCoords++;
                        if ((processedCoords & 0xFFFF) == 0) progress.tick();
                        if (pIdx < paletteCache.length) {
                            BlockInfo info = paletteCache[pIdx];
                            if (info != null && !info.isAir) {
                                if (info.isUnknown) {
                                    skippedBlocks++;
                                } else {
                                    int score = x + rowBaseScore;
                                    info.collector.add(score);
                                    totalWritten++;
                                }
                            }
                        }
                    }
                }
            }
        }
        progress.finish();
        return buildResult(groupCollectors, mainSx, mainSy, mainSz, totalCoords,
                totalWritten, skippedBlocks, sensitive);
    }

    // ---------------- schem varint ----------------

    static int readSchemVarInt(byte[] data, int[] pos) {
        int value = 0;
        int shift = 0;
        while (true) {
            byte b = data[pos[0]++];
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
            if (shift > 28) break;
        }
        return value;
    }

    // ---------------- 尺寸读取 ----------------

    /** Size 可能是 compound{x,y,z} 或 int[3] (x,y,z) */
    static int[] readSize(Object sizeObj) {
        int[] size = new int[3];
        if (sizeObj instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) sizeObj;
            size[0] = ((Number) m.get("x")).intValue();
            size[1] = ((Number) m.get("y")).intValue();
            size[2] = ((Number) m.get("z")).intValue();
        } else if (sizeObj instanceof int[]) {
            int[] arr = (int[]) sizeObj;
            System.arraycopy(arr, 0, size, 0, Math.min(3, arr.length));
        } else if (sizeObj instanceof List) {
            List<?> l = (List<?>) sizeObj;
            for (int i = 0; i < Math.min(3, l.size()); i++) {
                size[i] = ((Number) l.get(i)).intValue();
            }
        }
        return size;
    }

    static int intOf(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    // ---------------- 方块解析（映射到基岩版） ----------------

    static BlockInfo resolvePaletteNode(Map<String, Object> node, Mappings mapping,
                                        Map<String, Collector> groupCollectors) {
        Object nameObj = node.get("Name");
        if (!(nameObj instanceof String)) {
            BlockInfo air = new BlockInfo();
            air.isAir = true;
            return air;
        }
        String name = (String) nameObj;
        return resolveMapped(name, propsFromMapObject(node.get("Properties")), mapping, groupCollectors);
    }

    static Map<String, String> propsFromMapObject(Object propsObj) {
        LinkedHashMap<String, String> props = new LinkedHashMap<>();
        if (propsObj instanceof Map) {
            for (Map.Entry<?, ?> kv : ((Map<?, ?>) propsObj).entrySet()) {
                props.put(String.valueOf(kv.getKey()), String.valueOf(kv.getValue()));
            }
        }
        return props;
    }

    static BlockInfo resolveBlockState(String blockState, Mappings mapping,
                                       Map<String, Collector> groupCollectors) {
        Map.Entry<String, LinkedHashMap<String, String>> parsed = parseBlockStateString(blockState);
        return resolveMapped(parsed.getKey(), parsed.getValue(), mapping, groupCollectors);
    }

    static BlockInfo resolveMapped(String name, Map<String, String> props, Mappings mapping,
                                   Map<String, Collector> groupCollectors) {
        BlockInfo info = new BlockInfo();
        if (isAir(name)) {
            info.isAir = true;
            return info;
        }
        Mappings.Entry be = mapping.lookup(name, props);
        if (be == null) {
            info.isUnknown = true;
            return info;
        }
        String stateStr = formatBlockState(be.state);
        String groupKey = be.identifier + "::" + stateStr;
        Collector col = groupCollectors.get(groupKey);
        if (col == null) {
            col = new Collector();
            groupCollectors.put(groupKey, col);
        }
        info.collector = col;
        return info;
    }

    // ==================== 结果组装 ====================

    static Result buildResult(Map<String, Collector> groupCollectors,
                              int mainSx, int mainSy, int mainSz, long totalCoords,
                              long totalWritten, int skippedBlocks, Sensitive sensitive) {

        List<String> fillLines = new ArrayList<>();
        List<String[]> allManual = new ArrayList<>();
        int fixCount = 0;

        for (Map.Entry<String, Collector> kv : groupCollectors.entrySet()) {
            String groupKey = kv.getKey();
            Collector col = kv.getValue();
            Intervals items = col.finish();
            if (items.len == 0) continue;
            int sep = groupKey.indexOf("::");
            String blockId = sep >= 0 ? groupKey.substring(0, sep) : groupKey;
            String stateStr = sep >= 0 ? groupKey.substring(sep + 2) : "";
            String blockPart = stateStr.isEmpty() ? blockId : blockId + " " + stateStr;
            String prefix = "execute as @e[name=a] unless entity @s[scores={";
            String suffix = "}] at @s run setblock ~ ~ ~ " + blockPart;
            GenResult r = generateCommands(prefix, suffix, items, sensitive, mainSx, mainSz, groupKey);
            fillLines.addAll(r.commands);
            allManual.addAll(r.manualBlocks);
            fixCount += r.sensitiveFixCount;
        }

        List<String> manualExtra = new ArrayList<>();
        for (String[] mb : allManual) {
            String id = mb[0];
            String stateStr = mb[1];
            int x = Integer.parseInt(mb[2]);
            int y = Integer.parseInt(mb[3]);
            int z = Integer.parseInt(mb[4]);
            manualExtra.add("/setblock ~" + (x + 1) + " ~" + y + " ~" + z + " " + id
                    + (stateStr.isEmpty() ? "" : " " + stateStr));
        }

        return new Result(mainSx, mainSy, mainSz, totalCoords, totalWritten,
                skippedBlocks, fixCount, fillLines, manualExtra);
    }

}
