# 03 DSL 语言快速上手

**本章目标**：掌握写 `.dsl` 所需的全部语法——模块、类型、函数与可见性、语句、字符串与 null 处理、内置模块（网络/日志/JSON）。读完能独立读懂和修改任何插件源码。

**前置**：[00 快速开始](00-快速开始.md)。本章示例均与工程真实源码一致。

**一句话定位**：DSL 是一门**类 C 的小语言**，语法窄、无类、无继承；系统能力全部以「命名空间.函数」（宿主桥）形式暴露。

---

## 3.1 模块 = 一个文件

每个 `.dsl` 文件是一个**模块**。文件首行声明包名，其他文件用包名导入它。

```java
package mio.utils
// 这是 MioEngFun 包装模块，声明它属于 mio.utils 包

import dsl.System          // 导入其他模块的包路径（跨模块调用依赖导入）
import mio.utils.MioSensor
```

- `package <包名>`：本模块归属；经编译器组装路径后就是其他模块 `import` 的名字。
- `import <包名>`：引入别的模块，之后才能 `包名.函数` 调用它。
- 注释用 `//`（行注释），没有块注释。

### `package` 必须等于目录路径（编译期强校验）

这是新手第一大报错源：

| 文件位置（相对 `src/script/`） | 首行必须是 |
|---|---|
| `mio/shortcutKey/autoAttack.dsl` | `package mio.shortcutKey` |
| `mio/utils/MioEngFun.dsl` | `package mio.utils` |
| `main.dsl` / `config.dsl`（根目录） | 不写 `package` |

不一致会直接构建失败：`package '…' 与目录路径不符`。

> 官方样例 `main.dsl`：
> ```java
> import dsl.System
> import dsl.Net
>
> export fn Void loading() {
> }
> ```

---

## 3.2 数据类型与字面量

| 类型 | 含义 | 字面量示例 |
|---|---|---|
| `Void` | 无返回值 | — |
| `Bool` | 布尔 | `true` / `false` |
| `Int` | 整数 | `25`、`-5` |
| `Float` | 浮点 | `1.5`（**整数运算默认整除，任一侧浮点才转浮点**） |
| `String` | 字符串 | `"自动攻击"` |
| `[String]`、`[Int]` 等 | 列表 | `["KEYCODE_Q", "KEYCODE_W"]` |
| `Object` | 任意值 / JSON 节点 / 宿主返回 | `null`、`parseJson(raw)` 的结果 |
| `Callback(...) -> Void` | 函数值（lambda） | `(code, data) => { … }` |

- 列表可按下标读：`list[0]`。
- **Map 没有 `.get` 方法**：用 `obj["key"]` 或 `obj.key` 下标/属性读。
- **`Object` 是"没类型时的类型"**：`HostData.get`、`Json.parse` 都返回 `Object`，要用时直接赋给具体类型变量（运行期转换），失败则由执行层报错。

### 变量与赋值

```java
// 局部变量：先声明后用，类型写在名字前
Int count = 0
Bool ok  = MioEngFun.tap("KEYCODE_Q", 5)
String name = "autoAttack"

// 赋值
count = count + 1
```

### 模块级变量（跨函数共享状态）

```java
private String ACTION_ID = "autoAttack"   // 模块私有
public  Int    retryCount = 0             // 跨模块可读
```

- `private` 只在本模块可见；`public` 跨模块可读。
- 模块级变量在**服务线程内**被所有入口函数共享——这正是「上一次触发的计数」能跨调用保留的原因；但别指望跨插件共享。

---

## 3.3 函数

### 定义与返回

```java
export fn Bool myAction(String title, String id) {
    // 函数体
    return true
}
```

- 返回类型写在 `fn` 后；无返回值用 `Void`，可以写 `return` 提前结束（`return` 后不带值）。
- 参数在括号里依次列出，`类型 名字`。
- **没有默认参数、没有函数重载**：调用方传参必须与声明吻合（缺参时按 null 传递；Mio 的桥接函数会再校验并抛 `参数无效`，见 [08 调试与错误处理](08-调试与错误处理.md)）。**尾部参数省略**是常见写法：`MioConfig.onSwitch(k, t, s, true)` 后两参自动为 null。

### 可见性关键字（导出控制）

| 关键字 | 语义 |
|---|---|
| `export` | **宿主可调用** + 跨模块可见 ⭐ 插件入口函数（`loading/keyInit/onTriggerDown/schema/serverInit/on<事件名>`）必须用它 |
| `public` | 仅跨模块可见，宿主不可直接调用 |
| `private` | 仅本模块可见（最常用，模块内辅助函数一律 `private fn`） |

宿主（Mio）只认 `export` 的函数；对未导出的函数调用会得到 `宿主可调用函数不存在（需 EXPORT）` 的报错。可选入口没写则**静默忽略**（[08 §8.4](08-调试与错误处理.md)）。

