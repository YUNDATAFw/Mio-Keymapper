package io.takamiya.mio.building;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.takamiya.mio.building.core.Mappings;
import io.takamiya.mio.building.core.Sensitive;

/**
 * 映射表加载器：assets/generator_blocks.zip 解压到外部私有目录（保留复用），
 * 解析为 Mappings 单例；敏感词库同步释放。全部在调用方线程执行（应在后台线程调用）。
 */
public final class MappingLoader {

    private static final String ASSET_ZIP = "mcconverter/generator_blocks.zip";
    private static final String ASSET_SENSITIVE = "mcconverter/sensitive_lite.json";
    private static final String MAPPING_FILE = "generator_blocks.json";
    private static final String SENSITIVE_FILE = "sensitive_lite.json";

    private static volatile Mappings mappings;
    private static volatile Sensitive sensitive;

    private MappingLoader() {
    }

    /** 外部私有目录 <externalFiles>/mcconverter，解压目标。 */
    public static File cacheDir(Context context) throws IOException {
        File ext = context.getExternalFilesDir(null);
        if (ext == null) {
            throw new IOException("外部存储不可用，无法准备映射表");
        }
        File dir = new File(ext, "mcconverter");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建映射表目录: " + dir);
        }
        return dir;
    }

    /** 确保映射 json 落盘（已存在则跳过），返回文件。 */
    public static File ensureMappingFile(Context context) throws IOException {
        File dir = cacheDir(context);
        File json = new File(dir, MAPPING_FILE);
        if (json.exists() && json.length() > 0) {
            return json;
        }
        try {
            extractAsset(context, ASSET_ZIP, json);
        } catch (IOException e) {
            // 解压失败残留半成品：删除重试一次
            //noinspection ResultOfMethodCallIgnored
            json.delete();
            extractAsset(context, ASSET_ZIP, json);
        }
        return json;
    }

    /** 确保敏感词库落盘（已存在则跳过），返回文件（可能不存在，Sensitive.load 容错）。 */
    public static File ensureSensitiveFile(Context context) throws IOException {
        File dir = cacheDir(context);
        File f = new File(dir, SENSITIVE_FILE);
        if (f.exists() && f.length() > 0) {
            return f;
        }
        extractAsset(context, ASSET_SENSITIVE, f);
        return f;
    }

    /** 加载（或复用）映射表单例。首次需解压 + 解析约数秒，应在后台线程调用。 */
    public static Mappings getMappings(Context context) throws IOException {
        Mappings m = mappings;
        if (m != null) {
            return m;
        }
        synchronized (MappingLoader.class) {
            if (mappings == null) {
                File json = ensureMappingFile(context);
                mappings = Mappings.load(json);
            }
            return mappings;
        }
    }

    /** 加载（或复用）敏感词库单例；失败时返回未启用的空实例。 */
    public static Sensitive getSensitive(Context context) {
        Sensitive s = sensitive;
        if (s != null) {
            return s;
        }
        synchronized (MappingLoader.class) {
            if (sensitive == null) {
                File f = null;
                try {
                    f = ensureSensitiveFile(context);
                } catch (IOException ignored) {
                }
                sensitive = Sensitive.load(f);
            }
            return sensitive;
        }
    }

    /** 释放单次转换不再需要时可调用；不调用则常驻复用。 */
    public static void release() {
        synchronized (MappingLoader.class) {
            mappings = null;
            sensitive = null;
        }
    }

    private static void extractAsset(Context context, String assetPath, File target) throws IOException {
        File tmp = new File(target.getPath() + ".tmp");
        try (InputStream in = context.getAssets().open(assetPath);
             ZipInputStream zin = new ZipInputStream(in);
             FileOutputStream out = new FileOutputStream(tmp)) {
            ZipEntry entry;
            boolean found = false;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = zin.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                zin.closeEntry();
                found = true;
                break;
            }
            if (!found) {
                throw new IOException("zip 内无文件: " + assetPath);
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }
        if (target.exists() && !target.delete()) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("无法覆盖目标文件: " + target);
        }
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("无法移动解压结果: " + target);
        }
    }
}
