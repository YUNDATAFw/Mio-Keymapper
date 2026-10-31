package io.takamiya.mio.bean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ForumPost {
    private int id;
    private int userId;
    private String authorName;
    private String authorNickname;
    private String authorAvatar;
    private int authorMemberLevel;
    private int sectionId;
    private String sectionName;
    private String title;
    private String content;
    private String contentSummary;
    private int views;
    private int likes;
    private int favorites;
    private int coins;
    private double heat;
    private int isTop;
    private int isRecommend;
    private String createdAt;
    private String updatedAt;
    private int auditStatus;
    private List<String> tags; // 简化为name列表

    public static ForumPost fromJson(JSONObject obj) {
        ForumPost p = new ForumPost();
        p.id = obj.optInt("id", 0);
        p.userId = obj.optInt("user_id", 0);
        p.authorName = obj.optString("author_name", "");
        p.authorNickname = obj.optString("author_nickname", "");
        p.authorAvatar = obj.optString("author_avatar", "");
        p.authorMemberLevel = obj.optInt("author_member_level", 0);
        p.sectionId = obj.optInt("section_id", 0);
        p.sectionName = obj.optString("section_name", "");
        p.title = obj.optString("title", "");
        p.content = obj.optString("content", "");
        p.contentSummary = obj.optString("content_summary", "");
        if (p.contentSummary.isEmpty()) {
            p.contentSummary = p.content;
        }
        p.views = obj.optInt("views", 0);
        p.likes = obj.optInt("likes", 0);
        p.favorites = obj.optInt("favorites", 0);
        p.coins = obj.optInt("coins", 0);
        p.heat = obj.optDouble("heat", 0.0);
        p.isTop = obj.optInt("is_top", 0);
        p.isRecommend = obj.optInt("is_recommend", 0);
        p.auditStatus = obj.optInt("audit_status", 0);
        p.createdAt = obj.optString("created_at", "");
        p.updatedAt = obj.optString("updated_at", "");

        // tags 为对象列表 [{id, name, color}]
        JSONArray tagArr = obj.optJSONArray("tags");
        p.tags = new ArrayList<>();
        if (tagArr != null) {
            for (int i = 0; i < tagArr.length(); i++) {
                JSONObject tagObj = tagArr.optJSONObject(i);
                if (tagObj != null) {
                    p.tags.add(tagObj.optString("name", ""));
                }
            }
        }
        return p;
    }

    public static List<ForumPost> fromJsonArray(JSONArray arr) {
        List<ForumPost> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            try {
                list.add(fromJson(arr.getJSONObject(i)));
            } catch (Exception e) {
                // skip invalid
            }
        }
        return list;
    }

    // Getters
    public int getId() { return id; }
    public int getUserId() { return userId; }
    public String getAuthorName() { return authorName; }
    public String getAuthorNickname() { return authorNickname; }
    public String getAuthorAvatar() { return authorAvatar; }
    public int getAuthorMemberLevel() { return authorMemberLevel; }
    public int getSectionId() { return sectionId; }
    public String getSectionName() { return sectionName; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getContentSummary() { return contentSummary; }
    public int getViews() { return views; }
    public int getLikes() { return likes; }
    public int getFavorites() { return favorites; }
    public int getCoins() { return coins; }
    public double getHeat() { return heat; }
    public int isTop() { return isTop; }
    public int isRecommend() { return isRecommend; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public int getAuditStatus() { return auditStatus; }
    public List<String> getTags() { return tags; }

    public String getAuditStatusName() {
        switch (auditStatus) {
            case 0: return "待审核";
            case 1: return "通过";
            case 2: return "已通过";
            case 3: return "驳回";
            default: return "未知";
        }
    }

    public String getTagsText() {
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append("、");
            sb.append(tags.get(i));
        }
        return sb.toString();
    }
}