#pragma once
#include <algorithm>
#include <cmath>
#include <vector>
#include <random>
#include <imgui.h>

class MathUtils {
public:
    static float animate(float endPoint, float current, float speed);
    static float lerp(float a, float b, float t);
    static ImVec4 lerp(ImVec4& a, ImVec4& b, float t);
    static ImVec2 lerp(ImVec2& a, ImVec2& b, float t);
    static ImColor lerpImColor(ImColor& a, ImColor& b, float t);

    template <typename T>
    static T clamp(T value, T min, T max) {
        return std::max(min, std::min(value, max));
    }
    static float clamp(float value, float min, float max);
    static float wrap(float val, float min, float max);
    static float random(float min, float max);
    static int random(int min, int max);
};
