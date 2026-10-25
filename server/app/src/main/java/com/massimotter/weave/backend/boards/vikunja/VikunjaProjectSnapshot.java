package com.massimotter.weave.backend.boards.vikunja;

import java.net.URI;

public record VikunjaProjectSnapshot(
        long id,
        String title,
        String description,
        boolean archived,
        URI webUrl) {
}
