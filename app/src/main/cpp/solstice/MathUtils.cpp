#include "MathUtils.hpp"
#include <random>

float MathUtils::animate(float endPoint, float current, float speed) {
    if (speed < 0.0f) speed = 0.0f;
    else if (speed > 1.0f) speed = 1.0f;
    float dif = std::fmax(endPoint, current) - std::fmin(endPoint, current);
    float factor = dif * speed;
    return current + (endPoint > current ? factor : -factor);
}

float MathUtils::lerp(float a, float b, float t) {
    return a + t * (b - a);
}

ImVec4 MathUtils::lerp(ImVec4& a, ImVec4& b, float t) {
    return ImVec4(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.z, b.z, t), lerp(a.w, b.w, t));
}

ImVec2 MathUtils::lerp(ImVec2& a, ImVec2& b, float t) {
    return ImVec2(lerp(a.x, b.x, t), lerp(a.y, b.y, t));
}

ImColor MathUtils::lerpImColor(ImColor& a, ImColor& b, float t) {
    return ImColor(lerp(a.Value.x, b.Value.x, t), lerp(a.Value.y, b.Value.y, t),
                   lerp(a.Value.z, b.Value.z, t), lerp(a.Value.w, b.Value.w, t));
}

float MathUtils::clamp(float value, float min, float max) {
    return std::max(min, std::min(value, max));
}

float MathUtils::wrap(float val, float min, float max) {
    return fmodf(fmodf(val - min, max - min) + (max - min), max - min) + min;
}

float MathUtils::random(float min, float max) {
    return min + static_cast<float>(rand()) / (static_cast<float>(RAND_MAX / (max - min)));
}

int MathUtils::random(int min, int max) {
    std::random_device rd;
    std::mt19937 eng(rd());
    std::uniform_int_distribution<> distr(min, max);
    return distr(eng);
}
