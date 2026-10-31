# Mio 配置结构重构方案（ActionGroup）

> 实施参考文档。目标：`Mio.java` 的 `APP_CONFIG_ENGINE` 从 3 升级到 4，彻底重构配置结构，引入 ActionGroup，无旧格式兼容。

---

## 0. 核心决策

| 项 | 值 |
|----|----|
| `Mio.APP_CONFIG_ENGINE` | 3 → **4** |
| 安装拒绝1 | `minEngine > 4`（配置需更新引擎） |
| 安装拒绝2 | `minEngine < 4`（旧格式，直接拒绝安装） |
| 旧格式兼容 | **无**，ConfigLoader 只解析新格式 |
| 已装旧配置 | 列表显示"不适配"，不加载、不可编辑、enable 置灰、提供导出 |

---

## 1. 数据模型

```
Plugin
├── PluginConfig                    — 元数据
└── List<ActionItem> actionList
    └── ActionItem
        ├── 基础属性
        ├── List<ActionGroup> groups
        └── Map<ShortcutEventType, List<ActionGroup>> events
```

### PluginConfig（不变）
`name, packageName, description, author, updateUrl, versionName, versionCode, minEngine(必填4), enable, keyHoldDuration`

### ActionItem（改造）
| 属性 | 类型 | 说明 |
|------|------|------|
| title | String | 名称 |
| description | String | 描述 |
| id | String | 唯一ID |
| group | String | 分组标签 |
| triggerModel | TriggerModel | BUTTON/SWITCH/PRESSURE_PLATE |
| holdThresholdMs | Integer | 压力板激活阈值 |
| releaseDelayMs | Integer | 压力板松开延迟 |
| groups | List\<ActionGroup\> | **新** |
| events | Map\<ShortcutEventType, List\<ActionGroup\>\> | **改造** |

旧 `action` 字段删除。

### ActionGroup（★ 新增）
| 分类 | 属性 | 类型 | 说明 |
|------|------|------|------|
| 动作 | actions | List\<Action\> | 纯动作序列 |
| 循环 | loopMode | LoopMode | ONCE/UNLIMITED/COUNT/DURATION |
| | loopCount | Integer | COUNT 时次数 ≥1 |
| | loopDurationMs | Long | DURATION 时时长 |
| | loopIntervalMs | Long | 循环间隔，默认 40 |
| | stopCondition | StopRepeatingCondition | 停止条件 |
| 触发 | groupHoldMode | GroupHoldMode | NONE/TRIGGER/LONG_PRESS |
| | groupHoldDurationMs | Long | LONG_PRESS 时长 |

默认：loopMode=ONCE, loopIntervalMs=40, stopCondition=TRIGGER_RELEASE, groupHoldMode=NONE

### Action（精简）
| 属性 | 类型 | 说明 |
|------|------|------|
| type | ActionType | INPUT_KEY/GROUP/TEXT/TOAST |
| key | String | 按键名 |
| text | String | 文本 |
| combinationKeys | List\<String\> | 修饰键 |
| delayMs | Long | 步骤前延迟 |
| charDelayMs | Long | 逐字符延迟 |
| hold | Boolean | 按键按住不放 |
| holdDuration | Long | 按键持续时间，**与 hold 互斥** |
| toastSettings | ToastSettings | Toast 样式 |

移除：repeat, loopCount, longPress, keyHoldDuration, duplicateConfiguration, longPressSettings

### 枚举
```java
enum LoopMode { ONCE, UNLIMITED, COUNT, DURATION }         // 新
enum GroupHoldMode { NONE, TRIGGER, LONG_PRESS }            // 新
enum TriggerModel { BUTTON, SWITCH, PRESSURE_PLATE }        // 不变
enum ActionType { INPUT_KEY, INPUT_KEY_GROUP, INPUT_TEXT, TOAST }  // 不变
enum StopRepeatingCondition { TRIGGER_RELEASE, TRIGGER_ON_CLICK, REACH_LIMIT, FOLLOW_TRIGGER_STATE }  // 不变
enum ShortcutEventType { /* 15种，不变 */ }
```

