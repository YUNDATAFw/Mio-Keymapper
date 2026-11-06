package io.takamiya.mio.dsl;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.fsy.plugin.kore.library.PluginCallback;
import cn.fsy.plugin.kore.library.KoreEngine;
import cn.fsy.plugin.kore.library.KoreException;
import cn.fsy.plugin.kore.library.internal.manage.open.OpenManager;
import cn.fsy.plugin.kore.library.plugin.PluginItem;
import cn.panda.utils.library.AppLogger;

/**
 * 插件资源抽取工具：安装成功后从 zip 补抽 {@code res/*} 到 {@code <pluginDir>/res/*}。
 *
 * <p>Kore 安装流程：{@link Install} 完成解压、签名校验、安全校验、落盘到 {@code kore-plugins/{pkg}/}，
 * 然后由 {@link DslPluginManager.installKoreZip} 调用本类的 {@code extractRes} 补抽资源。
 *
 * <p>资源路径在 onKeyInit 里以 "@res/..." 引用（见 MioEngFunBridge.resolveCardBg）。
 * 新增/更新 res 资源后需重新安装插件才会落地（覆盖安装会先清空 pluginDir）。
 */
public final class PluginResInstaller {

    private static final String TAG = "PluginResInstaller";

    private PluginResInstaller() {
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