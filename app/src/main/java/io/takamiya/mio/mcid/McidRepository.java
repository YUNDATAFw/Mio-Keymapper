package io.takamiya.mio.mcid;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.bean.McidItem;

/**
 * MCID 数据仓库（离线版，异步加载）。
 * 从 {@code res/raw/} 读取预置的 JSON 文件，不再发起网络请求。
 * 加载在后台线程执行，回调分发到主线程。
 */
public class McidRepository {

    private static final String TAG = "McidRepository";

    /** 文件名 → 列表键 → 来源类型 */
    private static final String[][] FILES = {
            {"minecraft_items", "items", "items"},
            {"minecraft_blocks", "items", "blocks"},
            {"minecraft_enchantments", "enchantments", "enchantments"},
            {"minecraft_effects", "effects", "effects"},
            {"minecraft_particles", "particles", "particles"},
            {"minecraft_biomes", "biomes", "biomes"},
            {"minecraft_entities", "entities", "entities"},
            {"minecraft_fog", "fog", "fog"},
    };

    /** 后台线程池（单线程，避免并发读取原资源冲突） */
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "McidRepository-IO");
        t.setDaemon(true);
        return t;
    });

    /** 主线程 Handler（回调分发） */
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onStart();
        void onProgress(int loaded, int total);  // 可选进度
        void onResult(List<McidItem> items);
        void onFinish();
        void onError(String error);
    }

    /**
     * 异步加载所有 MCID 数据。
     * <p>
     * 立即在主线程回调 {@link Callback#onStart()}，随后在后台线程加载，
     * 完成后在主线程回调 {@link Callback#onResult}/{@link Callback#onFinish}。
     *
     * @param context 上下文（用于访问资源）
     * @param callback 回调（所有方法均在主线程调用）
     */
    public static void loadAll(Context context, Callback callback) {
        if (context == null || callback == null) return;

        // 主线程回调 onStart
        MAIN_HANDLER.post(callback::onStart);

        IO_EXECUTOR.execute(() -> {
            List<McidItem> allItems = new ArrayList<>();
            int totalFiles = FILES.length;
            int loadedFiles = 0;

            for (String[] file : FILES) {
                String fileName = file[0];
                String listKey = file[1];
                String sourceType = file[2];

                List<McidItem> partial = loadFromRaw(context, fileName, listKey, sourceType);
                if (partial != null && !partial.isEmpty()) {
                    allItems.addAll(partial);
                }

                loadedFiles++;
                // 进度回调（主线程）
                final int progressLoaded = loadedFiles;
                final int progressTotal = totalFiles;
                MAIN_HANDLER.post(() -> {
                    if (callback != null) {
                        try { callback.onProgress(progressLoaded, progressTotal); } catch (Exception ignored) {}
                    }
                });
            }

            // 完成回调
            MAIN_HANDLER.post(() -> {
                if (callback != null) {
                    try { callback.onResult(allItems); } catch (Exception ignored) {}
                    try { callback.onFinish(); } catch (Exception ignored) {}
                }
            });
        });
    }

    /**
     * 从 res/raw 读取单个 JSON 文件并解析（同步，运行在 IO 线程）。
     */
    private static List<McidItem> loadFromRaw(Context context, String fileName, String listKey, String sourceType) {
        try {
            Resources res = context.getResources();
            int resId = res.getIdentifier(fileName, "raw", context.getPackageName());
            if (resId == 0) {
                AppLogger.w(TAG, "raw resource not found: " + fileName);
                return null;
            }

            try (InputStream is = res.openRawResource(resId);
                 Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
                String jsonStr = scanner.hasNext() ? scanner.next() : "";
                if (jsonStr.isEmpty()) {
                    AppLogger.w(TAG, "empty json: " + fileName);
                    return null;
                }

                JSONObject json = new JSONObject(jsonStr);
                JSONArray arr = json.optJSONArray(listKey);
                if (arr == null) {
                    AppLogger.w(TAG, "key '" + listKey + "' not found in " + fileName);
                    return null;
                }

                List<McidItem> result = new ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) {
                    try {
                        result.add(McidItem.fromJson(arr.getJSONObject(i), sourceType));
                    } catch (Exception e) {
                        AppLogger.w(TAG, "parse item " + i + " in " + fileName + " failed: " + e.getMessage());
                    }
                }
                AppLogger.i(TAG, "loaded " + result.size() + " entries from " + fileName);
                return result;
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "load " + fileName + " failed: " + e.getMessage());
            return null;
        }
    }
}