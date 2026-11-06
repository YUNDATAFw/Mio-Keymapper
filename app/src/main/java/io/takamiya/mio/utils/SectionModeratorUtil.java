package io.takamiya.mio.utils;

import cn.panda.utils.library.UrlBuilder;

/**
 * 板块版主权限检查（离线模式存根）。
 * 网络功能已禁用，直接返回 false。
 */
public class SectionModeratorUtil {

    public interface Callback {
        void onResult(boolean isModerator);
    }

    public static void check(int sectionId, Callback callback) {
        // 离线模式：直接返回非版主
        if (callback != null) {
            callback.onResult(false);
        }
    }
}