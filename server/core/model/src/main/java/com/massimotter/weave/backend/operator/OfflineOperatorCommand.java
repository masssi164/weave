package com.massimotter.weave.backend.operator;

import java.io.PrintStream;

/** One explicitly invoked offline command. Discovery must never access keys, providers or Spring. */
public interface OfflineOperatorCommand {
    String name();

    int execute(String[] arguments, PrintStream output, PrintStream errors);
}
