package com.massimotter.weave.backend.spaces.port;

import java.util.Set;

/** Explicit organization setup. A repeat may only observe the same durable membership intent. */
public interface SpaceProvisioningPort {
    Result provision(String organizationRef, String spaceRef, String actorAccountRef,
            Set<String> memberAccountRefs);

    enum Result { CREATED, UNCHANGED }

    class Conflict extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Conflict() {
            super("The Space already has different durable membership intent.");
        }
    }
}
