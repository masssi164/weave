package com.massimotter.weave.backend.operator;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

public class OfflineOperatorCommandsTest {
    @TempDir Path directory;

    @Test
    void unknownOrUninstalledCommandFailsWithoutEchoingArguments() throws Exception {
        try (var loader = loader("")) {
            var bytes = new ByteArrayOutputStream();
            assertThat(OfflineOperatorCommands.executeRequired("missing", new String[]{"secret"},
                    new PrintStream(bytes), new PrintStream(bytes), loader)).isEqualTo(2);
            assertThat(bytes.toString()).contains("offline-operator-command-unavailable").doesNotContain("secret");
        }
    }

    @Test
    void actualServiceDiscoveryPreservesArgumentOutputAndExitStatus() throws Exception {
        try (var loader = loader(First.class.getName())) {
            var bytes = new ByteArrayOutputStream();
            String[] args = {"first", "second"};
            assertThat(OfflineOperatorCommands.executeRequired("test", args,
                    new PrintStream(bytes), new PrintStream(bytes), loader)).isEqualTo(7);
            assertThat(bytes.toString()).contains("[first, second]");
            assertThat(args).containsExactly("first", "second");
        }
    }

    @Test
    void duplicateNamesFailBeforeEitherCommandExecutes() throws Exception {
        try (var loader = loader(First.class.getName() + "\n" + Second.class.getName())) {
            var bytes = new ByteArrayOutputStream();
            assertThat(OfflineOperatorCommands.executeRequired("test", new String[]{"secret"},
                    new PrintStream(bytes), new PrintStream(bytes), loader)).isEqualTo(2);
            assertThat(bytes.toString()).contains("invalid-registration").doesNotContain("secret");
        }
    }

    private URLClassLoader loader(String descriptor) throws Exception {
        Path resource = directory.resolve("META-INF/services/" + OfflineOperatorCommand.class.getName());
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, descriptor);
        return new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, getClass().getClassLoader());
    }

    public static class First implements OfflineOperatorCommand {
        public String name() { return "test"; }
        public int execute(String[] arguments, PrintStream output, PrintStream errors) {
            output.println(Arrays.toString(arguments));
            arguments[0] = "changed";
            return 7;
        }
    }

    public static class Second extends First {}
}
