package com.massimotter.weave.backend.chat.domain.matrix;

public class MatrixProtocolException extends RuntimeException {

    private final String errcode;

    public MatrixProtocolException(String errcode, String message) {
        super(message);
        this.errcode = errcode;
    }

    public String errcode() {
        return errcode;
    }
}
