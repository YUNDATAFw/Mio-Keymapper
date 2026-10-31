package io.takamiya.mio.utils;

import cn.panda.utils.library.AppLogger;

/**
 * 仅在 debug 构建输出的 d 级日志，避免 release 下热路径字符串拼接与写入开销。
 */
public final class PDebug {
    private PDebug() {
    }

    public static void d(String tag, String msg) {
        if (io.takamiya.mio.BuildConfig.DEBUG) {
            AppLogger.d(tag, msg);
        }
    }
}
