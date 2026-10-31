package io.takamiya.mio.bean;

import org.json.JSONObject;

public class ChatMessage {

    private final int id;
    private final String direction;
    private final String content;
    private final String createdAt;
    private final boolean failed;

    public ChatMessage(int id, String direction, String content, String createdAt, boolean failed) {
        this.id = id;
        this.direction = direction;
        this.content = content;
        this.createdAt = createdAt;
        this.failed = failed;
    }

    /**
     * 本地构造（发送时临时使用）。
     */
    public ChatMessage(String content, String createdAt, boolean mine, boolean failed) {
        this(0, mine ? "sent" : "received", content, createdAt, failed);
    }

    public static ChatMessage fromJson(JSONObject obj) {
        if (obj == null) return null;
        return new ChatMessage(
                obj.optInt("id", 0),
                obj.optString("direction", "received"),
                obj.optString("content", ""),
                obj.optString("created_at", ""),
                false
        );
    }

    public int getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public boolean isMine() {
        return "sent".equals(direction);
    }

    public boolean isFailed() {
        return failed;
    }
}