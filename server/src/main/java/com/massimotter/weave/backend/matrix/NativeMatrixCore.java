package com.massimotter.weave.backend.matrix;

public final class NativeMatrixCore {

    public static final String LIBRARY_NAME = "weave_matrix_core";
    public static final String JNI_METHOD = "projectJson";

    private static final Throwable LOAD_FAILURE;

    static {
        Throwable failure = null;
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
        } catch (Throwable throwable) {
            failure = throwable;
        }
        LOAD_FAILURE = failure;
    }

    private NativeMatrixCore() {
    }

    public static void ensureLoaded() {
        if (LOAD_FAILURE != null) {
            throw new IllegalStateException(
                    "The required Rust/Ruma Matrix protocol core could not be loaded; Matrix facade startup is blocked.",
                    LOAD_FAILURE);
        }
    }

    public static boolean isLoaded() {
        return LOAD_FAILURE == null;
    }

    public static native String projectJson(String operation, String inputJson, String serverName);
}
