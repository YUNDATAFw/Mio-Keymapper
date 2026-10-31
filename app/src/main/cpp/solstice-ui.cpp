#include <jni.h>
#include <android/log.h>
#include <GLES3/gl3.h>
#include <imgui.h>
#include <imgui_impl_opengl3.h>
#include <chrono>
#include <memory>
#include <mutex>
#include <utility>
#include <vector>

#include "solstice/resources/font.h"

#include "solstice/Arraylist.hpp"
#include "solstice/Notifications.hpp"

#define LOG_TAG "SolsticeUI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static Arraylist gArraylist;
static Notifications gNotifications;

static std::mutex gUiMutex;

static bool gContextCreated = false;
static bool gGLInitialized = false;
static std::chrono::steady_clock::time_point gLastTime;

ImFont* gFontDefault = nullptr;
ImFont* gFontLarge   = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_Init(JNIEnv* env, jclass clazz) {
    if (gGLInitialized) {
        ImGui_ImplOpenGL3_Shutdown();
        gGLInitialized = false;
    }
    if (gContextCreated) {
        ImGui::DestroyContext();
        gContextCreated = false;
    }

    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();

    ImGui::StyleColorsDark();
    ImGuiStyle& style = ImGui::GetStyle();
    style.WindowRounding = 0.0f;
    style.WindowBorderSize = 0.0f;
    style.WindowPadding = ImVec2(0, 0);

    ImFontConfig cfg;
    cfg.PixelSnapH = false;
    cfg.OversampleH = 1;
    cfg.OversampleV = 1;
    cfg.FontDataOwnedByAtlas = false;

    gFontDefault = io.Fonts->AddFontFromMemoryTTF(
        (void*)OPPOSans_H, (int)OPPOSans_H_size, 20.0f, &cfg);
    gFontLarge = io.Fonts->AddFontFromMemoryTTF(
        (void*)OPPOSans_H, (int)OPPOSans_H_size, 100.0f, &cfg);

    io.FontDefault = gFontDefault;
    gContextCreated = true;

    ImGui_ImplOpenGL3_Init("#version 300 es");
    gGLInitialized = true;
    LOGI("ImGui init done");
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_Resize(JNIEnv* env, jclass clazz, jint width, jint height) {
    if (!gContextCreated) return;
    ImGui::GetIO().DisplaySize = ImVec2((float)width, (float)height);
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_Render(JNIEnv* env, jclass clazz) {
    if (!gContextCreated || !gGLInitialized) return;

    auto now = std::chrono::steady_clock::now();
    float deltaTime = std::chrono::duration<float>(now - gLastTime).count();
    gLastTime = now;
    if (deltaTime <= 0.0f || deltaTime > 1.0f) deltaTime = 1.0f / 60.0f;

    ImGui_ImplOpenGL3_NewFrame();
    ImGuiIO& io = ImGui::GetIO();
    io.DeltaTime = deltaTime;
    ImGui::NewFrame();

    ImGui::SetNextWindowPos(ImVec2(0, 0));
    ImGui::SetNextWindowSize(io.DisplaySize);
    ImGui::Begin("Overlay", nullptr,
        ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize |
        ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoScrollbar |
        ImGuiWindowFlags_NoInputs | ImGuiWindowFlags_NoBackground);

    {
        std::lock_guard<std::mutex> lock(gUiMutex);
        gArraylist.render(ImGui::GetBackgroundDrawList(), io.DisplaySize, deltaTime);
        gNotifications.render(ImGui::GetBackgroundDrawList(), io.DisplaySize, deltaTime);
    }

    ImGui::End();
    ImGui::Render();

    glViewport(0, 0, (int)io.DisplaySize.x, (int)io.DisplaySize.y);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_Shutdown(JNIEnv* env, jclass clazz) {
    if (gGLInitialized) {
        ImGui_ImplOpenGL3_Shutdown();
        gGLInitialized = false;
    }
    if (gContextCreated) {
        ImGui::DestroyContext();
        gContextCreated = false;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetItems(JNIEnv* env, jclass clazz,
        jobjectArray titles, jobjectArray keys, jbooleanArray enabled) {
    jsize count = env->GetArrayLength(titles);
    jsize keyCount = env->GetArrayLength(keys);
    jsize enabledCount = env->GetArrayLength(enabled);
    jsize n = count;
    if (keyCount < n) n = keyCount;
    if (enabledCount < n) n = enabledCount;

    jboolean* enabledArr = env->GetBooleanArrayElements(enabled, nullptr);

    std::vector<std::shared_ptr<Module>> modules;
    modules.reserve(n);
    for (jsize i = 0; i < n; i++) {
        jstring jt = (jstring)env->GetObjectArrayElement(titles, i);
        jstring jk = (jstring)env->GetObjectArrayElement(keys, i);
        const char* t = jt ? env->GetStringUTFChars(jt, nullptr) : "";
        const char* k = jk ? env->GetStringUTFChars(jk, nullptr) : "";

        auto mod = std::make_shared<Module>();
        mod->name = t ? t : "";
        mod->id = k ? k : "";
        mod->enabled = enabledArr[i];
        mod->visibleInArrayList = true;
        modules.push_back(mod);

        if (jt) env->ReleaseStringUTFChars(jt, t);
        if (jk) env->ReleaseStringUTFChars(jk, k);
        env->DeleteLocalRef(jt);
        env->DeleteLocalRef(jk);
    }

    env->ReleaseBooleanArrayElements(enabled, enabledArr, JNI_ABORT);
    {
        std::lock_guard<std::mutex> lock(gUiMutex);
        gArraylist.setModules(std::move(modules));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetActiveKey(JNIEnv* env, jclass clazz, jstring key) {
    const char* k = key ? env->GetStringUTFChars(key, nullptr) : "";
    {
        std::lock_guard<std::mutex> lock(gUiMutex);
        gArraylist.setActive(k ? k : "");
    }
    if (key) env->ReleaseStringUTFChars(key, k);
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetActiveState(JNIEnv* env, jclass clazz, jstring key, jboolean active) {
    const char* k = key ? env->GetStringUTFChars(key, nullptr) : "";
    {
        std::lock_guard<std::mutex> lock(gUiMutex);
        gArraylist.setActiveState(k ? k : "", active);
    }
    if (key) env->ReleaseStringUTFChars(key, k);
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_Notify(JNIEnv* env, jclass, jstring msg, jint type, jfloat duration) {
    const char* cstr = env->GetStringUTFChars(msg, nullptr);
    {
        std::lock_guard<std::mutex> lock(gUiMutex);
        gNotifications.add(cstr ? cstr : "", (Notification::Type)type, duration);
    }
    env->ReleaseStringUTFChars(msg, cstr);
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetNotifyColor(JNIEnv*, jclass, jint argb) {
    float a = ((argb >> 24) & 0xFF) / 255.0f;
    float r = ((argb >> 16) & 0xFF) / 255.0f;
    float g = ((argb >> 8) & 0xFF) / 255.0f;
    float b = (argb & 0xFF) / 255.0f;
    {
        std::lock_guard<std::mutex> lock(gUiMutex);
        gNotifications.mNotifyColor = ImColor(r, g, b, a);
        gNotifications.mHasNotifyColor = true;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetGlow(JNIEnv*, jclass, jboolean v) {
    std::lock_guard<std::mutex> lock(gUiMutex);
    gArraylist.mGlow = v;
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetGlowDensity(JNIEnv*, jclass, jint v) {
    std::lock_guard<std::mutex> lock(gUiMutex);
    gArraylist.mGlowDensity = v;
    gNotifications.mShadowDensity = v;
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetGlowRadius(JNIEnv*, jclass, jfloat v) {
    std::lock_guard<std::mutex> lock(gUiMutex);
    gArraylist.mGlowStrength = v;
    gNotifications.mShadowBlur = v * 100.0f;
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetRightOffset(JNIEnv*, jclass, jfloat v) {
    std::lock_guard<std::mutex> lock(gUiMutex);
    gArraylist.mRightOffset = v;
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetDisplay(JNIEnv*, jclass, jint v) {
    std::lock_guard<std::mutex> lock(gUiMutex);
    gArraylist.mDisplay = (Arraylist::Display)v;
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetTextShadow(JNIEnv*, jclass, jboolean v) {
    std::lock_guard<std::mutex> lock(gUiMutex);
    gArraylist.mTextShadow = v;
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetShadowOffset(JNIEnv*, jclass, jfloat v) {
    std::lock_guard<std::mutex> lock(gUiMutex);
    gArraylist.mShadowOffset = v;
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetWatermarkText(JNIEnv* env, jclass, jstring text) {
    const char* cstr = env->GetStringUTFChars(text, nullptr);
    {
        std::lock_guard<std::mutex> lock(gUiMutex);
        gArraylist.mWatermarkText = cstr ? cstr : "";
    }
    env->ReleaseStringUTFChars(text, cstr);
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetFontSize(JNIEnv*, jclass, jfloat v) {
    std::lock_guard<std::mutex> lock(gUiMutex);
    gArraylist.mFontSize = v;
    gNotifications.mFontSize = v * (30.0f / 35.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetFeatureListFontSize(JNIEnv*, jclass, jfloat v) {
    std::lock_guard<std::mutex> lock(gUiMutex);
    gArraylist.mFontSize = v;
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetNotifyFontSize(JNIEnv*, jclass, jfloat v) {
    std::lock_guard<std::mutex> lock(gUiMutex);
    gNotifications.mFontSize = v;
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetNotifyPosition(JNIEnv*, jclass, jint v) {
    std::lock_guard<std::mutex> lock(gUiMutex);
    gNotifications.mPosition = (Notifications::Position)v;
}

extern "C" JNIEXPORT void JNICALL
Java_io_takamiya_mio_floating_SolsticeUI_SetNotifyGradient(JNIEnv*, jclass, jboolean v) {
    std::lock_guard<std::mutex> lock(gUiMutex);
    gNotifications.mColorGradient = v;
}
