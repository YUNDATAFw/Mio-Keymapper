package io.takamiya.mio.config.lhrb;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;

import io.takamiya.mio.config.Plugin;
import io.takamiya.mio.config.PluginConfig;

/**
 * 在应用内将插件配置编译打包为受签名的 LHRB (v0x04) 文件。
 *
 * <p>产物写入 {@code cacheDir/export/}（可经 FileProvider 分享）。
 * 编译全程回调日志监听器，供 UI 实时展示。
 */
public final class LhrbCompiler {

    private static final String TAG = "LhrbCompiler";

    public interface LogListener {
        void log(String line);
    }

    public static final class Result {
        public final File file;
        public final String packageName;
        public final String versionName;
        public final int versionCode;
        public final int minEngine;
        public final String fileSha256;
        public final String signatureSha256;
        public final String pubKeyFingerprint;
        public final boolean selfCheckOk;

        Result(File file, String packageName, String versionName, int versionCode,
               int minEngine, String fileSha256, String signatureSha256,
               String pubKeyFingerprint, boolean selfCheckOk) {
            this.file = file;
            this.packageName = packageName;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.minEngine = minEngine;
            this.fileSha256 = fileSha256;
            this.signatureSha256 = signatureSha256;
            this.pubKeyFingerprint = pubKeyFingerprint;
            this.selfCheckOk = selfCheckOk;
        }

        public long getFileSize() {
            return file.length();
        }
    }

    private LhrbCompiler() {
    }

    public static Result compile(Context context, Plugin plugin, String json, LogListener listener)
            throws Exception {
        PluginConfig config = plugin.getConfig();

        listener.log("== 编译打包开始 ==");

        listener.log("加载/生成签名密钥...");
        SigningKeyStore keyStore = new SigningKeyStore(context);
        KeyPair keyPair = keyStore.getOrCreate();
        String fingerprint = keyStore.getPublicKeyFingerprint();
        listener.log("公钥指纹: " + fingerprint);

        int minEngine = config != null && config.getMinEngine() != null ? config.getMinEngine() : 1;
        if (minEngine < 2) {
            listener.log("提示: minEngine = v" + minEngine + "，LHRB 配置建议设置 minEngine >= 2");
        }
        listener.log("包名: " + (config != null ? config.getPackageName() : ""));
        listener.log("版本: " + (config != null ? config.getVersionName() : "") + " ("
                + (config != null && config.getVersionCode() != null ? config.getVersionCode() : 0) + ")");

        listener.log("序列化配置 JSON (" + json.length() + " 字符)...");
        JSONObject root = new JSONObject(json);

        listener.log("编码 VALUE 数据区...");
        byte[] payload = LhrbWriter.encode(root);
        listener.log("编码完成: " + payload.length + " 字节");

        listener.log("zlib 压缩...");
        byte[] compressed = LhrbWriter.compress(payload);
        listener.log("压缩完成: " + payload.length + " -> " + compressed.length + " 字节");

        listener.log("构建文件头 (magic/version/flags/crc32)...");
        byte[] header = LhrbWriter.buildHeader(compressed);
        byte[] signedMessage = concat(header, compressed);

        listener.log("ECDSA P-256 / SHA-256 签名...");
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(signedMessage);
        byte[] signature = signer.sign();
        listener.log("签名完成: " + signature.length + " 字节 (DER)");

        byte[] pubKey = keyPair.getPublic().getEncoded();
        byte[] signatureBlock = LhrbWriter.buildSignatureBlock(pubKey, signature);

        File dir = new File(context.getCacheDir(), "export");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建导出目录: " + dir);
        }
        String safeName = safeFileName(config != null ? config.getPackageName() : "plugin");
        File file = new File(dir, safeName + ".lhrb");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(signedMessage);
            fos.write(signatureBlock);
            fos.flush();
        }
        listener.log("写入文件: " + file.getAbsolutePath());

        listener.log("自校验 (magic/CRC32/ECDSA 签名/公钥指纹)...");
        byte[] fileBytes = readFileBytes(file);
        LhrbReader.parse(fileBytes, SigningKeyStore.trustedFingerprints(context));
        listener.log("自校验通过: CRC32 一致，签名有效，公钥指纹受信任");

        String fileSha = sha256Hex(file);
        String sigSha = sha256Hex(signature);
        listener.log("文件 SHA-256: " + fileSha);
        listener.log("签名哈希: " + sigSha);
        listener.log("== 编译打包完成 (" + file.length() + " 字节) ==");

        return new Result(
                file,
                config != null ? config.getPackageName() : "",
                config != null ? config.getVersionName() : "",
                config != null && config.getVersionCode() != null ? config.getVersionCode() : 0,
                minEngine,
                fileSha,
                sigSha,
                fingerprint,
                true);
    }

    private static String safeFileName(String packageName) {
        return packageName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] readFileBytes(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int off = 0;
            while (off < data.length) {
                int n = fis.read(data, off, data.length - off);
                if (n < 0) {
                    throw new IOException("unexpected EOF: " + file);
                }
                off += n;
            }
            return data;
        }
    }

    private static String sha256Hex(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        return toHex(md.digest());
    }

    private static String sha256Hex(byte[] data) throws Exception {
        return toHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
