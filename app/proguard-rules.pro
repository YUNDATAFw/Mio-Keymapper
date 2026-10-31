# ═══════════════════════════════════════════════════════════════
# R8 / ProGuard 混淆配置 — Mio
# ═══════════════════════════════════════════════════════════════

# ── 混淆级别 ──
# 优化 + 混淆 + 移除无用代码
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-obfuscationdictionary obfuscation_dict.txt

# ── Android 组件 ──
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment

# ── AIDL 接口 ──
-keep interface io.takamiya.mio.shizuku.IShellService
-keep class io.takamiya.mio.shizuku.IShellService$* { *; }

# ── Shizuku 相关 ──
-keep class moe.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class io.takamiya.mio.shizuku.** { *; }

# ── 反射调用的系统类 ──
-keep class android.hardware.input.InputManager { *; }
-keep class android.os.ServiceManager { *; }
-keep class android.view.IInputManager { *; }
-keepclassmembers class * {
    @java.lang.reflect.Method <methods>;
}

# ── 枚举 ──
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Parcelable ──
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ── Serializable ──
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ── 注解 ──
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ── 泛型 ──
-keepattributes Signature

# ── R8 兼容性 ──
-keep class com.google.android.material.** { *; }
-keep class androidx.** { *; }

# ── Glide ──
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

# ── OkHttp ──
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ── Rosemoe Editor ──
-keep class io.github.rosemoe.** { *; }
-dontwarn io.github.rosemoe.**

# ── Navigation ──
-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable

# ── DataBinding / ViewBinding ──
-keep class io.takamiya.mio.databinding.** { *; }
-keep class io.takamiya.mio.databinding.**$* { *; }

# ── 应用业务类 ──
-keep class io.takamiya.mio.bean.** { *; }

# ── Toolbox 工具页（不混淆，否则显示不出来） ──
-keep class io.takamiya.mio.floating.toolBoxFragment.tool.** { *; }

# ── WS 面板页（反射 newInstance + 伪 Fragment，不混淆否则显示不出来） ──
-keep class io.takamiya.mio.floating.wss.** { *; }
-keep class io.takamiya.mio.floating.FloatingWsPanel { *; }

# 保留 KeyMapping（枚举 + 静态方法）
-keep class io.takamiya.mio.config.KeyMapping { *; }

# ── 保留类名（堆栈跟踪） ──
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ── 不混淆 native 方法 ──
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── 字符串加密库（JNI 符号依赖类名 o.s.o.l） ──
-keep class o.s.o.l { *; }

# ── APK/DEX 完整性哈希库（RegisterNatives 通过 FindClass + 方法签名匹配） ──
-keep class io.takamiya.mio.utils.ApkIntegrity {
    native <methods>;
}

# ── 原生服务器校验调用的薄封装（JNI 符号依赖类名） ──
-keep class io.takamiya.mio.utils.NativeHttp { *; }
-keep class cn.panda.utils.library.DeviceInfoUtils { *; }

# ── 不混淆 View 构造函数（XML 实例化） ──
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# ── Plugin-SDL-Library（DSL 运行时） ──
-keep class cn.fsy.plugin.sdl.library.** { *; }
-keep class io.takamiya.mio.dsl.** { *; }

# ── 抑制缺失类警告 ──
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.lang.model.**
-dontwarn org.jetbrains.annotations.**
-dontwarn cn.fsy.plugin.sdl.library.**

# ── DSL 工具链（dsl-cli.jar）──
-keep class com.dsl.** { *; }
