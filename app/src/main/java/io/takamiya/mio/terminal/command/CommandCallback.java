package io.takamiya.mio.terminal.command;

public interface CommandCallback {
    void onStateChange(CommandState state);

    void onResult(CommandResult result);
}
