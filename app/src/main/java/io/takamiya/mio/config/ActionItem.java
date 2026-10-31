package io.takamiya.mio.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionItem {
    private String title;
    private String description;
    private TriggerModel triggerModel = TriggerModel.BUTTON;
    private String id;
    private String group;
    private Integer holdThresholdMs;
    private Integer releaseDelayMs;
    private List<Action> action;
    private List<ActionGroup> groups;
    private Map<ShortcutEventType, List<ActionGroup>> events;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TriggerModel getTriggerModel() { return triggerModel; }
    public void setTriggerModel(TriggerModel triggerModel) { this.triggerModel = triggerModel; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public List<Action> getAction() { return action; }
    public void setAction(List<Action> action) { this.action = action; }

    public List<ActionGroup> getGroups() { return groups; }
    public void setGroups(List<ActionGroup> groups) { this.groups = groups; }

    public boolean isSwitch() {
        return triggerModel == TriggerModel.SWITCH;
    }

    public boolean isPressurePlate() {
        return triggerModel == TriggerModel.PRESSURE_PLATE;
    }

    public Integer getHoldThresholdMs() { return holdThresholdMs; }
    public void setHoldThresholdMs(Integer holdThresholdMs) { this.holdThresholdMs = holdThresholdMs; }

    public Integer getReleaseDelayMs() { return releaseDelayMs; }
    public void setReleaseDelayMs(Integer releaseDelayMs) { this.releaseDelayMs = releaseDelayMs; }

    public Map<ShortcutEventType, List<ActionGroup>> getEvents() { return events; }
    public void setEvents(Map<ShortcutEventType, List<ActionGroup>> events) { this.events = events; }

    public List<ActionGroup> getEventGroups(ShortcutEventType type) {
        return events != null ? events.get(type) : null;
    }

    public boolean hasEvents() {
        return events != null && !events.isEmpty();
    }

    public List<Action> collectAllActions() {
        java.util.ArrayList<Action> all = new java.util.ArrayList<>();
        if (action != null) all.addAll(action);
        if (groups != null) {
            for (ActionGroup g : groups) {
                if (g.getActions() != null) all.addAll(g.getActions());
            }
        }
        return all;
    }
}
