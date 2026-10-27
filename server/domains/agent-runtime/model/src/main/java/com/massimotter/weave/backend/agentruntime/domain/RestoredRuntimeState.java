package com.massimotter.weave.backend.agentruntime.domain;

import java.util.Arrays;

/** Decrypted runtime-internal state plus its authenticated generation metadata. */
public final class RestoredRuntimeState {
    private final RuntimeStateGeneration generation;
    private final byte[] state;

    public RestoredRuntimeState(RuntimeStateGeneration generation, byte[] state) {
        if (generation == null || state == null) {
            throw new IllegalArgumentException("runtime state generation and bytes are required");
        }
        if (generation.plaintextBytes() != state.length) {
            throw new IllegalArgumentException("runtime state byte count does not match generation metadata");
        }
        this.generation = generation;
        this.state = state.clone();
    }

    public RuntimeStateGeneration generation() {
        return generation;
    }

    public byte[] state() {
        return state.clone();
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof RestoredRuntimeState other
                && generation.equals(other.generation)
                && Arrays.equals(state, other.state);
    }

    @Override
    public int hashCode() {
        return 31 * generation.hashCode() + Arrays.hashCode(state);
    }

    @Override
    public String toString() {
        return "RestoredRuntimeState[generation=" + generation + ", state=<redacted>]";
    }
}
