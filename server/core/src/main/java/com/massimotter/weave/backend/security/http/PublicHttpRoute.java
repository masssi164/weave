package com.massimotter.weave.backend.security.http;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpMethod;

/** Exact public endpoint owned by an installed module. Null method preserves an existing all-method route. */
public record PublicHttpRoute(String owner, String path, HttpMethod method) {
    public PublicHttpRoute {
        Objects.requireNonNull(owner, "Route owner");
        Objects.requireNonNull(path, "Route path");
        if (owner.isBlank() || !path.startsWith("/") || path.contains("*")
                || path.contains("{") || path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException("Public routes require an owner and an exact path");
        }
    }

    public static List<PublicHttpRoute> validated(List<PublicHttpRoute> routes) {
        var declarations = List.copyOf(routes);
        var paths = new HashSet<String>();
        for (var route : declarations) {
            String key = route.path() + " " + route.method();
            if (!paths.add(key) || declarations.stream().anyMatch(other -> other != route
                    && other.path().equals(route.path()) && (other.method() == null || route.method() == null))) {
                throw new IllegalArgumentException("Duplicate or overlapping public HTTP route");
            }
        }
        return declarations;
    }
}
