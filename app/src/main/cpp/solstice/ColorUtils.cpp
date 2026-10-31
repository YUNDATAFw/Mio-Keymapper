#include "ColorUtils.hpp"
#include "MathUtils.hpp"
#include <chrono>
#include <regex>

#define NOW std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now().time_since_epoch()).count()

ImColor ColorUtils::Rainbow(float seconds, float saturation, float brightness, int index) {
    float hue = ((NOW + index) % (int)(seconds * 1000)) / (float)(seconds * 1000);
    float r, g, b;
    ImGui::ColorConvertHSVtoRGB(hue, saturation, brightness, r, g, b);
    return ImColor(r, g, b, 1.0f);
}

ImColor ColorUtils::LerpColors(float seconds, float index, std::vector<ImColor> colors, uint64_t ms) {
    if (colors.empty()) return ImColor(255, 255, 255, 255);
    float time = 10000.0f / seconds;
    auto angle = static_cast<float>(((ms == 0 ? NOW : ms) + static_cast<int>(index)) % static_cast<int>(time));
    float segmentTime = time / colors.size();
    int segmentIndex = static_cast<int>(angle / segmentTime);
    float segmentIndexFloat = angle / segmentTime - segmentIndex;
    ImColor startColor = colors[segmentIndex];
    ImColor endColor = colors[(segmentIndex + 1) % colors.size()];
    return MathUtils::lerpImColor(startColor, endColor, segmentIndexFloat);
}

// ColorUtils.cpp
ImColor ColorUtils::getThemedColor(float index, uint64_t ms) {
    static std::vector<ImColor> theme = {
        ImColor(0xE9, 0xA8, 0xBC, 255),   // 浅粉 #E9A8BC
        ImColor(0x6E, 0xC8, 0xF1, 255),   // 浅蓝 #6EC8F1
        ImColor(255, 255, 255, 128),  // 白
    };
    return LerpColors(3.0f, index, theme, ms);
}



std::string ColorUtils::removeColorCodes(const std::string& text) {
    static std::regex colorCodeRegex("§[0-9a-z]");
    return std::regex_replace(text, colorCodeRegex, std::string(""));
}
