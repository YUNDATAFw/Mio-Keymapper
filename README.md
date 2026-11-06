# Mio

[简体中文](README.md) | [English](README.en.md)

Mio 是一个面向 Android 的 Minecraft 辅助 / 映射引擎宿主应用，核心由 **悬浮窗 UI + DSL 插件系统 + 输入注入 + WebSocket 游戏联动** 组成。

> 本项目主要用于学习、研究与个人使用。请遵守 Minecraft 服务端规则及当地法律法规，不要将本项目用于破坏游戏公平性或任何违法用途。

## 脱机版

当前仓库发布的是 **脱机版（Offline Build）**，特点：

- 启动时跳过服务器校验与版本检查，无网络也能进入主页。
- 不进入登录 / 注册 / 找回密码 / 论坛 / 会员页面，打开即用。
- 用户协议内置本地文本，离线状态下也可点击「我同意」。
- **网络功能仍然保留**：WebSocket 服务、插件下载、在线接口在联网后可正常使用。

### 下载与安装

前往 [Releases](https://github.com/YUNDATAFw/Mio-Keymapper/releases/latest) 下载 APK：

| 文件 | 适用设备 |
|---|---|
| `mio-1.2.9-universal.apk` | 通用包，不确定架构时选这个 |
| `mio-1.2.9-arm64-v8a.apk` | 绝大多数现代 Android 手机 |
| `mio-1.2.9-armeabi-v7a.apk` | 较老的 32 位设备 |

安装要求：Android 8.0（API 26）及以上。

> 脱机版使用 Debug 签名，若设备上已安装其他签名的 Mio，需要先卸载旧版本再安装。

## 功能概览

- **实体键 / 外接键盘触发**：外接键鼠按键可绑定到插件快捷键，支持按下、松开、长按、开关等触发方式。
- **悬浮窗面板**：快捷键、WS 功能开关、MCID 查询等面板，可拖动、缩放、最小化。
- **DSL 插件系统**：插件安装 / 卸载 / 启用 / 禁用、插件配置页、事件回调、权限声明。
- **Kore 插件引擎**：基于 `runtime` / `runtime-core` / `utils` AAR，兼容旧 DSL 插件接口。
- **WebSocket 联动**：内置 MC 基岩版 WebSocket 服务，提供事件订阅与 `Ws.*` 宿主桥。
- **输入注入**：输入法、Shizuku、虚拟手柄等多种注入通道。
- **MCID 离线数据**：方块、物品、附魔、效果、粒子、生物群系、实体、雾等数据从 `res/raw/*.json` 异步加载。
- **Material 3 主题**：支持深色模式、动态取色与侧边栏头部渐变。

## 技术栈

| 项 | 版本 / 说明 |
|---|---|
| 语言 | Java 17 |
| compileSdk / targetSdk / minSdk | 36 / 35 / 26 |
| Gradle | 8.14.6 |
| Android Gradle Plugin | 8.10.1 |
| NDK | 29.0.14206865 |
| CMake | 3.22.1 |
| UI | ViewBinding / DataBinding、Navigation、Material 3 |
| 网络 | OkHttp |
| 图片 / Markdown | Glide、Markwon |
| 插件运行时 | Kore `runtime-release.aar`、`runtime-core.jar`、`utils-release.aar` |
| 插件编译工具 | `app/libs/dsl-cli.jar` |

## 目录结构

```
Mio/
├─ app/
│  ├─ libs/                     # 本地 AAR / JAR（Kore runtime、pandaLibrary、dsl-cli）
│  └─ src/main/
│     ├─ cpp/                   # native 注入 / 完整性 / UI 相关 C++
│     ├─ java/io/takamiya/mio/
│     │  ├─ app/                # Application 初始化
│     │  ├─ dsl/                # 插件生命周期、宿主桥注册
│     │  ├─ floating/           # 悬浮窗服务与面板
│     │  ├─ mcid/               # MCID 离线数据仓库
│     │  ├─ navigation/         # 主页 / 插件 / 配置 / 设置等 Fragment
│     │  ├─ ws/                 # WebSocket 服务与功能中心
│     │  └─ utils/              # 通用工具
│     └─ res/raw/               # MCID 离线 JSON 数据
├─ docs/                        # DSL 插件开发文档（从 docs/README.md 开始）
├─ gradle/                      # Gradle wrapper 与版本目录
└─ settings.gradle.kts
```

## 构建

### 环境要求

- JDK 17
- Android SDK 36
- Android NDK `29.0.14206865`
- CMake `3.22.1`

### 步骤

1. 在项目根目录创建 `local.properties`，写入 SDK 路径：

   ```properties
   sdk.dir=D:\\Android\\Sdk
   ```

2. 确认 `app/libs/` 下存在以下文件：

   ```
   runtime-release.aar
   runtime-core.jar
   utils-release.aar
   pandaLibrary-release.aar
   dsl-cli.jar
   ```

3. 编译脱机版（Debug）APK：

   ```bash
   ./gradlew :app:assembleDebug
   ```

4. 产物位置：

   ```
   app/build/outputs/apk/debug/mio-1.2.9-universal.apk
   app/build/outputs/apk/debug/mio-1.2.9-arm64-v8a.apk
   app/build/outputs/apk/debug/mio-1.2.9-armeabi-v7a.apk
   ```

> `gradle/wrapper/gradle-wrapper.properties` 默认使用腾讯云 Gradle 镜像，便于国内网络下载。若你的网络可直连官方源，可改为：
>
> ```properties
> distributionUrl=https\://services.gradle.org/distributions/gradle-8.14.6-bin.zip
> ```

## 插件开发文档

插件作者请从 [`docs/README.md`](docs/README.md) 开始阅读，包含：

- DSL 语言语法与快速上手
- 宿主桥 / 模块 API 速查
- 事件系统与实体键触发模型
- 注入与按键码
- WS 服务与游戏联动
- 配置页与 HostData

## 当前调试开关

脱机版保留了以下开关，**发布正式联网版本前请恢复**：

| 位置 | 开关 | 当前值 | 说明 |
|---|---|---|---|
| `app/src/main/cpp/integrity-lib.cpp` | `kIntegrityEnforcementEnabled` | `false` | 关闭 native 完整性门禁、反调试与监控强杀 |
| `app/src/main/java/io/takamiya/mio/MainActivity.java` | `SKIP_STARTUP_NETWORK_CHECK` | `true` | 跳过服务器校验与版本检查，直接进入主页 |
| `app/src/main/java/io/takamiya/mio/LoginActivity.java` | — | 直接跳转主页 | 离线模式下登录页已停用 |
| `app/src/main/java/io/takamiya/mio/UserAgreementActivity.java` | — | 内置协议兜底 | 无网络时也可同意协议 |

## 已知限制

- 未在所有真机 / 系统版本上完整验证，尤其是 Kore 插件安装、WS 连接、实体键触发与注入通道。
- 部分在线功能依赖服务端接口，离线环境下不可用。
- `app/libs/` 中的 AAR / JAR 为本地二进制依赖，仓库不保证其来源与许可证；公开分发前请自行确认授权。
- `app/release/` 与 IDE 配置 `.idea/` 已加入 `.gitignore`，不再随仓库分发。

## 安全与隐私

- 签名密钥 `key/key.jks` 与 `local.properties` 已加入 `.gitignore`，不会提交。
- 但本地 AAR / APK 中可能包含服务端地址、密钥或私有逻辑，**公开仓库前请自行审查**。
- 本项目不收集、不上传任何用户数据；网络请求仅用于你配置的服务端接口。

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 开源。

```
Copyright 2026 YUNDATAFw

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

第三方依赖与 `app/libs/` 中的本地 AAR / JAR 遵循其各自许可证，详见对应项目；本项目不改变、也不代表其授权。

## 致谢

- Kore 插件运行时
- pandaLibrary
- 以及所有开源依赖的作者
