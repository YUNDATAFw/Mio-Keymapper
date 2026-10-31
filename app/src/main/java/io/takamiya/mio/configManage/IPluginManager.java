package io.takamiya.mio.configManage;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.Inflater;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.RegistryManager;
import io.takamiya.mio.config.Plugin;
import io.takamiya.mio.config.PluginConfig;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

/**
 * DSL 插件管理器（替代旧的 IPluginManager + IPluginInstaller）。
 * 扫描 SdlEngine 安装的 ZIP 插件目录（plugin/private/），提供列表/读取/卸载/启用禁用功能。
 */
public class IPluginManager {

    private static final String TAG = "PluginManager";

    /** SdlEngine 安装目录 */
    private static final String PLUGIN_PRIVATE_DIR = "plugin/private";

    private final Context context;
    private final File pluginDir;

    public IPluginManager(Context context) {
        this.context = context.getApplicationContext();
        this.pluginDir = new File(this.context.getFilesDir(), PLUGIN_PRIVATE_DIR);
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
        }
        AppLogger.i(TAG, "PluginManager initialized, dir=" + pluginDir.getAbsolutePath());
    }

    // ==================== 查询 ====================

    public Plugin get(String packageName) {
        File pkgDir = new File(pluginDir, packageName);
        File configFile = new File(pkgDir, "config.json");
        if (!configFile.isFile()) {
            AppLogger.w(TAG, "get: plugin not found: " + packageName);
            return null;
        }
        Plugin plugin = readPluginFromDir(pkgDir, packageName);
        if (plugin != null) {
            AppLogger.d(TAG, "get: found \"" + plugin.getConfig().getName()
                    + "\" " + plugin.getConfig().getVersionName());
        }
        return plugin;
    }

    public String getPluginJson(String packageName) {
        File pkgDir = new File(pluginDir, packageName);
        File configFile = new File(pkgDir, "config.json");
        if (!configFile.isFile()) return null;
        try {
            return decompressConfig(configFile);
        } catch (Exception e) {
            AppLogger.e(TAG, "getPluginJson failed: " + packageName + " - " + e.getMessage());
            return null;
        }
    }

    public byte[] getPluginRawFile(String packageName) {
        File pkgDir = new File(pluginDir, packageName);
        File configFile = new File(pkgDir, "config.json");
        if (!configFile.isFile()) return null;
        try (FileInputStream fis = new FileInputStream(configFile)) {
            byte[] data = new byte[(int) configFile.length()];
            int off = 0;
            while (off < data.length) {
                int r = fis.read(data, off, data.length - off);
                if (r < 0) break;
                off += r;
            }
            return data;
        } catch (Exception e) {
            AppLogger.e(TAG, "getPluginRawFile failed: " + packageName, e);
            return null;
        }
    }

    public List<Plugin> list() {
        if (!pluginDir.exists() || !pluginDir.isDirectory()) {
            return Collections.emptyList();
        }

        File[] children = pluginDir.listFiles();
        if (children == null || children.length == 0) {
            return Collections.emptyList();
        }

        List<Plugin> plugins = new ArrayList<>();
        for (File pkgDir : children) {
            if (!pkgDir.isDirectory()) continue;
            String packageName = pkgDir.getName();
            Plugin plugin = readPluginFromDir(pkgDir, packageName);
            if (plugin != null) {
                plugins.add(plugin);
            }
        }
        AppLogger.d(TAG, "list: " + plugins.size() + " plugin(s)");
        return plugins;
    }

    public boolean isInstalled(String packageName) {
        File pkgDir = new File(pluginDir, packageName);
        return new File(pkgDir, "config.json").isFile();
    }

    public List<String> getInstalledPackages() {
        List<Plugin> plugins = list();
        List<String> packages = new ArrayList<>(plugins.size());
        for (Plugin p : plugins) {
            packages.add(p.getConfig().getPackageName());
        }
        return packages;
    }

    // ==================== 卸载 ====================

    public void uninstall(String packageName) {
        AppLogger.i(TAG, "uninstall: " + packageName);
        File pkgDir = new File(pluginDir, packageName);
        if (pkgDir.isDirectory()) {
            deleteRecursive(pkgDir);
            AppLogger.i(TAG, "uninstall: deleted " + pkgDir.getAbsolutePath());
        }
    }

    // ==================== 启用/禁用 ====================

    public boolean setEnabled(String packageName, boolean enabled) {
        try {
            Registry registry = RegistryUtils.get();
            if (registry == null) return false;
            String key = "plugin_enabled_" + packageName;
            registry.set(key, enabled);
            registry.saveAsync();
            AppLogger.d(TAG, "setEnabled: " + packageName + " = " + enabled);
            return true;
        } catch (Exception e) {
            AppLogger.e(TAG, "setEnabled failed: " + packageName, e);
            return false;
        }
    }

    public boolean isEnabled(String packageName) {
        try {
            Registry registry = RegistryUtils.get();
            if (registry == null) return true;
            String key = "plugin_enabled_" + packageName;
            return registry.getBoolean(key);
        } catch (Exception e) {
            return true;
        }
    }

    public int getEnabledCount() {
        int count = 0;
        for (Plugin p : list()) {
            if (isEnabled(p.getConfig().getPackageName())) {
                count++;
            }
        }
        return count;
    }

    // ==================== 内部方法 ====================

    private Plugin readPluginFromDir(File pkgDir, String packageName) {
        File configFile = new File(pkgDir, "config.json");
        if (!configFile.isFile()) return null;

        try {
            String json = decompressConfig(configFile);
            if (json == null || json.trim().isEmpty()) return null;

            JSONObject root = new JSONObject(json);
            JSONObject pluginObj = root.optJSONObject("plugin");
            if (pluginObj == null) return null;

            JSONObject configObj = pluginObj.optJSONObject("config");
            if (configObj == null) return null;

            PluginConfig pc = new PluginConfig();
            pc.setName(configObj.optString("name", packageName));
            pc.setPackageName(configObj.optString("package", packageName));
            pc.setDescription(configObj.optString("description", ""));
            pc.setAuthor(configObj.optString("author", ""));
            pc.setUpdateUrl(configObj.optString("updateUrl", ""));
            pc.setVersionName(configObj.optString("versionName", "1.0.0"));
            pc.setVersionCode(configObj.optInt("versionCode", 1));
            pc.setMinEngine(configObj.optInt("minEngine", 4));
            pc.setEnable(configObj.optBoolean("enable", true));
            pc.setKeyHoldDuration((long) configObj.optInt("keyHoldDuration", 0));

            Plugin plugin = new Plugin();
            plugin.setConfig(pc);

            return plugin;
        } catch (Exception e) {
            AppLogger.w(TAG, "readPluginFromDir failed: " + packageName + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * 解压 config.json（SdlEngine 安装的 ZIP 中 config.json 使用 zlib 压缩）
     */
    private String decompressConfig(File configFile) throws Exception {
        byte[] raw = readFileBytes(configFile);
        if (raw == null || raw.length == 0) return null;

        // 尝试直接读取（未压缩的 JSON）
        String direct = new String(raw, StandardCharsets.UTF_8).trim();
        if (direct.startsWith("{")) {
            return direct;
        }

        // 尝试 zlib 解压
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(raw);
            byte[] output = new byte[raw.length * 4];
            int count = 0;
            while (!inflater.finished()) {
                if (count >= output.length) {
                    byte[] extended = new byte[output.length * 2];
                    System.arraycopy(output, 0, extended, 0, count);
                    output = extended;
                }
                count += inflater.inflate(output, count, output.length - count);
            }
            inflater.end();
            return new String(output, 0, count, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 不是 zlib 压缩，返回原始内容
            return direct;
        }
    }

    private byte[] readFileBytes(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int off = 0;
            while (off < data.length) {
                int r = fis.read(data, off, data.length - off);
                if (r < 0) break;
                off += r;
            }
            return data;
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
