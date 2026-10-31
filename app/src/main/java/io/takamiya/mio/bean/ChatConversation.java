package io.takamiya.mio.bean;

import org.json.JSONObject;

public class ChatConversation {

    private final int chatUserId;
    private final String username;
    private final String nickname;
    private final String displayName;
    private final String avatar;
    private final String lastMessage;
    private final String lastTime;
    private final int unreadCount;
    private final int memberLevel;

    public ChatConversation(int chatUserId, String username, String nickname, String displayName,
                            String avatar, String lastMessage, String lastTime, int unreadCount,
                            int memberLevel) {
        this.chatUserId = chatUserId;
        this.username = username;
        this.nickname = nickname;
        this.displayName = displayName;
        this.avatar = avatar;
        this.lastMessage = lastMessage;
        this.lastTime = lastTime;
        this.unreadCount = unreadCount;
        this.memberLevel = memberLevel;
    }

    public static ChatConversation fromJson(JSONObject obj) {
        if (obj == null) return null;
        return new ChatConversation(
                obj.optInt("chat_user_id", 0),
                obj.optString("username", ""),
                obj.optString("nickname", ""),
                obj.optString("display_name", ""),
                obj.optString("avatar", ""),
                obj.optString("last_message", ""),
                obj.optString("last_time", ""),
                obj.optInt("unread_count", 0),
                obj.optInt("member_level", 0)
        );
    }

    public int getMemberLevel() {
        return memberLevel;
    }

    public int getChatUserId() {
        return chatUserId;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        String name = displayName != null && !displayName.isEmpty() ? displayName : nickname;
        return name != null ? name : "";
    }

    public String getAvatar() {
        return avatar;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public String getLastTime() {
        return lastTime;
    }

    public int getUnreadCount() {
        return unreadCount;
    }
}