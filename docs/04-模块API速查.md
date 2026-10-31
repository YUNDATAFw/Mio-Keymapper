# 04 模块 API 速查

**本章目标**：查签名。六个 Mio 命名空间的**插件侧签名**、含义与细节。

**前置**：[03 DSL 语言快速上手](03-DSL语言快速上手.md)。配置页全解见 [11](11-配置页与HostData.md)，WS 全解见 [10](10-WS服务与游戏联动.md)，全量宿主桥索引见 [09 附录 §D](09-附录.md)。

## 0. 总约定

- 动作类函数返回 `Bool`：成功 `true`；注入器不可用 / 注入失败返回 `false`（不抛错）。
- 查询类按真实类型返回（`Bool` / `Int` / `Float` / `String`）。
- 纯动作且无成功语义（浮窗、手柄、sleep）返回 `Void`。
- **参数错误**（缺参、类型错、未知按键名、未知触发模式、越界 inputType）抛 `SdlException(INVALID_ARGUMENT)`，由执行层记录并弹通知（见 [08 调试与错误处理](08-调试与错误处理.md)）。
- 全部阻塞式执行；`onTriggerDown` 里可直接写循环睡眠。
- **尾部参数可省略**（缺参按 null 传递）。

```java
import mio.utils.MioEngFun
import mio.utils.MioGamepad
import mio.utils.MioSensor
import mio.utils.MioFloating
import mio.utils.MioConfig
import mio.ws.MioWs            // WS 桥包装（声明 WS_SERVICE 的模块才需要）
```

## 1. MioEngFun（核心）

### 1.1 快捷键注册

| 函数 | 类型 | 说明 |
|---|---|---|
| `onKeyInit(String title, String triggerModel, String id, String description, String group, Int widthDp, Int heightDp, String bgRes)` | Bool | 注册一个快捷键（前 **5 参**为原有参数，第 5 参 `group` 起均可省略；后 3 参为可选尾参，省略 = 旧行为） |
| `onPhysicalKeyInit(String keyCode, String triggerModel, String id)` | Bool | 注册**实体键绑定**（外接键盘按键直接触发，[05 §5.3.1](05-事件系统与触发模型.md)）：`keyCode` 规范名（`F6` / `KEYCODE_F6` 等价，大小写不敏感；**空串 = 解绑**，返回 `false`）；`triggerModel`/`id` 语义同 `onKeyInit`（id 全局唯一，共用 `isTriggerDown(id)` 状态与触发路由）。**不进悬浮面板**（不入 `registeredShortcuts`），配置键 `<id>.physicalKey` 可覆盖默认键 |

- `triggerModel` 取值 `"BUTTON"`（点按）/ `"SWITCH"`（开关）/ `"PRESSURE_PLATE"`（压力板）；其他值抛「未知触发模式」。
- `id` 唯一，重复注册返回 `false` 并记日志（宿主发一次系统通知，[08 §8.5](08-调试与错误处理.md) 陷阱 7）。
- `title` 显示在悬浮窗快捷键面板上；`id` 是 `isTriggerDown(id)`、`isTriggerUp(id)` 及触发事件关联的 key。
- **`group`（第 5 参）= 快捷键分组标签**（宿主 `item.setGroup`），用于面板归类（真实工程用 `"战斗"`、`"移动"`、`"物品与交互"`、`"状态效果"` 等）。省略该参 = 落入默认分组。
- **`widthDp` / `heightDp`（第 6/7 参）= 悬浮卡片尺寸**（dp，`0`/`null` = 按内容自适应，即旧行为）；宿主会夹在 16dp ~ 屏幕尺寸内。
- **`bgRes`（第 8 参）= 卡片背景引用**：`"@res/bg.9.png"`；按下态用 `|` 再给一张 `"@res/bg.9.png|@res/bg_press.9.png"`（两张点九图，常态/按下各一张）。文件放插件工程 `res/` 下随包分发，安装时落地（见 [02 §2.5](02-插件包与运行环境.md)）。非点九图按位图拉伸；只给常态图时按下态叠主题色滤镜；文件缺失/引用非法回落宿主默认背景并记警告。
- 一般在 `keyInit()` 里调用；宿主在 `keyInit` 执行期间用 ThreadLocal 捕获该服务的归属，供触发事件路由（[01 §1.4](01-总览与原理.md)）。

