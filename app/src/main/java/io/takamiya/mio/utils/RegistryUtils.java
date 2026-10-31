package io.takamiya.mio.utils;

import android.content.Context;
import android.net.Uri;

import java.io.File;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.*;

public class RegistryUtils {
    private static final String TAG = "RegistryUtils";
    private static Registry registry;

    public static void init(Context context) {
        Uri uri = Uri.fromFile(new File(context.getFilesDir(), "app.regbin"));
        try {
            RegistrySettings settings = RegistrySettings.defaults();
            settings.setUndoEnabled(false);
            registry = Registry.open(context, uri, settings);
        } catch (Exception e) {
            AppLogger.e(TAG, "registry init failed: " + e.getMessage());
        }
    }

    public static Registry get() {
        return registry;
    }
}
