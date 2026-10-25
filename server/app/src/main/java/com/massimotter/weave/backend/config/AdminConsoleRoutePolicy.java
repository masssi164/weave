package com.massimotter.weave.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;

/**
 * Keeps the explicitly scoped Admin Console subtree outside every Weave
 * protocol, control-plane, management, and security namespace.
 */
final class AdminConsoleRoutePolicy {

    static final String ROOT = "/admin-console";

    private AdminConsoleRoutePolicy() {
    }

    static boolean isPublicConsoleRequest(HttpServletRequest request) {
        return isSafeReadMethod(request.getMethod()) && isConsolePath(request.getRequestURI());
    }

    static boolean isConsolePath(String requestPath) {
        if (requestPath == null || requestPath.isBlank() || !requestPath.startsWith("/")) {
            return false;
        }
        if (requestPath.contains("\\") || requestPath.contains("..")) {
            return false;
        }
        return requestPath.equals(ROOT) || requestPath.startsWith(ROOT + "/");
    }

    private static boolean isSafeReadMethod(String method) {
        return HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method);
    }
}
