package io.takamiya.mio.config.lhrb;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Inflater;

/**
 * LHRB (v0x04) 二进制配置格式解析与校验。
 *
 * <p>文件布局（全部小端序）：
 * <pre>
 *   Header 16B: magic "LHRB" | version u8 | flags u8 | reserved 2B
 *               | payloadLen u32 | crc32 u32
 *   Payload:    payloadLen 字节（flags.bit0==1 时为 zlib 压缩后的字节）
 *   Signature:  sigAlg u8 | pubKeyLen u16 | pubKey(SPKI DER)
 *               | sigLen u16 | signature(ECDSA P-256 SHA-256, DER)
 * </pre>
 * 签名对象 M = Header + Payload 的原始落盘字节。
 * 使用文件内嵌公钥验签，仅保证内容未被篡改，不做白名单校验。
 */
public final class LhrbReader {

    private static final int HEADER_LEN = 16;
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

    private LhrbReader() {
    }

    public static boolean isLhrb(byte[] data) {
        return data != null && data.length >= HEADER_LEN
                && data[0] == 'L' && data[1] == 'H' && data[2] == 'R' && data[3] == 'B';
    }

    /**
     * 解析并校验 LHRB 文件，返回解码后的 JSON 文本与版本号。
     * 使用文件内嵌公钥验签，仅保证内容未被篡改，不做白名单校验。
     *
     * @throws LhrbParseException 任何格式/校验失败
     */
    public static ParseResult parse(byte[] data) throws LhrbParseException {
        return parse(data, Collections.emptySet());
    }

    /**
     * 解析并校验 LHRB 文件，返回解码后的 JSON 文本与版本号。
     * 使用文件内嵌公钥验签，仅保证内容未被篡改，不做白名单校验。
     * <p>trustedFingerprints 参数为兼容历史调用保留，当前实现不参与校验。
     *
     * @throws LhrbParseException 任何格式/校验失败
     */
    public static ParseResult parse(byte[] data, Set<String> trustedFingerprints) throws LhrbParseException {
        if (data == null || data.length < HEADER_LEN) {
            throw new LhrbParseException("文件过小（<16字节），不是有效的LHRB文件");
        }
        if (!isLhrb(data)) {
            throw new LhrbParseException("魔数错误，不是有效的LHRB文件");
        }

        int version = data[4] & 0xFF;
        int flags = data[5] & 0xFF;
        long payloadLen = readU32(data, 8);
        long crcStored = readU32(data, 12);

        if (version < 4) {
            throw new LhrbParseException("不支持的LHRB版本 0x" + String.format("%02x", version)
                    + "，仅支持 v0x04");
        }

        if (payloadLen > data.length - HEADER_LEN) {
            throw new LhrbParseException("文件截断：声明数据区 " + payloadLen
                    + " 字节，实际只有 " + (data.length - HEADER_LEN));
        }

        int payloadEnd = HEADER_LEN + (int) payloadLen;
        byte[] m = Arrays.copyOfRange(data, 0, payloadEnd);
        byte[] payload = Arrays.copyOfRange(data, HEADER_LEN, payloadEnd);

        CRC32 crc = new CRC32();
        crc.update(payload);
        if (crc.getValue() != crcStored) {
            throw new LhrbParseException("数据校验失败，文件可能已损坏，请重新获取");
        }

        SignatureBlock sb = readSignatureBlock(data, payloadEnd);
        if (sb.sigAlg != SIG_ALG_ECDSA_P256) {
            throw new LhrbParseException("不支持的签名算法 " + sb.sigAlg);
        }
        verifySignature(sb.pubKey, sb.signature, m);

        byte[] rawPayload = (flags & FLAG_ZLIB) != 0 ? inflate(payload) : payload;

        Object rootValue = decodeValue(new Reader(rawPayload, 0));
        if (!(rootValue instanceof JSONObject)) {
            throw new LhrbParseException("LHRB 根节点必须是 JSON 对象");
        }
        return new ParseResult(version, ((JSONObject) rootValue).toString());
    }

    private static long readU32(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }

    private static SignatureBlock readSignatureBlock(byte[] data, int offset) throws LhrbParseException {
        Reader r = new Reader(data, offset);
        int sigAlg = r.u8();
        int pubKeyLen = r.u16();
        byte[] pubKey = new byte[pubKeyLen];
        try {
            r.readBytes(pubKey);
        } catch (LhrbParseException e) {
            throw new LhrbParseException("签名块公钥截断");
        }
        int sigLen = r.u16();
        byte[] signature = new byte[sigLen];
        try {
            r.readBytes(signature);
        } catch (LhrbParseException e) {
            throw new LhrbParseException("签名块签名截断");
        }
        return new SignatureBlock(sigAlg, pubKey, signature);
    }

