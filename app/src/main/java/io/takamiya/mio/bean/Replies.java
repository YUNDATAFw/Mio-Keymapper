package io.takamiya.mio.bean;

public class Replies {
    private int id;
    private int userId;
    private String authorName;
    private String authorNickname;
    private String authorAvatar;
    private int authorMemberLevel;
    private int authorUserLevel;
    private int likeCount;
    private String authorUserLevelTitle;
    private String content;
    private String createdAt;
    private String replyToUserNickname;
    private int parentId;
    private boolean liked;

    public Replies(int id, String authorName,String authorNickname, String authorAvatar, int authorMemberLevel, int authorUserLevel, int likeCount, String authorUserLevelTitle, String content, String createdAt, String replyToUserNickname) {
        this.id = id;
        this.authorName = authorName;
        this.authorNickname = authorNickname;
        this.authorAvatar = authorAvatar;
        this.authorMemberLevel = authorMemberLevel;
        this.authorUserLevel = authorUserLevel;
        this.likeCount = likeCount;
        this.authorUserLevelTitle = authorUserLevelTitle;
        this.content = content;
        this.createdAt = createdAt;
        this.replyToUserNickname = replyToUserNickname;
    }

    public int getId() {
        return id;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getAuthorNickname() {
        return authorNickname;
    }

    public String getAuthorAvatar() {
        return authorAvatar;
    }

    public int getAuthorMemberLevel() {
        return authorMemberLevel;
    }

    public int getAuthorUserLevel() {
        return authorUserLevel;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public String getAuthorUserLevelTitle() {
        return authorUserLevelTitle;
    }

    public String getContent() {
        return content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getReplyToUserNickname() {
        return replyToUserNickname;
    }

    public int getParentId() {
        return parentId;
    }

    public void setParentId(int parentId) {
        this.parentId = parentId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getUserId() {
        return userId;
    }

    public void setLiked(boolean liked) {
        this.liked = liked;
    }

    public boolean isLiked() {
        return liked;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }
}
