package io.takamiya.mio.building;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;

import java.util.List;
import java.util.function.Predicate;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.config.KeyMapping;
import io.takamiya.mio.injector.InjectionException;
import io.takamiya.mio.injector.KeyInjector;
import io.takamiya.mio.injector.KeyInjectorFactory;
import io.takamiya.mio.injector.KeyInjectorType;
import io.takamiya.mio.injector.MioAccessibilityService;
import io.takamiya.mio.injector.ServiceState;
import io.takamiya.mio.injector.ShizukuKeyInjector;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.InputModelManager;
import io.takamiya.mio.utils.RegistryUtils;

/**
 * 建筑方块命令执行器：读取导入设置页保存的点击坐标，
 * 组合「映射引擎按键/文本注入 + 无障碍坐标点击」逐步执行当前命令。
 *
 * <p>序列（每步之间延时 {@link #STEP_DELAY_MS}，任一步失败立即停止且不推进进度）：
 * <ol>
 *   <li>映射引擎按 E 键</li>
 *   <li>index==1：方块类型 → 重复；index&gt;1：方块类型 → 连锁 → 激活方式 → 保持开启</li>
 *   <li>命令编辑框 → 输入整条命令 → 关闭命令面板</li>
 * </ol>
 * 成功后进度自动 +1（封顶 total），不自动连续执行下一条。
 */
public final class BuildingCommandRunner {

    private static final String TAG = "BuildingCommandRunner";
    /** 执行时序默认值（导入设置页可覆盖，见 IMPORT_DELAY_* / IMPORT_PRESS_DURATION）。 */
    private static final long STEP_DELAY_MS = DataManage.DEFAULT_DELAY_STEP_MS;
    private static final long PANEL_OPEN_DELAY_MS = DataManage.DEFAULT_DELAY_PANEL_OPEN_MS;
    private static final long LIST_OPEN_DELAY_MS = DataManage.DEFAULT_DELAY_LIST_OPEN_MS;
    private static final long EDIT_FOCUS_DELAY_MS = DataManage.DEFAULT_DELAY_EDIT_FOCUS_MS;
    private static final long PRESS_DURATION_MS = DataManage.DEFAULT_PRESS_DURATION_MS;
    /** SQLite 方块命令 seq 为 1-based，与进度 index 一致。 */
    private static final int FIRST_SEQ = 1;

    /** 主线程回调。 */
    public interface Listener {
        /** 执行成功，进度已推进到 currentIndex（1-based）。 */
        void onProgress(int currentIndex, int total);

        /** 失败，进度停在当前条。 */
        void onFailed(String message);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BuildingStore store;

    private Listener listener;
    private String buildingName;
    private int total;
    /** 当前命令序号（1-based）。 */
    private int index = FIRST_SEQ;
    private volatile boolean running;
    private volatile boolean cancelled;
    /** 点击坐标是否落在某个悬浮窗（操作 chip）内的检查，每次执行最多告警一次。 */
    private Predicate<int[]> overlapChecker;
    private volatile boolean overlapWarned;

    /** 本次执行使用的时序（每次执行前从 Registry 读取，读不到回退默认）。 */
    private long delayStep = STEP_DELAY_MS;
    private long delayPanelOpen = PANEL_OPEN_DELAY_MS;
    private long delayListOpen = LIST_OPEN_DELAY_MS;
    private long delayEditFocus = EDIT_FOCUS_DELAY_MS;
    private long pressDuration = PRESS_DURATION_MS;

