package io.takamiya.mio.config;

import java.util.ArrayList;
import java.util.List;

public class Action {
    public enum ActionTarget {
        KEYBOARD,
        GAMEPAD;

        public static ActionTarget fromString(String value) {
            if (value == null) return KEYBOARD;
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return KEYBOARD;
            }
        }
    }

    private ActionType type = ActionType.INPUT_KEY;
    private ActionTarget target = ActionTarget.KEYBOARD;
    private String key;
    private String text;
    private List<String> combinationKeys;
    private Long beforeDelayMs;
    private Long afterDelayMs;
    private Long repeatBeforeDelayMs;
    private Long repeatAfterDelayMs;
    private Long charDelayMs;
    private boolean hold;
    private Long keyHoldDuration;
    private boolean repeat;
    private DuplicateConfiguration duplicateConfiguration;
    private ToastSettings toastSettings;

    public ActionType getType() { return type; }
    public void setType(ActionType type) { this.type = type; }

    public ActionTarget getTarget() { return target; }
    public void setTarget(ActionTarget target) {
        this.target = target != null ? target : ActionTarget.KEYBOARD;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public List<String> getCombinationKeys() { return combinationKeys; }
    public void setCombinationKeys(List<String> combinationKeys) { this.combinationKeys = combinationKeys; }

    public Long getBeforeDelayMs() { return beforeDelayMs; }
    public void setBeforeDelayMs(Long beforeDelayMs) { this.beforeDelayMs = beforeDelayMs; }

    public Long getAfterDelayMs() { return afterDelayMs; }
    public void setAfterDelayMs(Long afterDelayMs) { this.afterDelayMs = afterDelayMs; }

    public Long getRepeatBeforeDelayMs() { return repeatBeforeDelayMs; }
    public void setRepeatBeforeDelayMs(Long repeatBeforeDelayMs) { this.repeatBeforeDelayMs = repeatBeforeDelayMs; }

    public Long getRepeatAfterDelayMs() { return repeatAfterDelayMs; }
    public void setRepeatAfterDelayMs(Long repeatAfterDelayMs) { this.repeatAfterDelayMs = repeatAfterDelayMs; }

    public Long getCharDelayMs() { return charDelayMs; }
    public void setCharDelayMs(Long charDelayMs) { this.charDelayMs = charDelayMs; }

    public boolean isHold() { return hold; }
    public void setHold(boolean hold) { this.hold = hold; }

    public Long getKeyHoldDuration() { return keyHoldDuration; }
    public void setKeyHoldDuration(Long keyHoldDuration) { this.keyHoldDuration = keyHoldDuration; }

    public boolean isRepeat() { return repeat; }
    public void setRepeat(boolean repeat) { this.repeat = repeat; }

    public DuplicateConfiguration getDuplicateConfiguration() { return duplicateConfiguration; }
    public void setDuplicateConfiguration(DuplicateConfiguration duplicateConfiguration) { this.duplicateConfiguration = duplicateConfiguration; }

    public DuplicateConfiguration ensureDuplicateConfiguration() {
        if (duplicateConfiguration == null) {
            duplicateConfiguration = new DuplicateConfiguration();
        }
        return duplicateConfiguration;
    }

    public ToastSettings getToastSettings() { return toastSettings; }
    public void setToastSettings(ToastSettings toastSettings) { this.toastSettings = toastSettings; }

    public int getKeyCode() {
        return KeyMapping.getKeyCode(key);
    }

    public List<Integer> getCombinationKeyCodes() {
        List<Integer> codes = new ArrayList<>();
        if (combinationKeys != null) {
            for (String k : combinationKeys) {
                codes.add(KeyMapping.getKeyCode(k));
            }
        }
        return codes;
    }
}
