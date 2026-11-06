package io.takamiya.mio.dsl.bridge;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;

import cn.fsy.plugin.kore.library.BridgeHandler;
import cn.fsy.plugin.kore.library.KoreException;
import cn.fsy.plugin.kore.library.internal.manage.open.OpenManager;
import cn.fsy.plugin.kore.library.plugin.PluginItem;
import cn.fsy.plugin.kore.library.KoreEngine;
import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.R;
import io.takamiya.mio.app.Mio;
import io.takamiya.mio.bean.ControlActionItem;
import io.takamiya.mio.config.Action;
import io.takamiya.mio.config.ActionItem;
import io.takamiya.mio.config.ActionType;
import io.takamiya.mio.config.KeyMapping;
import io.takamiya.mio.config.TriggerModel;
import io.takamiya.mio.gamepad.GamepadEngine;
import io.takamiya.mio.injector.CpsTracker;
import io.takamiya.mio.injector.ImeKeyInjector;
import io.takamiya.mio.injector.InjectionException;
import io.takamiya.mio.injector.KeyInjector;
import io.takamiya.mio.injector.SafeKeyInjector;
import io.takamiya.mio.injector.ShizukuKeyInjector;
import io.takamiya.mio.shizuku.ShizukuHelper;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.InputModelManager;
import io.takamiya.mio.utils.RegistryUtils;

/**
 * MioEngFun 命名空间桥接实现。
 * 提供映射引擎核心能力：快捷键注册、状态查询、按键/文本注入、触发状态、辅助工具。
 *
 * 约定：
 * - 动作类函数返回 Boolean 表示是否执行成功（注入器不可用/注入异常返回 false）；
 * - 查询类按真实类型返回（Bool/Int/String）；
 * - 参数缺失或类型错误抛出 {@link KoreException(INVALID_ARGUMENT)}，由执行层统一记录。
 */
public class MioEngFunBridge {

    private static final String TAG = "MioEngFunBridge";

    private final Context context;
    private final Handler mainHandler;
    private final KeyInjector shizukuInjector;

    /** 缓存当前可用的注入器，避免每次调用都重新检测 */
    private volatile KeyInjector cachedInjector;
    private volatile long cachedInjectorCheckTime;
    private static final long INJECTOR_CACHE_TTL = 3000L;

    /** id → ControlActionItem 映射，用于 isTriggerDown/Up 与快捷键面板 */
    private final Map<String, ControlActionItem> registeredShortcuts = new ConcurrentHashMap<>();

    /** id → engaged 状态（由 ShortcutKeyDispatcher 更新） */
    private final Map<String, Boolean> triggerStates = new ConcurrentHashMap<>();

    /** actionId → 注册该快捷键的服务名（onKeyInit 时由当前调用线程捕获） */
    private final Map<String, String> shortcutOwners = new ConcurrentHashMap<>();

    /** actionId → 注册该快捷键的插件包名（onKeyInit 时由当前调用线程捕获） */
    private final Map<String, String> shortcutPackages = new ConcurrentHashMap<>();

    /** 当前正在执行 keyInit 的服务名（由 DslPluginHandle.ServiceThread 设置，仅注册线程可读） */
    private final ThreadLocal<String> currentShortcutOwner = new ThreadLocal<>();

    /** 当前正在执行 keyInit/serverInit 的插件包名（由 DslPluginHandle.ServiceThread 设置） */
    private final ThreadLocal<String> currentShortcutPackage = new ThreadLocal<>();

    /** 实体键绑定：规范化键名（如 "F6"/"NUMPAD_7"）→ actionId 列表（一个键可绑多个动作） */
    private final Map<String, List<String>> physicalBindings = new ConcurrentHashMap<>();

    /** 实体键绑定：actionId → 规范化键名（清理/换绑用） */
    private final Map<String, String> physicalKeyById = new ConcurrentHashMap<>();

    // ==================== 重复快捷键 id 检测 ====================

    private static final String DUP_CHANNEL_ID = "dsl_duplicate_id";
    private static final int DUP_NOTE_BASE = 3000;
    private static volatile boolean dupChannelCreated;
    /** 每个冲突 id 每次进程运行只发一次系统通知（日志每次都写） */
    private final Set<String> notifiedDuplicateIds = ConcurrentHashMap.newKeySet();

