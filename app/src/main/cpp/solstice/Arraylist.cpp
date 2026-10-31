// Arraylist.cpp
#include "Arraylist.hpp"
#include "MathUtils.hpp"
#include "ColorUtils.hpp"
#include <algorithm>

Arraylist::Arraylist() {}

void Arraylist::initModules() {
    if (mInitialized) return;
    mInitialized = true;
}

void Arraylist::toggleModule(const std::string& name, const std::string& setting, bool addIfMissing) {
    for (auto& mod : mModules) {
        if (mod->name == name) {
            mod->toggle();
            return;
        }
    }
    if (addIfMissing) {
        auto mod = std::make_shared<Module>();
        mod->name = name;
        mod->settingDisplay = setting;
        mod->enabled = true;
        mod->visibleInArrayList = true;
        mModules.push_back(mod);
    }
}

void Arraylist::setModules(std::vector<std::shared_ptr<Module>> modules) {
    mModules = std::move(modules);
}

void Arraylist::setActive(const std::string& id) {
    for (auto& mod : mModules) {
        mod->active = !id.empty() && mod->id == id;
    }
}

void Arraylist::setActiveState(const std::string& id, bool active) {
    for (auto& mod : mModules) {
        if (mod->id == id) {
            mod->active = active;
        }
    }
}

void Arraylist::drawShadowRectDense(ImDrawList* d, ImVec2 min, ImVec2 max, ImColor col, float radius, int density, ImVec2 offset, float rounding) {
    if (density <= 1) {
        d->AddShadowRect(min, max, col, radius, offset, 0, rounding);
        return;
    }
    for (int i = 0; i < density; i++) {
        float t = (float)i / (density - 1);
        float r = radius * (0.25f + 0.75f * t);
        float a = col.Value.w * (0.5f + 0.5f * (1.0f - t));
        d->AddShadowRect(min, max, ImColor(col.Value.x, col.Value.y, col.Value.z, a), r, offset, 0, rounding);
    }
}

void Arraylist::drawShadowCircleDense(ImDrawList* d, ImVec2 center, float radius, ImColor col,
                                        int density, int segments, ImVec2 offset, float rounding) {
    if (density <= 1) {
        d->AddShadowCircle(center, radius, col, segments, offset, 0, rounding);
        return;
    }
    for (int i = 0; i < density; i++) {
        float t = (float)i / (density - 1);
        float r = radius * (0.25f + 0.75f * t);
        d->AddShadowCircle(center, r, col, segments, offset, 0, rounding);
    }
}

//Watermark
void Arraylist::renderWatermark(ImDrawList* drawList, ImVec2 displaySize, float deltaTime) {
    static float anim = 0.f;
    anim = MathUtils::lerp(anim, 1.0f, deltaTime * 10.f);
    anim = MathUtils::clamp(anim, 0.f, 1.f);
    if (anim < 0.01f) return;

    extern ImFont* gFontLarge;
    extern ImFont* gFontDefault;

    //字体85/35比例
    float watermarkFontSize = mFontSize * (85.0f / 35.0f);

    if (gFontLarge) {
        ImGui::PushFont(gFontLarge, 0.0f);
    } else if (gFontDefault) {
        ImGui::PushFont(gFontDefault, 0.0f);
    }

    float totalWidth = 0.f;
    for (size_t i = 0; i < mWatermarkText.length(); i++) {
        char c = mWatermarkText[i];
        ImVec2 charSize = ImGui::GetFont()->CalcTextSizeA(watermarkFontSize, FLT_MAX, 0.0f, &c, &c + 1);
        totalWidth += charSize.x;
    }

    ImVec2 pos = ImVec2(displaySize.x - totalWidth - mRightOffset, mTopOffset);

    for (size_t i = 0; i < mWatermarkText.length(); i++) {
        char c = mWatermarkText[i];
        ImVec2 charSize = ImGui::GetFont()->CalcTextSizeA(watermarkFontSize, FLT_MAX, 0.0f, &c, &c + 1);
        ImColor color = ColorUtils::getThemedColor(i * 100);

        if (mWatermarkShadowDensity > 0) {
            drawList->AddShadowCircle(
                ImVec2(pos.x + charSize.x / 2, pos.y + charSize.y / 2),
                mWatermarkShadowRadius * anim,
                ImColor(color.Value.x, color.Value.y, color.Value.z, anim),
                100, ImVec2(0, 0), 0, 12);
        }

        ImVec2 shadowPos = ImVec2(pos.x + 3.25f, pos.y + 3.25f);
        ImColor shadowColor = ImColor(color.Value.x * 0.25f, color.Value.y * 0.25f, color.Value.z * 0.25f, 0.925f);
        drawList->AddText(ImGui::GetFont(), watermarkFontSize, shadowPos, shadowColor, &c, &c + 1);
        drawList->AddText(ImGui::GetFont(), watermarkFontSize, pos, color, &c, &c + 1);

        pos.x += charSize.x;
    }

    ImGui::PopFont();
}

