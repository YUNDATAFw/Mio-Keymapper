package io.takamiya.mio.dsl;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.fsy.plugin.sdl.library.PluginCallback;
import cn.fsy.plugin.sdl.library.SdlEngine;
import cn.fsy.plugin.sdl.library.SdlException;
import cn.fsy.plugin.sdl.library.internal.manage.open.OpenManager;
import cn.fsy.plugin.sdl.library.plugin.PluginItem;
import cn.panda.utils.library.AppLogger;

/**
 * 插件安装入口：包装 {@link SdlEngine#install}，成功后从同一 zip 补抽 {@code res/*}
 * 到 {@code <pluginDir>/res/*}。
 *
 * <p>引擎 doInstallPackage 只搬运 config.json / icon.png / script/**，zip 内其他文件
 * （如插件资源 res/）解到临时目录后不会落地，需宿主补抽。安全清单只盖 config/icon/script，
 * {@code verifyDigests} 为正向校验，额外文件不会被判篡改。</p>
 *
 * <p>资源路径在 onKeyInit 里以 "@res/..." 引用（见 MioEngFunBridge.resolveCardBg）。
 * 新增/更新 res 资源后需重新安装插件才会落地（覆盖安装会先清空 pluginDir）。</p>
 */
public final class PluginResInstaller {

    private static final String TAG = "PluginResInstaller";

    private PluginResInstaller() {
    }

    /** 安装 zip，成功回调前补抽 res/* 到 pluginDir（失败只记日志，不阻断安装成功）。 */
    public static void install(Context context, String zipPath, PluginCallback callback) {
        Context app = context.getApplicationContext();
        SdlEngine.install(zipPath, wrap(app, zipPath, callback));
    }

    static PluginCallback wrap(Context app, String zipPath, PluginCallback callback) {
        return new PluginCallback() {
            @Override
            public void onStart(java.nio.file.Path installPath) {
                if (callback != null) callback.onStart(installPath);
            }

            @Override
            public void onProgress(int progressPercent) {
                if (callback != null) callback.onProgress(progressPercent);
            }

            @Override
            public void onSuccess(PluginItem item) {
                try {
                    extractRes(app, zipPath, item != null ? item.getPluginPackage() : null);
                } catch (Exception e) {
                    AppLogger.w(TAG, "extract res failed: " + e.getMessage());
                }
                if (callback != null) callback.onSuccess(item);
            }

            @Override
            public void onFailure(SdlException e) {
                if (callback != null) callback.onFailure(e);
            }

            @Override
            public void onCancel() {
                if (callback != null) callback.onCancel();
            }
        };
    }

    /**
     * 从安装 zip 抽取 {@code res/**} 到 {@code <pluginDir>/res/**}（Zip Slip 防护）。
     *
     * @return 抽取的文件数
     */
    static int extractRes(Context context, String zipPath, String pkg) throws Exception {
        if (zipPath == null || pkg == null || pkg.isEmpty()) return 0;
        File root = OpenManager.pluginDir(context.getFilesDir(), pkg);
        if (!root.isDirectory()) {
            AppLogger.w(TAG, "extract res skipped, plugin dir missing: " + root);
            return 0;
        }
        String rootPath = root.getCanonicalPath();
        int count = 0;
        try (ZipFile zf = new ZipFile(zipPath)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName().replace('\\', '/');
                if (e.isDirectory() || !name.startsWith("res/") || name.contains("..")) continue;
                File out = new File(root, name);
                if (!out.getCanonicalPath().startsWith(rootPath + File.separator)) {
                    AppLogger.w(TAG, "zip slip skipped: " + name);
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    AppLogger.w(TAG, "mkdir failed: " + parent);
                    continue;
                }
                try (InputStream in = zf.getInputStream(e);
                     FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                    }
                }
                count++;
            }
        }
        if (count > 0) {
            AppLogger.i(TAG, "extracted " + count + " res file(s) -> " + new File(root, "res"));
        }
        return count;
    }
}
