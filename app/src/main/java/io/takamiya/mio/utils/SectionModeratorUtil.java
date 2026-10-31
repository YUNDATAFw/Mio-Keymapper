package io.takamiya.mio.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import cn.panda.utils.library.UrlBuilder;

/**
 * 判断当前登录用户是否为某板块的版主（role=1）或管理员（role=2）。
 * 依据板块详情接口返回的 admins 列表与 owner，与当前用户 id 比对。
 */
public class SectionModeratorUtil {

    public interface Callback {
        void onResult(boolean isModerator);
    }

    public static void check(int sectionId, Callback callback) {
        if (sectionId <= 0 || !UserToken.isLoggedIn()) {
            if (callback != null) callback.onResult(false);
            return;
        }
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(48))
                .param("action", "detail")
                .param("id", String.valueOf(sectionId))
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();
        HttpManage.getInstance().get(url)
                .onSuccess((code, body) -> {
                    boolean moder = false;
                    try {
                        JSONObject resp = new JSONObject(body);
                        if (code == 200 && resp.getInt("code") == 200) {
                            JSONObject data = resp.optJSONObject("data");
                            if (data != null) {
                                int sub = UserToken.getSub();
                                JSONArray admins = data.optJSONArray("admins");
                                if (admins != null) {
                                    for (int i = 0; i < admins.length(); i++) {
                                        JSONObject a = admins.optJSONObject(i);
                                        if (a != null && a.optInt("id", 0) == sub) {
                                            moder = true;
                                            break;
                                        }
                                    }
                                }
                                if (!moder) {
                                    JSONObject owner = data.optJSONObject("owner");
                                    if (owner != null && owner.optInt("id", 0) == sub) {
                                        moder = true;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        moder = false;
                    }
                    if (callback != null) callback.onResult(moder);
                })
                .onFailure((code, err) -> {
                    if (callback != null) callback.onResult(false);
                })
                .execute();
    }
}