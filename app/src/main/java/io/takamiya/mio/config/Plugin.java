package io.takamiya.mio.config;

import java.util.ArrayList;
import java.util.List;

public class Plugin {
    private PluginConfig config;
    private List<ActionItem> actionList;
    private boolean locked;
    private boolean incompatible;

    public PluginConfig getConfig() { return config; }
    public void setConfig(PluginConfig config) { this.config = config; }

    public List<ActionItem> getActionList() { return actionList; }
    public void setActionList(List<ActionItem> actionList) { this.actionList = actionList; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    /** 旧格式/不适配当前引擎的配置，仅展示与导出，不可加载/编辑。 */
    public boolean isIncompatible() { return incompatible; }
    public void setIncompatible(boolean incompatible) { this.incompatible = incompatible; }

    public boolean isEnabled() {
        return config != null && config.isEnable();
    }
}
