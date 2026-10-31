package io.takamiya.mio.configManage;

import android.content.Context;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.app.Mio;
import io.takamiya.mio.config.ConfigLoader;
import io.takamiya.mio.config.ConfigParseException;
import io.takamiya.mio.config.Plugin;
import io.takamiya.mio.config.lhrb.LhrbParseException;
import io.takamiya.mio.config.lhrb.LhrbReader;
import io.takamiya.mio.config.lhrb.SigningKeyStore;

public class IPluginInstaller {

    private static final String TAG = "IPluginInstaller";
    private static final String CONFIG_DIR = "configs";
    private static final String LHRB_EXT = ".lhrb";
    private static final String LHRB_META_EXT = ".lhrb.meta";

    private final File configDir;
    private final Context context;

    public IPluginInstaller(Context context) {
        this.context = context.getApplicationContext();
        this.configDir = new File(this.context.getFilesDir(), CONFIG_DIR);
        if (!configDir.exists()) {
            boolean created = configDir.mkdirs();
            AppLogger.i(TAG, "Config directory created: " + configDir.getAbsolutePath() + " = " + created);
        } else {
            AppLogger.d(TAG, "Config directory already exists: " + configDir.getAbsolutePath());
        }
    }

    public synchronized InstallResult install(String json) {
        AppLogger.i(TAG, "install() called, force=false, json.length=" + json.length());
        return install(json, false);
    }

