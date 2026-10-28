package com.massimotter.weave.backend.operator;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.ServiceLoader;

/** Dispatches an explicit offline request only; ordinary serving startup never discovers commands. */
public final class OfflineOperatorCommands {
    private OfflineOperatorCommands() {}

    public static int executeRequired(
            String name, String[] arguments, PrintStream output, PrintStream errors, ClassLoader classLoader) {
        var commands = new LinkedHashMap<String, OfflineOperatorCommand>();
        for (var command : ServiceLoader.load(OfflineOperatorCommand.class, classLoader)) {
            if (command.name() == null || command.name().isBlank()
                    || commands.putIfAbsent(command.name(), command) != null) {
                errors.println("offline-operator-command-invalid-registration");
                return 2;
            }
        }
        var command = commands.get(name);
        if (command == null) {
            errors.println("offline-operator-command-unavailable");
            return 2;
        }
        return command.execute(arguments.clone(), output, errors);
    }
}
