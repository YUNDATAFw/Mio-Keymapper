package io.takamiya.mio.injector;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.List;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.shizuku.IShellService;
import io.takamiya.mio.shizuku.ShizukuHelper;
import io.takamiya.mio.tile.ImePickerActivity;

public final class ImeAutoSwitchHelper {

    private static final String TAG = "ImeAutoSwitchHelper";

    private static final String SETTINGS_IME_HISTORY = "input_methods_subtype_history";

    private ImeAutoSwitchHelper() {}

    public static boolean isMioIme(Context context, String imeId) {
        return imeId != null && imeId.equals(ImeKeyInjector.getMioImeId(context));
    }

    public static String findLastUsedNonMioIme(Context context) {
        List<String> enabled = new ArrayList<>();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            List<InputMethodInfo> list = imm.getEnabledInputMethodList();
            if (list != null) {
                for (InputMethodInfo info : list) {
                    enabled.add(info.getId());
                }
            }
        }
        String history = null;
        try {
            history = Settings.Secure.getString(context.getContentResolver(), SETTINGS_IME_HISTORY);
        } catch (Exception e) {
            AppLogger.w(TAG, "read ime history failed: " + e.getMessage());
        }
        if (history == null || history.isEmpty()) {
            AppLogger.d(TAG, "findLastUsedNonMioIme: empty history");
            return null;
        }
        for (String entry : history.split(":")) {
            if (entry == null || entry.isEmpty()) continue;
            int semi = entry.indexOf(';');
            String id = semi >= 0 ? entry.substring(0, semi) : entry;
            if (id.isEmpty()) continue;
            if (isMioIme(context, id)) continue;
            if (enabled.contains(id)) {
                AppLogger.d(TAG, "findLastUsedNonMioIme: found " + id);
                return id;
            }
        }
        AppLogger.d(TAG, "findLastUsedNonMioIme: no non-Mio enabled IME in history");
        return null;
    }

    public static void switchToMioIme(Context context) {
        if (ImeKeyInjector.isImeCurrent(context)) {
            AppLogger.d(TAG, "switchToMioIme: already current");
            return;
        }
        switchToAsync(context, ImeKeyInjector.getMioImeId(context), true);
    }

    public static void restorePreviousIme(Context context) {
        AppLogger.d(TAG, "restorePreviousIme: showing picker");
        showPicker(context);
    }

    public static boolean restoreIme(String imeId) {
        AppLogger.d(TAG, "restoreIme: imeId=" + imeId);
        if (imeId == null || imeId.isEmpty()) {
            AppLogger.w(TAG, "restoreIme: empty imeId, skipped");
            return false;
        }
        return trySwitchViaShizuku(imeId);
    }

    public static String getCurrentImeId() {
        return getCurrentImeId(null);
    }

    public static String getCurrentImeId(Context context) {
        try {
            ShizukuHelper helper = ShizukuHelper.getInstance();
            if (helper.isRunning() && helper.isGranted() && helper.isServiceBound()) {
                IShellService service = helper.getShellService();
                if (service != null) {
                    String out = service.execCommand("settings get secure default_input_method");
                    String id = out != null ? out.trim() : null;
                    if (id != null && !id.isEmpty() && !"null".equals(id)) {
                        AppLogger.d(TAG, "getCurrentImeId (shizuku): " + id);
                        return id;
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "getCurrentImeId shizuku failed: " + e.getMessage());
        }
        if (context != null) {
            try {
                String id = Settings.Secure.getString(
                        context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
                AppLogger.d(TAG, "getCurrentImeId (settings): " + id);
                return id;
            } catch (Exception e) {
                AppLogger.w(TAG, "getCurrentImeId settings failed: " + e.getMessage());
            }
        }
        AppLogger.w(TAG, "getCurrentImeId: all paths failed");
        return null;
    }

    public static boolean waitForMioImeCurrent(Context context, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (ImeKeyInjector.isImeCurrent(context)) {
                return true;
            }
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        AppLogger.w(TAG, "waitForMioImeCurrent: timed out after " + timeoutMs + "ms");
        return false;
    }

    private static void switchToAsync(Context context, String imeId, boolean ensureEnabled) {
        new Thread(() -> switchToSync(context, imeId, ensureEnabled), "ime-switch").start();
    }

    private static boolean switchToSync(Context context, String imeId, boolean ensureEnabled) {
        if (ensureEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            MioAccessibilityService service = MioAccessibilityService.getInstance();
            if (service != null) {
                service.enableOwnIme();
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MioAccessibilityService service = MioAccessibilityService.getInstance();
            if (service != null && service.switchIme(imeId)) {
                AppLogger.d(TAG, "switchToSync: switched via accessibility, id=" + imeId);
                return true;
            }
        }
        if (trySwitchViaShizuku(imeId)) {
            AppLogger.d(TAG, "switchToSync: switched via Shizuku, id=" + imeId);
            return true;
        }
        AppLogger.w(TAG, "switchToSync: all automatic paths failed, showing picker, id=" + imeId);
        showPicker(context);
        return false;
    }

    private static boolean trySwitchViaShizuku(String imeId) {
        try {
            ShizukuHelper helper = ShizukuHelper.getInstance();
            if (!helper.isRunning() || !helper.isGranted() || !helper.isServiceBound()) {
                AppLogger.w(TAG, "trySwitchViaShizuku: not available");
                return false;
            }
            IShellService service = helper.getShellService();
            if (service == null) {
                return false;
            }
            String out = service.execCommand("settings put secure default_input_method " + imeId);
            AppLogger.d(TAG, "trySwitchViaShizuku: out=" + out);
            return true;
        } catch (Exception e) {
            AppLogger.w(TAG, "trySwitchViaShizuku failed: " + e.getMessage());
            return false;
        }
    }

    public static void showPicker(Context context) {
        try {
            Intent intent = new Intent(context, ImePickerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            AppLogger.d(TAG, "showPicker: launched ImePickerActivity");
        } catch (Exception e) {
            AppLogger.w(TAG, "showPicker via activity failed: " + e.getMessage());
            try {
                InputMethodManager imm =
                        (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                }
            } catch (Exception e2) {
                AppLogger.w(TAG, "showPicker fallback failed: " + e2.getMessage());
            }
        }
    }
}