---

## 2. hold / holdDuration 互斥

| hold | holdDuration | 效果 |
|------|--------------|------|
| true | 忽略 | 按键按下不放，group 结束才 keyUp |
| false | >0 | 按键持续 holdDuration ms |
| false | 0/空 | 普通点按 |

- 解析：hold=true 时忽略 holdDuration（或校验冲突抛错）
- UI：切换 hold 时清空 holdDuration，反之亦然
- 执行：hold=true 优先

---

## 3. JSON Schema（新格式唯一）

```json
{
  "plugin": {
    "config": {
      "name": "示例插件", "package": "com.example.demo",
      "description": "", "author": "", "updateUrl": "",
      "versionName": "1.0", "versionCode": 1,
      "minEngine": 4, "enable": true, "keyHoldDuration": 0
    },
    "actionList": [
      {
        "title": "abc循环", "description": "", "id": "abc_loop", "group": "",
        "triggerModel": "SWITCH", "holdThresholdMs": 0, "releaseDelayMs": 0,
        "groups": [
          {
            "loopMode": "UNLIMITED", "loopCount": 0, "loopDurationMs": 0,
            "loopIntervalMs": 50, "stopCondition": "TRIGGER_RELEASE",
            "groupHoldMode": "TRIGGER", "groupHoldDurationMs": 0,
            "actions": [
              { "type": "INPUT_KEY", "key": "KEYCODE_A", "hold": false, "holdDuration": 0 },
              { "type": "INPUT_KEY", "key": "KEYCODE_B", "hold": false, "holdDuration": 0 },
              { "type": "INPUT_KEY", "key": "KEYCODE_C", "hold": false, "holdDuration": 0 },
              { "type": "INPUT_KEY", "key": "KEYCODE_ENTER", "hold": false, "holdDuration": 0 }
            ]
          }
        ],
        "events": {
          "KEY_ON": [
            { "loopMode": "ONCE", "actions": [
              { "type": "INPUT_TEXT", "text": "开机提示", "hold": false, "holdDuration": 0 }
            ] }
          ]
        }
      }
    ]
  }
}
```

---

## 4. 触发器路由（ShortcutKeyDispatcher）

```
BUTTON
  ├─ groupHoldMode=NONE       → executeOnce（按 loopMode 执行）
  └─ groupHoldMode=LONG_PRESS → 长按 groupHoldDurationMs 后 → executeGroupOn

SWITCH / PRESSURE_PLATE
  ├─ 激活(ON)  → executeGroupOn
  └─ 关闭(OFF) → executeGroupOff
```

### ActionExecutor 核心方法
```
executeOnce(item)         — 遍历所有 group 各执行一次（含 loopMode 控制）
executeGroupOn(item)      — 启动 TRIGGER 模式 group 循环
executeGroupOff(item)     — 停止所有循环
executeEvent(item, type)  — 触发 events[type] 的 group
```

### group 执行逻辑
```
按 loopMode 决定循环次数：
  ONCE      → 执行 actions 一次
  UNLIMITED → while(触发器激活) 执行 actions
  COUNT     → 执行 actions 共 loopCount 次
  DURATION  → 执行直到达到 loopDurationMs
每次循环间隔 loopIntervalMs；每轮检查 stopCondition
actions 内部按顺序逐个执行
```

### Action 执行逻辑
```
先 sleep(delayMs)
再按 type 分支：
  INPUT_KEY / INPUT_KEY_GROUP
    ├─ hold=true → keyDown 保持（group结束才 keyUp）
    └─ 否则 → pressKey(key, holdDuration)
  INPUT_TEXT → inputText(text, 按 charDelayMs 逐字符)
  TOAST → 显示 toast
```

---

## 5. 不适配旧配置列表处理

需求：显示"不适配当前引擎"、不加载、不可编辑、enable 置灰禁用、提供导出（仅 JSON）。

