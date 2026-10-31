package io.takamiya.mio.utils;

import java.io.File;
import java.util.Map;

import okhttp3.MediaType;

public class UserApi {

    private UserApi() {
    }

    private static String url(String path) {
        return cn.panda.utils.library.UrlBuilder.getInstance()
                .longPath(path).param(SecureStrings.path(95), AppToken.getToken()).build();
    }

    public static void getProfile(SuccessListener success, FailureListener failure) {
        HttpManage.getInstance().get(url(SecureStrings.path(6)))
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess(success::onSuccess)
                .onFailure(failure::onFailure)
                .execute();
    }

    public static void updateProfile(Map<String, Object> fields,
                                     SuccessListener success, FailureListener failure) {
        HttpManage.getInstance().post(url(SecureStrings.path(6)))
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(fields)
                .onSuccess(success::onSuccess)
                .onFailure(failure::onFailure)
                .execute();
    }

    public static void updateProfileInfo(Map<String, Object> fields,
                                         SuccessListener success, FailureListener failure) {
        HttpManage.getInstance().post(url(SecureStrings.path(7)))
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(fields)
                .onSuccess(success::onSuccess)
                .onFailure(failure::onFailure)
                .execute();
    }

    public static void uploadAvatar(File file, String filename, String mimeType,
                                    SuccessListener success, FailureListener failure) {
        HttpManage.getInstance().post(url(SecureStrings.path(8)))
                .header(SecureStrings.path(96), UserToken.getToken())
                .file("avatar", filename, file, MediaType.parse(mimeType))
                .onSuccess(success::onSuccess)
                .onFailure(failure::onFailure)
                .execute();
    }

    public interface SuccessListener {
        void onSuccess(int code, String body);
    }

    public interface FailureListener {
        void onFailure(int code, String error);
    }
}
