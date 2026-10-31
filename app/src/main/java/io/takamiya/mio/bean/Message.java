package io.takamiya.mio.bean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Message {
    private int id;
    private int senderId;
    private int receiverId;
    private String senderName;
    private String title;
    private String content;
    private String type;
    private int isRead;
    private String readAt;
    private int isRevoked;
    private int deletedByReceiver;
    private String createdAt;
    private String updatedAt;

    public static Message fromJson(JSONObject obj) {
        Message m = new Message();
        m.id = obj.optInt("id", 0);
        m.senderId = obj.optInt("sender_id", 0);
        m.receiverId = obj.optInt("receiver_id", 0);
        m.senderName = obj.optString("sender_name", "");
        m.title = obj.optString("title", "");
        m.content = obj.optString("content", "");
        m.type = obj.optString("type", "");
        m.isRead = obj.optInt("is_read", 0);
        m.readAt = obj.optString("read_at", "");
        m.isRevoked = obj.optInt("is_revoked", 0);
        m.deletedByReceiver = obj.optInt("deleted_by_receiver", 0);
        m.createdAt = obj.optString("created_at", "");
        m.updatedAt = obj.optString("updated_at", "");
        return m;
    }

    public static List<Message> fromJsonArray(JSONArray arr) {
        List<Message> list = new ArrayList<>();
        if (arr == null) return list;
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
    public int getSenderId() { return senderId; }
    public int getReceiverId() { return receiverId; }
    public String getSenderName() { return senderName; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getType() { return type; }
    public int getIsRead() { return isRead; }
    public String getReadAt() { return readAt; }
    public int getIsRevoked() { return isRevoked; }
    public int getDeletedByReceiver() { return deletedByReceiver; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }

    public void setIsRead(int isRead) { this.isRead = isRead; }
}
