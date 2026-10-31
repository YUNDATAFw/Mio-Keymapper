#pragma once

#include <string>
#include <vector>
#include <imgui.h>

struct Notification {
    enum class Type { Info, Warning, Error };

    std::string mMessage;
    Type mType = Type::Info;
    float mDuration = 3.0f;
    float mTimeShown = 0.0f;
    float mCurrentDuration = 0.0f;
    bool mIsTimeUp = false;

    Notification() = default;
    Notification(const std::string& msg, Type type, float duration)
        : mMessage(msg), mType(type), mDuration(duration) {}
};

class Notifications {
public:
    enum class Style { Solaris };
    enum class Position { Bottom, Top };

    Style mStyle = Style::Solaris;
    Position mPosition = Position::Bottom;
    bool mShowOnToggle = true;
    bool mShowOnJoin = true;
    bool mColorGradient = false;
    bool mLimitNotifications = false;
    int mMaxNotifications = 6;
    float mShadowBlur = 50.0f;
    int mShadowDensity = 2;

    // 自定义通知颜色，未设置时回退主题色 (getThemedColor)
    ImColor mNotifyColor = ImColor(0.30f, 0.35f, 0.63f, 1.0f);
    bool mHasNotifyColor = false;

    // 字体大小，默认 30，与 Arraylist 默认 35 成 30/35 比例
    float mFontSize = 30.0f;

    std::vector<Notification> mNotifications;

    Notifications() = default;

    void add(const std::string& msg, Notification::Type type = Notification::Type::Info, float duration = 3.0f);
    void render(ImDrawList* drawList, ImVec2 displaySize, float deltaTime);

    std::string getSettingDisplay() const {
        return mStyle == Style::Solaris ? "Solaris" : "Unknown";
    }
};