### 宿主桥绑定：`= call(...)`

Mio 的宿主桥函数通过包装语法绑定到 DSL 名字：

```java
export fn Bool tap(String keyName, Int pressMs) = call("MioEngFun.tap")
private fn Object readCfg(String key) = call("HostData.get")
```

- `call("MioEngFun.tap")` 声明：调用本 DSL 函数时，实际调用宿主在 `MioEngFun.tap` 注册的实现，参数按位置透传。
- 一行式（函数体只有 `call`）= 纯转发；也可以写多行函数体做校验/组装（如 `dsl/System.dsl` 的 `isEmpty`）。
- 你写业务模块时直接调用 `MioEngFun.tap(...)` 即可；只有**新增包装**或**读 HostData** 时才需要自己写 `call`（读 HostData 已有包装模块 `mio/utils/MioData.dsl`——`MioData.get(key)` / `MioData.getInt(key, def)`，无需自己写 `readCfg` 转发）。

### lambda 与 Callback（异步网络用）

参数类型可以是 `Callback(参数类型…) -> Void`，调用处传 lambda：

```java
dsl.Net.get("https://example.com/api",
    (code, data) => {                     // onSuccess：code 状态码、data 响应体
        System.logInfo("api", "code=" + code)
    },
    (msg) => {                            // onError
        System.logWarn("api", msg)
    }
)
```

- 语法：`(参数名, …) => { 语句… }`，参数类型由被调函数签名推定。
- lambda 在**回调线程**执行（宿主调度），不是服务线程——回调里不要碰触发循环的共享状态，必要时只更新模块级变量。

---

## 3.4 语句

```java
// if / else
if(!MioEngFun.isEngineReady()) {
    MioEngFun.showToast("按键注入未就绪")
    return
} else {
    // …
}

// while（触发循环的惯用法，见 07 章）
while(MioEngFun.isTriggerDown(ACTION_ID)) {
    // …
    break
}

// for、continue 同样可用
for(Int i = 0; i < 3; i = i + 1) {
    MioEngFun.sleep(50)
}
```

- **`break` 退出循环 / `continue` 跳过本轮**；`return` 退出整个函数。
- 运行时还支持 `switch`（匹配到即执行该分支，**无 fall-through**）、`try … catch … finally`（仅捕获脚本内 `throw` 的异常）、`throw` 表达式等 AST 能力；`.dsl` 源层的关键字形式以 `dsl-cli` 为准。

### 阻塞循环的退出契约（最重要的一条语句规则）

```java
while(MioEngFun.isTriggerDown(ID)) {   // ✅ 有退出条件，宿主可停线程
    MioEngFun.tap("KEYCODE_Q", 5)
    MioEngFun.sleep(25)
    if(MioEngFun.isTriggerUp(ID)) break
}
```

写 `while(true)` 而不检查触发状态 = 服务线程永远无法停止（禁用插件/保存配置都要等它），**必须避免**（[08 §8.5](08-调试与错误处理.md) 陷阱 8）。

---

## 3.5 运算符

| 类别 | 运算符 | 说明 |
|---|---|---|
| 算术 | `+ - * / %` | 两整数相除为**整除**（`7/2=3`）；任一侧 `Float` 转浮点；`+` 遇字符串即拼接；`%` 取模（已验证可用） |
| 比较 | `== != < > <= >=` | 数值直比；**字符串内容比较见 3.6** |
| 逻辑 | `&& \|\| !` | 短路求值 |
| 下标 | `[ ]` | 列表/Map |
| 属性 | `.` | `obj.key`、模块函数 `包名.函数()` |

**没有的运算符**（逐个实测过，别写）：

| 写法 | 报错层级 | 代替写法 |
|---|---|---|
| 三元 `a ? b : c` | `lexer FAILED` | `if/else` 语句 |
| `+=` `-=` 等复合赋值 | `parser FAILED` | `i = i + 1` |
| `++` `--` | `parser FAILED` | `i = i + 1` |

未列于此表的写法默认**不确定支持**——先抄一个最小片段 `dsl-cli build` 验证再入码。

---

## 3.6 字符串处理（高频坑）

```java
// ✅ 内容比较：用 System.stringEquals（封装 String.equals）
if(System.stringEquals(mode, "快速")) { … }

// ❌ 不要假设 == 对字符串做内容比较

// 去空格 / 判空 / 分割
String raw = System.trim(input)
if(System.isEmpty(raw)) { return }              // trim 后 == ""
[String] parts = System.stringSplit(line, ",")  // 拆 onChip 保存的逗号串

// 拼接（+ 即可）
String msg = "按住了 " + keyName
```

