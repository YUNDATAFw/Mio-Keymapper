package io.takamiya.mio.bean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ForumResource {

    private int id;
    private int userId;

    private String authorName;
    private String authorNickname;
    private String authorAvatar;
    private int authorMemberLevel;
    private String authorMemberLevelName;
    private String authorMemberExpireTime;
    private int authorUserLevel;
    private String authorUserLevelTitle;
    private int authorTagKey;
    private String authorTagKeyName;

    private int sectionId;
    private String sectionName;

    private String title;
    private String intro;
    private String introSummary;

    private int resType;
    private String resTypeName;

    private int isPaid;
    private double coinPrice;
    private double discountPrice;
    private int expireDays;
    private String expireDaysText;
    private boolean isPurchased;
    private String purchaseExpireTime;

    private int likes;
    private int favorites;
    private int coins;
    private int views;
    private double heat;
    private int isTop;
    private int isRecommend;

    private List<ForumTag> tags;

    private int auditStatus;

    private boolean isLiked;
    private boolean isFavorited;
    private boolean isCoined;
    private int userCoinCount;

    private String createdAt;
    private String updatedAt;

    public static class ForumTag {
        private final int id;
        private final String name;
        private final String color;

        public ForumTag(int id, String name, String color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getColor() { return color; }
    }

    public static ForumResource fromJson(JSONObject obj) {
        ForumResource r = new ForumResource();
        r.id = obj.optInt("id", 0);
        r.userId = obj.optInt("user_id", 0);

        r.authorName = obj.optString("author_name", "");
        r.authorNickname = obj.optString("author_nickname", "");
        r.authorAvatar = obj.optString("author_avatar", "");
        r.authorMemberLevel = obj.optInt("author_member_level", 0);
        r.authorMemberLevelName = obj.optString("author_member_level_name", "");
        r.authorMemberExpireTime = obj.optString("author_member_expire_time", "");
        r.authorUserLevel = obj.optInt("author_user_level", 0);
        r.authorUserLevelTitle = obj.optString("author_user_level_title", "");
        r.authorTagKey = obj.optInt("author_tag_key", 0);
        r.authorTagKeyName = obj.optString("author_tag_key_name", "");

        r.sectionId = obj.optInt("section_id", 0);
        r.sectionName = obj.optString("section_name", "");

        r.title = obj.optString("title", "");
        r.intro = obj.optString("intro", "");
        r.introSummary = obj.optString("intro_summary", "");
        if (r.introSummary.isEmpty()) {
            r.introSummary = r.intro;
        }

        r.resType = obj.optInt("res_type", 0);
        r.resTypeName = obj.optString("res_type_name", "");

        r.isPaid = obj.optInt("is_paid", 0);
        r.coinPrice = obj.optDouble("coin_price", 0.0);
        r.discountPrice = obj.optDouble("discount_price", 0.0);
        r.expireDays = obj.optInt("expire_days", 0);
        r.expireDaysText = obj.optString("expire_days_text", "");
        r.isPurchased = obj.optBoolean("is_purchased", false);
        r.purchaseExpireTime = obj.optString("purchase_expire_time", "");

        r.likes = obj.optInt("likes", 0);
        r.favorites = obj.optInt("favorites", 0);
        r.coins = obj.optInt("coins", 0);
        r.views = obj.optInt("views", 0);
        r.heat = obj.optDouble("heat", 0.0);
        r.isTop = obj.optInt("is_top", 0);
        r.isRecommend = obj.optInt("is_recommend", 0);

        JSONArray tagArr = obj.optJSONArray("tags");
        r.tags = new ArrayList<>();
        if (tagArr != null) {
            for (int i = 0; i < tagArr.length(); i++) {
                JSONObject tagObj = tagArr.optJSONObject(i);
                if (tagObj != null) {
                    r.tags.add(new ForumTag(
                            tagObj.optInt("id", 0),
                            tagObj.optString("name", ""),
                            tagObj.optString("color", "")));
                }
            }
        }

        r.isLiked = obj.optBoolean("is_liked", false);
        r.isFavorited = obj.optBoolean("is_favorited", false);
        r.isCoined = obj.optBoolean("is_coined", false);
        r.auditStatus = obj.optInt("audit_status", 0);
        r.userCoinCount = obj.optInt("user_coin_count", 0);

        r.createdAt = obj.optString("created_at", "");
        r.updatedAt = obj.optString("updated_at", "");
        return r;
    }

    public static List<ForumResource> fromJsonArray(JSONArray arr) {
        List<ForumResource> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            try {
                list.add(fromJson(arr.getJSONObject(i)));
            } catch (Exception e) {
                // skip invalid
            }
        }
        return list;
    }

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public String getAuthorName() { return authorName; }
    public String getAuthorNickname() { return authorNickname; }
    public String getAuthorAvatar() { return authorAvatar; }
    public int getAuthorMemberLevel() { return authorMemberLevel; }
    public String getAuthorMemberLevelName() { return authorMemberLevelName; }
    public String getAuthorMemberExpireTime() { return authorMemberExpireTime; }
    public int getAuthorUserLevel() { return authorUserLevel; }
    public String getAuthorUserLevelTitle() { return authorUserLevelTitle; }
    public int getAuthorTagKey() { return authorTagKey; }
    public String getAuthorTagKeyName() { return authorTagKeyName; }
    public int getSectionId() { return sectionId; }
    public String getSectionName() { return sectionName; }
    public String getTitle() { return title; }
    public String getIntro() { return intro; }
    public String getIntroSummary() { return introSummary; }
    public int getResType() { return resType; }
    public String getResTypeName() { return resTypeName; }
    public int isPaid() { return isPaid; }
    public double getCoinPrice() { return coinPrice; }
    public double getDiscountPrice() { return discountPrice; }
    public int getExpireDays() { return expireDays; }
    public String getExpireDaysText() { return expireDaysText; }
    public boolean isPurchased() { return isPurchased; }
    public String getPurchaseExpireTime() { return purchaseExpireTime; }
    public int getLikes() { return likes; }
    public int getFavorites() { return favorites; }
    public int getCoins() { return coins; }
    public int getViews() { return views; }
    public double getHeat() { return heat; }
    public int isTop() { return isTop; }
    public int isRecommend() { return isRecommend; }
    public List<ForumTag> getTags() { return tags; }
    public int getAuditStatus() { return auditStatus; }
    public boolean isLiked() { return isLiked; }
    public boolean isFavorited() { return isFavorited; }
    public boolean isCoined() { return isCoined; }
    public int getUserCoinCount() { return userCoinCount; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }

    public String getAuthorDisplayName() {
        return authorNickname != null && !authorNickname.isEmpty() ? authorNickname : authorName;
    }

    public String getTagsText() {
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append("、");
            sb.append(tags.get(i).getName());
        }
        return sb.toString();
    }

    public String getPriceText() {
        if (isPaid() == 0) return "免费";
        if (discountPrice > 0) {
            return String.format("¥%.2f", discountPrice);
        }
        return String.format("%.2f 币", coinPrice);
    }
}