package com.massimotter.weave.backend.agentruntime.operator;

import com.massimotter.weave.backend.operator.OfflineOperatorCommand;
import com.massimotter.weave.backend.operator.OfflineOperatorCommands;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OfflineOperatorDiscoveryTest {
    @Test
    void descriptorDiscoversOnlyTheTwoActualOfflineCommandsWithoutKeyAccess() {
        assertThat(ServiceLoader.load(OfflineOperatorCommand.class).stream()
                .map(provider -> provider.get().name())).containsExactly(
                        "runtime-profile-signing-keys", "runtime-state-wrapping-keys");
    }

    @Test
    void dispatchedFailuresRetainActualCliOutputAndExitStatus() {
        String[] arguments = {"--invalid-argument"};
        var expected = new ByteArrayOutputStream();
        var actual = new ByteArrayOutputStream();
        int expectedStatus = RuntimeProfileSigningKeyCli.run(arguments,
                new PrintStream(expected), new PrintStream(expected), Clock.systemUTC(), new SecureRandom());
        int actualStatus = OfflineOperatorCommands.executeRequired("runtime-profile-signing-keys", arguments,
                new PrintStream(actual), new PrintStream(actual), getClass().getClassLoader());
        assertThat(actualStatus).isEqualTo(expectedStatus);
        assertThat(actual.toString()).isEqualTo(expected.toString());
        expected.reset();
        actual.reset();
        expectedStatus = RuntimeStateWrappingKeyCli.run(arguments,
                new PrintStream(expected), new PrintStream(expected), Clock.systemUTC(), new SecureRandom());
        actualStatus = OfflineOperatorCommands.executeRequired("runtime-state-wrapping-keys", arguments,
                new PrintStream(actual), new PrintStream(actual), getClass().getClassLoader());
        assertThat(actualStatus).isEqualTo(expectedStatus);
        assertThat(actual.toString()).isEqualTo(expected.toString());
    }
}
