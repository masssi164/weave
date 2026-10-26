package com.massimotter.weave.backend.agentruntime.operator;

import com.massimotter.weave.backend.operator.OfflineOperatorCommand;
import java.io.PrintStream;
import java.security.SecureRandom;
import java.time.Clock;

/** Offline Agent Runtime key lifecycle; key access starts only after explicit execution. */
public final class RuntimeProfileSigningKeyCommand implements OfflineOperatorCommand {
    @Override
    public String name() {
        return "runtime-profile-signing-keys";
    }

    @Override
    public int execute(String[] arguments, PrintStream output, PrintStream errors) {
        return RuntimeProfileSigningKeyCli.run(arguments, output, errors, Clock.systemUTC(), new SecureRandom());
    }
}
