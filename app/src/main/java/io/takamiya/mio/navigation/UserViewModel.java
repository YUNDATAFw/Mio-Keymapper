package io.takamiya.mio.navigation;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import org.json.JSONObject;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.utils.UserApi;

public class UserViewModel extends AndroidViewModel {

    private static final String TAG = "UserViewModel";

    private JSONObject profileData;
    private int currentGender;
    private boolean profileLoaded;

    public UserViewModel(@NonNull Application application) {
        super(application);
    }

    public JSONObject getProfileData() {
        return profileData;
    }

    public int getCurrentGender() {
        return currentGender;
    }

    public void setCurrentGender(int currentGender) {
        this.currentGender = currentGender;
    }

    public boolean isProfileLoaded() {
        return profileLoaded;
    }

    public void loadProfile(ProfileCallback callback) {
        UserApi.getProfile((code, body) -> {
            try {
                JSONObject info = new JSONObject(body.trim());
                if (code == 200 && info.getInt("code") == 200) {
                    JSONObject data = info.optJSONObject("data");
                    if (data != null) {
                        profileData = data;
                        currentGender = data.optInt("gender", 0);
                        profileLoaded = true;
                        callback.onSuccess(data);
                    } else {
                        callback.onError("暂无用户资料");
                    }
                } else {
                    String errMsg = info.optString("msg", "获取用户信息失败");
                    AppLogger.e(TAG, "load profile failed: " + errMsg);
                    callback.onError(errMsg);
                }
            } catch (Exception e) {
                AppLogger.e(TAG, "load profile parse error: " + e.getMessage());
                callback.onError("解析失败");
            }
        }, (code, error) -> {
            AppLogger.e(TAG, "load profile request failed: " + error);
            callback.onError("网络请求失败");
        });
    }

    public interface ProfileCallback {
        void onSuccess(JSONObject data);
        void onError(String message);
    }
}
