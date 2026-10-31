package io.takamiya.mio.building.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class NbtReader {

    public static final int TAG_END = 0, TAG_BYTE = 1, TAG_SHORT = 2, TAG_INT = 3, TAG_LONG = 4,
            TAG_FLOAT = 5, TAG_DOUBLE = 6, TAG_BYTE_ARRAY = 7, TAG_STRING = 8, TAG_LIST = 9,
            TAG_COMPOUND = 10, TAG_INT_ARRAY = 11, TAG_LONG_ARRAY = 12;

    /** Long array represented as a zero-copy slice over the decompressed buffer (big-endian). */
    public static final class LongArraySlice {
        public final byte[] buf;
        public final int offset;
        public final int length;

        public LongArraySlice(byte[] buf, int offset, int length) {
            this.buf = buf;
            this.offset = offset;
            this.length = length;
        }

        public int readUInt32(int index) {
            int p = offset + index;
            return ((buf[p] & 0xFF) << 24) | ((buf[p + 1] & 0xFF) << 16)
                    | ((buf[p + 2] & 0xFF) << 8) | (buf[p + 3] & 0xFF);
        }
    }

    public static final class Cursor {
        public final byte[] buf;
        public int off;

        public Cursor(byte[] buf) { this.buf = buf; }
    }

    public static byte[] gunzip(byte[] in) throws IOException {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(in))) {
            byte[] tmp = new byte[8192];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int n;
            while ((n = gz.read(tmp)) > 0) out.write(tmp, 0, n);
            return out.toByteArray();
        }
    }

    public static Map<String, Object> parseFile(byte[] raw) throws IOException {
        byte[] data = raw;
        if (raw.length >= 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B) {
            data = gunzip(raw);
        }
        Cursor c = new Cursor(data);
        int type = u8(c);
        if (type != TAG_COMPOUND) {
            throw new IOException("根标签不是 Compound，类型: " + type);
        }
        readString(c); // root name
        Map<String, Object> root = new LinkedHashMap<>();
        readCompoundContents(c, root);
        return root;
    }

    private static void readCompoundContents(Cursor c, Map<String, Object> target) throws IOException {
        while (true) {
            if (c.off >= c.buf.length) break;
            int type = u8(c);
            if (type == TAG_END) break;
            String name = readString(c);
            target.put(name, readTagged(c, type));
        }
    }

    public static Object readTagged(Cursor c, int type) throws IOException {
        switch (type) {
            case TAG_BYTE: return (byte) c.buf[c.off++];
            case TAG_SHORT: {
                int v = ((c.buf[c.off] & 0xFF) << 8) | (c.buf[c.off + 1] & 0xFF);
                c.off += 2;
                return (short) v;
            }
            case TAG_INT: {
                int v = ((c.buf[c.off] & 0xFF) << 24) | ((c.buf[c.off + 1] & 0xFF) << 16)
                        | ((c.buf[c.off + 2] & 0xFF) << 8) | (c.buf[c.off + 3] & 0xFF);
                c.off += 4;
                return v;
            }
            case TAG_LONG: {
                long v = ((long) (c.buf[c.off] & 0xFF) << 56) | ((long) (c.buf[c.off + 1] & 0xFF) << 48)
                        | ((long) (c.buf[c.off + 2] & 0xFF) << 40) | ((long) (c.buf[c.off + 3] & 0xFF) << 32)
                        | ((long) (c.buf[c.off + 4] & 0xFF) << 24) | ((long) (c.buf[c.off + 5] & 0xFF) << 16)
                        | ((long) (c.buf[c.off + 6] & 0xFF) << 8) | (c.buf[c.off + 7] & 0xFF);
                c.off += 8;
                return v;
            }
            case TAG_FLOAT: {
                int v = ((c.buf[c.off] & 0xFF) << 24) | ((c.buf[c.off + 1] & 0xFF) << 16)
                        | ((c.buf[c.off + 2] & 0xFF) << 8) | (c.buf[c.off + 3] & 0xFF);
                c.off += 4;
                return Float.intBitsToFloat(v);
            }
            case TAG_DOUBLE: {
                long v = ((long) (c.buf[c.off] & 0xFF) << 56) | ((long) (c.buf[c.off + 1] & 0xFF) << 48)
                        | ((long) (c.buf[c.off + 2] & 0xFF) << 40) | ((long) (c.buf[c.off + 3] & 0xFF) << 32)
                        | ((long) (c.buf[c.off + 4] & 0xFF) << 24) | ((long) (c.buf[c.off + 5] & 0xFF) << 16)
                        | ((long) (c.buf[c.off + 6] & 0xFF) << 8) | (c.buf[c.off + 7] & 0xFF);
                c.off += 8;
                return Double.longBitsToDouble(v);
            }
            case TAG_BYTE_ARRAY: {
                int len = readInt(c);
                check(c, len);
                byte[] arr = new byte[len];
                System.arraycopy(c.buf, c.off, arr, 0, len);
                c.off += len;
                return arr;
            }
            case TAG_STRING: return readString(c);
            case TAG_LIST: {
                int elemType = u8(c);
                int len = readInt(c);
                List<Object> list = new ArrayList<>(len);
                for (int i = 0; i < len; i++) {
                    list.add(readTagged(c, elemType));
                }
                return list;
            }
            case TAG_COMPOUND: {
                Map<String, Object> map = new LinkedHashMap<>();
                readCompoundContents(c, map);
                return map;
            }
            case TAG_INT_ARRAY: {
                int len = readInt(c);
                check(c, len * 4);
                int[] arr = new int[len];
                for (int i = 0; i < len; i++) {
                    arr[i] = ((c.buf[c.off] & 0xFF) << 24) | ((c.buf[c.off + 1] & 0xFF) << 16)
                            | ((c.buf[c.off + 2] & 0xFF) << 8) | (c.buf[c.off + 3] & 0xFF);
                    c.off += 4;
                }
                return arr;
            }
            case TAG_LONG_ARRAY: {
                int len = readInt(c);
                check(c, len * 8);
                LongArraySlice slice = new LongArraySlice(c.buf, c.off, len);
                c.off += len * 8;
                return slice;
            }
            default:
                throw new IOException("未知 NBT 标签类型: " + type);
        }
    }

    private static void check(Cursor c, int bytes) throws IOException {
        if (bytes < 0 || c.off + bytes > c.buf.length) {
            throw new IOException("NBT 数据越界 (off=" + c.off + ", need=" + bytes + ", len=" + c.buf.length + ")");
        }
    }

    private static int readInt(Cursor c) throws IOException {
        int v = ((c.buf[c.off] & 0xFF) << 24) | ((c.buf[c.off + 1] & 0xFF) << 16)
                | ((c.buf[c.off + 2] & 0xFF) << 8) | (c.buf[c.off + 3] & 0xFF);
        c.off += 4;
        return v;
    }

    private static int u8(Cursor c) { return c.buf[c.off++] & 0xFF; }

    private static String readString(Cursor c) throws IOException {
        int len = ((c.buf[c.off] & 0xFF) << 8) | (c.buf[c.off + 1] & 0xFF);
        c.off += 2;
        check(c, len);
        String s = new String(c.buf, c.off, len, StandardCharsets.UTF_8);
        c.off += len;
        return s;
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        return (List<Object>) o;
    }

    public static int asInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return (int) o;
    }

    public static long asLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof LongArraySlice) return 0;
        return (long) o;
    }
}