package io.takamiya.mio.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件体组：仅作为容纳一串 Action 的容器（兼容壳）。
 * 循环/重复/持有等控制已下放到单个 Action 上（KeyMapper 风格），
 * 本类不再持有任何循环配置字段。
 */
public class ActionGroup {
    private String name;

    private List<Action> actions = new ArrayList<>();

    public List<Action> getActions() { return actions; }
    public void setActions(List<Action> actions) { this.actions = actions; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