- 工程 `dsl/System.dsl` 还提供 `stringCopies(str, count)`（重复字符串）、`isNull(obj)`（`Objects.isNull` 桥）。
- **`HostData.get` 返回 `Object`**：先 `== null` 判断再使用（[11 §11.6](11-配置页与HostData.md)）。

---

## 3.7 JSON 与网络（内置模块 `dsl.Net` / `Json`）

工程自带 `dsl/Net.dsl`（异步）与 `dsl/System.dsl`（同步 HTTP + 工具），`import dsl.Net` 后可直接用：

```java
import dsl.Net
import dsl.System

// 异步 GET：回调在宿主线程执行
Net.get("https://api.example.com/state",
    (code, data) => {
        if(code == 200) {
            Object root = Net.parseJson(data)    // String → Object
            String player = root.player           // Map 属性读
            System.logInfo("api", player)
        }
    },
    (msg) => { System.logWarn("api", msg) }
)
```

`dsl.Net` 导出：`request` / `get` / `post` / `postJson` / `put` / `del` / `getSimple` / `fetchJson`（自动 parse）/ `parseJson` / `isSuccess`。
同步简易版在 `dsl.System`：`httpGet` / `httpPost` / `httpPostJson`（返回 `String`，**阻塞当前服务线程**）。

取 JSON 字段用 `dsl.Json` 的点路径版本（缺字段不报错，按类型给缺省值）：

| 函数 | 返回 | 缺字段时 |
|---|---|---|
| `Json.jsonString(raw, path)` | `String` | `""` |
| `Json.jsonInt(raw, path)` | `Int` | `0` |
| `Json.jsonBool(raw, path)` | `Bool` | `false` |
| `Json.jsonStringArray(raw, path)` | `[String]` | 空列表 |
| `Json.parseJson(raw)` | `Object` | 由调用方判空 |

```java
import dsl.Json

String sender = Json.jsonString(json, "sender")            // 顶层字段
Int    x      = Json.jsonInt(json, "block.coordinate.x")   // 点路径取嵌套
Bool   ok     = Json.jsonBool(json, "trusted")
if(ok) {
    System.logInfo("ws", sender + " trusted, x=" + System.intToString(x))
}
```

选型：**只要几个字段** → `Json.jsonXxx` 点路径（缺省值友好）；**要遍历/结构判断** → `Net.parseJson` 拿对象后用属性读（`root.player`）。WS 事件处理里两种都常见（[10 §10.5](10-WS服务与游戏联动.md)）。

---

## 3.8 内置命名空间速查（宿主预注册）

`SdlEngine.init` 预注册的命名空间可直接使用（无需 import；若 `dsl-cli` 要求包装文件，用官方 `dsl/System.dsl`、`dsl/Net.dsl` 模式）：

| 命名空间 | 能力 | 插件侧常用入口（经 `dsl/System.dsl` 包装） |
|---|---|---|
| `System` | toast、日志、时间、主题 | `toast` `logDebug/Info/Warn/Error` `currentTimeMillis` `currentThreadName` `getPackage` |
| `String` | 字符串工具 | `stringEquals` `trim` `stringSplit` `isEmpty` |
| `Integer` / `Float` | 数值转换 | `parseInt` `parseFloat` `intToString` |
| `Objects` / `Object` | 对象通用 | `isNull` |
| `Map` / `List` | 容器操作（**用下标读，无 `.get`**） | — |
| `Device` | 设备信息 | `screenWidth/Height` `screenDensity` `osVersion` `deviceModel` `deviceId` |
| `Network` | 网络状态 | `isNetworkAvailable` `isWifiConnected` `networkType` |
| `Http` / `Url` | 网络；`Http.request` 异步+回调 | `httpGet` `httpPost` `httpPostJson` `urlEncode/Decode` |
| `Json` / `Base64` / `Crypto` | 编码加密 | `parseJson`（Net） `base64Encode/Decode` `md5` `sha256` |
| `Date` | 日期时间（固定 UTC） | — |
| `Convert` / `Math` / `Random` / `UUID` | 转换、数学、随机、UUID | — |
| `Permission` | 权限感知与请求（Mio 覆盖了 request 实现） | `checkPermission` `permissionStatus([name])` `requestPermission(s)`（弹 Mio 授权界面，非「不支持」） |
| `System`（Mio 追加） | 应用版本 | `getAppVersion()` `getAppVersionCode()` |

Mio 特有命名空间（`MioEngFun` / `MioGamepad` / `MioSensor` / `MioFloating` / `MioConfig` / `Ws`）见 [04 模块 API 速查](04-模块API速查.md)。

---

## 3.9 常见语法错误对照