//Arraylist
void Arraylist::renderModules(ImDrawList* drawList, ImVec2 displaySize, float deltaTime) {
    float glowStrength = mGlowStrength * 100.0f;
    float fontSize = mFontSize;

    std::ranges::sort(mModules, [this, fontSize](const std::shared_ptr<Module>& a, const std::shared_ptr<Module>& b) {
        std::string aName = a->getName();
        std::string bName = b->getName();
        if (!a->getSettingDisplayText().empty() && mRenderMode) aName += " " + a->getSettingDisplayText();
        if (!b->getSettingDisplayText().empty() && mRenderMode) bName += " " + b->getSettingDisplayText();
        return ImGui::GetFont()->CalcTextSizeA(fontSize, FLT_MAX, 0, aName.c_str()).x >
               ImGui::GetFont()->CalcTextSizeA(fontSize, FLT_MAX, 0, bName.c_str()).x;
    });

    // Watermark 字体大小
    float watermarkFontSize = mFontSize * (85.0f / 35.0f);

    float watermarkHeight = 0.f;
    for (size_t i = 0; i < mWatermarkText.length(); i++) {
        char c = mWatermarkText[i];
        ImVec2 charSize = ImGui::GetFont()->CalcTextSizeA(watermarkFontSize, FLT_MAX, 0.0f, &c, &c + 1);
        watermarkHeight = std::max(watermarkHeight, charSize.y);
    }

    //10px 间距
    ImVec2 pos = ImVec2(displaySize.x - mRightOffset, mTopOffset + watermarkHeight + 10.0f);

    struct TempRenderInfo {
        std::string moduleName;
        ImVec2 start;
        ImVec2 end;
        ImColor color;
        Module* mod;
    };

    struct TempLineRenderInfo {
        std::string moduleName;
        ImVec2 start;
        ImVec2 end;
        ImColor color;
        Module* mod;
    };

    std::vector<TempRenderInfo> backgroundRects;

    for (auto& mod : mModules) {
        if (!mod->visibleInArrayList) continue;
        if (mVisibility == ModuleVisibility::Bound && mod->key == 0) continue;

        mod->arrayListAnim = MathUtils::lerp(mod->arrayListAnim, mod->enabled ? 1.0f : 0.0f, deltaTime * 12.0f);
        mod->arrayListAnim = MathUtils::clamp(mod->arrayListAnim, 0.0f, 1.0f);
        if (mod->arrayListAnim < 0.01f) continue;

        ImColor color = ColorUtils::getThemedColor(pos.y * 2);

        std::string name = mod->getName();
        std::string settingDisplay = mod->getSettingDisplayText();
        if (!mRenderMode) settingDisplay = "";
        if (!settingDisplay.empty()) settingDisplay = " " + settingDisplay;
        ImVec2 textSize = ImGui::GetFont()->CalcTextSizeA(fontSize, FLT_MAX, 0, name.c_str());
        ImVec2 textPos = ImVec2(pos.x, pos.y);

        if (mDisplay == Display::Bar || mDisplay == Display::Split) {
            textPos.x -= 7;
        }

        ImVec2 displaySizeText = {0, 0};
        if (!settingDisplay.empty()) {
            displaySizeText = ImGui::GetFont()->CalcTextSizeA(fontSize, FLT_MAX, 0, settingDisplay.c_str());
        }
        float endPos = textPos.x - textSize.x - displaySizeText.x;
        textPos.x = MathUtils::lerp(displaySize.x + 14.0f, endPos, mod->arrayListAnim);

        float textX = ImGui::GetFont()->CalcTextSizeA(fontSize, FLT_MAX, 0, (name + settingDisplay).c_str()).x;

        ImVec4 rect = {textPos.x, textPos.y, textPos.x + textSize.x + displaySizeText.x, textPos.y + textSize.y};
        rect.x -= 3;
        rect.z += 1;
        rect.z += 3;

        bool addedPadding = (mDisplay == Display::Bar || mDisplay == Display::Split);

        backgroundRects.push_back({name,
            ImVec2(rect.x + (addedPadding ? 7.0f : 0.0f), rect.y),
            ImVec2(rect.z + (addedPadding ? 7.0f : 0.0f), rect.w),
            color, mod.get()});

        // None 文字发光
        if (mGlow && mDisplay == Display::None) {
            drawShadowRectDense(drawList,
                ImVec2(textPos.x, textPos.y),
                ImVec2(textPos.x + textX, textPos.y + textSize.y),
                ImColor(color.Value.x, color.Value.y, color.Value.z, 0.83f * mod->arrayListAnim),
                glowStrength * mod->arrayListAnim, mGlowDensity, ImVec2(0, 0), 12);
        }

        // Outline 发光
        if (mDisplay == Display::Outline) {
            ImColor c1 = ColorUtils::getThemedColor(pos.y * 2);
            if (mGlow) {
                drawShadowRectDense(drawList,
                    ImVec2(rect.x, rect.y),
                    ImVec2(rect.z + (addedPadding ? 7.0f : 0.0f), rect.w),
                    ImColor(c1.Value.x, c1.Value.y, c1.Value.z, 0.6f * mod->arrayListAnim),
                    glowStrength * mod->arrayListAnim, mGlowDensity, ImVec2(0, 0), 0);
            }
        }

        // Bar
        if (mDisplay == Display::Bar) {
            if (mGlow) {
                drawShadowRectDense(drawList,
                    ImVec2(textPos.x, textPos.y),
                    ImVec2(textPos.x + textX, textPos.y + textSize.y),
                    ImColor(color.Value.x, color.Value.y, color.Value.z, 0.83f * mod->arrayListAnim),
                    glowStrength * mod->arrayListAnim, mGlowDensity, ImVec2(0, 0), 12);
            }

            if (mGlow) {
                drawShadowRectDense(drawList,
                    ImVec2(textPos.x + textX - 2, textPos.y - 5),
                    ImVec2(textPos.x + textX + 4, textPos.y + textSize.y + 5),
                    ImColor(color.Value.x, color.Value.y, color.Value.z, 0.7f * mod->arrayListAnim),
                    glowStrength * mod->arrayListAnim, mGlowDensity, ImVec2(0, 0), 12);
            }

            drawList->AddRectFilled(
                ImVec2(textPos.x + textX, textPos.y),
                ImVec2(textPos.x + textX + 2, textPos.y + textSize.y),
                color);
        }

        // Split
        if (mDisplay == Display::Split) {
            if (mGlow) {
                drawShadowRectDense(drawList,
                    ImVec2(textPos.x, textPos.y),
                    ImVec2(textPos.x + textX, textPos.y + textSize.y),
                    ImColor(color.Value.x, color.Value.y, color.Value.z, 0.83f * mod->arrayListAnim),
                    glowStrength * mod->arrayListAnim, mGlowDensity, ImVec2(0, 0), 12);
            }

            ImVec2 lineStart = ImVec2(rect.z + 2, textPos.y + 4);
            ImVec2 lineEnd = ImVec2(lineStart.x + 4, lineStart.y + textSize.y - 6);
            drawList->AddRectFilled(lineStart, lineEnd, color, 3.0f);

            if (mGlow) {
                drawShadowRectDense(drawList,
                    ImVec2(lineStart.x - 3, lineStart.y - 5),
                    ImVec2(lineEnd.x + 3, lineEnd.y + 5),
                    ImColor(color.Value.x, color.Value.y, color.Value.z, 0.7f * mod->arrayListAnim),
                    glowStrength * mod->arrayListAnim, mGlowDensity, ImVec2(0, 0), 12);
            }
        }

        pos.y += (textSize.y * mod->arrayListAnim);
    }

    // 渲染文字
    pos = ImVec2(displaySize.x - mRightOffset, mTopOffset + watermarkHeight + 10.0f);

    for (auto& mod : mModules) {
        if (!mod->visibleInArrayList) continue;
        if (mVisibility == ModuleVisibility::Bound && mod->key == 0) continue;
        if (mod->arrayListAnim < 0.01f) continue;

        ImColor color = ColorUtils::getThemedColor(pos.y * 2);

        std::string name = mod->getName();
        std::string settingDisplay = mod->getSettingDisplayText();
        if (!mRenderMode) settingDisplay = "";
        if (!settingDisplay.empty()) settingDisplay = " " + settingDisplay;
        ImVec2 textSize = ImGui::GetFont()->CalcTextSizeA(fontSize, FLT_MAX, 0, name.c_str());
        ImVec2 textPos = ImVec2(pos.x, pos.y - 5);

        bool addedPadding = false;
        if (mDisplay == Display::Bar || mDisplay == Display::Split) {
            textPos.x -= 7;
            addedPadding = true;
        }

        ImVec2 displaySizeText = {0, 0};
        if (!settingDisplay.empty()) {
            displaySizeText = ImGui::GetFont()->CalcTextSizeA(fontSize, FLT_MAX, 0, settingDisplay.c_str());
        }
        float endPos = textPos.x - textSize.x - displaySizeText.x;
        textPos.x = MathUtils::lerp(displaySize.x + 14.0f, endPos, mod->arrayListAnim);

        ImVec2 mousePos = ImGui::GetIO().MousePos;
        bool isHovered = mousePos.x >= textPos.x && mousePos.x <= textPos.x + textSize.x + displaySizeText.x
                      && mousePos.y >= textPos.y && mousePos.y <= textPos.y + textSize.y;

        ImVec4 rect = {textPos.x, textPos.y, textPos.x + textSize.x + displaySizeText.x, textPos.y + textSize.y};
        rect.x -= 3;
        rect.z += 1;
        rect.z += 3;

        ImColor textColor = color;
        if (mod->active) {
            drawList->AddRectFilled(ImVec2(rect.x - 4, rect.y), ImVec2(rect.z + 4, rect.w),
                ImColor(color.Value.x, color.Value.y, color.Value.z, 0.18f), 4.0f);
            textColor = ImColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // 画阴影
        if (mTextShadow) {
            ImVec2 shadowPos = ImVec2(textPos.x + mShadowOffset, textPos.y + mShadowOffset);
            ImColor shadowColor = ImColor(textColor.Value.x * 0.25f, textColor.Value.y * 0.25f, textColor.Value.z * 0.25f, 0.925f);
            drawList->AddText(ImGui::GetFont(), fontSize, shadowPos, shadowColor, name.c_str());
            if (!settingDisplay.empty()) {
                ImVec2 shadowSettingPos = ImVec2(shadowPos.x + textSize.x, shadowPos.y);
                drawList->AddText(ImGui::GetFont(), fontSize, shadowSettingPos,
                    ImColor(0.9f * 0.25f, 0.9f * 0.25f, 0.9f * 0.25f, 0.925f), settingDisplay.c_str());
            }
        }

        // 渲染主文字
        drawList->AddText(ImGui::GetFont(), fontSize, textPos, textColor, name.c_str());
        if (!settingDisplay.empty()) {
            textPos.x += textSize.x;
            drawList->AddText(ImGui::GetFont(), fontSize, textPos, ImColor(0.9f, 0.9f, 0.9f, 1.0f), settingDisplay.c_str());
        }

        if (isHovered) {
            drawList->AddRectFilled(ImVec2(rect.x, rect.y), ImVec2(rect.z, rect.w), ImColor(1.0f, 1.0f, 1.0f, 0.1f));
            if (ImGui::IsMouseClicked(0)) {
                mod->toggle();
            }
        }

        pos.y += (textSize.y * mod->arrayListAnim);
    }

    if (mDisplay == Display::None) return;

    std::vector<TempLineRenderInfo> lines;
    int bgi = 0;
    ImVec4 startingRect;

    for (auto& [name, start, end, color, mod] : backgroundRects) {
        start.x -= 2.0f;
        end.x -= 2.0f;

        std::vector<TempRenderInfo> bgsAfter;
        for (size_t i = bgi + 1; i < backgroundRects.size(); i++) {
            bgsAfter.push_back({backgroundRects[i].moduleName, backgroundRects[i].start, backgroundRects[i].end,
                                backgroundRects[i].color, backgroundRects[i].mod});
        }

        TempRenderInfo next;
        if (!bgsAfter.empty()) {
            std::ranges::sort(bgsAfter, [](const TempRenderInfo& a, const TempRenderInfo& b) {
                return a.start.x < b.start.x;
            });
            next = bgsAfter.front();
        }
        bool hasNext = !next.moduleName.empty();

        if (mDisplay == Display::Outline) {
            lines.push_back({name, ImVec2(end.x + 2, start.y), ImVec2(end.x + 2, end.y), color, mod});
            if ((hasNext && next.start.x >= start.x) || !hasNext) {
                lines.push_back({name, ImVec2(start.x, start.y), ImVec2(start.x, end.y), color, mod});
            } else if (hasNext) {
                lines.push_back({name, ImVec2(next.start.x, start.y), ImVec2(next.start.x, start.y), color, mod});
            }
            if (bgi == 0) {
                startingRect = {start.x, start.y, end.x, end.y};
                lines.push_back({name, ImVec2(start.x, start.y), ImVec2(end.x + 2, start.y), color, mod});
            }
            if (!hasNext) {
                lines.push_back({name, ImVec2(start.x, end.y), ImVec2(end.x + 2, end.y), color, mod});
            } else if (next.start.x >= start.x) {
                if (next.start.x - start.x > 2) {
                    lines.push_back({name, ImVec2(start.x, end.y), ImVec2(next.start.x - 1, end.y), color, mod});
                }
            }
        }
        bgi++;
    }

    std::ranges::sort(backgroundRects, [](const TempRenderInfo& a, const TempRenderInfo& b) {
        return a.start.x < b.start.x;
    });

    if (mDisplay == Display::Outline && !backgroundRects.empty()) {
        auto lowest = backgroundRects.front();
        lines.push_back({lowest.moduleName, ImVec2(lowest.start.x, lowest.start.y),
                         ImVec2(startingRect.x + 2, lowest.start.y), lowest.color, lowest.mod});
    }

    for (auto& line : lines) {
        drawList->AddLine(ImVec2(line.start.x, line.start.y), ImVec2(line.end.x, line.end.y),
                          line.color, mDisplay == Display::Outline ? 2.0f : 4.0f);
    }
}




//主
void Arraylist::render(ImDrawList* drawList, ImVec2 displaySize, float deltaTime) {
    initModules();
    renderWatermark(drawList, displaySize, deltaTime);
    renderModules(drawList, displaySize, deltaTime);
}
