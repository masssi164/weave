package com.massimotter.weave.backend.spaces.port;

/** Bounded setup: create a never-existing default Space or verify current owner administration. */
public interface DefaultSpacePreparationPort {
    Result prepare(String organizationRef, String ownerAccountRef);

    enum Result { CREATED, VERIFIED }

    class Conflict extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public Conflict() { super("Current default Space owner access cannot be prepared."); }
    }
}
