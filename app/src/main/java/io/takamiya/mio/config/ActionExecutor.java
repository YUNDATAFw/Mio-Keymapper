package io.takamiya.mio.config;

import cn.panda.utils.library.AppLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import androidx.core.util.Consumer;

import io.takamiya.mio.injector.InjectionException;
import io.takamiya.mio.injector.KeyInjector;

public class ActionExecutor {
    private static final String TAG = "ActionExecutor";

    private static class Task {
        final ActionItem item;
        final Thread thread;

        Task(ActionItem item, Thread thread) {
            this.item = item;
            this.thread = thread;
        }
    }

    private static class HeldKey {
        final ActionItem item;
        final int keyCode;

        HeldKey(ActionItem item, int keyCode) {
            this.item = item;
            this.keyCode = keyCode;
        }
    }

    private final KeyInjector injector;
    private final Consumer<String> errorListener;
    private final BiConsumer<String, ToastSettings> toastShower;
    private final CopyOnWriteArrayList<Task> activeTasks = new CopyOnWriteArrayList<>();
    private final Map<Action, HeldKey> heldKeys = new ConcurrentHashMap<>();
    private long globalKeyHoldDuration;

    public ActionExecutor(KeyInjector injector) {
        this(injector, null, null);
    }

    public ActionExecutor(KeyInjector injector, Consumer<String> errorListener) {
        this(injector, errorListener, null);
    }

    public ActionExecutor(KeyInjector injector, Consumer<String> errorListener,
                          BiConsumer<String, ToastSettings> toastShower) {
        this.injector = injector;
        this.errorListener = errorListener;
        this.toastShower = toastShower;
    }

    public void setGlobalKeyHoldDuration(long duration) {
        this.globalKeyHoldDuration = duration;
    }

    public long getGlobalKeyHoldDuration() {
        return globalKeyHoldDuration;
    }

    private List<Action> collectActions(ActionItem item) {
        return item.collectAllActions();
    }

    public boolean needsEngagedExecution(ActionItem item) {
        List<Action> actions = collectActions(item);
        if (actions == null) return false;
        for (Action action : actions) {
            if (action.isHold()) return true;
            if (isRepeatAction(action)) return true;
        }
        return false;
    }

    public boolean isToastOnly(ActionItem item) {
        List<Action> actions = collectActions(item);
        if (actions == null || actions.isEmpty()) return false;
        for (Action action : actions) {
            if (action.getType() != ActionType.TOAST) return false;
        }
        return true;
    }

