#pragma once

#include <string>
#include <vector>
#include <memory>
#include <imgui.h>

struct Module {
    std::string name;
    std::string settingDisplay;
    bool enabled = false;
    bool visibleInArrayList = true;
    int key = 0;
    bool active = false;
    std::string id;
    float arrayListAnim = 0.f;

    std::string getName() const { return name; }
    std::string getSettingDisplayText() const { return settingDisplay; }
    void toggle() { enabled = !enabled; }
};

class Arraylist {
public:
    enum class BackgroundStyle { Opacity, Shadow, Both };
    enum class Display { Outline, Bar, Split, None };
    enum class ModuleVisibility { All, Bound };

    BackgroundStyle mBackground = BackgroundStyle::Shadow;
    float mBackgroundOpacity = 1.0f;
    float mBackgroundValue = 0.0f;
    float mBlurStrength = 0.6f;
    Display mDisplay = Display::Split;
    ModuleVisibility mVisibility = ModuleVisibility::All;
    bool mRenderMode = true;
    bool mGlow = true;
    float mGlowStrength = 1.9f;
    int mGlowDensity = 2;
    bool mBoldText = true;
    float mFontSize = 35.0f;
    float mTopOffset = 10.0f;
    float mRightOffset = 30.0f;

    bool mTextShadow = true;
    float mShadowOffset = 1.0f;

   
    std::string mWatermarkText = "Solstice V4";
    float mWatermarkShadowRadius = 40.0f;
    int mWatermarkShadowDensity = 5;

    Arraylist();
    void initModules();
    void render(ImDrawList* drawList, ImVec2 displaySize, float deltaTime);
    void toggleModule(const std::string& name, const std::string& setting = "", bool addIfMissing = true);
    void setModules(std::vector<std::shared_ptr<Module>> modules);
    void setActive(const std::string& id);
    void setActiveState(const std::string& id, bool active);

private:
    std::vector<std::shared_ptr<Module>> mModules;
    bool mInitialized = false;

    void drawShadowRectDense(ImDrawList* d, ImVec2 min, ImVec2 max, ImColor col, float radius, int density, ImVec2 offset, float rounding);
    void drawShadowCircleDense(ImDrawList* d, ImVec2 center, float radius, ImColor col, int density, int segments, ImVec2 offset, float rounding);
    void renderWatermark(ImDrawList* drawList, ImVec2 displaySize, float deltaTime);
    void renderModules(ImDrawList* drawList, ImVec2 displaySize, float deltaTime);
};
