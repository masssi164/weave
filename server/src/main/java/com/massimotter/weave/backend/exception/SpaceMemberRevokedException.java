package com.massimotter.weave.backend.exception;

/** Exposes only an administrator-authorized tombstone version for explicit regrant. */
public final class SpaceMemberRevokedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String strongEtag;

    public SpaceMemberRevokedException(String strongEtag) {
        super("The Space member grant was revoked.");
        if (strongEtag == null || !strongEtag.matches("\"sm-[0-9]+\"")) {
            throw new IllegalArgumentException("A strong Space member version is required");
        }
        this.strongEtag = strongEtag;
    }

    public String strongEtag() {
        return strongEtag;
    }
}