    public MioEngFunBridge(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.shizukuInjector = new SafeKeyInjector(new ShizukuKeyInjector());
    }

    /** 获取当前可用的注入器（根据用户选择的模式） */
    private KeyInjector getAvailableInjector() {
        long now = System.currentTimeMillis();
        KeyInjector cached = cachedInjector;
        if (cached != null && (now - cachedInjectorCheckTime) < INJECTOR_CACHE_TTL) {
            return cached;
        }

        int model = InputModelManager.MODEL_IME;
        try {
            Registry reg = RegistryUtils.get();
            if (reg != null && reg.exists(DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue())) {
                model = reg.getInt(DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue());
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "getAvailableInjector: read model failed: " + e.getMessage());
        }

        KeyInjector injector = null;
        if (model == InputModelManager.MODEL_SHIZUKU) {
            try {
                if (shizukuInjector.isAvailable()) injector = shizukuInjector;
            } catch (Exception e) {
                AppLogger.e(TAG, "getAvailableInjector: Shizuku error: " + e.getMessage());
            }
        } else {
            try {
                ImeKeyInjector ime = new ImeKeyInjector(context);
                if (ime.isAvailable()) injector = ime;
            } catch (Exception e) {
                AppLogger.e(TAG, "getAvailableInjector: IME error: " + e.getMessage());
            }
        }

        if (injector != null) {
            cachedInjector = injector;
            cachedInjectorCheckTime = now;
        } else {
            AppLogger.w(TAG, "getAvailableInjector: no injector available for model=" + model);
        }
        return injector;
    }

    /** 获取已注册的快捷键列表（供 DslPluginManager 读取） */
    public Map<String, ControlActionItem> getRegisteredShortcuts() {
        return registeredShortcuts;
    }

    /** 更新触发器状态（由 ShortcutKeyDispatcher 回调） */
    public void updateTriggerState(String id, boolean engaged) {
        triggerStates.put(id, engaged);
    }

    public void clearTriggerState(String id) {
        triggerStates.remove(id);
    }

    public void clearAll() {
        registeredShortcuts.clear();
        triggerStates.clear();
        shortcutOwners.clear();
        shortcutPackages.clear();
        physicalBindings.clear();
        physicalKeyById.clear();
        currentShortcutOwner.remove();
        currentShortcutPackage.remove();
    }

    /** 清除指定插件注册的全部快捷键（插件关闭/重载时调用，允许重载后重新注册） */
    public void clearPackage(String packageName) {
        if (packageName == null) return;
        for (Map.Entry<String, String> e : shortcutPackages.entrySet()) {
            if (packageName.equals(e.getValue())) {
                String id = e.getKey();
                registeredShortcuts.remove(id);
                triggerStates.remove(id);
                shortcutOwners.remove(id);
                shortcutPackages.remove(id);
                removePhysicalBinding(id);
            }
        }
    }

    /** 由 ServiceThread 在调用 keyInit 前设置，标识当前注册快捷键所属的服务 */
    public void setCurrentShortcutOwner(String serviceName) {
        if (serviceName == null) {
            currentShortcutOwner.remove();
        } else {
            currentShortcutOwner.set(serviceName);
        }
    }

    /** 查询快捷键注册归属的服务名（未注册/未知返回 null） */
    public String getShortcutOwner(String actionId) {
        return shortcutOwners.get(actionId);
    }

    /** 查询 actionId 注册时所属的插件包名（未注册/未知返回 null；用于跨插件触发路由过滤） */
    public String getShortcutPackage(String actionId) {
        return actionId == null ? null : shortcutPackages.get(actionId);
    }

    /** 实体键绑定查询：规范化键名 → actionId 列表（无绑定返回 null） */
    public List<String> getPhysicalIdsByKey(String normalizedKey) {
        return normalizedKey == null ? null : physicalBindings.get(normalizedKey);
    }

    /** 解除指定 actionId 的实体键绑定（无绑定时空操作） */
    private void removePhysicalBinding(String id) {
        String key = physicalKeyById.remove(id);
        if (key == null) return;
        List<String> ids = physicalBindings.get(key);
        if (ids != null) {
            ids.remove(id);
            if (ids.isEmpty()) {
                physicalBindings.remove(key, ids);
            }
        }
    }

