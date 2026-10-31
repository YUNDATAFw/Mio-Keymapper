package io.takamiya.mio.bean;

import org.json.JSONObject;

public class McidItem {

    private final String name;
    private final String id;
    private final int numericId;
    private final String category;
    private final String nameCn;

    public McidItem(String name, String id, int numericId, String category, String nameCn) {
        this.name = name;
        this.id = id;
        this.numericId = numericId;
        this.category = category;
        this.nameCn = nameCn;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public int getNumericId() {
        return numericId;
    }

    public String getCategory() {
        return category;
    }

    public String getNameCn() {
        return nameCn;
    }

    public static McidItem fromJson(JSONObject obj, String sourceType) {
        String name = obj.optString("name", "");
        String id = obj.optString("id", "");
        int numericId = obj.optInt("numeric_id", 0);
        String category = obj.optString("category", "");
        String nameCn = obj.optString("name_cn", "");

        if (id.isEmpty()) {
            id = obj.optString("resource_location", "");
        }
        if (id.isEmpty()) {
            id = obj.optString("internal_name", "");
        }

        switch (sourceType) {
            case "items":
                category = "物品";
                break;
            case "blocks":
                category = "方块";
                break;
            case "enchantments":
                category = "附魔";
                break;
            case "effects":
                category = "药水";
                break;
            case "particles":
                category = "粒子";
                break;
            case "biomes":
                category = "群系";
                break;
            case "entities":
                category = "生物";
                break;
            case "fog":
                category = "迷雾";
                break;
            default:
                if (category.isEmpty()) category = "其他";
                break;
        }

        return new McidItem(name, id, numericId, category, nameCn);
    }

    public String[] toArray() {
        String display = nameCn.isEmpty() ? name : nameCn;
        return new String[]{display, id, category};
    }
}
