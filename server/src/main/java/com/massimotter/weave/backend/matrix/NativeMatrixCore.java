package com.massimotter.weave.backend.matrix;

/**
 * Narrow JNI entry point for the optional server-side Matrix protocol codec.
 *
 * <p>The native library is intentionally loaded only when an enabled Matrix
 * facade actually invokes the protocol boundary. Native Weave Chat can therefore
 * start and operate without a platform-specific Matrix library.
 */
public final class NativeMatrixCore {

    public static final String LIBRARY_NAME = "weave_matrix_core";
    public static final String JNI_METHOD = "projectJson";

    private NativeMatrixCore() {
    }

    public static void ensureLoaded() {
        Throwable failure = LoadState.FAILURE;
        if (failure != null) {
            throw new IllegalStateException(
                    "The optional Rust/Ruma Matrix protocol core could not be loaded; the Matrix facade is unavailable.",
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
                String configuredPath = System.getProperty("weave.matrix.core.library.path");
                if (configuredPath == null || configuredPath.isBlank()) {
                    configuredPath = System.getenv("WEAVE_MATRIX_CORE_LIBRARY_PATH");
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
