package io.takamiya.mio.terminal.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommandRegistry {
    private final Map<String, ICommand> commands = new LinkedHashMap<>();

    public void register(ICommand command) {
        commands.put(command.getName().toLowerCase(), command);
    }

    public ICommand find(String name) {
        return commands.get(name.toLowerCase());
    }

    public List<ICommand> listAll() {
        return new ArrayList<>(commands.values());
    }

    public boolean exists(String name) {
        return commands.containsKey(name.toLowerCase());
    }
}
