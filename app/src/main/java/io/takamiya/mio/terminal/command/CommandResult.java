package io.takamiya.mio.terminal.command;

public class CommandResult {
    private final CommandState state;
    private final String output;
    private final String markdown;
    private final String error;
    private final long durationMs;

    private CommandResult(CommandState state, String output, String markdown, String error, long durationMs) {
        this.state = state;
        this.output = output;
        this.markdown = markdown;
        this.error = error;
        this.durationMs = durationMs;
    }

    public static CommandResult text(String output) {
        return new CommandResult(CommandState.SUCCESS, output, null, null, 0);
    }

    public static CommandResult markdown(String markdown) {
        return new CommandResult(CommandState.SUCCESS, null, markdown, null, 0);
    }

    public static CommandResult error(String error) {
        return new CommandResult(CommandState.ERROR, null, null, error, 0);
    }

    public CommandResult withDuration(long durationMs) {
        return new CommandResult(state, output, markdown, error, durationMs);
    }

    public CommandState getState() {
        return state;
    }

    public String getOutput() {
        return output;
    }

    public String getMarkdown() {
        return markdown;
    }

    public String getError() {
        return error;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isMarkdown() {
        return markdown != null;
    }

    public boolean isSuccess() {
        return state == CommandState.SUCCESS;
    }
}
