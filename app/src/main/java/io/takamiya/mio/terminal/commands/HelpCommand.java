package io.takamiya.mio.terminal.commands;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.takamiya.mio.terminal.command.CommandCallback;
import io.takamiya.mio.terminal.command.CommandRegistry;
import io.takamiya.mio.terminal.command.CommandResult;
import io.takamiya.mio.terminal.command.CommandState;
import io.takamiya.mio.terminal.command.ICommand;
import io.takamiya.mio.terminal.command.ParamDef;

public class HelpCommand implements ICommand {
    private final CommandRegistry registry;

    public HelpCommand(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "显示所有可用命令";
    }

    @Override
    public String getUsage() {
        return "help";
    }

    @Override
    public List<ParamDef> getParams() {
        return Collections.emptyList();
    }

    @Override
    public void execute(Map<String, Object> params, CommandCallback callback) {
        StringBuilder sb = new StringBuilder();
        for (ICommand cmd : registry.listAll()) {
            sb.append("**").append(cmd.getName()).append("**");
            sb.append(" - ").append(cmd.getDescription()).append("\n");
            sb.append("  用法: `").append(cmd.getUsage()).append("`\n\n");
        }
        callback.onResult(CommandResult.markdown(sb.toString()));
        callback.onStateChange(CommandState.SUCCESS);
    }
}
