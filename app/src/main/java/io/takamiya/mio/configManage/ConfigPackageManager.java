package io.takamiya.mio.configManage;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.config.ConfigLoader;
import io.takamiya.mio.config.Plugin;
import io.takamiya.mio.config.PluginConfig;

public class ConfigPackageManager {

    private static final String TAG = "ConfigPackageManager";

    private final IPluginInstaller installer;

    public ConfigPackageManager(Context context) {
        this.installer = new IPluginInstaller(context);
        AppLogger.i(TAG, "ConfigPackageManager initialized");
    }

    public Plugin get(String packageName) {
        AppLogger.d(TAG, "get() called: package=" + packageName);
        Plugin plugin = installer.readPlugin(packageName);
        if (plugin != null) {
            AppLogger.d(TAG, "get: found \"" + plugin.getConfig().getName()
                    + "\" " + plugin.getConfig().getVersionName());
        } else {
            AppLogger.w(TAG, "get: plugin \"" + packageName + "\" not found");
        }
        return plugin;
    }

    public String getPluginJson(String packageName) {
        AppLogger.d(TAG, "getPluginJson() called: package=" + packageName);
        return installer.readPluginJson(packageName);
    }

    /** 读取配置源文件原始字节（JSON 或 LHRB 均不解码），用于导出。 */
    public byte[] getPluginRawFile(String packageName) {
        return installer.readPluginRawFile(packageName);
    }

    public List<Plugin> list() {
        AppLogger.d(TAG, "list() called");
        File dir = installer.getPluginFile("").getParentFile();
        if (dir == null || !dir.exists()) {
            AppLogger.d(TAG, "list: config directory does not exist, returning empty list");
            return Collections.emptyList();
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json") || name.endsWith(".lhrb"));
        if (files == null || files.length == 0) {
            AppLogger.d(TAG, "list: no config files found in " + dir.getAbsolutePath());
            return Collections.emptyList();
        }

        AppLogger.d(TAG, "list: found " + files.length + " config file(s) in " + dir.getAbsolutePath());
        List<Plugin> plugins = new ArrayList<>();
        for (File file : files) {
            AppLogger.d(TAG, "list: reading file " + file.getName());
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[(int) file.length()];
                int read = fis.read(buffer);
                if (read <= 0) {
                    AppLogger.w(TAG, "list: empty file, skipping: " + file.getName());
                    continue;
                }

                Plugin plugin;
                if (file.getName().endsWith(".lhrb")) {
                    plugin = installer.readLhrbFile(file);
                    if (plugin == null) {
                        AppLogger.w(TAG, "list: LHRB parse failed, skipping: " + file.getName());
                    }
                } else {
                    String json = new String(buffer, StandardCharsets.UTF_8);
                    plugin = null;
                    try {
                        plugin = ConfigLoader.parse(json);
                    } catch (Exception e) {
                        AppLogger.w(TAG, "list: parse failed for " + file.getName() + ": " + e.getMessage());
                    }
                    if (plugin != null && plugin.getConfig() != null) {
                        plugins.add(plugin);
                        AppLogger.d(TAG, "list: loaded \"" + plugin.getConfig().getName()
                                + "\" (" + plugin.getConfig().getPackageName() + ")");
                        continue;
                    }
                    Plugin incompatible = buildIncompatiblePlaceholder(file.getName(), json);
                    if (incompatible != null) {
                        plugins.add(incompatible);
                        AppLogger.w(TAG, "list: added incompatible placeholder for: " + file.getName());
                    } else {
                        AppLogger.w(TAG, "list: skipping unreadable file: " + file.getName());
                    }
                    continue;
                }

                if (plugin != null && plugin.getConfig() != null) {
                    plugins.add(plugin);
                    AppLogger.d(TAG, "list: loaded \"" + plugin.getConfig().getName()
                            + "\" (" + plugin.getConfig().getPackageName() + ")");
                } else {
                    AppLogger.w(TAG, "list: skipping file: " + file.getName());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "list: skipping corrupt plugin file: " + file.getName() + ": " + e.getMessage(), e);
            }
        }
        AppLogger.i(TAG, "list: returning " + plugins.size() + " plugin(s)");
        return plugins;
    }

    public boolean isInstalled(String packageName) {
        boolean exists = installer.getPluginFile(packageName).exists()
                || installer.getLhrbFile(packageName).exists();
        AppLogger.d(TAG, "isInstalled(\"" + packageName + "\") = " + exists);
        return exists;
    }

    public List<String> getInstalledPackages() {
        AppLogger.d(TAG, "getInstalledPackages() called");
        List<Plugin> plugins = list();
        List<String> packages = new ArrayList<>(plugins.size());
        for (Plugin p : plugins) {
            String pkg = p.getConfig().getPackageName();
            packages.add(pkg);
            AppLogger.d(TAG, "getInstalledPackages: added \"" + pkg + "\"");
        }
        AppLogger.i(TAG, "getInstalledPackages: returning " + packages.size() + " package(s)");
        return packages;
    }

    public InstallResult install(String json) {
        return installer.install(json);
    }

    public InstallResult install(String json, boolean force) {
        return installer.install(json, force);
    }

    public InstallResult installLhrb(byte[] data) {
        return installer.installLhrb(data);
    }

    public InstallResult installLhrb(byte[] data, boolean force) {
        return installer.installLhrb(data, force);
    }

    public void uninstall(String packageName) {
        AppLogger.i(TAG, "uninstall() called: package=" + packageName);
        installer.uninstall(packageName);
    }

    public boolean setEnabled(String packageName, boolean enabled) {
        return installer.setEnabled(packageName, enabled);
    }

    public int getEnabledCount() {
        List<Plugin> plugins = list();
        int count = 0;
        for (Plugin p : plugins) {
            if (installer.isEnabled(p.getConfig().getPackageName())) {
                count++;
            }
        }
        return count;
    }

    /** 受信任公钥指纹集合（内建工具密钥 ∪ 本应用自持密钥）。 */
    public java.util.Set<String> getTrustedFingerprints() {
        return installer.getTrustedFingerprints();
    }

    /**
     * 对无法用当前引擎解析的 JSON（旧格式 v<4）构建"不适配"占位配置，
     * 以便在列表中展示与导出，但不可加载/编辑。
     */
    private Plugin buildIncompatiblePlaceholder(String fileName, String json) {
        String name = null;
        String pkg = null;
        Integer minEngine = null;
        try {
            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONObject pluginObj = root.optJSONObject("plugin");
            org.json.JSONObject config = pluginObj != null ? pluginObj.optJSONObject("config") : null;
            if (config != null) {
                name = config.optString("name", null);
                pkg = config.optString("package", null);
                if (config.has("minEngine")) minEngine = config.optInt("minEngine", 1);
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "buildIncompatiblePlaceholder: metadata scan failed: " + e.getMessage());
        }

        PluginConfig pc = new PluginConfig();
        pc.setName(name != null && !name.trim().isEmpty() ? name : fileName.replaceAll("\\.json$", ""));
        pc.setPackageName(pkg != null && !pkg.trim().isEmpty() ? pkg : "unknown");
        pc.setMinEngine(minEngine != null ? minEngine : 1);
        pc.setEnable(false);

        Plugin plugin = new Plugin();
        plugin.setConfig(pc);
        plugin.setIncompatible(true);
        return plugin;
    }
}
