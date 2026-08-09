package com.massimotter.weave.backend.matrix;

import java.util.Map;

/**
 * Server-side Matrix wire codec port.
 *
 * <p>Implementations may use JNI/Ruma, but authorization, canonical Chat state,
 * persistence and provider selection stay outside this boundary.
 */
public interface MatrixProtocolCodec {

    Map<String, Object> project(MatrixProtocolOperation operation, String inputJson);
}
