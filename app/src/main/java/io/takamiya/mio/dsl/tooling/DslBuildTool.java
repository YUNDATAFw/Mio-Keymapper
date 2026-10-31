package io.takamiya.mio.dsl.tooling;

import android.os.Environment;

import com.dsl.build_app.AstJsonCompressor;
import com.dsl.build_app.AstObfuscator;
import com.dsl.build_app.BuildConfig;
import com.dsl.build_app.PackageValidator;
import com.dsl.build_app.ZipSigner;
import com.dsl.lexer.DslLexerApplication;
import com.dsl.parser.DSLParserMain;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 基于 dsl-cli.jar 的插件编译打包工具。
 *
 * 只调用 jar 内的单步 execute()（返回 boolean、不 System.exit），
 * 绝不允许调用 PluginBuildMain.main / DslCli.main（其失败时 System.exit 会杀掉宿主进程）。
 * 管线与 PluginBuildMain 一致：clean → lexer → parser → package → obfuscate → pack → sign。
 * 输出约定：{projectDir}/src/script、build/token、build/ast、build/app.signed.zip、build.config.json。
 */
public final class DslBuildTool {

    /** 设备上插件默认目录（与设备端构建脚本一致）。 */
    public static final String DEFAULT_PROJECT_DIR = Environment.getExternalStorageDirectory()
            .getAbsolutePath() + File.separator + "程序" + File.separator
            + "MioConfigs" + File.separator + "Plugin";

