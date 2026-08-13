package com.massimotter.weave.backend.matrix;

/**
 * Required JNI entry point for the server-side Matrix Client-Server protocol codec.
 *
 * <p>The Matrix facade is a permanent northbound Weave Server contract. The
 * platform-specific Ruma/JNI protocol library is therefore a required runtime
 * artifact on every supported server platform. Missing or incompatible native
 * code fails startup/invocation closed with an actionable diagnostic; there is
 * no handwritten Java protocol fallback.</p>
 */
public final class NativeMatrixCore {

    public static final String LIBRARY_NAME = "weave_matrix_protocol";
    public static final String JNI_METHOD = "projectJson";

    private NativeMatrixCore() {
    }

    public static void ensureLoaded() {
        Throwable failure = LoadState.FAILURE;
        if (failure != null) {
            throw new IllegalStateException(
                    "The required Rust/Ruma Matrix protocol library could not be loaded.",
                    failure);
        }
    }

    public static boolean isLoaded() {
        return LoadState.FAILURE == null;
    }

    public static native String projectJson(String operation, String inputJson, String serverName);

    private static final class LoadState {
        private static final Throwable FAILURE = load();

        private static Throwable load() {
            try {
                String configuredPath = System.getProperty("weave.matrix.protocol.library.path");
                if (configuredPath == null || configuredPath.isBlank()) {
                    configuredPath = System.getenv("WEAVE_MATRIX_PROTOCOL_LIBRARY_PATH");
                }
                if (configuredPath == null || configuredPath.isBlank()) {
                    System.loadLibrary(LIBRARY_NAME);
                } else {
                    System.load(configuredPath);
                }
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        }
    }
}
