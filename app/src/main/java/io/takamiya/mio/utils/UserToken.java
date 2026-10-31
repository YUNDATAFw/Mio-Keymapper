package io.takamiya.mio.utils;

import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.*;
import io.takamiya.mio.utils.DataManage.Tag;

public class UserToken {
    private static final String TAG = "UserToken";

    public static boolean parseAndStore(String token) {
        if (token == null || token.isEmpty()) {
            AppLogger.e(TAG, "token is null or empty");
            return false;
        }

        try {
            JSONObject payload = decodePayload(token);
            if (payload == null) {
                AppLogger.e(TAG, "invalid token format");
                return false;
            }

            if (!payload.has("sub") || !payload.has("username") || !payload.has("email")
                    || !payload.has("iat") || !payload.has("exp")) {
                AppLogger.e(TAG, "missing required fields");
                return false;
            }

            int sub = payload.getInt("sub");
            int iat = payload.getInt("iat");
            int exp = payload.getInt("exp");
            String username = payload.getString("username");
            String email = payload.getString("email");

            if (sub <= 0 || exp <= iat || username.isEmpty() || email.isEmpty()) {
                AppLogger.e(TAG, "invalid field values: sub=" + sub + " iat=" + iat + " exp=" + exp);
                return false;
            }

            Registry reg = RegistryUtils.get();
            if (reg == null) {
                AppLogger.e(TAG, "registry not initialized");
                return false;
            }
            reg.set(Tag.TOKEN_TOKEN.getValue(), token);
            reg.set(Tag.TOKEN_SUB.getValue(), sub);
            reg.set(Tag.TOKEN_USERNAME.getValue(), username);
            reg.set(Tag.TOKEN_EMAIL.getValue(), email);
            reg.set(Tag.TOKEN_IAT.getValue(), iat);
            reg.set(Tag.TOKEN_EXP.getValue(), exp);
            reg.saveAsync();

            if (!verifyStored(sub, username)) {
                AppLogger.e(TAG, "read-back verification failed, clearing");
                clear();
                return false;
            }

            if (isExpired()) {
                AppLogger.e(TAG, "token already expired");
            }
            return true;
        } catch (Exception e) {
            AppLogger.e(TAG, "parse error: " + e.getMessage());
            return false;
        }
    }

    // 兼容服务器返回的 base64(JWT) 与标准 JWT 两种形态，返回 payload JSONObject；失败返回 null
    private static JSONObject decodePayload(String token) {
        String decoded = null;
        try {
            decoded = new String(decodeLenient(token), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        String[] attempts = decoded != null ? new String[]{decoded, token} : new String[]{token};
        for (String attempt : attempts) {
            String[] parts = attempt.split("\\.");
            if (parts.length < 2) continue;
            try {
                return new JSONObject(new String(decodeLenient(parts[1]), StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // 标准 + URL-safe 双字母表兼容的 base64 解码（容忍缺失 padding）
    private static byte[] decodeLenient(String s) {
        String normalized = s.replace('-', '+').replace('_', '/');
        return Base64.decode(normalized, Base64.DEFAULT);
    }

    private static boolean verifyStored(int expectedSub, String expectedUsername) {
        try {
            Registry reg = RegistryUtils.get();
            if (reg == null) return false;
            return reg.getInt(Tag.TOKEN_SUB.getValue()) == expectedSub
                    && expectedUsername.equals(reg.getString(Tag.TOKEN_USERNAME.getValue()));
        } catch (Exception e) {
            AppLogger.e(TAG, "verifyStored error: " + e.getMessage());
            return false;
        }
    }

    public static String getToken() {
        try {
            return RegistryUtils.get().getString(Tag.TOKEN_TOKEN.getValue());
        } catch (Exception e) {
            AppLogger.e(TAG, "getToken error: " + e.getMessage());
            return "";
        }
    }

    public static int getSub() {
        try {
            return RegistryUtils.get().getInt(Tag.TOKEN_SUB.getValue());
        } catch (Exception e) {
            AppLogger.e(TAG, "getSub error: " + e.getMessage());
            return 0;
        }
    }

    public static String getUsername() {
        try {
            return RegistryUtils.get().getString(Tag.TOKEN_USERNAME.getValue());
        } catch (Exception e) {
            AppLogger.e(TAG, "getUsername error: " + e.getMessage());
            return "";
        }
    }

    public static String getEmail() {
        try {
            return RegistryUtils.get().getString(Tag.TOKEN_EMAIL.getValue());
        } catch (Exception e) {
            AppLogger.e(TAG, "getEmail error: " + e.getMessage());
            return "";
        }
    }

    public static int getIat() {
        try {
            return RegistryUtils.get().getInt(Tag.TOKEN_IAT.getValue());
        } catch (Exception e) {
            AppLogger.e(TAG, "getIat error: " + e.getMessage());
            return 0;
        }
    }

    public static int getExp() {
        try {
            return RegistryUtils.get().getInt(Tag.TOKEN_EXP.getValue());
        } catch (Exception e) {
            AppLogger.e(TAG, "getExp error: " + e.getMessage());
            return 0;
        }
    }

    public static boolean isExpired() {
        int now = (int) (System.currentTimeMillis() / 1000);
        return now > getExp();
    }

    public static boolean hasToken() {
        try {
            return RegistryUtils.get().exists(Tag.TOKEN_SUB.getValue());
        } catch (Exception e) {
            AppLogger.e(TAG, "hasToken error: " + e.getMessage());
            return false;
        }
    }

    public static boolean isLoggedIn() {
        return hasToken() && !isExpired();
    }

    public static void clear() {
        try {
            Registry reg = RegistryUtils.get();
            if (reg == null) return;
            reg.delete(Tag.TOKEN_TOKEN.getValue());
            reg.delete(Tag.TOKEN_SUB.getValue());
            reg.delete(Tag.TOKEN_USERNAME.getValue());
            reg.delete(Tag.TOKEN_EMAIL.getValue());
            reg.delete(Tag.TOKEN_IAT.getValue());
            reg.delete(Tag.TOKEN_EXP.getValue());
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "clear error: " + e.getMessage());
        }
    }
}
