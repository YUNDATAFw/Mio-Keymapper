package io.takamiya.mio.config.lhrb;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * LHRB (v0x04) 二进制配置格式编码器，与 {@link LhrbReader} / lhrb_tool 完全兼容。
 *
 * <p>文件布局（全部小端序）：
 * <pre>
 *   Header 16B: magic "LHRB" | version 0x04 | flags 0x01(zlib) | reserved 2B
 *               | payloadLen u32 | crc32 u32
 *   Payload:    zlib 压缩后的通用 JSON 编码 VALUE
 *   Signature:  sigAlg 0x00 | pubKeyLen u16 | pubKey(SPKI DER)
 *               | sigLen u16 | signature(ECDSA P-256 SHA-256, DER)
 * </pre>
 * 签名对象 M = Header + Payload(压缩后) 的原始字节。
 */
public final class LhrbWriter {

    private static final int HEADER_LEN = 16;
    private static final int VERSION = 0x04;
    private static final int FLAG_ZLIB = 0x01;
    private static final int SIG_ALG_ECDSA_P256 = 0x00;

    private static final int T_NULL = 0x00;
    private static final int T_TRUE = 0x01;
    private static final int T_FALSE = 0x02;
    private static final int T_INT = 0x03;
    private static final int T_FLOAT = 0x04;
    private static final int T_STRING = 0x05;
    private static final int T_ARRAY = 0x06;
    private static final int T_OBJECT = 0x07;

    private LhrbWriter() {
    }

    /** 将任意 JSON 文档编码为单个通用 VALUE。 */
    public static byte[] encode(JSONObject root) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        encodeValue(root, bos);
        return bos.toByteArray();
    }

    /** zlib 压缩（与 Python zlib.compress 兼容）。 */
    public static byte[] compress(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            bos.write(buf, 0, n);
        }
        deflater.end();
        return bos.toByteArray();
    }

    /** 构建 16B header，crc32 作用于压缩后的 payload。 */
    public static byte[] buildHeader(byte[] compressedPayload) {
        byte[] header = new byte[HEADER_LEN];
        header[0] = 'L';
        header[1] = 'H';
        header[2] = 'R';
        header[3] = 'B';
        header[4] = (byte) VERSION;
        header[5] = (byte) FLAG_ZLIB;
        putU32(header, 8, compressedPayload.length);
        CRC32 crc = new CRC32();
        crc.update(compressedPayload);
        putU32(header, 12, crc.getValue());
        return header;
    }

    /** 构建签名块：sigAlg + pubKeyLen + pubKey + sigLen + signature。 */
    public static byte[] buildSignatureBlock(byte[] pubKey, byte[] signature) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(SIG_ALG_ECDSA_P256);
        writeU16(bos, pubKey.length);
        bos.write(pubKey);
        writeU16(bos, signature.length);
        bos.write(signature);
        return bos.toByteArray();
    }

    private static void encodeValue(Object v, ByteArrayOutputStream bos) throws IOException {
        if (v == null || v == JSONObject.NULL) {
            bos.write(T_NULL);
        } else if (v instanceof Boolean) {
            bos.write((Boolean) v ? T_TRUE : T_FALSE);
        } else if (v instanceof Integer || v instanceof Long
                || v instanceof Short || v instanceof Byte) {
            bos.write(T_INT);
            writeI64(bos, ((Number) v).longValue());
        } else if (v instanceof Double || v instanceof Float) {
            bos.write(T_FLOAT);
            writeF64(bos, ((Number) v).doubleValue());
        } else if (v instanceof String) {
            byte[] b = ((String) v).getBytes(StandardCharsets.UTF_8);
            if (b.length > 0xFFFF) {
                throw new IllegalArgumentException("string too long: " + b.length);
            }
            bos.write(T_STRING);
            writeU16(bos, b.length);
            bos.write(b);
        } else if (v instanceof JSONArray) {
            JSONArray arr = (JSONArray) v;
            int n = arr.length();
            if (n > 0xFFFF) {
                throw new IllegalArgumentException("array too long: " + n);
            }
            bos.write(T_ARRAY);
            writeU16(bos, n);
            for (int i = 0; i < n; i++) {
                encodeValue(arr.opt(i), bos);
            }
        } else if (v instanceof JSONObject) {
            JSONObject obj = (JSONObject) v;
            int n = obj.length();
            if (n > 0xFFFF) {
                throw new IllegalArgumentException("object too many keys: " + n);
            }
            bos.write(T_OBJECT);
            writeU16(bos, n);
            Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String key = it.next();
                byte[] kb = key.getBytes(StandardCharsets.UTF_8);
                writeU16(bos, kb.length);
                bos.write(kb);
                encodeValue(obj.opt(key), bos);
            }
        } else {
            throw new IllegalArgumentException("unsupported JSON value type: "
                    + (v == null ? "null" : v.getClass().getName()));
        }
    }

    private static void putU32(byte[] out, int offset, long value) {
        out[offset] = (byte) (value & 0xFF);
        out[offset + 1] = (byte) ((value >> 8) & 0xFF);
        out[offset + 2] = (byte) ((value >> 16) & 0xFF);
        out[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static void writeU16(ByteArrayOutputStream bos, int value) throws IOException {
        bos.write(value & 0xFF);
        bos.write((value >> 8) & 0xFF);
    }

    private static void writeI64(ByteArrayOutputStream bos, long value) throws IOException {
        for (int i = 0; i < 8; i++) {
            bos.write((int) ((value >> (8 * i)) & 0xFF));
        }
    }

    private static void writeF64(ByteArrayOutputStream bos, double value) throws IOException {
        writeI64(bos, Double.doubleToLongBits(value));
    }
}
