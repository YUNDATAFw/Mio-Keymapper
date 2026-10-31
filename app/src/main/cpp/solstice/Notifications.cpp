// Notifications.cpp
#include "Notifications.hpp"
#include "MathUtils.hpp"
#include "ColorUtils.hpp"
#include <algorithm>

void Notifications::add(const std::string& msg, Notification::Type type, float duration) {
    mNotifications.emplace_back(msg, type, duration);
}

bool CalcSize(ImVec2& boxSize, float& yOff, float& x, ImVec2 screenSize, float currentDuration, float rightMargin, bool fromTop, float step) {
    float beginX = screenSize.x - boxSize.x - rightMargin;
    float endX = screenSize.x + boxSize.x;
    x = MathUtils::lerp(endX, beginX, currentDuration);
    if (fromTop) {
        yOff = MathUtils::lerp(yOff, yOff + step, currentDuration);
        return x > screenSize.x + boxSize.x && yOff < -boxSize.y;
    }
    yOff = MathUtils::lerp(yOff, yOff - step, currentDuration);
    return x > screenSize.x + boxSize.x && yOff > screenSize.y + boxSize.y;
}

void Notifications::render(ImDrawList* drawList, ImVec2 displaySize, float deltaTime) {
    std::erase_if(mNotifications, [this](const Notification& n) {
        return n.mIsTimeUp && n.mTimeShown > n.mDuration + 3.0f;
    });

    bool fromTop = mPosition == Position::Top;
    float y = 0.0f;
    float x = 0;
    int i = 0;
    bool first = true;
    float prevBoxH = 0.0f;

    for (auto& notification : mNotifications) {
        if (i >= mMaxNotifications && mLimitNotifications) break;

        notification.mTimeShown += deltaTime;
        notification.mIsTimeUp = notification.mTimeShown >= notification.mDuration;
        notification.mCurrentDuration = MathUtils::lerp(
            notification.mCurrentDuration,
            notification.mIsTimeUp ? 0.0f : 1.0f,
            deltaTime * 5.0f
        );

        float percentDone = std::clamp(notification.mTimeShown / notification.mDuration, 0.0f, 1.0f);
        float fontSize = mFontSize;

        float textW = ImGui::GetFont()->CalcTextSizeA(fontSize, FLT_MAX, 0.0f, notification.mMessage.c_str()).x;
        float textH = ImGui::GetFont()->CalcTextSizeA(fontSize, FLT_MAX, 0.0f, notification.mMessage.c_str()).y;

        float boxW = fmax(200.0f, 50 + textW);
        float boxH = textH + 30.0f;
        auto boxSize = ImVec2(boxW, boxH);

        float step = boxH;
        if (first) {
            y = fromTop ? (10.0f - boxH) : (displaySize.y - 10.0f);
            first = false;
        } else if (fromTop) {
            step = prevBoxH;
        }

        if (CalcSize(boxSize, y, x, displaySize, notification.mCurrentDuration, 10.0f, fromTop, step)) continue;
        prevBoxH = boxH;

        ImColor themeColor = mHasNotifyColor ? mNotifyColor : ColorUtils::getThemedColor(y * 2);

        if (notification.mType == Notification::Type::Warning) {
            themeColor = ImColor(1.f, 0.8f, 0.f, 1.f);
        } else if (notification.mType == Notification::Type::Error) {
            themeColor = ImColor(1.f, 0.f, 0.f, 1.f);
        }

        themeColor.Value.w = 0.7f;

        float maxX = x + boxW;
        float progressW = boxW * percentDone + 6.f;
        ImVec2 progMax = ImVec2(x + progressW, y + (boxH - 10.f));
        progMax.x = std::clamp(progMax.x, x, maxX);
        ImVec2 bgMin = ImVec2(x + boxW * percentDone, y);
        ImVec2 bgMax = ImVec2(x + boxW, y + (boxH - 10.f));

        for (int d = 0; d < mShadowDensity; d++) {
            float t = (float)d / (mShadowDensity > 1 ? mShadowDensity - 1 : 1);
            float r = mShadowBlur * (0.25f + 0.75f * t);
            float a = 1.0f;
            ImColor c(themeColor.Value.x, themeColor.Value.y, themeColor.Value.z, a);
            drawList->AddShadowRect(ImVec2(x, y), progMax, c, r, ImVec2(), 0, 5.0f);
        }

        drawList->PushClipRect(ImVec2(x, y), ImVec2(x + (boxW * percentDone), y + (boxH - 10.f)));

        if (!mColorGradient) {
            drawList->AddRectFilled(ImVec2(x, y), progMax, themeColor, 5.0f);
        } else {
            ImColor rgb2 = ColorUtils::getThemedColor(y * 2 + ((x - progMax.x) * 1.2f));
            drawList->AddRectFilledMultiColor(
                ImVec2(x, y), progMax,
                themeColor, rgb2, rgb2, themeColor
            );
        }
        drawList->PopClipRect();

        drawList->PushClipRect(bgMin, bgMax);
        drawList->AddRectFilled(
            ImVec2(x + boxW * percentDone - 6, y), bgMax,
            ImColor(0.f, 0.f, 0.f, 0.7f), 5.0f
        );
        drawList->PopClipRect();

        drawList->AddText(
            ImGui::GetFont(), fontSize,
            ImVec2(x + 10, y + 10),
            ImColor(255, 255, 255, 255),
            notification.mMessage.c_str()
        );

        if (!notification.mIsTimeUp) i++;
    }
}
