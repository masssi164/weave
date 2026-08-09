package com.massimotter.weave.backend.matrix;

import java.util.Map;

/**
 * Infrastructure Port for server-side Matrix wire projection.
 *
 * <p>Implementations may use JNI/Ruma, but canonical Chat state, persistence and provider selection
 * remain outside this boundary. Ruma/JNI is therefore a protocol Infrastructure Adapter, not a Chat provider.</p>
 */
public interface MatrixProtocolCodec {

    Map<String, Object> project(MatrixProtocolOperation operation, String inputJson);
}
