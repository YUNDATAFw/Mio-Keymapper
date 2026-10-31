package io.takamiya.mio.terminal.command;

public enum CommandState {
    IDLE("空闲"),
    PARSING("解析中"),
    EXECUTING("执行中"),
    SUCCESS("成功"),
    ERROR("失败"),
    CANCELLED("已取消");

    private final String label;

    CommandState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