    /** 由 ServiceThread 在调用 keyInit/serverInit 前设置，标识当前注册所用的插件包名 */
    public void setCurrentShortcutPackage(String packageName) {
        if (packageName == null) {
            currentShortcutPackage.remove();
        } else {
            currentShortcutPackage.set(packageName);
        }
    }

    /** 当前正在初始化的服务名（仅注册线程可读，其他线程 null） */
    public String getCurrentShortcutOwner() {
        return currentShortcutOwner.get();
    }

    /** 当前正在初始化的插件包名（仅注册线程可读，其他线程 null） */
    public String getCurrentShortcutPackage() {
        return currentShortcutPackage.get();
    }

    // ==================== 快捷键注册 ====================

    /** onKeyInit - 注册快捷键；返回是否注册成功 */
    public BridgeHandler onKeyInit() {
        return (args) -> {
            String title = str(args, 0, "onKeyInit");
            String triggerModelStr = str(args, 1, "onKeyInit");
            String id = str(args, 2, "onKeyInit");
            String description = str(args, 3, "onKeyInit");
            String group = optStr(args, 4);
            // 可选尾参：卡片尺寸(dp)与背景引用（"@res/a.9.png" 或 "@res/a.9.png|@res/a_active.9.png"）
            Integer widthDp = optIntArg(args, 5);
            Integer heightDp = optIntArg(args, 6);
            String bgRef = optStr(args, 7);
            String pkg = currentShortcutPackage.get();

            String bgNormal = null;
            String bgActive = null;
            if (bgRef != null) {
                int sep = bgRef.indexOf('|');
                bgNormal = resolveCardBg(pkg, sep >= 0 ? bgRef.substring(0, sep).trim() : bgRef);
                if (sep >= 0) {
                    String act = bgRef.substring(sep + 1).trim();
                    if (!act.isEmpty()) bgActive = resolveCardBg(pkg, act);
                }
            }

            TriggerModel triggerModel;
            if ("BUTTON".equals(triggerModelStr)
                    || "SWITCH".equals(triggerModelStr)
                    || "PRESSURE_PLATE".equals(triggerModelStr)) {
                triggerModel = TriggerModel.fromString(triggerModelStr);
            } else {
                throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                        "onKeyInit: 未知触发模式 " + triggerModelStr + "（可选 BUTTON/SWITCH/PRESSURE_PLATE）");
            }
            if (id.isEmpty()) {
                throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT, "onKeyInit: id 不能为空");
            }

            ActionItem actionItem = new ActionItem();
            actionItem.setTitle(title);
            actionItem.setDescription(description);
            actionItem.setId(id);
            actionItem.setTriggerModel(triggerModel);

            Action placeholderAction = new Action();
            placeholderAction.setType(ActionType.INPUT_KEY);
            placeholderAction.setTarget(Action.ActionTarget.KEYBOARD);
            placeholderAction.setKey("KEYCODE_UNKNOWN");
            List<Action> actions = new ArrayList<>();
            actions.add(placeholderAction);
            actionItem.setAction(actions);

            ControlActionItem item = new ControlActionItem();
            item.setPackageName("io.takamiya.mio.dsl");
            item.setActionId(id);
            item.setTitle(title);
            item.setDescription(description);
            item.setEnabled(true);
            item.setTriggerModel(triggerModel);
            item.setGroup(group);
            item.setCardWidthDp(widthDp != null && widthDp > 0 ? widthDp : null);
            item.setCardHeightDp(heightDp != null && heightDp > 0 ? heightDp : null);
            item.setCardBgNormal(bgNormal);
            item.setCardBgActive(bgActive);
            item.setActionItem(actionItem);
            item.setSource("插件");
            item.setName(resolvePluginName(pkg));

            ControlActionItem existing = registeredShortcuts.putIfAbsent(id, item);
            if (existing != null) {
                onDuplicateShortcutId(id, existing, title);
                return false;
            }
            triggerStates.put(id, false);
            String owner = currentShortcutOwner.get();
            if (owner != null) {
                shortcutOwners.put(id, owner);
            }
            if (pkg != null) {
                shortcutPackages.put(id, pkg);
            }