### 实现步骤
1. `Plugin` 加 `boolean incompatible` 字段（默认 false）
2. `IPluginManager.list()` 对每个 `.json`/`.lhrb`：
   - 新格式 + minEngine>=4 + 解析成功 → 正常 Plugin
   - 新格式 + minEngine<4 → 解析成功但标记 incompatible
   - 无法解析（旧格式）→ 从 base64 文件名取包名，构造 incompatible stub Plugin
3. `bean/Config` 加 `incompatible` + `onExportClickListener`
4. `ConfigFragment.refreshList()` 检测 incompatible：
   - warning = "该配置不适配当前引擎（v4），无法加载与编辑"
   - 隐藏编辑按钮 + enable 开关置灰禁用
   - 显示导出按钮（仅非 LHRB / 非 locked）
5. `ConfigAdapter` + `item_configs.xml` 加 `btnExport`

### 导出逻辑
```
onExportClickListener（仅 JSON，非 LHRB）:
  raw = IPluginInstaller.readPluginRawFile(packageName)   // 读原始字节
  → 写 cacheDir/export/xxx.json
  → FileProvider.getUriForFile + ACTION_SEND → 分享面板
```

LHRB 不导出。`IPluginInstaller` 新增 `readPluginRawFile(packageName)` 返回原始文件字节。

---

## 6. 文件改动清单

### 版本/门槛
- `app/Mio.java` — APP_CONFIG_ENGINE 3→4
- `configManage/IPluginInstaller.java` — doInstall 增 minEngine<4 拒绝；新增 readPluginRawFile(); 不适配解析

### 新增模型
- `config/ActionGroup.java`
- `config/LoopMode.java`
- `config/GroupHoldMode.java`

### 重写模型
- `config/Action.java`
- `config/ActionItem.java`

### 解析/执行/调度
- `config/ConfigLoader.java`
- `config/ActionExecutor.java`
- `floating/ShortcutKeyDispatcher.java`

### 配置管理/列表
- `config/Plugin.java` — 加 incompatible
- `configManage/IPluginManager.java` — list() 不适配条目

### UI
- `bean/Config.java`
- `adapter/ConfigAdapter.java`
- `res/layout/item_configs.xml`
- `navigation/ConfigFragment.java`
- `adapter/ActionEventAdapter.java`
- `adapter/ActionItemAdapter.java`
- `VisualEditorActivity.java`
- `VisualEditorEventActivity.java`

### 删除
- `config/DuplicateConfiguration.java`
- `config/LongPressSettings.java`

### 不变
- `PluginConfig.java` `ActionType.java` `TriggerModel.java` `ShortcutEventType.java` `StopRepeatingCondition.java` `ToastSettings.java` `KeyMapping.java` `ConfigParseException.java` `bean/ControlActionItem.java`

---

## 7. 实现顺序

1. `Mio.java` 引擎版本 4
2. `IPluginInstaller` minEngine<4 拒绝
3. 枚举 LoopMode、GroupHoldMode
4. ActionGroup 模型
5. 精简 Action（hold/holdDuration 互斥）、改造 ActionItem
6. ConfigLoader 新解析+序列化
7. ActionExecutor group 执行
8. ShortcutKeyDispatcher 路由
9. Plugin incompatible + IPluginManager.list() 不适配
10. 列表 UI（Config bean / ConfigAdapter / item_configs / ConfigFragment 导出）
11. 编辑 UI（ActionEventAdapter / ActionItemAdapter / VisualEditorActivity / VisualEditorEventActivity）
12. 删除 DuplicateConfiguration、LongPressSettings
13. 编译验证

---

## 8. 注意事项

- LHRB 安装直接存源文件（`writeLhrb` 原始字节），不落地 JSON；读取时才 `LhrbReader.parse` 解码
- LHRB 格式层不受引擎版本影响，但是旧 JSON 内容新 ConfigLoader 解析会失败 → 靠 minEngine 门槛和 incompatible 标记处理
- 空白模板 `createBlankTemplate()` 的 minEngine 需改为 4
- ConfigEditActivity 保存走 `manager.install(json, true)`，会触发 minEngine 检查