    public synchronized InstallResult install(String json, boolean force) {
        AppLogger.i(TAG, "install() called, force=" + force + ", json.length=" + json.length());

        Plugin plugin;
        try {
            plugin = ConfigLoader.parseWithValidation(json);
        } catch (ConfigParseException e) {
            AppLogger.e(TAG, "install: parse failed: " + e.getMessage());
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("root", e.getMessage(), "valid JSON", e.getMessage()))
                    .build();
        } catch (Exception e) {
            AppLogger.e(TAG, "install: parse failed: " + e.getMessage());
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("root", "Invalid JSON: " + e.getMessage(), "valid JSON", e.getMessage()))
                    .build();
        }

        return doInstall(plugin, json, null, force, false);
    }

    public synchronized InstallResult installLhrb(byte[] data) {
        AppLogger.i(TAG, "installLhrb() called, force=false, bytes=" + (data == null ? 0 : data.length));
        return installLhrb(data, false);
    }

    public synchronized InstallResult installLhrb(byte[] data, boolean force) {
        AppLogger.i(TAG, "installLhrb() called, force=" + force + ", bytes=" + (data == null ? 0 : data.length));

        if (Mio.APP_CONFIG_ENGINE < 2) {
            AppLogger.w(TAG, "installLhrb: LHRB requires engine v2, current is v" + Mio.APP_CONFIG_ENGINE);
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("config.minEngine",
                            "LHRB 配置需要引擎 v2，当前为 v" + Mio.APP_CONFIG_ENGINE,
                            "APP_CONFIG_ENGINE >= 2", Mio.APP_CONFIG_ENGINE))
                    .build();
        }

        Plugin plugin;
        try {
            LhrbReader.ParseResult parsed = LhrbReader.parse(data, getTrustedFingerprints());
            plugin = ConfigLoader.parseWithValidation(parsed.getJson());
        } catch (LhrbParseException e) {
            AppLogger.e(TAG, "installLhrb: LHRB validation failed: " + e.getMessage());
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("root", "LHRB 校验失败: " + e.getMessage(), "valid signed LHRB", e.getMessage()))
                    .build();
        } catch (ConfigParseException e) {
            AppLogger.e(TAG, "installLhrb: parse failed: " + e.getMessage());
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("root", e.getMessage(), "valid JSON in LHRB", e.getMessage()))
                    .build();
        } catch (Exception e) {
            AppLogger.e(TAG, "installLhrb: parse failed: " + e.getMessage());
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("root", "LHRB 内容解析失败: " + e.getMessage(), "valid JSON in LHRB", e.getMessage()))
                    .build();
        }

        return doInstall(plugin, null, data, force, true);
    }

    private InstallResult doInstall(Plugin plugin, String json, byte[] lhrbData, boolean force, boolean isLhrb) {
        if (plugin.getConfig() == null) {
            AppLogger.e(TAG, "install: no config in plugin");
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("plugin.config", "missing config", "plugin.config", "nothing"))
                    .build();
        }

        int minEngine = plugin.getConfig().getMinEngine() != null ? plugin.getConfig().getMinEngine() : 1;
        if (minEngine > Mio.APP_CONFIG_ENGINE) {
            AppLogger.w(TAG, "install: plugin requires engine v" + minEngine
                    + ", current is v" + Mio.APP_CONFIG_ENGINE);
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("config.minEngine",
                            "Plugin requires engine v" + minEngine + " but current is v" + Mio.APP_CONFIG_ENGINE,
                            "minEngine <= " + Mio.APP_CONFIG_ENGINE, minEngine))
                    .build();
        }
        if (minEngine < Mio.APP_CONFIG_ENGINE) {
            AppLogger.w(TAG, "install: plugin is built for an older engine v" + minEngine
                    + ", current is v" + Mio.APP_CONFIG_ENGINE);
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("config.minEngine",
                            "该配置不适配当前引擎（v" + minEngine + " < v" + Mio.APP_CONFIG_ENGINE + "），无法安装",
                            "minEngine >= " + Mio.APP_CONFIG_ENGINE, minEngine))
                    .build();
        }

        String packageName = plugin.getConfig().getPackageName();
        int newVersionCode = plugin.getConfig().getVersionCode() != null ? plugin.getConfig().getVersionCode() : 0;
        String newVersionName = plugin.getConfig().getVersionName();
        String encodedName = encodePackageName(packageName);

        File lhrbFile = new File(configDir, encodedName + LHRB_EXT);
        File jsonFile = new File(configDir, encodedName + ".json");

        if (!isLhrb && lhrbFile.exists()) {
            if (!force) {
                AppLogger.w(TAG, "install: refusing to overwrite read-only LHRB config \"" + packageName + "\" with JSON");
                return InstallResult.builder()
                        .status(InstallResult.Status.FORMAT_ERROR)
                        .errors(singleError("file",
                                "该配置为只读 LHRB 配置，无法用 JSON 覆盖", "JSON-only package", "LHRB installed"))
                        .build();
            }
            AppLogger.i(TAG, "install: JSON overwrites existing LHRB (force), converting to JSON");
            deleteQuietly(lhrbFile);
            deleteQuietly(metaFileFor(lhrbFile));
        }

        File file = isLhrb ? lhrbFile : jsonFile;
        AppLogger.d(TAG, "Target file: " + file.getAbsolutePath());

        if (file.exists() && !force) {
            AppLogger.d(TAG, "Plugin file exists, checking version: installed vs " + newVersionCode);
            Plugin installedPlugin = readPlugin(packageName);
            if (installedPlugin != null) {
                int installedCode = installedPlugin.getConfig().getVersionCode() != null ? installedPlugin.getConfig().getVersionCode() : 0;
                String installedName = installedPlugin.getConfig().getVersionName();
                AppLogger.d(TAG, "Version comparison: installed v" + installedCode + " (" + installedName
                        + "), new v" + newVersionCode + " (" + newVersionName + ")");

                if (installedCode > newVersionCode) {
                    AppLogger.w(TAG, "install: DOWNGRADE blocked - installed v" + installedCode
                            + " > new v" + newVersionCode + " for package " + packageName);
                    return InstallResult.builder()
                            .status(InstallResult.Status.DOWNGRADE)
                            .packageName(packageName)
                            .installedVersionCode(installedCode)
                            .newVersionCode(newVersionCode)
                            .installedVersionName(installedName)
                            .newVersionName(newVersionName)
                            .build();
                }
                if (installedCode == newVersionCode) {
                    AppLogger.i(TAG, "install: VERSION_EQUAL - installed v" + installedCode
                            + " == new v" + newVersionCode + " for package " + packageName
                            + ", waiting for user confirmation");
                    return InstallResult.builder()
                            .status(InstallResult.Status.VERSION_EQUAL)
                            .packageName(packageName)
                            .installedVersionCode(installedCode)
                            .newVersionCode(newVersionCode)
                            .installedVersionName(installedName)
                            .newVersionName(newVersionName)
                            .build();
                }
                AppLogger.i(TAG, "Version check passed: installed v" + installedCode
                        + " < new v" + newVersionCode + ", proceeding to upgrade");
            } else {
                AppLogger.w(TAG, "File exists but could not read installed plugin, will overwrite");
            }
        } else if (file.exists() && force) {
            AppLogger.i(TAG, "Force install: overwriting existing plugin file");
        } else {
            AppLogger.i(TAG, "New plugin install, file does not exist yet");
        }

        boolean written;
        if (isLhrb) {
            written = writeLhrb(lhrbFile, lhrbData);
        } else {
            written = writeFile(file, json);
        }

        if (!written) {
            AppLogger.e(TAG, "install: failed to write plugin file: " + file.getAbsolutePath());
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("file", "Failed to write plugin file: " + file.getAbsolutePath(), "writable file", "I/O error"))
                    .build();
        }

        AppLogger.i(TAG, "install: SUCCESS - plugin \"" + plugin.getConfig().getName()
                + "\" (" + packageName + ") v" + newVersionName
                + " saved to " + file.getName());

        return InstallResult.builder()
                .status(InstallResult.Status.SUCCESS)
                .packageName(packageName)
                .newVersionCode(newVersionCode)
                .newVersionName(newVersionName)
                .plugin(plugin)
                .build();
    }

    public synchronized InstallResult modify(String packageName, String json) {
        AppLogger.i(TAG, "modify() called: package=" + packageName + ", json.length=" + json.length());

        String encodedName = encodePackageName(packageName);
        File lhrbFile = new File(configDir, encodedName + LHRB_EXT);
        if (lhrbFile.exists()) {
            AppLogger.w(TAG, "modify: \"" + packageName + "\" is a read-only LHRB config, modification blocked");
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("file", "只读 LHRB 配置不可修改", "editable JSON config", "LHRB"))
                    .build();
        }

        Plugin plugin;
        try {
            plugin = ConfigLoader.parse(json);
        } catch (Exception e) {
            AppLogger.e(TAG, "modify: parse failed: " + e.getMessage());
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("root", "Invalid JSON: " + e.getMessage(), "valid JSON", e.getMessage()))
                    .build();
        }

        if (plugin.getConfig() == null) {
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("plugin.config", "missing config", "plugin.config", "nothing"))
                    .build();
        }

        String jsonPackageName = plugin.getConfig().getPackageName();
        if (!jsonPackageName.equals(packageName)) {
            AppLogger.w(TAG, "modify: package mismatch - expected \"" + packageName
                    + "\", got \"" + jsonPackageName + "\"");
            return InstallResult.builder()
                    .status(InstallResult.Status.PACKAGE_MISMATCH)
                    .packageName(packageName)
                    .errors(singleError("config.package",
                            "Package mismatch: expected \"" + packageName + "\" but JSON has \"" + jsonPackageName + "\"",
                            packageName, jsonPackageName))
                    .build();
        }

        File file = new File(configDir, encodedName + ".json");
        if (!file.exists()) {
            AppLogger.w(TAG, "modify: plugin \"" + packageName + "\" not installed");
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("file", "Plugin \"" + packageName + "\" not installed", "installed plugin", "not found"))
                    .build();
        }

        if (!writeFile(file, json)) {
            AppLogger.e(TAG, "modify: failed to write file");
            return InstallResult.builder()
                    .status(InstallResult.Status.FORMAT_ERROR)
                    .errors(singleError("file", "Failed to write file", "writable file", "I/O error"))
                    .build();
        }

        AppLogger.i(TAG, "modify: SUCCESS - \"" + plugin.getConfig().getName() + "\"");
        return InstallResult.builder()
                .status(InstallResult.Status.SUCCESS)
                .packageName(packageName)
                .newVersionCode(plugin.getConfig().getVersionCode() != null ? plugin.getConfig().getVersionCode() : 0)
                .newVersionName(plugin.getConfig().getVersionName())
                .plugin(plugin)
                .build();
    }

    public synchronized void uninstall(String packageName) {
        AppLogger.i(TAG, "uninstall() called: package=" + packageName);
        String encodedName = encodePackageName(packageName);
        File jsonFile = new File(configDir, encodedName + ".json");
        File lhrbFile = new File(configDir, encodedName + LHRB_EXT);
        File metaFile = new File(configDir, encodedName + LHRB_META_EXT);

        boolean found = false;
        for (File f : new File[]{jsonFile, lhrbFile, metaFile}) {
            if (f.exists()) {
                found = true;
                if (f.delete()) {
                    AppLogger.i(TAG, "uninstall: deleted " + f.getName());
                } else {
                    AppLogger.e(TAG, "uninstall: FAILED to delete file: " + f.getAbsolutePath());
                }
            }
        }
        if (!found) {
            AppLogger.w(TAG, "uninstall: plugin \"" + packageName + "\" not found");
        }
    }

    Plugin readPlugin(String packageName) {
        String encodedName = encodePackageName(packageName);
        File lhrbFile = new File(configDir, encodedName + LHRB_EXT);
        if (lhrbFile.exists()) {
            return readLhrbPlugin(lhrbFile);
        }

        File file = new File(configDir, encodedName + ".json");
        if (!file.exists()) {
            AppLogger.w(TAG, "readPlugin: file not found for package \"" + packageName + "\"");
            return null;
        }
        byte[] buffer = readFileBytes(file);
        if (buffer == null) {
            AppLogger.w(TAG, "readPlugin: empty file for \"" + packageName + "\"");
            return null;
        }
        try {
            String json = new String(buffer, StandardCharsets.UTF_8);
            Plugin plugin = ConfigLoader.parse(json);
            AppLogger.d(TAG, "readPlugin: loaded \"" + packageName + "\" v" + plugin.getConfig().getVersionName());
            return plugin;
        } catch (Exception e) {
            AppLogger.e(TAG, "readPlugin: failed for \"" + packageName + "\": " + e.getMessage(), e);
            return null;
        }
    }

    public String readPluginJson(String packageName) {
        String encodedName = encodePackageName(packageName);
        File lhrbFile = new File(configDir, encodedName + LHRB_EXT);
        if (lhrbFile.exists()) {
            byte[] buffer = readFileBytes(lhrbFile);
            if (buffer == null) {
                AppLogger.w(TAG, "readPluginJson: empty LHRB file for \"" + packageName + "\"");
                return null;
            }
            try {
                LhrbReader.ParseResult parsed = LhrbReader.parse(buffer, getTrustedFingerprints());
                AppLogger.d(TAG, "readPluginJson: decoded LHRB (" + buffer.length + " bytes) for \"" + packageName + "\"");
                return parsed.getJson();
            } catch (Exception e) {
                AppLogger.e(TAG, "readPluginJson: LHRB decode failed for \"" + packageName + "\": " + e.getMessage(), e);
                return null;
            }
        }

        File file = new File(configDir, encodedName + ".json");
        if (!file.exists()) {
            AppLogger.w(TAG, "readPluginJson: file not found for \"" + packageName + "\"");
            return null;
        }
        byte[] buffer = readFileBytes(file);
        if (buffer == null) {
            AppLogger.w(TAG, "readPluginJson: empty file for \"" + packageName + "\"");
            return null;
        }
        String json = new String(buffer, StandardCharsets.UTF_8);
        AppLogger.d(TAG, "readPluginJson: loaded " + buffer.length + " bytes for \"" + packageName + "\"");
        return json;
    }

    File getPluginFile(String packageName) {
        String encoded = encodePackageName(packageName);
        return new File(configDir, encoded + ".json");
    }

    File getLhrbFile(String packageName) {
        String encoded = encodePackageName(packageName);
        return new File(configDir, encoded + LHRB_EXT);
    }

    /**
     * 读取配置源文件原始字节（JSON 或 LHRB 均不解码）。
     * 返回 null 表示不存在或读取失败。用于导出。
     */
    public byte[] readPluginRawFile(String packageName) {
        String encodedName = encodePackageName(packageName);
        File lhrbFile = new File(configDir, encodedName + LHRB_EXT);
        if (lhrbFile.exists()) {
            return readFileBytes(lhrbFile);
        }
        File jsonFile = new File(configDir, encodedName + ".json");
        if (jsonFile.exists()) {
            return readFileBytes(jsonFile);
        }
        AppLogger.w(TAG, "readPluginRawFile: no source file for \"" + packageName + "\"");
        return null;
    }

    public boolean isEnabled(String packageName) {
        Plugin plugin = readPlugin(packageName);
        if (plugin == null || plugin.getConfig() == null) {
            return false;
        }
        return plugin.getConfig().isEnable();
    }

    public synchronized boolean setEnabled(String packageName, boolean enabled) {
        AppLogger.i(TAG, "setEnabled() called: package=" + packageName + ", enabled=" + enabled);

        String encodedName = encodePackageName(packageName);
        File lhrbFile = new File(configDir, encodedName + LHRB_EXT);
        if (lhrbFile.exists()) {
            boolean ok = writeMeta(new File(configDir, encodedName + LHRB_META_EXT), enabled);
            if (ok) {
                AppLogger.i(TAG, "setEnabled: \"" + packageName + "\" enable=" + enabled + " (LHRB sidecar)");
            }
            return ok;
        }

        File file = getPluginFile(packageName);
        if (!file.exists()) {
            AppLogger.w(TAG, "setEnabled: plugin \"" + packageName + "\" not found");
            return false;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = fis.read(buffer);
            if (read <= 0) {
                AppLogger.w(TAG, "setEnabled: empty file for \"" + packageName + "\"");
                return false;
            }

            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONObject root = ConfigLoader.parseStrict(json);
            JSONObject pluginObj = root.optJSONObject("plugin");
            if (pluginObj == null) {
                AppLogger.w(TAG, "setEnabled: no plugin object in \"" + packageName + "\"");
                return false;
            }
            JSONObject config = pluginObj.optJSONObject("config");
            if (config == null) {
                AppLogger.w(TAG, "setEnabled: no config object in \"" + packageName + "\"");
                return false;
            }

            config.put("enable", enabled);
            String updatedJson = root.toString(2);

            if (!writeFile(file, updatedJson)) {
                AppLogger.e(TAG, "setEnabled: failed to write file for \"" + packageName + "\"");
                return false;
            }

            AppLogger.i(TAG, "setEnabled: \"" + packageName + "\" enable=" + enabled);
            return true;

        } catch (Exception e) {
            AppLogger.e(TAG, "setEnabled: failed for \"" + packageName + "\": " + e.getMessage(), e);
            return false;
        }
    }

    // ── LHRB helpers ──

    /** 受信任公钥指纹集合（内建工具密钥 ∪ 本应用自持密钥）。 */
    public Set<String> getTrustedFingerprints() {
        return SigningKeyStore.trustedFingerprints(context);
    }

    Plugin readLhrbFile(File lhrbFile) {
        return readLhrbPlugin(lhrbFile);
    }

    private Plugin readLhrbPlugin(File lhrbFile) {
        String packageName = "";
        try {
            byte[] buffer = readFileBytes(lhrbFile);
            if (buffer == null) {
                AppLogger.w(TAG, "readLhrbPlugin: empty file " + lhrbFile.getName());
                return null;
            }
            LhrbReader.ParseResult parsed = LhrbReader.parse(buffer, getTrustedFingerprints());
            Plugin plugin = ConfigLoader.parse(parsed.getJson());
            plugin.setLocked(true);
            if (plugin.getConfig() != null) {
                packageName = plugin.getConfig().getPackageName();
                Boolean metaEnable = readMetaEnable(metaFileFor(lhrbFile));
                if (metaEnable != null) {
                    plugin.getConfig().setEnable(metaEnable);
                }
            }
            AppLogger.d(TAG, "readLhrbPlugin: loaded \"" + packageName
                    + "\" v" + (plugin.getConfig() != null ? plugin.getConfig().getVersionName() : "?"));
            return plugin;
        } catch (Exception e) {
            AppLogger.e(TAG, "readLhrbPlugin: failed for " + lhrbFile.getName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    private boolean writeLhrb(File lhrbFile, byte[] data) {
        AppLogger.d(TAG, "writeLhrb: writing " + data.length + " bytes to " + lhrbFile.getAbsolutePath());
        try (FileOutputStream fos = new FileOutputStream(lhrbFile)) {
            fos.write(data);
            fos.flush();
        } catch (Exception e) {
            AppLogger.e(TAG, "writeLhrb: failed for " + lhrbFile.getAbsolutePath() + ": " + e.getMessage(), e);
            return false;
        }

        File metaFile = metaFileFor(lhrbFile);
        if (!writeMeta(metaFile, true)) {
            AppLogger.e(TAG, "writeLhrb: failed to write meta, rolling back " + lhrbFile.getAbsolutePath());
            deleteQuietly(lhrbFile);
            return false;
        }
        AppLogger.d(TAG, "writeLhrb: wrote " + data.length + " bytes + meta successfully");
        return true;
    }

    private File metaFileFor(File lhrbFile) {
        String name = lhrbFile.getName();
        String base = name.endsWith(LHRB_EXT)
                ? name.substring(0, name.length() - LHRB_EXT.length()) : name;
        return new File(lhrbFile.getParentFile(), base + LHRB_META_EXT);
    }

    private boolean writeMeta(File metaFile, boolean enabled) {
        try (FileOutputStream fos = new FileOutputStream(metaFile)) {
            JSONObject meta = new JSONObject();
            meta.put("source", "lhrb");
            meta.put("enable", enabled);
            fos.write(meta.toString().getBytes(StandardCharsets.UTF_8));
            fos.flush();
            return true;
        } catch (Exception e) {
            AppLogger.e(TAG, "writeMeta: failed for " + metaFile.getAbsolutePath() + ": " + e.getMessage(), e);
            return false;
        }
    }

    private Boolean readMetaEnable(File metaFile) {
        if (!metaFile.exists()) {
            return null;
        }
        try {
            byte[] buffer = readFileBytes(metaFile);
            if (buffer == null) {
                return null;
            }
            JSONObject meta = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
            return meta.optBoolean("enable", true);
        } catch (Exception e) {
            AppLogger.e(TAG, "readMetaEnable: failed for " + metaFile.getName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    // ── helpers ──

    private List<ValidationError> singleError(String field, String message, Object expected, Object actual) {
        List<ValidationError> list = new ArrayList<>(1);
        list.add(new ValidationError(field, message, expected, actual));
        return list;
    }

    // ── file I/O ──

    static String encodePackageName(String packageName) {
        byte[] data = packageName.getBytes(StandardCharsets.UTF_8);
        String encoded = Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        AppLogger.d(TAG, "encodePackageName: \"" + packageName + "\" -> \"" + encoded + "\"");
        return encoded;
    }

    private byte[] readFileBytes(File file) {
        if (!file.exists() || file.length() <= 0) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = fis.read(buffer);
            if (read <= 0) {
                return null;
            }
            return buffer;
        } catch (Exception e) {
            AppLogger.e(TAG, "readFileBytes: failed for " + file.getAbsolutePath() + ": " + e.getMessage(), e);
            return null;
        }
    }

    private boolean writeFile(File file, String content) {
        AppLogger.d(TAG, "writeFile: writing " + content.length() + " bytes to " + file.getAbsolutePath());
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            fos.write(data);
            fos.flush();
            AppLogger.d(TAG, "writeFile: wrote " + data.length + " bytes successfully");
            return true;
        } catch (Exception e) {
            AppLogger.e(TAG, "writeFile: failed for " + file.getAbsolutePath() + ": " + e.getMessage(), e);
            return false;
        }
    }

    private void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            AppLogger.w(TAG, "deleteQuietly: failed to delete " + file.getAbsolutePath());
        }
    }
}