```java
MioEngFun.onKeyInit("自动攻击", "SWITCH", "autoAttack",
    "开启后以配置频率持续按下Q键进行连续攻击（任意模式可用）", "战斗")

// 自定义卡片：100x40dp，常态/按下两张点九背景（res/bg.9.png、res/bg_press.9.png）
MioEngFun.onKeyInit("自动攻击", "SWITCH", "autoAttack",
    "开启后以配置频率持续按下Q键进行连续攻击（任意模式可用）", "战斗",
    100, 40, "@res/bg.9.png|@res/bg_press.9.png")
```

### 1.2 状态查询

| 函数 | 类型 | 说明 |
|---|---|---|
| `getEngineVersion()` | Int | 映射引擎版本号（当前 `4`） |
| `isImeReady()` | Bool | 输入法注入通道是否就绪（已启用且为当前输入法） |
| `isShizukuReady()` | Bool | Shizuku 是否已授权可用 |
| `isMapReady()` | Bool | 键盘映射通道是否可用（语义=Shizuku 通道可用性，历史命名） |
| `isGamepadReady()` | Bool | 虚拟手柄是否已开启 |
| `isSensorReady()` | Bool | 旋转传感器（游戏旋转矢量/旋转矢量）是否可用 |
| `isEngineReady()` | Bool | 任一注入通道可用 **或** 手柄已开启 |
| `getInputModel()` | Int | 当前注入模式：`0`=IME `1`=Shizuku `2`=Gamepad `-1`=无 |
| `isKeyValid(String keyName)` | Bool | 按键名是否合法（查 [06 注入与按键码](06-注入与按键码.md) 按键码表） |

### 1.3 按键注入

| 函数 | 类型 | 说明 |
|---|---|---|
| `keyDown(String keyName)` | Bool | 按住不放（配合 `keyUp`），不自动松开 |
| `keyUp(String keyName)` | Bool | 松开 |
| `tap(String keyName, Int pressMs)` | Bool | 单击：按下 → 保持 `pressMs` ms → 松开 |
| `tapGroup([String] keyNames, Int pressMs)` | Bool | 全部按下 → 保持 `pressMs` → 逆序松开 |
| `tapCombo(String primaryKey, String comboKey, Int primaryKeepMs, Int comboKeepMs)` | Bool | 按住主键期间点按一次组合键（如 Ctrl+C） |
| `inputText(String content)` | Bool | 注入一段文本 |

- 都走「当前可用注入器」（见 [06 注入与按键码](06-注入与按键码.md)）。
- 未知按键名抛「未知按键名 xxx」；`tapGroup` 空列表抛「按键列表不能为空」。

### 1.4 触发状态

| 函数 | 类型 | 说明 |
|---|---|---|
| `isTriggerDown(String id)` | Bool | 该快捷键当前是否处于激活（按下/开关开/压力板达标）状态 |
| `isTriggerUp(String id)` | Bool | 该快捷键当前是否处于松开状态（=`!isTriggerDown`） |

- 由宿主在触发/松开时更新，供脚本在 `onTriggerDown` 循环里判断是否该退出（见 [05 事件系统与触发模型](05-事件系统与触发模型.md)、[07 从零写示例](07-从零写示例.md)）。

### 1.5 辅助

| 函数 | 类型 | 说明 |
|---|---|---|
| `sleep(Int millis)` | Void | 阻塞等待毫秒数（`<=0` 直接返回） |
| `showToast(String msg)` | Void | 悬浮窗 Toast 提示 |

### 1.6 常用组合范式

**① 单发**（BUTTON，按一下做一次）：

```java
export fn Void onTriggerDown() {
    if(!MioEngFun.isEngineReady()) { MioEngFun.showToast("注入未就绪"); return }
    MioEngFun.tap("KEYCODE_J", 5)          // 失败不抛错，介意就检查返回值
}
```

**② 连发**（按住期间反复执行，靠触发状态退出）：

```java
while(MioEngFun.isTriggerDown(ID)) {
    if(!MioEngFun.tap("KEYCODE_J", 5)) { MioEngFun.showToast("注入失败"); return }
    MioEngFun.sleep(25)                     // 节奏 = pressMs + sleep
    if(MioEngFun.isTriggerUp(ID)) break
}
```

**③ 按住型**（长按一个键、松开才结束——`keyDown` 必须自己配对 `keyUp`，包括异常路径）：

