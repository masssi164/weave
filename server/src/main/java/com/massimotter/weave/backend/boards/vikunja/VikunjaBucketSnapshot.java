package com.massimotter.weave.backend.boards.vikunja;

public record VikunjaBucketSnapshot(
        long id,
        long projectId,
        String title,
        int position,
        Integer limit) {
}
