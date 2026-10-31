package io.takamiya.mio.configManage;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import io.takamiya.mio.app.Mio;
import io.takamiya.mio.config.ConfigLoader;
import io.takamiya.mio.config.Plugin;
import io.takamiya.mio.config.PluginConfig;
import io.takamiya.mio.config.lhrb.LhrbParseException;
import io.takamiya.mio.config.lhrb.LhrbReader;
import io.takamiya.mio.utils.HttpManage;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ConfigUpdateChecker {

    private static final String TAG = "ConfigUpdateChecker";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final HttpClientJava client = HttpManage.getInstance();
    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    public static class UpdateResult {
        private final String packageName;
        private final String configName;
        private final int currentVersionCode;
        private final String currentVersionName;
        private int remoteVersionCode;
        private String remoteVersionName;
        private String remoteDescription;
        private String remoteAuthor;
        private String remoteJson;
        private byte[] remoteLhrbBytes;
        private boolean hasUpdate;
        private String error;
        private List<ConfigDiffHelper.DiffLine> changelog;

        public UpdateResult(String packageName, String configName, int currentVersionCode, String currentVersionName) {
            this.packageName = packageName;
            this.configName = configName;
            this.currentVersionCode = currentVersionCode;
            this.currentVersionName = currentVersionName;
        }

        public String getPackageName() { return packageName; }
        public String getConfigName() { return configName; }
        public int getCurrentVersionCode() { return currentVersionCode; }
        public String getCurrentVersionName() { return currentVersionName; }
        public int getRemoteVersionCode() { return remoteVersionCode; }
        public String getRemoteVersionName() { return remoteVersionName; }
        public String getRemoteDescription() { return remoteDescription; }
        public String getRemoteAuthor() { return remoteAuthor; }
        public String getRemoteJson() { return remoteJson; }
        public byte[] getRemoteLhrbBytes() { return remoteLhrbBytes; }
        public boolean hasUpdate() { return hasUpdate; }
        public String getError() { return error; }
        public List<ConfigDiffHelper.DiffLine> getChangelog() { return changelog; }

        public boolean hasError() { return error != null && !error.isEmpty(); }
    }

    public interface Callback {
        void onResults(List<UpdateResult> results);
    }

    /** 使用内建工具密钥指纹集合检查更新。 */
    public static void checkAll(List<Plugin> plugins, Callback callback) {
        checkAll(plugins, Collections.singleton(Mio.getLhrbPublicKeyFingerprint()), callback);
    }

    public static void checkAll(List<Plugin> plugins, Set<String> trustedFingerprints, Callback callback) {
        List<Plugin> toCheck = new ArrayList<>();
        for (Plugin plugin : plugins) {
            PluginConfig config = plugin.getConfig();
            if (config != null && config.getUpdateUrl() != null && !config.getUpdateUrl().isEmpty()) {
                toCheck.add(plugin);
            }
        }

        if (toCheck.isEmpty()) {
            mainHandler.post(() -> callback.onResults(new ArrayList<>()));
            return;
        }

        executor.execute(() -> {
            List<UpdateResult> results = new ArrayList<>();
            AtomicInteger remaining = new AtomicInteger(toCheck.size());

            for (Plugin plugin : toCheck) {
                PluginConfig config = plugin.getConfig();
                UpdateResult result = new UpdateResult(
                        config.getPackageName(),
                        config.getName(),
                        config.getVersionCode() != null ? config.getVersionCode() : 0,
                        config.getVersionName()
                );

                try {
                    results.add(result);
                    if (plugin.isLocked()) {
                        checkLhrbUrl(result, config.getUpdateUrl(), trustedFingerprints);
                        if (remaining.decrementAndGet() == 0) {
                            mainHandler.post(() -> callback.onResults(results));
                        }
                    } else {
                        client.get(config.getUpdateUrl())
                                .onSuccess((code, body) -> {
                                    try {
                                        applyRemoteConfig(result, body);
                                    } catch (Exception e) {
                                        AppLogger.e(TAG, "Parse error for " + config.getPackageName() + ": " + e.getMessage());
                                        result.error = "解析失败: " + e.getMessage();
                                    }
                                    if (remaining.decrementAndGet() == 0) {
                                        mainHandler.post(() -> callback.onResults(results));
                                    }
                                })
                                .onFailure((code, err) -> {
                                    AppLogger.e(TAG, "Network error for " + config.getPackageName() + ": " + code + "," + err);
                                    result.error = "网络错误: " + code + "," + err;
                                    if (remaining.decrementAndGet() == 0) {
                                        mainHandler.post(() -> callback.onResults(results));
                                    }
                                })
                                .execute();
                    }
                } catch (Exception e) {
                    AppLogger.e(TAG, "Request failed for " + config.getPackageName() + ": " + e.getMessage());
                    result.error = "请求异常: " + e.getMessage();
                    if (remaining.decrementAndGet() == 0) {
                        mainHandler.post(() -> callback.onResults(results));
                    }
                }
            }
        });
    }

    private static void checkLhrbUrl(UpdateResult result, String url, Set<String> trustedFingerprints) {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                result.error = "网络错误: " + response.code();
                return;
            }
            byte[] body = response.body() != null ? response.body().bytes() : null;
            if (body == null || body.length == 0) {
                result.error = "远程响应为空";
                return;
            }

            if (LhrbReader.isLhrb(body)) {
                try {
                    LhrbReader.ParseResult parsed = LhrbReader.parse(body, trustedFingerprints);
                    applyRemoteConfig(result, parsed.getJson());
                    result.remoteLhrbBytes = body;
                } catch (LhrbParseException e) {
                    AppLogger.e(TAG, "Remote LHRB validation failed for " + result.getPackageName() + ": " + e.getMessage());
                    result.error = "远程LHRB校验失败: " + e.getMessage();
                }
            } else {
                applyRemoteConfig(result, new String(body, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "LHRB update fetch failed for " + result.getPackageName() + ": " + e.getMessage());
            result.error = "网络错误: " + e.getMessage();
        }
    }

    private static void applyRemoteConfig(UpdateResult result, String body) throws Exception {
        JSONObject root = new JSONObject(body.trim());
        JSONObject pluginObj = root.optJSONObject("plugin");
        if (pluginObj == null) {
            result.error = "无效的配置格式";
            return;
        }
        JSONObject remoteConfig = pluginObj.optJSONObject("config");
        if (remoteConfig != null) {
            result.remoteVersionCode = remoteConfig.optInt("versionCode", 0);
            result.remoteVersionName = remoteConfig.optString("versionName", "");
            result.remoteDescription = remoteConfig.optString("description", "");
            result.remoteAuthor = remoteConfig.optString("author", "");
            result.remoteJson = body.trim();
            result.hasUpdate = result.remoteVersionCode > result.currentVersionCode;
        }
    }

    /** 使用内建工具密钥指纹集合静默更新。 */
    public static void silentUpdate(List<Plugin> plugins, ConfigPackageManager manager, Consumer<List<InstallResult>> callback) {
        silentUpdate(plugins, Collections.singleton(Mio.getLhrbPublicKeyFingerprint()), manager, callback);
    }

    public static void silentUpdate(List<Plugin> plugins, Set<String> trustedFingerprints,
                                    ConfigPackageManager manager, Consumer<List<InstallResult>> callback) {
        checkAll(plugins, trustedFingerprints, results -> {
            List<Plugin> toUpdate = new ArrayList<>();
            for (UpdateResult result : results) {
                if (result.hasUpdate() && result.getRemoteJson() != null) {
                    for (Plugin p : plugins) {
                        if (p.getConfig().getPackageName().equals(result.getPackageName())) {
                            toUpdate.add(p);
                            break;
                        }
                    }
                }
            }

            if (toUpdate.isEmpty()) {
                mainHandler.post(() -> callback.accept(new ArrayList<>()));
                return;
            }

            executor.execute(() -> {
                List<InstallResult> installResults = new ArrayList<>();
                for (Plugin p : toUpdate) {
                    for (UpdateResult r : results) {
                        if (r.getPackageName().equals(p.getConfig().getPackageName()) && r.getRemoteJson() != null) {
                            InstallResult ir;
                            if (r.getRemoteLhrbBytes() != null) {
                                ir = manager.installLhrb(r.getRemoteLhrbBytes(), true);
                            } else {
                                ir = manager.install(r.getRemoteJson(), true);
                            }
                            installResults.add(ir);
                            break;
                        }
                    }
                }
                mainHandler.post(() -> callback.accept(installResults));
            });
        });
    }

    public interface Consumer<T> {
        void accept(T value);
    }
}