```java
MioEngFun.keyDown("KEYCODE_W")
while(MioEngFun.isTriggerDown(ID)) {
    MioEngFun.sleep(50)
    if(MioEngFun.isTriggerUp(ID)) break
}
MioEngFun.keyUp("KEYCODE_W")               // 收尾；提前 return 的分支里也要补
```

**④ 组合键**（有时序要求的用 `tapCombo`，别手写两次 `tap`）：

```java
MioEngFun.tapCombo("KEYCODE_CTRL_LEFT", "KEYCODE_C", 50, 30)   // Ctrl+C
MioEngFun.tapGroup(["KEYCODE_ALT_LEFT", "KEYCODE_F4"], 30)     // 同按同松
```

**⑤ 注入前探测**（未知键名会抛错，动态拼键名时先查）：

```java
String key = "KEYCODE_" + letter                // letter 来自配置/事件
if(MioEngFun.isKeyValid(key)) {
    MioEngFun.tap(key, 5)
}
```

## 2. MioGamepad（虚拟手柄）

依赖手柄通道（Shizuku/uinput），用前建议 `MioEngFun.isGamepadReady()` 检查。全部返回 `Void`。

| 函数 | 说明 |
|---|---|
| `setLeftStick(Int x, Int y)` | 左摇杆，`x/y ∈ -32767~32767`，越界自动截断 |
| `setRightStick(Int x, Int y)` | 右摇杆，同上 |
| `setTrigger(Int lt, Int rt)` | 扳机，`0~255` |
| `setDpad(Int x, Int y)` | 十字键，取值只取 `-1/0/1` |
| `pressButton(String button)` | 按下一个按钮 |
| `releaseButton(String button)` | 松开按钮 |

`button` 取值：`A/B/X/Y/LB/RB/LT/RT/SELECT/START/LS/RS` 以及 `DPAD_UP/DPAD_DOWN/DPAD_LEFT/DPAD_RIGHT`（也可写 `GAMEPAD_A` 等按键名，见 [06 注入与按键码](06-注入与按键码.md)）。未知按钮名仅记日志、不抛错。

```java
MioGamepad.setLeftStick(0, -32767)   // 左摇杆推满向上
MioGamepad.pressButton("A")
MioGamepad.releaseButton("A")
```

陷阱：

- `pressButton` / `releaseButton` **成对调用**——只按不松，按钮状态会保持按下（在循环/提前 return 的分支里尤其要保证释放，参照 [07 §7.2](07-从零写示例.md) 的守卫写法）。
- 全部返回 `Void` = **没有注入成功与否的回执**：先 `isGamepadReady()`，效果用游戏内反应验证。
- 摇杆/扳机数值与 `tap` 的键盘通道是两套语义，同一游戏的键位配置别混用。

## 3. MioSensor（旋转传感器）

读取姿态，基于游戏旋转矢量在悬浮窗服务中的 GyroController；未运行时返回 `0`。

| 函数 | 类型 | 说明 |
|---|---|---|
| `isSensorAvailable()` | Bool | 旋转传感器是否可用 |
| `getPitch()` | Float | 绝对姿态 pitch（弧度） |
| `getRoll()` | Float | 绝对姿态 roll（弧度） |
| `getYaw()` | Float | 绝对姿态 yaw（弧度） |
| `getDpitch()` | Float | 相对基准的 pitch 偏移（弧度，陀螺仪零点校准后的增量） |
| `getDyaw()` | Float | 相对基准的 yaw 偏移（弧度） |

> 没有 `getDroll`：当前版本相对基准的 roll 偏移恒为 0。
> 引擎同时保留了返回 Map 的 `Sensor.getOrientation` / `Sensor.getRelativeOrientation`（含 `pitch/roll/yaw`、`dPitch/dRoll/dYaw`），但插件侧包装以标量版本为准。

陷阱：

- 单位是**弧度**——要角度就 `pitch * 180 / 3.14159`（或 `System` 里没有现成转换，自己算）。
- `0` 有歧义：既可能是真实姿态，也可能是传感器**未运行**——先 `isSensorAvailable()`，再读数。
- 陀螺仪类数据有漂移，连续场景用 `getDpitch`/`getDyaw`（相对增量），不要拿绝对角做跨帧比较。

## 4. MioFloating（悬浮窗 UI）

全部 `Void`，全部切到主线程执行；**悬浮窗服务运行中**才有效果（`MioEngFun.showToast` 等价实现亦走此通道）。

