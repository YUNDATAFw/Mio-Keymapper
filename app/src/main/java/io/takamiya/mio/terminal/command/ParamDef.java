package io.takamiya.mio.terminal.command;

public class ParamDef {
    private final String name;
    private final ParamType type;
    private final boolean required;

    public ParamDef(String name, ParamType type, boolean required) {
        this.name = name;
        this.type = type;
        this.required = required;
    }

    public ParamDef(String name, ParamType type) {
        this(name, type, true);
    }

    public String getName() {
        return name;
    }

    public ParamType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }
}