    public BuildingCommandRunner(Context context) {
        this.context = context;
        this.store = BuildingStore.get(context);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** 注入坐标遮挡检查（如落在操作 chip 窗口内则告警提示拖动）。 */
    public void setOverlapChecker(Predicate<int[]> checker) {
        this.overlapChecker = checker;
    }

    /** 绑定建筑（进度重置到第 1 条）。 */
    public void bind(String name, int commandCount) {
        this.buildingName = name;
        this.total = Math.max(0, commandCount);
        this.index = FIRST_SEQ;
        this.running = false;
        this.cancelled = false;
    }

    public int getIndex() {
        return index;
    }

    public int getTotal() {
        return total;
    }

    public boolean isRunning() {
        return running;
    }

    /** 上一步/下一步（只切不执行），返回切换后的序号。 */
    public int step(int delta) {
        if (running || total <= 0) return index;
        index = Math.min(total, Math.max(FIRST_SEQ, index + delta));
        return index;
    }

    /** 取消当前执行（步骤间生效）。 */
    public void cancel() {
        cancelled = true;
    }

    /** 主线程调用：异步执行当前命令。 */
    public void executeCurrent() {
        if (running || total <= 0 || buildingName == null) return;
        cancelled = false;
        overlapWarned = false;
        final String name = buildingName;
        final int seq = index;
        running = true;
        store.io(() -> {
            final String failure;
            try {
                failure = runSequence(name, seq);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                finish("已取消", seq);
                return;
            } catch (Throwable t) {
                AppLogger.e(TAG, "execute failed: " + t.getMessage(), t);
                finish("执行异常: " + t.getMessage(), seq);
                return;
            }
            finish(failure, seq);
        });
    }

    /** 回到主线程收尾。 */
    private void finish(String failure, int seq) {
        running = false;
        mainHandler.post(() -> {
            if (failure != null) {
                Listener l = listener;
                if (l != null && !failure.equals("已取消")) l.onFailed(failure);
                return;
            }
            if (cancelled) return;
            // 成功：进度推进（封顶 total）
            if (seq == index && total > 0) {
                index = Math.min(total, seq + 1);
            }
            Listener l = listener;
            if (l != null) l.onProgress(index, total);
        });
    }

    /** @return null 表示成功，否则为失败原因。 */
    private String runSequence(String name, int seq) throws InterruptedException {
        if (cancelled) return "已取消";
        loadTiming();

        String command;
        try {
            List<String> one = store.repository().getBlockCommands(name, seq - 1, 1);
            if (one.isEmpty()) return "第 " + seq + " 条命令不存在";
            command = one.get(0);
        } catch (Exception e) {
            AppLogger.w(TAG, "read command failed: " + e.getMessage());
            return "读取命令失败: " + e.getMessage();
        }
        if (command == null || command.isEmpty()) return "第 " + seq + " 条命令为空";

        int[][] coords = readCoords();
        if (coords == null || coords.length < 7) return "导入设置页坐标未填全，请先填写点击坐标";

        KeyInjector injector = createInjector();
        if (injector == null) return "映射引擎不可用，请检查输入设置";

        try {
            return runSteps(injector, coords, seq, command);
        } finally {
            restoreInjector(injector);
        }
    }

    private String runSteps(KeyInjector injector, int[][] coords, int seq, String command)
            throws InterruptedException {
        int eKey = KeyMapping.getKeyCode("KEYCODE_E");
        if (eKey == 0) eKey = KeyEvent.KEYCODE_E;

        // 1) E 键
        if (!pressKey(injector, eKey)) return cancelledOr("按 E 键失败");
        if (!sleepStep(delayPanelOpen)) return "已取消";

        // 2) 方块类型
        if (!click("方块类型", coords[0])) return cancelledOr("点击「方块类型」失败");
        if (!sleepStep(delayListOpen)) return "已取消";

        // 3) 类型选择：第 1 条=重复；其余=连锁 → 激活方式 → 保持开启
        if (seq == FIRST_SEQ) {
            if (!click("重复", coords[1])) return cancelledOr("点击「重复」失败");
        } else {
            if (!click("连锁", coords[2])) return cancelledOr("点击「连锁」失败");
            if (!sleepStep()) return "已取消";
            if (!click("激活方式", coords[3])) return cancelledOr("点击「激活方式」失败");
            if (!sleepStep(delayListOpen)) return "已取消";
            if (!click("保持开启", coords[4])) return cancelledOr("点击「保持开启」失败");
        }
        if (!sleepStep()) return "已取消";

        // 4) 命令编辑框 → 等焦点就绪 → 输入 → 关闭面板
        if (!click("命令编辑框", coords[5])) return cancelledOr("点击「命令编辑框」失败");
        if (!sleepStep(delayEditFocus)) return "已取消";

        AppLogger.d(TAG, "inputText len=" + (command == null ? 0 : command.length()));
        try {
            injector.inputText(command, 0);
        } catch (InjectionException e) {
            return cancelledOr("输入命令失败: " + e.getMessage());
        }
        if (!sleepStep()) return "已取消";

        if (!click("关闭命令面板", coords[6])) return cancelledOr("点击「关闭命令面板」失败");
        if (!sleepStep()) return "已取消";

        return null;
    }

    private String cancelledOr(String msg) {
        return cancelled ? "已取消" : msg;
    }

    private boolean sleepStep() throws InterruptedException {
        return sleepStep(delayStep);
    }

    private boolean sleepStep(long ms) throws InterruptedException {
        Thread.sleep(ms);
        return !cancelled;
    }

    /** 从 Registry 读执行时序（导入设置页），缺失/非法回退默认值。 */
    private void loadTiming() {
        delayStep = readLong(DataManage.Tag.IMPORT_DELAY_STEP, STEP_DELAY_MS);
        delayPanelOpen = readLong(DataManage.Tag.IMPORT_DELAY_PANEL_OPEN, PANEL_OPEN_DELAY_MS);
        delayListOpen = readLong(DataManage.Tag.IMPORT_DELAY_LIST_OPEN, LIST_OPEN_DELAY_MS);
        delayEditFocus = readLong(DataManage.Tag.IMPORT_DELAY_EDIT_FOCUS, EDIT_FOCUS_DELAY_MS);
        pressDuration = readLong(DataManage.Tag.IMPORT_PRESS_DURATION, PRESS_DURATION_MS);
        AppLogger.d(TAG, "timing: step=" + delayStep + "ms panelOpen=" + delayPanelOpen
                + "ms listOpen=" + delayListOpen + "ms editFocus=" + delayEditFocus
                + "ms press=" + pressDuration + "ms");
    }

    private long readLong(DataManage.Tag tag, long def) {
        try {
            Registry reg = RegistryUtils.get();
            if (reg == null) return def;
            String key = tag.getValue();
            if (!reg.exists(key)) return def;
            long v = Long.parseLong(reg.getString(key).trim());
            return v >= 0 ? v : def;
        } catch (Exception e) {
            return def;
        }
    }

    private boolean pressKey(KeyInjector injector, int keyCode) {
        long t0 = SystemClock.uptimeMillis();
        try {
            injector.pressKey(keyCode);
            AppLogger.d(TAG, "pressKey keyCode=" + keyCode + " ok cost="
                    + (SystemClock.uptimeMillis() - t0) + "ms");
            return true;
        } catch (InjectionException e) {
            AppLogger.w(TAG, "pressKey failed: " + e.getMessage());
            return false;
        }
    }

    private boolean click(String step, int[] xy) {
        if (overlapChecker != null && overlapChecker.test(xy)) warnOverlap(step, xy);
        long t0 = SystemClock.uptimeMillis();
        boolean ok = MioAccessibilityService.clickAt(xy[0], xy[1], pressDuration);
        AppLogger.d(TAG, "click[" + step + "] (" + xy[0] + "," + xy[1] + ") ok=" + ok
                + " press=" + pressDuration + "ms cost=" + (SystemClock.uptimeMillis() - t0) + "ms");
        return ok;
    }

    /** 目标坐标落在操作 chip 窗口内（手势会点到 chip 自己），提示拖动 chip。 */
    private void warnOverlap(String step, int[] xy) {
        AppLogger.w(TAG, "click[" + step + "] (" + xy[0] + "," + xy[1]
                + ") 落在操作chip区域，手势可能被chip挡住");
        if (overlapWarned) return;
        overlapWarned = true;
        mainHandler.post(() -> Toast.makeText(context,
                "点击「" + step + "」的坐标被操作chip遮挡，请拖动chip后重试",
                Toast.LENGTH_SHORT).show());
    }

    /**
     * 读取 14 个坐标 → 7 组 [x,y]：
     * 方块类型、重复、连锁、激活方式、保持开启、命令编辑框、关闭命令面板。
     *
     * @return 任一缺失返回 null
     */
    private int[][] readCoords() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return null;
        DataManage.Tag[][] groups = {
                {DataManage.Tag.IMPORT_CLICK_BLOCK_TYPE_X, DataManage.Tag.IMPORT_CLICK_BLOCK_TYPE_Y},
                {DataManage.Tag.IMPORT_CLICK_REPEAT_X, DataManage.Tag.IMPORT_CLICK_REPEAT_Y},
                {DataManage.Tag.IMPORT_CLICK_CHAIN_X, DataManage.Tag.IMPORT_CLICK_CHAIN_Y},
                {DataManage.Tag.IMPORT_CLICK_ACTIVATION_X, DataManage.Tag.IMPORT_CLICK_ACTIVATION_Y},
                {DataManage.Tag.IMPORT_CLICK_KEEP_ON_X, DataManage.Tag.IMPORT_CLICK_KEEP_ON_Y},
                {DataManage.Tag.IMPORT_CLICK_COMMAND_EDIT_X, DataManage.Tag.IMPORT_CLICK_COMMAND_EDIT_Y},
                {DataManage.Tag.IMPORT_CLICK_CLOSE_PANEL_X, DataManage.Tag.IMPORT_CLICK_CLOSE_PANEL_Y},
        };
        int[][] out = new int[groups.length][];
        try {
            for (int i = 0; i < groups.length; i++) {
                String sx = reg.exists(groups[i][0].getValue())
                        ? reg.getString(groups[i][0].getValue()).trim() : "";
                String sy = reg.exists(groups[i][1].getValue())
                        ? reg.getString(groups[i][1].getValue()).trim() : "";
                if (sx.isEmpty() || sy.isEmpty()) return null;
                out[i] = new int[]{Integer.parseInt(sx), Integer.parseInt(sy)};
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "readCoords failed: " + e.getMessage());
            return null;
        }
        return out;
    }

