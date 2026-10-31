package io.takamiya.mio.bean;

import org.json.JSONObject;

public class ForumSection {
    private int id;
    private String name;
    private String intro;
    private int type;
    private String owner;
    private String ownerAvatar;
    private int ownerUserId;
    private int ownerMemberLevel;
    private String ownerMemberLevelName;
    private int ownerUserLevel;
    private String ownerUserLevelTitle;
    private int ownerTagKey;
    private String ownerTagKeyName;
    private String createdAt;
    private String updatedAt;

    public static ForumSection fromJson(JSONObject obj) {
        ForumSection s = new ForumSection();
        s.id = obj.optInt("id", 0);
        s.name = obj.optString("name", "");
        s.intro = obj.optString("intro", "");
        s.type = obj.optInt("type", 0);
        s.createdAt = obj.optString("created_at", "");
        s.updatedAt = obj.optString("updated_at", "");
        JSONObject ownerObj = obj.optJSONObject("owner");
        if (ownerObj != null) {
            s.owner = ownerObj.optString("display_name", "");
            if (s.owner.isEmpty()) {
                s.owner = ownerObj.optString("nickname", "");
            }
            s.ownerAvatar = ownerObj.optString("avatar", "");
            s.ownerUserId = ownerObj.optInt("id", 0);
            s.ownerMemberLevel = ownerObj.optInt("member_level", 0);
            s.ownerMemberLevelName = ownerObj.optString("member_level_name", "");
            s.ownerUserLevel = ownerObj.optInt("user_level", 0);
            s.ownerUserLevelTitle = ownerObj.optString("user_level_title", "");
            s.ownerTagKey = ownerObj.optInt("tag_key", 0);
            s.ownerTagKeyName = ownerObj.optString("tag_key_name", "");
        } else {
            s.owner = obj.optString("owner", "");
            s.ownerAvatar = obj.optString("owner_avatar", "");
            s.ownerUserId = obj.optInt("owner_id", 0);
            s.ownerMemberLevel = obj.optInt("owner_member_level", 0);
        }
        return s;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getIntro() { return intro; }
    public int getType() { return type; }
    public String getOwner() { return owner; }
    public String getOwnerAvatar() { return ownerAvatar; }
    public int getOwnerUserId() { return ownerUserId; }
    public int getOwnerMemberLevel() { return ownerMemberLevel; }
    public String getOwnerMemberLevelName() { return ownerMemberLevelName; }
    public int getOwnerUserLevel() { return ownerUserLevel; }
    public String getOwnerUserLevelTitle() { return ownerUserLevelTitle; }
    public int getOwnerTagKey() { return ownerTagKey; }
    public String getOwnerTagKeyName() { return ownerTagKeyName; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }

    public String getTypeLabel() {
        switch (type) {
            case 1: return "资源";
            case 2: return "交流";
            default: return "默认";
        }
    }
}
