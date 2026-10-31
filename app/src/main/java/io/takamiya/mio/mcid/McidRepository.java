package io.takamiya.mio.mcid;
import io.takamiya.mio.utils.SecureStrings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.bean.McidItem;
import io.takamiya.mio.utils.HttpManage;

public class McidRepository {

    private static final String TAG = "McidRepository";

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

    public interface Callback {
        void onStart();
        void onResult(List<McidItem> items);
        void onFinish();
        void onError(String error);
    }

    public static void loadAll(Callback callback) {
        callback.onStart();
        List<McidItem> allItems = new ArrayList<>();
        int[] remaining = {FILES.length};

        for (String[] file : FILES) {
            String fileName = file[0];
            String listKey = file[1];
            String sourceType = file[2];

            String url = UrlBuilder.getInstance()
                    .longPath(SecureStrings.path(61) + fileName + ".json")
                    .build();

            HttpManage.getInstance().get(url)
                    .onSuccess((code, body) -> {
                        List<McidItem> partial = new ArrayList<>();
                        try {
                            JSONObject json = new JSONObject(body);
                            JSONArray arr = json.optJSONArray(listKey);
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    partial.add(McidItem.fromJson(arr.getJSONObject(i), sourceType));
                                }
                            }
                        } catch (Exception e) {
                            AppLogger.e(TAG, "parse " + fileName + " failed: " + e.getMessage());
                        }

                        synchronized (allItems) {
                            allItems.addAll(partial);
                        }

                        remaining[0]--;
                        if (remaining[0] == 0) {
                            callback.onResult(allItems);
                            callback.onFinish();
                        }
                    })
                    .onFailure((code, err) -> {
                        AppLogger.e(TAG, "load " + fileName + " failed: " + err);
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            callback.onResult(allItems);
                            callback.onFinish();
                        }
                    })
                    .execute();
        }
    }
}