    public void execute(ActionItem item) {
        List<Action> actions = collectActions(item);
        if (actions == null || actions.isEmpty()) return;

        if (needsInjector(actions) && !injector.isAvailable()) {
            AppLogger.w(TAG, "Injector not available: " + injector.getName());
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                executeActionsOnce(actions);
            } catch (InjectionException e) {
                AppLogger.e(TAG, "Action inject failed", e);
                reportError("按键注入失败: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                injector.restoreSessionIme();
                removeTask(item);
            }
        }, "execute-" + item.getTitle());
        activeTasks.add(new Task(item, thread));
        thread.start();
    }

    public void executeOn(ActionItem item) {
        List<Action> actions = collectActions(item);
        if (actions == null || actions.isEmpty()) return;

        if (needsInjector(actions) && !injector.isAvailable()) {
            AppLogger.w(TAG, "Injector not available: " + injector.getName());
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                executeActionsEngaged(actions, item);
            } catch (InjectionException e) {
                AppLogger.e(TAG, "Action inject failed", e);
                reportError("按键注入失败: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                releaseAllHeldKeysFor(item);
                injector.restoreSessionIme();
                removeTask(item);
            }
        }, "engaged-" + item.getTitle());
        activeTasks.add(new Task(item, thread));
        thread.start();
    }

    public void executeActions(ActionItem item, List<ActionGroup> groups) {
        if (groups == null || groups.isEmpty()) return;
        java.util.ArrayList<Action> actions = new java.util.ArrayList<>();
        for (ActionGroup g : groups) {
            if (g.getActions() != null) actions.addAll(g.getActions());
        }
        if (actions.isEmpty()) return;
        if (needsInjector(actions) && !injector.isAvailable()) {
            AppLogger.w(TAG, "Injector not available for event groups: " + injector.getName());
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                executeActionsOnce(actions);
            } catch (InjectionException e) {
                AppLogger.e(TAG, "Event inject failed", e);
                reportError("按键注入失败: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                injector.restoreSessionIme();
                removeTask(item);
            }
        }, "event-" + item.getTitle());
        activeTasks.add(new Task(item, thread));
        thread.start();
    }

    private void executeActionsOnce(List<Action> actions)
            throws InjectionException, InterruptedException {
        for (Action action : actions) {
            if (Thread.currentThread().isInterrupted()) break;
            long beforeDelay = action.getBeforeDelayMs() != null ? action.getBeforeDelayMs() : 0;
            if (beforeDelay > 0) Thread.sleep(beforeDelay);
            executeActionSync(action);
            long afterDelay = action.getAfterDelayMs() != null ? action.getAfterDelayMs() : 0;
            if (afterDelay > 0) Thread.sleep(afterDelay);
        }
    }

    /**
     * 分阶段执行模型：
     * 1. 连续的非循环 action 顺序执行（ONCE 阶段）
     * 2. 连续的循环 action 绑定为一组，串行执行（REPEAT_GROUP 阶段）
     * 3. 循环阶段会阻塞后续 action，直到组内所有循环完成
     * 4. hold action 在开始时按下，结束时释放
     */
    private void executeActionsEngaged(List<Action> actions, ActionItem item)
            throws InjectionException, InterruptedException {

        List<Action> holdActions = new ArrayList<>();
        List<List<Action>> phases = new ArrayList<>();

        // 分离 hold action，其余按类型分组为 phase
        for (Action action : actions) {
            if (action.isHold()) {
                holdActions.add(action);
            } else if (isRepeatAction(action)) {
                // 循环 action：寻找或创建当前 REPEAT_GROUP phase
                if (!phases.isEmpty() && phases.get(phases.size() - 1).get(0).isRepeat()) {
                    phases.get(phases.size() - 1).add(action);
                } else {
                    List<Action> newPhase = new ArrayList<>();
                    newPhase.add(action);
                    phases.add(newPhase);
                }
            } else {
                // 非循环 action：寻找或创建当前 ONCE phase
                if (!phases.isEmpty() && !phases.get(phases.size() - 1).get(0).isRepeat()) {
                    phases.get(phases.size() - 1).add(action);
                } else {
                    List<Action> newPhase = new ArrayList<>();
                    newPhase.add(action);
                    phases.add(newPhase);
                }
            }
        }

        // 按下所有 hold action
        for (Action action : holdActions) {
            int keyCode = action.getKeyCode();
            if (keyCode != 0) {
                injector.keyDown(keyCode);
                heldKeys.put(action, new HeldKey(item, keyCode));
            }
        }

        // 按阶段顺序执行
        for (List<Action> phase : phases) {
            if (Thread.currentThread().isInterrupted()) break;

            if (phase.get(0).isRepeat()) {
                executeRepeatPhase(phase);
            } else {
                // ONCE 阶段：顺序执行
                for (Action action : phase) {
                    if (Thread.currentThread().isInterrupted()) break;
                    long beforeDelay = action.getBeforeDelayMs() != null ? action.getBeforeDelayMs() : 0;
                    if (beforeDelay > 0) Thread.sleep(beforeDelay);
                    executeActionSync(action);
                    long afterDelay = action.getAfterDelayMs() != null ? action.getAfterDelayMs() : 0;
                    if (afterDelay > 0) Thread.sleep(afterDelay);
                }
            }
        }

        // 纯 hold action（无 repeat/once phase）时，阻塞等待直到被中断
        // executeOff() → stopItem() → thread.interrupt() 会中断此线程
        // finally 块中的 releaseAllHeldKeysFor 会负责释放按键
        if (!holdActions.isEmpty() && phases.isEmpty()) {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean isRepeatAction(Action action) {
        if (action.isHold()) return false;
        if (!action.isRepeat()) return false;
        DuplicateConfiguration dc = action.getDuplicateConfiguration();
        return dc != null;
    }

    /**
     * 执行一个 REPEAT_GROUP 阶段：组内所有 repeat action 串行执行，
     * 每轮内 action 按顺序执行，全部完成一轮后进入下一轮，直到达到重复次数。
     * 第1轮使用 beforeDelayMs/afterDelayMs，第2轮起使用 repeatBeforeDelayMs/repeatAfterDelayMs。
     * 当显式延迟未设置时，回退到 duplicateConfiguration.intervalDuration。
     */
    private void executeRepeatPhase(List<Action> repeatActions)
            throws InterruptedException {
        int maxRepeats = 0;
        boolean infiniteRepeat = false;
        for (Action action : repeatActions) {
            DuplicateConfiguration dc = action.getDuplicateConfiguration();
            if (dc == null) continue;
            int repeats = dc.getNumberOfRepetitions();
            if (repeats <= 0) {
                infiniteRepeat = true;
            } else if (repeats > maxRepeats) {
                maxRepeats = repeats;
            }
        }

        int round = 0;
        while (infiniteRepeat || round < maxRepeats) {
            if (Thread.currentThread().isInterrupted()) break;

            for (Action action : repeatActions) {
                if (Thread.currentThread().isInterrupted()) break;

                DuplicateConfiguration dc = action.getDuplicateConfiguration();
                if (dc == null) continue;
                int actionRepeats = dc.getNumberOfRepetitions();
                if (actionRepeats > 0 && round >= actionRepeats) continue;

                long intervalMs = dc.getIntervalDuration();

                if (round == 0) {
                    long beforeDelay = action.getBeforeDelayMs() != null ? action.getBeforeDelayMs() : 0;
                    if (beforeDelay > 0) Thread.sleep(beforeDelay);
                } else {
                    long repeatBeforeDelay = action.getRepeatBeforeDelayMs() != null ? action.getRepeatBeforeDelayMs() : 0;
                    if (repeatBeforeDelay > 0) {
                        Thread.sleep(repeatBeforeDelay);
                    } else if (intervalMs > 0) {
                        Thread.sleep(intervalMs);
                    }
                }

                try {
                    executeActionSync(action);
                } catch (InjectionException e) {
                    AppLogger.e(TAG, "Repeat action inject failed", e);
                }

                if (round == 0) {
                    long afterDelay = action.getAfterDelayMs() != null ? action.getAfterDelayMs() : 0;
                    if (afterDelay > 0) {
                        Thread.sleep(afterDelay);
                    } else if (intervalMs > 0) {
                        Thread.sleep(intervalMs);
                    }
                } else {
                    long repeatAfterDelay = action.getRepeatAfterDelayMs() != null ? action.getRepeatAfterDelayMs() : 0;
                    if (repeatAfterDelay > 0) {
                        Thread.sleep(repeatAfterDelay);
                    } else if (intervalMs > 0) {
                        Thread.sleep(intervalMs);
                    }
                }
            }
            round++;
        }
    }

    public void executeOff(ActionItem item) {
        releaseAllHeldKeysFor(item);
        stopItem(item);
    }

    public void stopAll() {
        releaseAllHeldKeys();
        for (Task task : activeTasks) {
            task.thread.interrupt();
        }
        activeTasks.clear();
    }

    private void stopItem(ActionItem item) {
        for (Task task : activeTasks) {
            if (task.item == item) {
                task.thread.interrupt();
            }
        }
    }

    private void releaseAllHeldKeysFor(ActionItem item) {
        for (Map.Entry<Action, HeldKey> entry : heldKeys.entrySet()) {
            if (entry.getValue().item == item) {
                try {
                    injector.keyUp(entry.getValue().keyCode);
                } catch (InjectionException e) {
                    AppLogger.e(TAG, "Release held key failed", e);
                }
            }
        }
        heldKeys.entrySet().removeIf(entry -> entry.getValue().item == item);
    }

    private void releaseAllHeldKeys() {
        for (Map.Entry<Action, HeldKey> entry : heldKeys.entrySet()) {
            try {
                injector.keyUp(entry.getValue().keyCode);
            } catch (InjectionException e) {
                AppLogger.e(TAG, "Release held key failed", e);
            }
        }
        heldKeys.clear();
    }

    private boolean needsInjector(List<Action> actions) {
        for (Action action : actions) {
            if (action.getType() != ActionType.TOAST) return true;
        }
        return false;
    }

    private void reportError(String message) {
        if (errorListener != null) {
            errorListener.accept(message);
        }
    }

    private void removeTask(ActionItem item) {
        Thread current = Thread.currentThread();
        activeTasks.removeIf(task -> task.item == item && task.thread == current);
    }

    private long resolveKeyHoldDuration(Action action) {
        if (action.getKeyHoldDuration() != null && action.getKeyHoldDuration() > 0) return action.getKeyHoldDuration();
        if (globalKeyHoldDuration > 0) return globalKeyHoldDuration;
        return 0;
    }

    private void executeActionSync(Action action) throws InjectionException, InterruptedException {
        switch (action.getType()) {
            case INPUT_KEY:
                executeInputKeySync(action);
                break;
            case INPUT_KEY_GROUP:
                executeKeyGroupSync(action);
                break;
            case INPUT_TEXT:
                executeInputTextSync(action);
                break;
            case TOAST:
                executeToastSync(action);
                break;
        }
    }

    private void executeInputTextSync(Action action) throws InjectionException, InterruptedException {
        String text = action.getText();
        if (text == null || text.isEmpty()) {
            AppLogger.w(TAG, "INPUT_TEXT: empty text, ignored");
            return;
        }
        inputTextOnce(action);
    }

    private void inputTextOnce(Action action) throws InjectionException, InterruptedException {
        String text = action.getText();
        long charDelay = action.getCharDelayMs() != null ? action.getCharDelayMs() : 0;
        if (charDelay <= 0) {
            injector.inputText(text, 0);
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Thread.currentThread().isInterrupted()) break;
            injector.inputText(String.valueOf(text.charAt(i)), 0);
            if (i < text.length() - 1) {
                injector.delay(charDelay);
            }
        }
    }

    private void executeToastSync(Action action) {
        String text = action.getText();
        if (text == null || text.isEmpty()) {
            AppLogger.w(TAG, "TOAST: empty text, ignored");
            return;
        }
        if (toastShower == null) {
            AppLogger.w(TAG, "TOAST: no toast shower available");
            return;
        }
        toastShower.accept(text, action.getToastSettings());
    }

    private void executeInputKeySync(Action action) throws InjectionException, InterruptedException {
        int keyCode = action.getKeyCode();
        if (keyCode == 0) {
            AppLogger.w(TAG, "Unknown key: " + action.getKey());
            return;
        }
        long holdDuration = resolveKeyHoldDuration(action);
        if (holdDuration > 0) {
            injector.pressKey(keyCode, holdDuration);
        } else {
            injector.pressKey(keyCode);
        }
    }

    private void executeKeyGroupSync(Action action) throws InjectionException {
        pressKeyGroupOnce(action);
    }

    private void pressKeyGroupOnce(Action action) throws InjectionException {
        int keyCode = action.getKeyCode();
        if (keyCode == 0) {
            AppLogger.w(TAG, "Unknown key: " + action.getKey());
            return;
        }
        List<Integer> modifiers = action.getCombinationKeyCodes();
        long holdDuration = resolveKeyHoldDuration(action);
        if (modifiers == null || modifiers.isEmpty()) {
            if (holdDuration > 0) {
                injector.pressKey(keyCode, holdDuration);
            } else {
                injector.pressKey(keyCode);
            }
            return;
        }
        int[] mods = new int[modifiers.size()];
        for (int i = 0; i < modifiers.size(); i++) mods[i] = modifiers.get(i);
        if (holdDuration > 0) {
            injector.pressKeyCombo(keyCode, holdDuration, mods);
        } else {
            injector.pressKeyCombo(keyCode, mods);
        }
    }
}
