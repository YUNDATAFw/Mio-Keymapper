package io.takamiya.mio.utils;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import cn.panda.utils.library.UrlBuilder;

/**
 * 版主置顶/推荐操作（离线模式存根）。
 * 网络功能已禁用，所有操作直接返回"离线模式不可用"。
 */
public class ModerationUtil {

    public interface Callback {
        void onDone(boolean ok, String msg);
    }

    private static void toggle(String path, String action, String field, int id,
                               boolean value, Callback cb) {
        // 离线模式：直接返回不可用
        if (cb != null) {
            cb.onDone(false, "离线模式：版主操作不可用（需网络）");
        }
    }

    public static void setPostTop(int id, boolean top, Callback cb) {
        toggle(null, "post-top", "is_top", id, top, cb);
    }

    public static void setPostRecommend(int id, boolean recommend, Callback cb) {
        toggle(null, "post-recommend", "is_recommend", id, recommend, cb);
    }

    public static void setResourceTop(int id, boolean top, Callback cb) {
        toggle(null, "post-top", "is_top", id, top, cb);
    }

    public static void setResourceRecommend(int id, boolean recommend, Callback cb) {
        toggle(null, "post-recommend", "is_recommend", id, recommend, cb);
    }
}