package io.takamiya.mio.terminal.command;

import java.util.Map;

public class CommandDispatcher {
    private final CommandRegistry registry;

    public CommandDispatcher(CommandRegistry registry) {
        this.registry = registry;
    }

    public void dispatch(String input, CommandCallback callback) {
        callback.onStateChange(CommandState.PARSING);

        CommandParser.ParsedCommand parsed = CommandParser.parse(input);
        if (parsed == null) {
            callback.onResult(CommandResult.error("输入为空"));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        ICommand command = registry.find(parsed.name);
        if (command == null) {
            callback.onResult(CommandResult.error("未知命令: " + parsed.name));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        Map<String, Object> params;
        try {
            params = CommandParser.matchParams(parsed, command.getParams());
        } catch (IllegalArgumentException e) {
            callback.onResult(CommandResult.error(e.getMessage()));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        callback.onStateChange(CommandState.EXECUTING);

        long start = System.currentTimeMillis();
        CommandCallback internalCallback = new CommandCallback() {
            @Override
            public void onStateChange(CommandState state) {
                callback.onStateChange(state);
            }

            @Override
            public void onResult(CommandResult result) {
                long duration = System.currentTimeMillis() - start;
                callback.onResult(result.withDuration(duration));
            }
        };

        try {
            command.execute(params, internalCallback);
        } catch (Exception e) {
            callback.onResult(CommandResult.error("执行异常: " + e.getMessage()));
            callback.onStateChange(CommandState.ERROR);
        }
    }
}
