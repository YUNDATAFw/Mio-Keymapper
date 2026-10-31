#pragma once
#include <imgui.h>
#include <vector>
#include <string>

class ColorUtils {
public:
    static ImColor Rainbow(float seconds, float saturation, float brightness, int index);
    static ImColor LerpColors(float seconds, float index, std::vector<ImColor> colors, uint64_t ms = 0);
    static ImColor getThemedColor(float index, uint64_t ms = 0);
    static std::string removeColorCodes(const std::string& text);
};
