import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.takamiya.mio"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "io.takamiya.mio"
        minSdk = 26
        targetSdk = 35
        versionCode = 102090
        versionName = "1.2.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        aidl = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
}

val aarDir = file("libs")
if (!aarDir.exists()) {
    aarDir.mkdirs()
}
aarDir.walkTopDown()
    .filter { it.isFile && (it.extension.equals("aar", ignoreCase = true) || it.extension.equals("jar", ignoreCase = true)) }
    .forEach { libFile ->
        val libName = libFile.nameWithoutExtension
        print("引入库包：$libName (${libFile.absolutePath})")
        dependencies.add("implementation", files(libFile.absolutePath))
    }

dependencies {

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.annotation)
    implementation(libs.okhttp)
    implementation(libs.custom.qr.generator)
    implementation(platform(libs.editor.bom))
    implementation(libs.editor)
    implementation(libs.language.textmate)
    implementation(libs.core)
    implementation(libs.ext.tables)
    implementation(libs.ext.strikethrough)
    implementation(libs.ext.tasklist)
    implementation(libs.image.glide)
    implementation(libs.mpandroidchart)
    implementation(libs.zxing.core)

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.glide)
    implementation(libs.swiperefreshlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)

    implementation(files("libs/dsl-cli.jar"))

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

android.applicationVariants.all {
    outputs.all {
        val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        val abi = filters.firstOrNull { 
            it.filterType == "ABI"
        }?.identifier ?: "universal"
        output.outputFileName = "mio-${versionName}-${abi}.apk"
    }
}
