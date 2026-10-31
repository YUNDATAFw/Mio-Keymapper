package io.takamiya.mio.bean;

/**
 * 稿件管理列表统一条目：帖子 / 资源。
 * type = 0 帖子，1 资源。
 */
public class ManuscriptItem {

    public static final int TYPE_POST = 0;
    public static final int TYPE_RESOURCE = 1;

    private int type;
    private int id;
    private String title;
    private String sectionName;
    private int auditStatus;
    private int views;
    private int likes;
    private String createdAt;

    public ManuscriptItem(int type, int id, String title, String sectionName,
                          int auditStatus, int views, int likes, String createdAt) {
        this.type = type;
        this.id = id;
        this.title = title == null ? "" : title;
        this.sectionName = sectionName == null ? "" : sectionName;
        this.auditStatus = auditStatus;
        this.views = views;
        this.likes = likes;
        this.createdAt = createdAt == null ? "" : createdAt;
    }

    public int getType() { return type; }
    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getSectionName() { return sectionName; }
    public int getAuditStatus() { return auditStatus; }
    public int getViews() { return views; }
    public int getLikes() { return likes; }
    public String getCreatedAt() { return createdAt; }
}