| 函数 | 说明 |
|---|---|
| `showToast(String text)` | 悬浮窗 Toast（时长固定约 2 秒） |
| `setCrosshairVisible(Bool visible)` | 准心显隐（attach/detach） |
| `setCrosshairPosition(Int x, Int y)` | 准心位置（像素坐标）⚠️ 当前实现实际执行 `center()`：无论传什么都把准心居中 |
| `updateStatusBar(String text)` | 更新状态栏附加文字 |

### 4.1 使用示例

```java
// 触发时给用户反馈（showToast 与 MioFloating.showToast 等价）
MioFloating.showToast("连发已启动")

// 仪表：循环里刷新状态栏（每 500ms 一次，别每 10ms 刷）
MioFloating.updateStatusBar("节拍 " + System.intToString(count))

// 对局前后开关准心
MioFloating.setCrosshairVisible(true)
// …
MioFloating.setCrosshairVisible(false)
```

- 这些函数**没有返回值也无从判断成败**——悬浮窗服务没跑时静默无效。
- 从 WS/网络回调线程调用也安全（内部切主线程，[10 §10.9](10-WS服务与游戏联动.md)）。

## 5. MioConfig（配置声明，速查）

**完整详解见 [11 配置页与 HostData](11-配置页与HostData.md)**——控件总表、校验矩阵、保存即重启、`HostData.get` 读取、`onButton` 契约。此处只留签名速查。

在 `config` 模块导出的 `schema()` 里**一行一项**声明配置页内容。宿主打开配置页时执行 `schema()` 收集声明；窗口外调用（如 `loading`/`keyInit`）抛「必须在 config 模块的 schema() 中调用」。

> **前置声明**：`res/config.json` 的 `plugin.service[]` 必须加一项 `{ "name": "config", "tag": "CONFIG" }`（`name` = `config.dsl` 的包名，无包声明写叶文件名）。宿主按该声明决定是否调用 `schema()`；**不声明 = 不需要配置页**，Mio 点「编辑配置」只弹 MD3 提示，不会产生「函数不存在」错误。插件**禁用状态下也可正常打开/编辑配置页**。

| 函数 | 类型 | 说明 |
|---|---|---|
| `onSection(String title)` | Bool | 开始新分组（配置页顶部 Tab）；不调用则首项落入「配置」组 |
| `onSwitch(String key, String title, String subtitle, Bool value)` | Bool | 开关 |
| `onCheckbox(String key, String title, String subtitle, Bool value)` | Bool | 复选框 |
| `onText(String key, String title, String subtitle, String value, String hint, String inputType, Int min, Int max)` | Bool | 文本输入；`inputType` 可选 `""`/`int`/`decimal`/`digits`/`english` |
| `onRadio(String key, String title, String subtitle, [String] options, String value)` | Bool | 单选（`value` 默认选中项，可省略） |
| `onChip(String key, String title, String subtitle, [String] options, [String] selected)` | Bool | 多选（`selected` 默认选中列表，可省略） |
| `onProgress(String key, String title, String subtitle, Int value, Int max, String text)` | Bool | 进度环 |
| `onTextView(String key, String title, String subtitle, String body)` | Bool | 纯文本说明（不可编辑） |
| `onButton(String key, String title, String subtitle, String label)` | Bool | 按钮；点击回调插件导出的 `config.onButton(key)` |

- **一行一项、无 Map 字面量**；`subtitle` 及各类型尾部参数可省略（缺参按 `null` 传递）。
- `key` 必须非空且全插件唯一，重复注册返回 `false` 并记日志；`title` 为空抛「参数无效」。
- `inputType` 写错直接抛错（不再像旧式 Map 那样静默丢弃整项）。
- 返回 `Bool`：登记成功 `true`；`key` 重复 `false`。
- **已保存值回显免写**：宿主保存时直写插件 HostData，开配置页时宿主按 scheme 的 key 直读覆盖默认值。
  旧插件若仍导出 `config.values()` 则优先用它（兼容）；按钮回调仍走 `config.onButton(key)`。

```java
import mio.utils.MioConfig

export fn Void schema() {
    MioConfig.onSection("战斗")
    MioConfig.onSwitch("autoAttack", "自动攻击", "开启后按配置频率点按Q", false)
    MioConfig.onText("interval", "点按间隔", "毫秒", "25", "5~500", "int", 5, 500)
    MioConfig.onRadio("mode", "模式", "", ["快速", "均衡", "省电"], "快速")
    MioConfig.onButton("reset", "重置", "", "一键重置")
}
```

