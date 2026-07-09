package com.massimotter.weave.backend.matrix;

public final class NativeMatrixCore {

    public static final String LIBRARY_NAME = "weave_matrix_core";
    public static final String JNI_METHOD = "matrixFacadeDescriptorJson";

    private NativeMatrixCore() {
    }

    public static native String matrixFacadeDescriptorJson(String serverName);
}
