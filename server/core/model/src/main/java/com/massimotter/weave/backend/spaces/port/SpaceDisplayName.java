package com.massimotter.weave.backend.spaces.port;

/** Space presentation metadata never substitutes for its stable reference or membership. */
public final class SpaceDisplayName {
    public static final String DEFAULT_WORKSPACE = "Organization workspace";
    private SpaceDisplayName() {}

    public static String normalize(String value) {
        if (value == null || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("A readable Space name is required");
        }
        String name = value.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 160) {
            throw new IllegalArgumentException("A Space name requires 1 to 160 Unicode code points");
        }
        return name;
    }
}
