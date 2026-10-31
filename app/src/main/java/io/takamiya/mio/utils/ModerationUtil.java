package io.takamiya.mio.utils;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import cn.panda.utils.library.UrlBuilder;

/**
 * 版主置顶/推荐操作，走用户侧 bbs-api：
 * posts.php / resources.php 的 action=post-top / action=post-recommend，
 * body 为 {"id": <id>, "is_top"/"is_recommend": bool}，Bearer 用户 token 鉴权。
 */
public class ModerationUtil {

    public interface Callback {
        void onDone(boolean ok, String msg);
    }

    private static void toggle(String path, String action, String field, int id,
                               boolean value, Callback cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put(field, value);
        String url = UrlBuilder.getInstance()
                .longPath(path)
                .param("action", action)
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();
        HttpManage.getInstance().post(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, resp) -> {
                    try {
                        JSONObject respJson = new JSONObject(resp.trim());
                        if (code == 200 && respJson.optInt("code") == 200) {
                            if (cb != null) cb.onDone(true, respJson.optString("msg", "操作成功"));
                        } else {
                            if (cb != null) cb.onDone(false, respJson.optString("msg", "操作失败"));
                        }
                    } catch (Exception e) {
                        if (cb != null) cb.onDone(false, "操作失败");
                    }
                })
                .onFailure((code, err) -> {
                    if (cb != null) cb.onDone(false, "网络异常：" + err);
                })
                .execute();
    }

    public static void setPostTop(int id, boolean top, Callback cb) {
        toggle(SecureStrings.path(50), "post-top", "is_top", id, top, cb);
    }

    public static void setPostRecommend(int id, boolean recommend, Callback cb) {
        toggle(SecureStrings.path(50), "post-recommend", "is_recommend", id, recommend, cb);
    }

    public static void setResourceTop(int id, boolean top, Callback cb) {
        toggle(SecureStrings.path(51), "post-top", "is_top", id, top, cb);
    }

    public static void setResourceRecommend(int id, boolean recommend, Callback cb) {
        toggle(SecureStrings.path(51), "post-recommend", "is_recommend", id, recommend, cb);
    }
}