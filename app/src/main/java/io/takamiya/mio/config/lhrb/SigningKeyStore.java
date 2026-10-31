package io.takamiya.mio.config.lhrb;

import android.content.Context;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.app.Mio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 应用内 LHRB 签名密钥对管理。
 *
 * <p>首次编译打包时自动生成 ECDSA P-256 密钥对并持久化到
 * {@code filesDir/signing/}（DER 供运行时使用，同时落盘 PKCS#8/SPKI PEM
 * 便于与 lhrb_tool 交叉验证）。公钥指纹即 SPKI DER 的 SHA-256（hex，小写），
 * 与 {@link Mio} 内置工具密钥指纹口径一致。
 */
public final class SigningKeyStore {

    private static final String TAG = "SigningKeyStore";
    private static final String DIR_NAME = "signing";
    private static final String PRIV_FILE = "mio_sign.key";
    private static final String PUB_FILE = "mio_sign.key.pub";
    private static final String PRIV_PEM_FILE = "mio_sign.key.pem";
    private static final String PUB_PEM_FILE = "mio_sign.key.pub.pem";

    private final File dir;
    private KeyPair keyPair;
    private String fingerprint;

    public SigningKeyStore(Context context) {
        this.dir = new File(context.getFilesDir(), DIR_NAME);
    }

    /** 获取（必要时生成并持久化）密钥对。 */
    public synchronized KeyPair getOrCreate() throws Exception {
        if (keyPair != null) {
            return keyPair;
        }
        File priv = new File(dir, PRIV_FILE);
        File pub = new File(dir, PUB_FILE);
        if (priv.exists() && pub.exists()) {
            try {
                keyPair = loadKeyPair(priv, pub);
                return keyPair;
            } catch (Exception e) {
                AppLogger.e(TAG, "load signing key failed, will regenerate", e);
            }
        }
        keyPair = generateAndSave(priv, pub);
        return keyPair;
    }

    /** 公钥指纹（SPKI DER 的 SHA-256 hex，小写）。 */
    public synchronized String getPublicKeyFingerprint() throws Exception {
        if (fingerprint == null) {
            fingerprint = sha256Hex(getOrCreate().getPublic().getEncoded());
        }
        return fingerprint;
    }

    /**
     * 受信任的公钥指纹集合 = 内建工具密钥（lhrb_tool 产物可导入）
     * ∪ 本应用自持签名密钥（应用内编译产物可回导）。
     */
    public static Set<String> trustedFingerprints(Context context) {
        Set<String> set = new LinkedHashSet<>();
        set.add(Mio.getLhrbPublicKeyFingerprint());
        try {
            set.add(new SigningKeyStore(context).getPublicKeyFingerprint());
        } catch (Exception e) {
            AppLogger.e(TAG, "unable to resolve app signing key fingerprint", e);
        }
        return Collections.unmodifiableSet(set);
    }

    private KeyPair loadKeyPair(File privFile, File pubFile) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("EC");
        byte[] privDer = read(privFile);
        byte[] pubDer = read(pubFile);
        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privDer));
        PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubDer));
        return new KeyPair(pub, priv);
    }

    private KeyPair generateAndSave(File privFile, File pubFile) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();

        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("cannot create dir: " + dir);
        }
        write(privFile, kp.getPrivate().getEncoded());
        write(pubFile, kp.getPublic().getEncoded());
        write(new File(dir, PRIV_PEM_FILE), pemEncode("PRIVATE KEY", kp.getPrivate().getEncoded()));
        write(new File(dir, PUB_PEM_FILE), pemEncode("PUBLIC KEY", kp.getPublic().getEncoded()));
        return kp;
    }

    private static byte[] pemEncode(String type, byte[] der) throws IOException {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der);
        return ("-----BEGIN " + type + "-----\n"
                + b64 + "\n"
                + "-----END " + type + "-----\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static void write(File file, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }

    private static byte[] read(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            int off = 0;
            while (off < data.length) {
                int n = fis.read(data, off, data.length - off);
                if (n < 0) {
                    throw new IOException("unexpected EOF: " + file);
                }
                off += n;
            }
        }
        return data;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