> 旧式「`schema()` 返回嵌套 `List<Map>`」仍兼容（宿主先收集桥声明，收不到再解析返回值），但新插件请用上表写法。

### 读取已保存配置（非 MioConfig，但配置页的另一半）

配置页保存后的值由宿主写入插件 HostData，业务代码用包装模块 **`mio/utils/MioData.dsl`** 读（任何模块都可）：

```java
import mio.utils.MioData

MioData.get("demo.holdMs")               // 原值（未保存 → null）
MioData.getInt("demo.holdMs", 115)       // 带默认值：getOr/getBool/getInt/getDouble/getString
```

底层即宿主调用 `call("HostData.get")`（手写 `private fn Object readCfg(...)` 转发与之完全等价）。控件→保存类型、消费模式见 [11 §11.6](11-配置页与HostData.md)。

## 6. Ws（WebSocket 联动，速查）

**完整详解见 [10 WS 服务与游戏联动](10-WS服务与游戏联动.md)**——架构图、29 个事件、功能开关、聊天机器人示例、排障。包装模块 `mio/ws/MioWs.dsl`（`import mio.ws.MioWs`），23 个桥全部转发宿主 `Ws.*`：

| 分类 | 插件侧函数（= 宿主桥 `Ws.*`） |
|---|---|
| 状态 | `isWsRunning()` `isWsConnected()` `wsPort()` `wsConnectCommand()` `wsPlayerNames()` `wsPlayers()` |
| 启停 | `wsStart()` `wsStartWithPort(Int port)` `wsStop()` |
| 发送 | `sendCommand(String cmd)` → **requestId 或 null**（无响应回调） · `sendChat` `sendTell` `sendToAll` `sendTitle` `sendActionbar` `sendTellraw` `sendChatAs` `sendMe` |
| 声音/标题 | `playSound(sound, target, volume, pitch)` `stopSound(player, sound)` `sendTitleTimes(player, in, stay, out)` `clearTitle(player)` |
| 功能开关 | `registerFeature(key, name, desc)`（**必须在 `serverInit()` 内调用**） |

入口：`serverInit()`、`on<事件名>(String json)`、`onWsFeatureToggle(key, enabled)`——全部 `export`，声明 `WS_SERVICE` 后宿主才调。

## 7. 优先级建议

- 能先查再动：写剧本前先 `isEngineReady()` / `isImeReady()` 等做通道守卫，失败时 `showToast` 提示并 `return`（样例见 [07 从零写示例](07-从零写示例.md)）。
- 需要「按住期间反复执行」→ `tap` + `sleep` 循环，用 `isTriggerUp(id)` 退出。
- 需要「组合宏」→ `tapGroup` / `tapCombo`。
- 需要用户可调参数 → [11 配置页](11-配置页与HostData.md)；需要游戏侧联动 → [10 WS 服务](10-WS服务与游戏联动.md)。

## 8. 权限与生命周期（速查）

**权限**（包装 `dsl/System.dsl`，宿主桥 `Permission.*`，实现见 `PermissionBridge.java`）：

| 函数 | 返回 | 说明 |
|---|---|---|
| `checkPermission(String name)` | Bool | 是否已授权（`Permission.check`） |
| `permissionStatus()` | Map | `{declared, granted, missing}` 三列表 |
| `permissionStatus(String name)` | Map | 单项 `{name, declared, granted, description, level, dangerous}` |
| `requestPermission(String name, Callback cb)` | Bool | 弹单权限授权框；**插件线程调用同步等待**，cb 收 Bool |
| `requestPermissions([String] names, Callback cb)` | Bool | 弹插件授权 BottomSheet，cb 收本次保存授权数（Int） |

**生命周期与检测更新**（`main.dsl` 导出，宿主 `callIfExported` 回调，未导出跳过）：

| 入口 | 触发 |
|---|---|
| `onStart()` | 插件包服务全部启动后（悬浮窗加载时） |
| `onEnable()` / `onDisable()` | 插件页启用/禁用开关切换（disable 在句柄关闭前） |
| `onUninstall()` | 插件页删除确认后、关句柄/引擎卸载前 |
| `checkUpdate()` | 插件页「检查更新」按钮（后台线程调用，可联网）。返回 JSON 字符串/Map `{hasUpdate, version, versionCode, url, description}`，宿主弹窗并按 `url` 下载 |

入口全集见 [09 附录](09-附录.md) §C-5；宿主调用时序见 [01 §1.4](01-总览与原理.md)。