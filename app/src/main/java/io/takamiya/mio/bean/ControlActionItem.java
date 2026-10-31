package io.takamiya.mio.bean;

import io.takamiya.mio.config.ActionItem;
import io.takamiya.mio.config.TriggerModel;

public class ControlActionItem {

    private String packageName;
    private String actionId;
    private String title;
    private String description;
    private boolean enabled;
    private TriggerModel triggerModel = TriggerModel.BUTTON;
    private String group;
    private Integer holdThresholdMs;
    private Integer releaseDelayMs;
    private ActionItem actionItem;
    private Integer cardWidthDp;
    private Integer cardHeightDp;
    private String cardBgNormal;
    private String cardBgActive;
    private String source;
    private String name;

    public ControlActionItem() {
    }

    public ControlActionItem(String packageName, String actionId, String title, String description, boolean enabled, String source, String name) {
        this.packageName = packageName;
        this.actionId = actionId;
        this.title = title;
        this.description = description;
        this.enabled = enabled;
        this.source = source;
        this.name = name;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public TriggerModel getTriggerModel() {
        return triggerModel;
    }

    public void setTriggerModel(TriggerModel triggerModel) {
        this.triggerModel = triggerModel;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public ActionItem getActionItem() {
        return actionItem;
    }

    public void setActionItem(ActionItem actionItem) {
        this.actionItem = actionItem;
    }

    public Integer getHoldThresholdMs() {
        return holdThresholdMs;
    }

    public void setHoldThresholdMs(Integer holdThresholdMs) {
        this.holdThresholdMs = holdThresholdMs;
    }

    public Integer getReleaseDelayMs() {
        return releaseDelayMs;
    }

    public void setReleaseDelayMs(Integer releaseDelayMs) {
        this.releaseDelayMs = releaseDelayMs;
    }

    public Integer getCardWidthDp() {
        return cardWidthDp;
    }

    public void setCardWidthDp(Integer cardWidthDp) {
        this.cardWidthDp = cardWidthDp;
    }

    public Integer getCardHeightDp() {
        return cardHeightDp;
    }

    public void setCardHeightDp(Integer cardHeightDp) {
        this.cardHeightDp = cardHeightDp;
    }

    public String getCardBgNormal() {
        return cardBgNormal;
    }

    public void setCardBgNormal(String cardBgNormal) {
        this.cardBgNormal = cardBgNormal;
    }

    public String getCardBgActive() {
        return cardBgActive;
    }

    public void setCardBgActive(String cardBgActive) {
        this.cardBgActive = cardBgActive;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
