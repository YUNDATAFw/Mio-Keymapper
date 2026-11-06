package io.takamiya.mio.dsl;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import cn.fsy.plugin.kore.library.BridgeHandler;
import cn.fsy.plugin.kore.library.internal.manage.open.OpenManager;
import cn.fsy.plugin.kore.library.internal.registry.RegistryManagerHolder;
import cn.fsy.plugin.kore.library.internal.running.AstInterpreter;
import cn.fsy.plugin.kore.library.internal.running.PermissionGuard;
import cn.panda.utils.library.AppLogger;

/**
 * 插件 HostData 存储（{@code <pluginDir>/.registry/HostData.regbin}）损坏自愈。
 *
 * <p>背景：panda Registry 的 {@code FileBackend.load} 在读取损坏文件时会把垃圾字节
 * 当作长度字段，抛出 {@link NegativeArraySizeException} / {@link java.nio.BufferUnderflowException}
 * 等运行时异常，导致引擎 {@code HostData.get} 桥以 BRIDGE_EXECUTION_FAILED 失败，
 * 插件 {@code keyInit} / {@code loading} 被中断（实例：NegativeArraySizeException(-1015823501)）。</p>
 *
 * <p>自愈策略：原始桥首次失败且异常链具备“文件损坏”特征时，记录文件头 hex、
 * 清空宿主侧 host store 缓存、把损坏文件改名为 {@code *.corrupt-<ts>} 隔离，
 * 再重试一次让引擎重建空存储（已保存配置丢失，插件回落默认值，功能恢复）。</p>
 */
public final class HostDataRepair {

    private static final String TAG = "HostDataRepair";

    private HostDataRepair() {
    }

    /**
     * 包裹引擎原始 {@code HostData.get} 桥，加入“损坏则备份重建重试一次”能力。
     * 在 {@link MioBridgeRegistry#register} 中于捕获原始桥后调用。
     */
    public static BridgeHandler wrap(Context context, BridgeHandler original) {
        final Context appCtx = context.getApplicationContext();
        return args -> {
            if (args == null || args.length == 0 || !(args[0] instanceof String)) {
                return original.invoke(args); // 参数校验交给引擎（INVALID_ARGUMENT）
            }
            try {
                return original.invoke(args);
            } catch (RuntimeException e) {
                if (!looksCorrupt(e)) throw e;
                String pkg = currentPackage();
                if (pkg == null) throw e;
                File pluginDir = OpenManager.pluginDir(appCtx.getFilesDir(), pkg);
                if (!heal(pluginDir, e)) throw e;
                return original.invoke(args); // 隔离损坏文件后重试（引擎重建空存储）
            }
        };
    }

    /**
     * 隔离损坏的 HostData.regbin 并清理宿主侧缓存，返回是否可安全重试。
     *
     * @return true = 损坏文件已移走（或本就不存在），可以重建重试
     */
    public static boolean heal(File pluginDir, Throwable error) {
        try {
            AppLogger.e(TAG, "heal: HostData store unreadable @ " + pluginDir
                    + " - " + describe(error));
            RegistryManagerHolder.closeHostStore(pluginDir);
            File dir = new File(pluginDir, ".registry");
            File[] files = dir.listFiles();
            if (files == null) return true;
            boolean movedAny = false;
            for (File f : files) {
                String name = f.getName();
                // 隔离全部 HostData 主文件（regbin/wal 等），与引擎 quarantineHostFiles 口径一致；
                // 跳过已隔离的 *.corrupt-* 备份
                if (!name.startsWith("HostData") || name.contains(".corrupt-")) continue;
                AppLogger.e(TAG, "heal: quarantine " + name + " size=" + f.length()
                        + " head=" + headHex(f, 48));
                File bak = new File(dir, name + ".corrupt-" + System.currentTimeMillis());
                boolean moved = f.renameTo(bak);
                if (!moved) moved = f.delete();
                if (moved) {
                    movedAny = true;
                    AppLogger.i(TAG, "heal: quarantined -> " + bak.getName());
                } else if (f.exists()) {
                    AppLogger.e(TAG, "heal: failed to quarantine " + name);
                    return false;
                }
            }
            return movedAny || !new File(dir, "HostData.regbin").exists();
        } catch (Throwable t) {
            AppLogger.e(TAG, "heal failed: " + t);
            return false;
        }
    }

    /**
     * 异常链（cause + suppressed）是否具备“文件损坏”特征——避免把业务异常
     * （参数非法、key 不存在等）误判为损坏而隔离健康文件。
     *
     * <p>新版引擎自愈失败时抛 {@code REGISTRY_ERROR}，根因在 suppressed 里，
     * 故除 cause 外还需扫描 suppressed。</p>
     */
    public static boolean looksCorrupt(Throwable error) {
        return hasCorruptFeature(error, 0);
    }

    private static boolean hasCorruptFeature(Throwable t, int depth) {
        if (t == null || depth > 16) return false;
        String name = t.getClass().getName();
        if (name.contains("Corrupt") || name.contains("MagicMismatch")
                || name.contains("VersionMismatch")) {
            return true;
        }
        if (t instanceof IOException) return true;                   // EOF / 读中断
        if (t instanceof java.nio.BufferUnderflowException) return true;
        if (t instanceof NegativeArraySizeException) return true;
        Throwable cause = t.getCause();
        if (cause != null && cause != t && hasCorruptFeature(cause, depth + 1)) {
            return true;
        }
        for (Throwable s : t.getSuppressed()) {
            if (s != t && hasCorruptFeature(s, depth + 1)) return true;
        }
        return false;
    }

    /** 当前插件调用上下文的包名（非插件上下文返回 null）。 */
    private static String currentPackage() {
        try {
            AstInterpreter in = AstInterpreter.current();
            if (in == null) return null;
            PermissionGuard g = in.getPermissionGuard();
            return g != null ? g.pluginPackage() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 异常链摘要（类名 + 消息，逐层）。 */
    private static String describe(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable t = error;
        int depth = 0;
        while (t != null && depth++ < 8) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(t.getClass().getSimpleName());
            if (t.getMessage() != null) sb.append(": ").append(t.getMessage());
            Throwable cause = t.getCause();
            if (cause == t) break;
            t = cause;
        }
        return sb.toString();
    }

    /** 文件头 hex（用于判断文件内容形态，辅助定位损坏原因）。 */
    private static String headHex(File f, int max) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[max];
            int n = in.read(buf);
            StringBuilder sb = new StringBuilder(n * 3);
            for (int i = 0; i < n; i++) {
                sb.append(String.format("%02X ", buf[i]));
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return "<read failed: " + e.getMessage() + ">";
        }
    }
}
