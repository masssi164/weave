package com.massimotter.weave.backend.agentruntime.operator;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimeStateKeyWrapper;
import com.massimotter.weave.backend.agentruntime.adapter.FileSecretStoreAccess;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateWrappingKeyLifecycle;
import java.io.PrintStream;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Offline operator entrypoint; it never starts Spring or opens a network port. */
public final class RuntimeStateWrappingKeyCli {
    private RuntimeStateWrappingKeyCli() {
    }

    public static void main(String[] arguments) {
        int status = run(arguments, System.out, System.err, Clock.systemUTC(), new SecureRandom());
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(
            String[] arguments,
            PrintStream output,
            PrintStream errors,
            Clock clock,
            SecureRandom secureRandom) {
        try {
            Arguments parsed = Arguments.parse(arguments);
            ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
            FileRuntimeStateKeyWrapper keys = new FileRuntimeStateKeyWrapper(
                    parsed.root(), mapper, clock, secureRandom, FileSecretStoreAccess.READ_WRITE);
            RuntimeStateWrappingKeyLifecycle.KeyRingState result = switch (parsed.action()) {
                case INITIALIZE -> keys.initialize(parsed.requiredOperationRef());
                case ROTATE -> keys.rotate(parsed.requiredOperationRef());
                case STATUS -> keys.current();
            };
            output.println(mapper.writeValueAsString(result));
            return 0;
        } catch (Exception failure) {
            errors.println("runtime-state-wrapping-key-operation-failed: " + safeMessage(failure));
            return 2;
        }
    }

    private static String safeMessage(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private enum Action {
        INITIALIZE("initialize"),
        ROTATE("rotate"),
        STATUS("status");

        private final String wireValue;

        Action(String wireValue) {
            this.wireValue = wireValue;
        }

        private static Action parse(String value) {
            for (Action action : values()) {
                if (action.wireValue.equals(value)) {
                    return action;
                }
            }
            throw new IllegalArgumentException("--action must be initialize, rotate, or status");
        }
    }

    private record Arguments(Action action, Path root, String operationRef) {
        private static Arguments parse(String[] arguments) {
            if (arguments == null) {
                throw new IllegalArgumentException("operator arguments are required");
            }
            Map<String, String> values = new LinkedHashMap<>();
            Set<String> allowed = Set.of("action", "root", "operation-ref");
            for (String argument : arguments) {
                if (argument == null || !argument.startsWith("--") || !argument.contains("=")) {
                    throw new IllegalArgumentException("arguments must use --name=value syntax");
                }
                int separator = argument.indexOf('=');
                String name = argument.substring(2, separator);
                String value = argument.substring(separator + 1);
                if (!allowed.contains(name) || value.isBlank() || values.putIfAbsent(name, value) != null) {
                    throw new IllegalArgumentException("unknown, blank, or duplicate operator argument: " + name);
                }
            }
            Action action = Action.parse(required(values, "action"));
            Path root = Path.of(required(values, "root")).normalize();
            if (!root.isAbsolute()) {
                throw new IllegalArgumentException("--root must be an explicit absolute path");
            }
            String operationRef = values.get("operation-ref");
            if (action != Action.STATUS && (operationRef == null || operationRef.isBlank())) {
                throw new IllegalArgumentException("--operation-ref is required for mutating operations");
            }
            if (action == Action.STATUS && operationRef != null) {
                throw new IllegalArgumentException("--operation-ref is not accepted for status");
            }
            return new Arguments(action, root, operationRef);
        }

        private String requiredOperationRef() {
            if (operationRef == null) {
                throw new IllegalStateException("operation reference is unavailable");
            }
            return operationRef;
        }

        private static String required(Map<String, String> values, String name) {
            String value = values.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("--" + name + " is required");
            }
            return value;
        }
    }
}