            AppLogger.i(TAG, "onKeyInit: registered id=" + id + " title=" + title);
            return true;
        };
    }

    /**
     * onPhysicalKeyInit - 注册实体键绑定（外接键鼠监听 {@code InputMonitorEngine} 触发）。
     * <p>与 onKeyInit 的区别：<b>不进 {@code registeredShortcuts}</b>，因此悬浮面板/控制面板
     * 不显示该卡片；但复用 {@code shortcutOwners}/{@code shortcutPackages}/{@code triggerStates}
     * 做归属路由与触发状态（与卡片共用全局 id 空间，id 需自行避免与卡片冲突）。
     * <p>keyCode 形如 "F6"/"NUMPAD_7"/"BUTTON_1"（可带 "KEYCODE_" 前缀，大小写不敏感）；
     * 空串/空白 = 解绑（返回 false）。triggerModel 取值同 onKeyInit（BUTTON/SWITCH/PRESSURE_PLATE）。
     */
    public BridgeHandler onPhysicalKeyInit() {
        return (args) -> {
            String rawKey = str(args, 0, "onPhysicalKeyInit");
            String triggerModelStr = str(args, 1, "onPhysicalKeyInit");
            String id = str(args, 2, "onPhysicalKeyInit");

            if ("BUTTON".equals(triggerModelStr)
                    || "SWITCH".equals(triggerModelStr)
                    || "PRESSURE_PLATE".equals(triggerModelStr)) {
                // 与 onKeyInit 同一取值校验；触发执行链按缺省 BUTTON 解析即无差异
                TriggerModel.fromString(triggerModelStr);
            } else {
                throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                        "onPhysicalKeyInit: 未知触发模式 " + triggerModelStr + "（可选 BUTTON/SWITCH/PRESSURE_PLATE）");
            }
            if (id.isEmpty()) {
                throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT, "onPhysicalKeyInit: id 不能为空");
            }

            String trimmed = rawKey.trim();
            if (trimmed.isEmpty()) {
                // 配置页清空触发键 = 解绑
                removePhysicalBinding(id);
                AppLogger.i(TAG, "onPhysicalKeyInit: key 为空，解绑 id=" + id);
                return false;
            }
            String key = normalizePhysicalKey(trimmed);
            if (key == null) {
                AppLogger.w(TAG, "onPhysicalKeyInit: 非法键名 " + rawKey + "（id=" + id + "）");
                return false;
            }

            // 重复 id 检测：与卡片注册共用全局 id 空间
            ControlActionItem existing = registeredShortcuts.get(id);
            if (existing != null) {
                onDuplicateShortcutId(id, existing, key);
                return false;
            }
            if (shortcutOwners.containsKey(id)) {
                AppLogger.e(TAG, "onPhysicalKeyInit: duplicate id=" + id
                        + " existing(owner=" + shortcutOwners.get(id)
                        + ", pkg=" + shortcutPackages.get(id) + ", key=" + physicalKeyById.get(id) + ")"
                        + " vs new(key=" + key + ", owner=" + currentShortcutOwner.get()
                        + ", pkg=" + currentShortcutPackage.get() + ")");
                return false;
            }

            String oldKey = physicalKeyById.get(id);
            if (key.equals(oldKey)) {
                return true; // 同键重复注册，幂等
            }
            if (oldKey != null) {
                removePhysicalBinding(id); // 换绑：先摘旧键
            }

            triggerStates.put(id, false);
            String owner = currentShortcutOwner.get();
            if (owner != null) {
                shortcutOwners.put(id, owner);
            } else {
                AppLogger.w(TAG, "onPhysicalKeyInit: 未捕获服务归属（owner=null），id=" + id);
            }
            String pkg = currentShortcutPackage.get();
            if (pkg != null) {
                shortcutPackages.put(id, pkg);
            }
            physicalKeyById.put(id, key);
            physicalBindings.computeIfAbsent(key, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(id);

            AppLogger.i(TAG, "onPhysicalKeyInit: bound id=" + id + " key=" + key
                    + " owner=" + owner + " pkg=" + pkg);
            return true;
        };
    }

    /**
     * 规范化实体键名：trim、去 "KEYCODE_" 前缀、转大写；仅允许 [A-Z0-9_]。
     * 非法（空/含其它字符）返回 null。
     */
    private static String normalizePhysicalKey(String raw) {
        if (raw == null) return null;
        String k = raw.trim();
        if (k.regionMatches(true, 0, "KEYCODE_", 0, "KEYCODE_".length())) {
            k = k.substring("KEYCODE_".length());
        }
        k = k.toUpperCase(Locale.ROOT);
        if (k.isEmpty()) return null;
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) return null;
        }
        return k;
    }

    /** 可选整数参数：缺失/空返回 null；非整数记警告并按缺失处理（不中断注册） */
    private static Integer optIntArg(Object[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) return null;
        Object o = args[index];
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            String s = o.toString().trim();
            if (s.isEmpty()) return null;
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            AppLogger.w(TAG, "onKeyInit: 第 " + (index + 1) + " 个参数应为整数，收到 " + o);
            return null;
        }
    }

    /**
     * 由插件包名取显示名（build.config.json 的 {@code plugin.name}，如 "CuO • 实体键"）。
     * 包名未知/未安装/查询失败时回退：包名本身（再空则 "插件"）。
     */
    private static String resolvePluginName(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "插件";
        try {
            PluginItem info = KoreEngine.pluginInfo(pkg);
            String name = info != null ? info.getPluginName() : null;
            if (name != null && !name.isEmpty()) return name;
        } catch (Throwable t) {
            AppLogger.w(TAG, "resolvePluginName failed for " + pkg + ": " + t.getMessage());
        }
        return pkg;
    }

    /**
     * 解析卡片背景引用 "@res/xxx.9.png" → {@code <pluginDir>/res/xxx.9.png} 绝对路径。
     * <p>引用非法（非 @res/ 前缀、路径穿越）或文件缺失返回 null（回落宿主默认背景）。
     * res/ 资源在安装时由宿主 PluginResInstaller 从 zip 补抽落地，新增资源后需重新安装插件。</p>
     */
    private String resolveCardBg(String pkg, String ref) {
        if (ref == null || ref.isEmpty()) return null;
        if (!ref.startsWith("@res/")) {
            AppLogger.w(TAG, "onKeyInit: 卡片背景必须以 @res/ 开头: " + ref);
            return null;
        }
        String rel = ref.substring("@res/".length());
        if (rel.isEmpty() || rel.startsWith("/") || rel.contains("..")) {
            AppLogger.w(TAG, "onKeyInit: 非法资源路径: " + ref);
            return null;
        }
        if (pkg == null || pkg.isEmpty()) {
            AppLogger.w(TAG, "onKeyInit: 无法解析资源（插件包名未知）: " + ref);
            return null;
        }
        try {
            File resDir = new File(OpenManager.pluginDir(context.getFilesDir(), pkg), "res");
            File f = new File(resDir, rel);
            if (!f.isFile()) {
                AppLogger.w(TAG, "onKeyInit: 资源文件不存在: " + f
                        + "（新增 res/ 资源后需重新安装插件）");
                return null;
            }
            return f.getAbsolutePath();
        } catch (Exception e) {
            AppLogger.w(TAG, "onKeyInit: 资源解析失败 " + ref + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 重复快捷键 id 检测：onKeyInit 注册时命中已存在的 id。
     * 写入 error 级 AppLogger 日志（含双方 title/服务/包名），并发送系统通知
     * （同一 id 每次进程运行只通知一次，避免服务重启刷屏）。注册被拒绝，不覆盖已有项。
     */
    private void onDuplicateShortcutId(String id, ControlActionItem existing, String newTitle) {
        String owner = shortcutOwners.get(id);
        String pkg = shortcutPackages.get(id);
        String newOwner = currentShortcutOwner.get();
        String newPkg = currentShortcutPackage.get();
        AppLogger.e(TAG, "onKeyInit: duplicate id=" + id
                + " existing(title=" + existing.getTitle() + ", owner=" + owner + ", pkg=" + pkg + ")"
                + " vs new(title=" + newTitle + ", owner=" + newOwner + ", pkg=" + newPkg + ")");

        try {
            if (!notifiedDuplicateIds.add(id)) return;
            ensureDupChannel();
            String existingDesc = describeReg(existing.getTitle(), owner, pkg);
            String newDesc = describeReg(newTitle, newOwner, newPkg);
            String content = String.format(Locale.CHINA,
                    context.getString(R.string.dsl_duplicate_id_notification_content), id, existingDesc, newDesc);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DUP_CHANNEL_ID)
                    .setSmallIcon(R.drawable.mio)
                    .setContentTitle(context.getString(R.string.dsl_duplicate_id_notification_title))
                    .setContentText(String.format(Locale.CHINA,
                            context.getString(R.string.dsl_duplicate_id_notification_content),
                            id, existingDesc, newDesc))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(DUP_NOTE_BASE + Math.abs(id.hashCode() % 1000), builder.build());
            }
        } catch (Exception ex) {
            AppLogger.w(TAG, "notifyDuplicateShortcutId failed: " + ex.getMessage());
        }
    }

    private static String describeReg(String title, String owner, String pkg) {
        return String.format(Locale.CHINA, "%s（服务=%s，插件=%s）",
                title == null || title.isEmpty() ? "(无标题)" : title,
                owner == null ? "?" : owner,
                pkg == null ? "?" : pkg);
    }

    private void ensureDupChannel() {
        if (dupChannelCreated) return;
        synchronized (MioEngFunBridge.class) {
            if (dupChannelCreated) return;
            try {
                NotificationChannel channel = new NotificationChannel(
                        DUP_CHANNEL_ID,
                        context.getString(R.string.dsl_duplicate_id_channel_name),
                        NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription(context.getString(R.string.dsl_duplicate_id_channel_desc));
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.createNotificationChannel(channel);
                    dupChannelCreated = true;
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "ensureDupChannel failed: " + e.getMessage());
            }
        }
    }

    // ==================== 状态查询 ====================

    /** getEngineVersion - 映射引擎版本号 */
    public BridgeHandler getEngineVersion() {
        return (args) -> Mio.APP_CONFIG_ENGINE;
    }

    /** isImeReady - 输入法(IME)注入通道是否就绪 */
    public BridgeHandler isImeReady() {
        return (args) -> {
            try {
                return new ImeKeyInjector(context).isAvailable();
            } catch (Exception e) {
                AppLogger.e(TAG, "isImeReady error: " + e.getMessage());
                return false;
            }
        };
    }

    /** isShizukuReady - Shizuku 注入通道是否已授权可用 */
    public BridgeHandler isShizukuReady() {
        return (args) -> ShizukuHelper.getInstance().isGranted();
    }

    /** isMapReady - 键盘映射通道是否可用 */
    public BridgeHandler isMapReady() {
        return (args) -> shizukuInjector.isAvailable();
    }

    /** isGamepadReady - 虚拟手柄是否已开启 */
    public BridgeHandler isGamepadReady() {
        return (args) -> GamepadEngine.get(context).isOpen();
    }

    /** isSensorReady - 旋转传感器是否可用 */
    public BridgeHandler isSensorReady() {
        return (args) -> {
            android.hardware.SensorManager sm = (android.hardware.SensorManager)
                    context.getSystemService(Context.SENSOR_SERVICE);
            if (sm == null) return false;
            android.hardware.Sensor s = sm.getDefaultSensor(android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (s == null) s = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR);
            return s != null;
        };
    }

    /** isEngineReady - 引擎整体是否就绪（任一注入通道可用或手柄已开启） */
    public BridgeHandler isEngineReady() {
        return (args) -> getAvailableInjector() != null || GamepadEngine.get(context).isOpen();
    }

    /** getInputModel - 当前注入模式：0=IME 1=Shizuku 2=Gamepad -1=无 */
    public BridgeHandler getInputModel() {
        return (args) -> {
            int model = InputModelManager.MODEL_IME;
            try {
                Registry reg = RegistryUtils.get();
                if (reg != null && reg.exists(DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue())) {
                    model = reg.getInt(DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "getInputModel: read model failed: " + e.getMessage());
            }
            return model;
        };
    }

    /** isKeyValid - 按键名是否合法（如 "KEYCODE_Q"） */
    public BridgeHandler isKeyValid() {
        return (args) -> KeyMapping.getKeyCode(str(args, 0, "isKeyValid")) != 0;
    }

    // ==================== 按键注入 ====================

    /** DSL 按键动作计入与快捷栏共用的 CPS 计数器（CpsTracker）。 */
    private void countDslInject(int charCount) {
        if (charCount <= 0) return;
        try {
            io.takamiya.mio.floating.FloatingWindowService svc =
                    io.takamiya.mio.floating.FloatingWindowService.getInstance();
            if (svc == null) return;
            io.takamiya.mio.floating.ShortcutKeyDispatcher dispatcher = svc.getShortcutDispatcher();
            if (dispatcher == null) return;
            CpsTracker tracker = dispatcher.getCpsTracker();
            if (tracker != null) {
                tracker.recordDslInject(charCount);
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "countDslInject failed: " + e.getMessage());
        }
    }

    /** keyDown - 按住按键不放（配合 keyUp 使用） */
    public BridgeHandler keyDown() {
        return (args) -> {
            String keyName = str(args, 0, "keyDown");
            int keyCode = requireKeyCode(keyName, "keyDown");
            KeyInjector injector = getAvailableInjector();
            if (injector == null) {
                AppLogger.e(TAG, "keyDown: no injector available");
                return false;
            }
            try {
                injector.keyDown(keyCode);
                countDslInject(1);
                return true;
            } catch (InjectionException e) {
                AppLogger.e(TAG, "keyDown failed: " + e.getMessage());
                return false;
            }
        };
    }

    /** keyUp - 松开按键 */
    public BridgeHandler keyUp() {
        return (args) -> {
            String keyName = str(args, 0, "keyUp");
            int keyCode = requireKeyCode(keyName, "keyUp");
            KeyInjector injector = getAvailableInjector();
            if (injector == null) {
                AppLogger.e(TAG, "keyUp: no injector available");
                return false;
            }
            try {
                injector.keyUp(keyCode);
                return true;
            } catch (InjectionException e) {
                AppLogger.e(TAG, "keyUp failed: " + e.getMessage());
                return false;
            }
        };
    }

    /** tap - 单击：按下 → 保持 pressMs → 松开 */
    public BridgeHandler tap() {
        return (args) -> {
            String keyName = str(args, 0, "tap");
            int pressMs = num(args, 1, "tap");
            int keyCode = requireKeyCode(keyName, "tap");
            KeyInjector injector = getAvailableInjector();
            if (injector == null) {
                AppLogger.e(TAG, "tap: no injector available");
                return false;
            }
            try {
                injector.keyDown(keyCode);
                if (pressMs > 0) Thread.sleep(pressMs);
                injector.keyUp(keyCode);
                countDslInject(1);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (InjectionException e) {
                AppLogger.e(TAG, "tap failed: " + e.getMessage());
                return false;
            }
        };
    }

    /** tapGroup - 按键组：全部按下 → 保持 pressMs → 逆序松开 */
    public BridgeHandler tapGroup() {
        return (args) -> {
            List<Object> keyNames = list(args, 0, "tapGroup");
            int pressMs = num(args, 1, "tapGroup");
            if (keyNames.isEmpty()) {
                throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT, "tapGroup: 按键列表不能为空");
            }
            int[] codes = new int[keyNames.size()];
            for (int i = 0; i < keyNames.size(); i++) {
                codes[i] = requireKeyCode(toString(keyNames.get(i)), "tapGroup");
            }
            KeyInjector injector = getAvailableInjector();
            if (injector == null) {
                AppLogger.e(TAG, "tapGroup: no injector available");
                return false;
            }
            try {
                for (int code : codes) injector.keyDown(code);
                Thread.sleep(Math.max(0, pressMs));
                for (int i = codes.length - 1; i >= 0; i--) injector.keyUp(codes[i]);
                countDslInject(codes.length);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (InjectionException e) {
                try {
                    for (int i = codes.length - 1; i >= 0; i--) injector.keyUp(codes[i]);
                } catch (InjectionException ignored) {
                }
                AppLogger.e(TAG, "tapGroup failed: " + e.getMessage());
                return false;
            }
        };
    }

    /** tapCombo - 组合键：按住主键期间点按一次组合键 */
    public BridgeHandler tapCombo() {
        return (args) -> {
            String primaryKey = str(args, 0, "tapCombo");
            String comboKey = str(args, 1, "tapCombo");
            int primaryKeepMs = num(args, 2, "tapCombo");
            int comboKeepMs = num(args, 3, "tapCombo");
            int primaryCode = requireKeyCode(primaryKey, "tapCombo");
            int comboCode = requireKeyCode(comboKey, "tapCombo");
            KeyInjector injector = getAvailableInjector();
            if (injector == null) {
                AppLogger.e(TAG, "tapCombo: no injector available");
                return false;
            }
            try {
                injector.keyDown(primaryCode);
                try {
                    injector.keyDown(comboCode);
                    Thread.sleep(Math.max(0, comboKeepMs));
                    injector.keyUp(comboCode);
                } finally {
                    Thread.sleep(Math.max(0, primaryKeepMs));
                    injector.keyUp(primaryCode);
                }
                countDslInject(1);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (InjectionException e) {
                AppLogger.e(TAG, "tapCombo failed: " + e.getMessage());
                return false;
            }
        };
    }

    /** inputText - 注入文本 */
    public BridgeHandler inputText() {
        return (args) -> {
            String content = str(args, 0, "inputText");
            KeyInjector injector = getAvailableInjector();
            if (injector == null) {
                AppLogger.e(TAG, "inputText: no injector available");
                return false;
            }
            try {
                injector.inputText(content);
                countDslInject(content != null ? content.length() : 0);
                return true;
            } catch (InjectionException e) {
                AppLogger.e(TAG, "inputText failed: " + e.getMessage());
                return false;
            }
        };
    }

    // ==================== 触发状态 ====================

    /** isTriggerDown - 快捷键是否处于按下状态 */
    public BridgeHandler isTriggerDown() {
        return (args) -> {
            String id = str(args, 0, "isTriggerDown");
            Boolean state = triggerStates.get(id);
            return state != null && state;
        };
    }

    /** isTriggerUp - 快捷键是否处于松开状态 */
    public BridgeHandler isTriggerUp() {
        return (args) -> {
            String id = str(args, 0, "isTriggerUp");
            Boolean state = triggerStates.get(id);
            return state == null || !state;
        };
    }

    // ==================== 辅助 ====================

    /** sleep - 阻塞等待 millis 毫秒 */
    public BridgeHandler sleep() {
        return (args) -> {
            int millis = num(args, 0, "sleep");
            if (millis <= 0) return null;
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        };
    }

    /** showToast - 悬浮窗 Toast 提示 */
    public BridgeHandler showToast() {
        return (args) -> {
            String msg = str(args, 0, "showToast");
            mainHandler.post(() -> {
                try {
                    io.takamiya.mio.floating.FloatingWindowService svc =
                            io.takamiya.mio.floating.FloatingWindowService.getInstance();
                    if (svc != null) {
                        svc.showToast(msg);
                    }
                } catch (Exception e) {
                    AppLogger.e(TAG, "showToast failed: " + e.getMessage());
                }
            });
            return null;
        };
    }

    // ==================== 参数解析（strict） ====================

    private static String str(Object[] args, int index, String fn) {
        if (args == null || index >= args.length || args[index] == null) {
            throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 缺少第 " + (index + 1) + " 个参数");
        }
        return args[index].toString();
    }

    /** 可选字符串参数：缺失/空串/null 返回 null，去除首尾空白 */
    private static String optStr(Object[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) {
            return null;
        }
        String s = args[index].toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static int num(Object[] args, int index, String fn) {
        if (args == null || index >= args.length || args[index] == null) {
            throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 缺少第 " + (index + 1) + " 个参数");
        }
        Object o = args[index];
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 第 " + (index + 1) + " 个参数必须为整数");
        }
    }

    private static List<Object> list(Object[] args, int index, String fn) {
        if (args == null || index >= args.length || args[index] == null) {
            throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 缺少第 " + (index + 1) + " 个参数");
        }
        Object o = args[index];
        if (o instanceof List) return new ArrayList<>((List<?>) o);
        if (o instanceof JSONArray) {
            JSONArray arr = (JSONArray) o;
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.opt(i));
            }
            return list;
        }
        if (o instanceof Object[]) {
            List<Object> list = new ArrayList<>();
            for (Object item : (Object[]) o) {
                list.add(item);
            }
            return list;
        }
        throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                fn + ": 第 " + (index + 1) + " 个参数必须为按键列表");
    }

    private static int requireKeyCode(String keyName, String fn) {
        int code = KeyMapping.getKeyCode(keyName);
        if (code == 0) {
            throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 未知按键名 " + keyName);
        }
        return code;
    }

    private static String toString(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
