package io.takamiya.mio.terminal.command;

import java.util.List;

public interface ICommand {
    String getName();

    String getDescription();

    String getUsage();

    List<ParamDef> getParams();

    void execute(java.util.Map<String, Object> params, CommandCallback callback);
}
