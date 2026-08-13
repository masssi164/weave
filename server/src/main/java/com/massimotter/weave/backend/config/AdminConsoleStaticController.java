package com.massimotter.weave.backend.config;

import io.swagger.v3.oas.annotations.Hidden;
import java.time.Duration;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Serves the embedded immutable Vite artifact without a second runtime process. */
@Controller
@Hidden
final class AdminConsoleStaticController {

    private static final String RESOURCE_ROOT = "META-INF/resources/admin-console/";
    private static final String INDEX = "index.html";

    @GetMapping({
            AdminConsoleRoutePolicy.ROOT,
            AdminConsoleRoutePolicy.ROOT + "/",
            AdminConsoleRoutePolicy.ROOT + "/{*resourcePath}"
    })
    ResponseEntity<Resource> serve(@PathVariable(required = false) String resourcePath) {
        String normalized = normalize(resourcePath);
        Optional<Resource> exact = hasFileExtension(normalized)
                ? resource(normalized)
                : Optional.empty();
        if (exact.isPresent()) {
            return response(exact.get(), normalized.startsWith("assets/"));
        }
        if (hasFileExtension(normalized)) {
            return ResponseEntity.notFound().build();
        }
        return resource(INDEX)
                .map(index -> response(index, false))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<Resource> response(Resource resource, boolean immutableAsset) {
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        CacheControl cacheControl = immutableAsset
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()
                : CacheControl.noCache();
        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .contentType(mediaType)
                .body(resource);
    }

    private Optional<Resource> resource(String path) {
        if (path.isBlank() || path.contains("..") || path.contains("\\")) {
            return Optional.empty();
        }
        Resource resource = new ClassPathResource(RESOURCE_ROOT + path);
        return resource.exists() && resource.isReadable()
                ? Optional.of(resource)
                : Optional.empty();
    }

    private String normalize(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return INDEX;
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private boolean hasFileExtension(String path) {
        int separator = path.lastIndexOf('/');
        return path.substring(separator + 1).contains(".");
    }
}