| 构建/运行报错 | 原因 | 修法 |
|---|---|---|
| `[FAIL] lexer FAILED` + `解析错误数: 1`（无位置） | ① 文件带 **BOM**；② 写了 `?:` 等词法不认的符号（BOM 实测：同内容带 BOM 挂、去 BOM 过） | 「UTF-8 无 BOM」另存；对照 §3.5 已验证清单 |
| `package '…' 与目录路径不符` | 首行 package ≠ 目录路径 | 按 3.1 表改（根目录文件不写 package） |
| `宿主可调用函数不存在（需 EXPORT）` | 入口/函数少了 `export` | 加 `export` |
| `模块中不存在可调用函数` | 跨模块调用缺 `import` 或包名错 | 检查 `import` 与包路径 |
| `桥接函数未注册` | `call("…")` 写了不存在的宿主桥 | 对 [09 附录 §D](09-附录.md) |
| `缺少第 n 个参数` / `必须为整数` | 实参与签名不符 | 对 [04 章](04-模块API速查.md) |
| 字符串里写 `\"` 转义 | DSL 字符串**不支持转义序列**（词法解析失败） | 用 `'` 或改写文案，避免在字符串里嵌引号 |
| `Expected function keyword 'fn' after visibility modifier`（parser FAILED） | **模块级变量声明写在了函数定义之后** | 把 `private String x = …` 等模块变量统一放到文件头部（第一个 `fn` 之前） |
| 整数除法结果少 1 | 整除 | 让任一侧为 `Float` |

---

## 3.10 实战练习：把知识点串成一个模块

下面模块综合了本章全部语法点（**结构可直接编译**——就是 [00 快速开始](00-快速开始.md) 的 Hello 变体）：

```java
package mio.shortcutKey

import mio.utils.MioEngFun
import mio.utils.MioFloating
import dsl.System

// ── 模块级变量：必须在第一个 fn 之前声明 ──
private String COUNTER_ID = "counter"
private Int    clickCount = 0

// ── 模块内辅助函数：private，不导出 ──
private fn Object readCfg(String key) = call("HostData.get")

// ── 入口：宿主可调用，必须 export ──
export fn Void keyInit() {
    Object en = readCfg("counter.enabled")      // 配置开关（可能为 null）
    if(en == false) {
        return
    }
    MioEngFun.onKeyInit("计数狂点", "BUTTON", COUNTER_ID,
        "按住时狂点并把次数显示到状态栏", "工具")
}

export fn Void onTriggerDown() {
    if(!MioEngFun.isEngineReady()) {
        MioEngFun.showToast("注入未就绪")
        return
    }
    clickCount = 0                               // 模块变量跨函数保留
    while(MioEngFun.isTriggerDown(COUNTER_ID)) {
        Bool ok = MioEngFun.tap("KEYCODE_J", 5)
        if(!ok) {
            MioEngFun.showToast("注入失败，已停止")
            return                               // 提前 return：不发 onTriggerUp
        }
        clickCount = clickCount + 1
        MioFloating.updateStatusBar("点了 " + System.intToString(clickCount) + " 次")
        MioEngFun.sleep(25)
        if(MioEngFun.isTriggerUp(COUNTER_ID)) {
            break                                // 正常结束：宿主补发 onTriggerUp
        }
    }
}

export fn Void onTriggerUp() {
    MioEngFun.showToast("本次共点 " + System.intToString(clickCount))
}
```

> 注意每个用到的命名空间都要 `import`——漏掉 `MioFloating` 这一行就会报「模块中不存在可调用函数」（[08 §8.3](08-调试与错误处理.md)）。

自测题（答案都在本章）：

1. `clickCount = clickCount + 1` 能跨次触发累加吗？（能——模块级变量随插件存活；重启插件清零）
2. 把 `while(...)` 换成 `while(true)` 会怎样？（服务线程停不掉，[08 陷阱 8](08-调试与错误处理.md)）
3. `if(en == false)` 为什么不用 `!en`？（`en` 是 `Object` 可能为 `null`；`!null` 语义不保证——显式比较更安全）
4. 删掉 `import mio.utils.MioFloating` 会报什么？（`模块中不存在可调用函数`）
5. `System.intToString` 前面为什么是 `System` 不是 `MioEngFun`？（它属内置 `System` 命名空间，见 §3.8）

---

## 3.11 一句话速记

- 每个功能模块 = 一个 `.dsl` 文件：**`package` = 目录路径**；入口函数（`keyInit` / `onTriggerDown` / `schema` / `serverInit`）一律 `export`。
- 宿主桥 = `export fn Xxx(参数) = call("命名空间.函数")`；读配置 = `call("HostData.get")`。
- 字符串比内容用 `System.stringEquals`；读 Map 用 `obj.key`，别找 `.get`。
- 缺参为 null、多参丢弃、无默认参数、整数除法整除、字符串无 `\"` 转义。
- `while(isTriggerDown)` 是触发循环的唯一正确写法。

---

**继续**：[04 模块 API 速查](04-模块API速查.md) · 上一步：[02 插件包与运行环境](02-插件包与运行环境.md)