    private static void verifySignature(byte[] pubKey, byte[] signature, byte[] m) throws LhrbParseException {
        try {
            KeyFactory kf = KeyFactory.getInstance("EC");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubKey));
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(m);
            if (!verifier.verify(signature)) {
                throw new LhrbParseException("签名校验失败");
            }
        } catch (LhrbParseException e) {
            throw e;
        } catch (Exception e) {
            throw new LhrbParseException("签名校验异常: " + e.getMessage());
        }
    }

    private static byte[] inflate(byte[] compressed) throws LhrbParseException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n > 0) {
                    bos.write(buf, 0, n);
                } else if (inflater.needsDictionary()) {
                    throw new LhrbParseException("zlib 解压失败：需要字典");
                } else if (inflater.needsInput() || inflater.getRemaining() == 0) {
                    break;
                }
            }
            if (!inflater.finished()) {
                throw new LhrbParseException("zlib 解压未完成");
            }
            return bos.toByteArray();
        } catch (LhrbParseException e) {
            throw e;
        } catch (java.util.zip.DataFormatException e) {
            throw new LhrbParseException("zlib 解压失败: " + e.getMessage());
        } catch (IOException e) {
            throw new LhrbParseException("zlib 解压失败: " + e.getMessage());
        } finally {
            inflater.end();
        }
    }

    private static Object decodeValue(Reader r) throws LhrbParseException {
        int t = r.u8();
        switch (t) {
            case T_NULL:
                return JSONObject.NULL;
            case T_TRUE:
                return Boolean.TRUE;
            case T_FALSE:
                return Boolean.FALSE;
            case T_INT:
                return r.i64();
            case T_FLOAT: {
                double d = r.f64();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    throw new LhrbParseException("浮点数非法 (NaN/Infinity)");
                }
                return d;
            }
            case T_STRING:
                return r.str();
            case T_ARRAY: {
                int n = r.u16();
                JSONArray arr = new JSONArray();
                for (int i = 0; i < n; i++) {
                    arr.put(decodeValue(r));
                }
                return arr;
            }
            case T_OBJECT: {
                int n = r.u16();
                JSONObject obj = new JSONObject();
                for (int i = 0; i < n; i++) {
                    String key = r.str();
                    try {
                        obj.put(key, decodeValue(r));
                    } catch (org.json.JSONException e) {
                        throw new LhrbParseException("JSON对象构造失败: " + e.getMessage());
                    }
                }
                return obj;
            }
            default:
                throw new LhrbParseException("未知的值类型 " + t);
        }
    }

    public static class ParseResult {
        private final int version;
        private final String json;

        ParseResult(int version, String json) {
            this.version = version;
            this.json = json;
        }

        public int getVersion() {
            return version;
        }

        public String getJson() {
            return json;
        }
    }

    private static class SignatureBlock {
        final int sigAlg;
        final byte[] pubKey;
        final byte[] signature;

        SignatureBlock(int sigAlg, byte[] pubKey, byte[] signature) {
            this.sigAlg = sigAlg;
            this.pubKey = pubKey;
            this.signature = signature;
        }
    }

    private static class Reader {
        private final byte[] data;
        private int off;

        Reader(byte[] data, int base) {
            this.data = data;
            this.off = base;
        }

        void need(int n) throws LhrbParseException {
            if (off + n > data.length) {
                throw new LhrbParseException("数据截断 at offset " + off + " (need " + n + ")");
            }
        }

        int u8() throws LhrbParseException {
            need(1);
            return data[off++] & 0xFF;
        }

        int u16() throws LhrbParseException {
            need(2);
            int v = (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8);
            off += 2;
            return v;
        }

        long i64() throws LhrbParseException {
            need(8);
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v |= (long) (data[off + i] & 0xFF) << (8 * i);
            }
            off += 8;
            return v;
        }

        double f64() throws LhrbParseException {
            return Double.longBitsToDouble(i64());
        }

        void readBytes(byte[] out) throws LhrbParseException {
            need(out.length);
            System.arraycopy(data, off, out, 0, out.length);
            off += out.length;
        }

        String str() throws LhrbParseException {
            int n = u16();
            need(n);
            String s = new String(data, off, n, StandardCharsets.UTF_8);
            off += n;
            return s;
        }
    }
}
