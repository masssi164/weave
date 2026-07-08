package com.massimotter.weave.backend.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
public class MatrixClientServerProjectionController {

    private static final String MATRIX_ALLOW = "OPTIONS, GET, POST, PUT";

    @RequestMapping(value = "/_matrix/client/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.ALLOW, MATRIX_ALLOW)
                .header("X-Weave-Projection", "matrix-client-server")
                .build();
    }

    @RequestMapping("/_matrix/client/**")
    public ResponseEntity<Map<String, Object>> handle(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if (!List.of("GET", "POST", "PUT").contains(method)) {
            return matrixError(
                    HttpStatus.NOT_IMPLEMENTED,
                    "M_WEAVE_MATRIX_METHOD_NOT_IMPLEMENTED",
                    "This Weave Matrix Client-Server projection skeleton only reserves OPTIONS, GET, POST, and PUT.");
        }
        return matrixError(
                HttpStatus.SERVICE_UNAVAILABLE,
                "M_WEAVE_MATRIX_PROJECTION_UNAVAILABLE",
                "Weave Chat requires a configured Matrix Client-Server projection before member chat data is served.");
    }

    private ResponseEntity<Map<String, Object>> matrixError(HttpStatus status, String errcode, String error) {
        return ResponseEntity.status(status)
                .header("X-Weave-Projection", "matrix-client-server")
                .body(Map.of(
                        "errcode", errcode,
                        "error", error,
                        "weaveBoundary", "northbound-matrix-client-server",
                        "canonicalDomain", "chat",
                        "supportSafe", true,
                        "providerDataPlaneExposed", false));
    }
}