    public interface Callback {
        void onDone(boolean ok, String log);
    }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dsl-build");
        return t;
    });

    private DslBuildTool() {
    }

    /** 后台执行编译任务，完成后回调（工作线程，自行切回主线程）。 */
    public static void runAsync(String mode, String projectDir, Callback cb) {
        EXEC.execute(() -> {
            Result r;
            try {
                r = run(mode, projectDir);
            } catch (Exception e) {
                r = new Result(false, "error: " + e.getMessage());
            }
            cb.onDone(r.ok, r.log);
        });
    }

    /**
     * 后台生成签名密钥到项目目录（keys/private_key.pem + public_key.pem）。
     * 项目内缺少 build.config.json 时补一份默认配置（签名指向 keys/）。
     */
    public static void genKeysAsync(String projectDir, Callback cb) {
        EXEC.execute(() -> {
            Result r = genKeys(projectDir);
            cb.onDone(r.ok, r.log);
        });
    }

    private static Result run(String mode, String projectDir) {
        File prj = new File(normalizePath(projectDir));
        if (!prj.isDirectory()) {
            return new Result(false, "error: 项目目录不存在: " + prj.getAbsolutePath());
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        PrintStream tee = new PrintStream(new TeeOutput(buf, origOut));
        System.setOut(tee);
        System.setErr(tee);
        boolean ok = false;
        try {
            ok = dispatch(mode, prj, tee);
        } catch (Exception e) {
            tee.println("error: " + e);
        } finally {
            tee.flush();
            System.setOut(origOut);
            System.setErr(origErr);
        }
        String log = sanitize(new String(buf.toByteArray(), StandardCharsets.UTF_8));
        writeLogFile(prj, log);
        return new Result(ok, log);
    }

    private static boolean dispatch(String mode, File prj, PrintStream out) throws Exception {
        switch (mode) {
            case "build":
                return doBuild(prj, out);
            case "parse":
                return doParse(prj, out);
            case "sign":
                return doSign(prj, out);
            default:
                throw new IllegalArgumentException("unknown mode: " + mode);
        }
    }

    private static boolean doBuild(File prj, PrintStream out) throws Exception {
        long t0 = System.currentTimeMillis();
        File buildDir = new File(prj, "build");
        out.println("> Task :config");
        BuildConfig cfg = loadConfig(prj);
        if (cfg != null) {
            out.println("  " + cfg.plugin.name + " v" + cfg.plugin.version
                    + " (code=" + cfg.plugin.versionCode + ")");
        } else {
            out.println("  using defaults");
            cfg = new BuildConfig();
        }
        if (!clean(out, buildDir)) return false;
        String base = slashTerminated(prj.getAbsolutePath());
        String srcDir = base + "src/script/";
        String tokenDir = base + "build/token/";
        String astDir = base + "build/ast/";
        String buildStr = base + "build/";
        if (!task(out, "lexer", DslLexerApplication.execute(srcDir, tokenDir))) return false;
        if (!task(out, "parser", DSLParserMain.execute(tokenDir, astDir, srcDir))) return false;
        if (!task(out, "package", PackageValidator.execute(base))) return false;
        if (!task(out, "obfuscate", AstObfuscator.execute(base, cfg))) return false;
        if (!task(out, "pack", AstJsonCompressor.execute(base, cfg))) return false;
        if (!task(out, "sign", ZipSigner.execute(buildStr, cfg))) return false;
        File zip = new File(buildDir, "app.signed.zip");
        out.println();
        out.println("BUILD SUCCESSFUL in " + (System.currentTimeMillis() - t0) / 1000.0f + "s");
        out.println("  Output:   build/app.signed.zip ("
                + (zip.exists() ? String.format(Locale.US, "%.1f KB", zip.length() / 1024.0) : "N/A") + ")");
        out.println("  Log:      " + new File(prj, "build.log").getAbsolutePath());
        return true;
    }

    private static boolean doParse(File prj, PrintStream out) throws Exception {
        String base = slashTerminated(prj.getAbsolutePath());
        String srcDir = base + "src/script/";
        String tokenDir = base + "build/token/";
        String astDir = base + "build/ast/";
        if (!task(out, "lexer", DslLexerApplication.execute(srcDir, tokenDir))) return false;
        if (!task(out, "parser", DSLParserMain.execute(tokenDir, astDir, srcDir))) return false;
        out.println();
        out.println("PARSE SUCCESSFUL");
        return true;
    }

    private static boolean doSign(File prj, PrintStream out) throws Exception {
        BuildConfig cfg = loadConfig(prj);
        if (cfg == null) cfg = new BuildConfig();
        File buildDir = new File(prj, "build");
        String buildStr = slashTerminated(buildDir.getAbsolutePath());
        if (!task(out, "sign", ZipSigner.execute(buildStr, cfg))) return false;
        File zip = new File(buildDir, "app.signed.zip");
        out.println();
        out.println("SIGN SUCCESSFUL -> build/app.signed.zip ("
                + (zip.exists() ? String.format(Locale.US, "%.1f KB", zip.length() / 1024.0) : "N/A") + ")");
        return true;
    }

    private static boolean clean(PrintStream out, File dir) {
        out.println("> Task :clean");
        rmDir(dir);
        out.println("  [OK] done");
        return true;
    }

    private static boolean task(PrintStream out, String name, boolean ok) {
        out.println("> Task :" + name);
        out.println("  " + (ok ? "[OK] done" : "[FAIL] " + name + " FAILED"));
        return ok;
    }

    private static Result genKeys(String projectDir) {
        StringBuilder log = new StringBuilder();
        File prj = new File(normalizePath(projectDir));
        if (!prj.isDirectory()) {
            return new Result(false, "error: 项目目录不存在: " + prj.getAbsolutePath());
        }
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();

            File keysDir = new File(prj, "keys");
            if (!keysDir.exists() && !keysDir.mkdirs()) {
                return new Result(false, "error: 无法创建目录 " + keysDir.getAbsolutePath());
            }
            File priv = new File(keysDir, "private_key.pem");
            File pub = new File(keysDir, "public_key.pem");
            writePem(priv, "PRIVATE KEY", kp.getPrivate().getEncoded());
            writePem(pub, "PUBLIC KEY", kp.getPublic().getEncoded());

            log.append("> Task :genkeys\n");
            log.append("  [OK] ").append(priv.getAbsolutePath()).append("\n");
            log.append("  [OK] ").append(pub.getAbsolutePath()).append("\n");

            File cfgFile = new File(prj, "build.config.json");
            if (cfgFile.exists()) {
                if (hasSigning(cfgFile)) {
                    log.append("  [OK] build.config.json 已存在且含签名配置（保留）\n");
                } else {
                    patchSigning(cfgFile);
                    log.append("  [OK] 已更新 build.config.json 签名配置 → keys/\n");
                }
            } else {
                writeDefaultConfig(cfgFile);
                log.append("  [OK] 已生成 build.config.json（签名默认指向 keys/）\n");
            }
            log.append("GENKEYS SUCCESSFUL");
            return new Result(true, log.toString());
        } catch (Exception e) {
            return new Result(false, "error: " + e.getMessage());
        }
    }

    private static void writePem(File f, String type, byte[] der) throws Exception {
        String b64 = Base64.getEncoder().encodeToString(der);
        StringBuilder sb = new StringBuilder("-----BEGIN ").append(type).append("-----\n");
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64, i, Math.min(b64.length(), i + 64)).append('\n');
        }
        sb.append("-----END ").append(type).append("-----\n");
        writeString(f, sb.toString());
    }

    private static boolean hasSigning(File cfgFile) {
        try {
            JSONObject o = new JSONObject(readString(cfgFile));
            JSONObject s = o.optJSONObject("signing");
            return s != null
                    && !s.optString("privateKey").isEmpty()
                    && !s.optString("publicKey").isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static void patchSigning(File cfgFile) throws Exception {
        JSONObject o = new JSONObject(readString(cfgFile));
        JSONObject s = o.optJSONObject("signing");
        if (s == null) {
            s = new JSONObject();
            o.put("signing", s);
        }
        s.put("privateKey", "keys/private_key.pem");
        s.put("publicKey", "keys/public_key.pem");
        if (s.optString("algorithm").isEmpty()) {
            s.put("algorithm", "SHA256withRSA");
        }
        writeString(cfgFile, o.toString(2));
    }

    private static void writeDefaultConfig(File cfgFile) throws Exception {
        JSONObject config = new JSONObject();

        JSONObject plugin = new JSONObject();
        plugin.put("name", "MioPlugin");
        plugin.put("package", "io.mio.plugin");
        plugin.put("icon", "res/icon.png");
        plugin.put("version", "1.0");
        plugin.put("versionCode", 100);
        config.put("plugin", plugin);

        JSONObject engine = new JSONObject();
        engine.put("minVersion", 1);
        engine.put("targetVersion", 2);
        config.put("engine", engine);

        JSONObject signing = new JSONObject();
        signing.put("privateKey", "keys/private_key.pem");
        signing.put("publicKey", "keys/public_key.pem");
        signing.put("algorithm", "SHA256withRSA");
        config.put("signing", signing);

        JSONObject build = new JSONObject();
        build.put("compressIcon", true);
        build.put("compressLevel", 9);
        build.put("binSplitThreshold", 0);
        config.put("build", build);

        JSONObject obf = new JSONObject();
        obf.put("enabled", false);
        obf.put("preserveNames", new JSONArray().put("dsl"));
        obf.put("dictionary", new JSONArray());
        obf.put("obfuscateProperties", false);
        obf.put("obfuscatePaths", true);
        obf.put("obfuscateMethodNames", true);
        obf.put("mappingFile", "build/mapping.json");
        obf.put("dictionaryFile", "");
        config.put("obfuscation", obf);

        config.put("repos", new JSONArray());
        writeString(cfgFile, config.toString(2));
    }

    private static String readString(File f) throws Exception {
        byte[] bytes = new byte[(int) f.length()];
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            int off = 0;
            while (off < bytes.length) {
                int n = in.read(bytes, off, bytes.length - off);
                if (n < 0) break;
                off += n;
            }
        } finally {
            in.close();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(File f, String text) throws Exception {
        FileOutputStream fos = new FileOutputStream(f);
        try {
            fos.write(text.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } finally {
            fos.close();
        }
    }

    private static BuildConfig loadConfig(File prj) {
        File cfgFile = new File(prj, "build.config.json");
        if (!cfgFile.isFile()) return null;
        try {
            return BuildConfig.fromFile(cfgFile.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
    }

    private static void rmDir(File f) {
        if (!f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isDirectory()) {
                    rmDir(c);
                } else {
                    c.delete();
                }
            }
        }
        f.delete();
    }

    private static void writeLogFile(File prj, String log) {
        try {
            FileOutputStream fos = new FileOutputStream(new File(prj, "build.log"));
            fos.write(log.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.close();
        } catch (Exception ignored) {
        }
    }

    /** 去除 ANSI 颜色码，命令行界面不渲染这些序列。 */
    private static String sanitize(String raw) {
        return raw.replaceAll("\u001B\\[[0-9;]*[mK]", "");
    }

    /** 去除尾部路径分隔符，避免外层字符串拼接出双斜杠或漏斜杠。 */
    private static String normalizePath(String path) {
        if (path == null) return null;
        String s = path.trim();
        while (s.length() > 1 && (s.endsWith("/") || s.endsWith("\\"))) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * dsl-cli.jar 的约定：传给 lexer/parser/package/obfuscate/pack/sign 的目录参数一律以 "/" 结尾
     * （jar 内部是 base + "src/script/" 这种直接字符串拼接）。这里统一补尾斜杠。
     */
    private static String slashTerminated(String path) {
        String s = path == null ? "" : path;
        return s.endsWith("/") ? s : s + "/";
    }

    /** 同时写入内存缓冲与原始 System.out（保留 logcat 输出）。 */
    private static final class TeeOutput extends OutputStream {
        private final ByteArrayOutputStream sink;
        private final PrintStream mirror;

        TeeOutput(ByteArrayOutputStream sink, OutputStream mirror) {
            this.sink = sink;
            this.mirror = new PrintStream(mirror);
        }

        @Override
        public void write(int b) {
            sink.write(b);
            mirror.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            sink.write(b, off, len);
            mirror.write(b, off, len);
        }
    }

    private static final class Result {
        final boolean ok;
        final String log;

        Result(boolean ok, String log) {
            this.ok = ok;
            this.log = log;
        }
    }
}