    /** 按当前输入模型创建注入器（与 ShortcutKeyDispatcher 口径一致）。 */
    private KeyInjector createInjector() {
        int model = InputModelManager.resolveModel(readSavedModel(), context);
        if (model == InputModelManager.MODEL_NONE) return null;
        KeyInjectorType type = model == InputModelManager.MODEL_SHIZUKU
                ? KeyInjectorType.SHIZUKU
                : KeyInjectorType.IME;
        try {
            KeyInjector injector = KeyInjectorFactory.create(type, context);
            if (injector.getServiceState() != ServiceState.READY) {
                AppLogger.w(TAG, "injector not ready");
                return null;
            }
            return injector;
        } catch (IllegalStateException e) {
            AppLogger.w(TAG, "create injector failed: " + e.getMessage());
            return null;
        }
    }

    private int readSavedModel() {
        try {
            Registry reg = RegistryUtils.get();
            if (reg != null && reg.exists(DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue())) {
                return reg.getInt(DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue());
            }
        } catch (Exception ignored) {
        }
        return InputModelManager.MODEL_IME;
    }

    private void restoreInjector(KeyInjector injector) {
        if (injector instanceof ShizukuKeyInjector) {
            try {
                ((ShizukuKeyInjector) injector).restoreSessionIme();
            } catch (Exception ignored) {
            }
        }
    }
}
