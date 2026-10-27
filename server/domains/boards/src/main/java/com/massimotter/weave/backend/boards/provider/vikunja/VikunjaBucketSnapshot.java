package com.massimotter.weave.backend.boards.provider.vikunja;

public record VikunjaBucketSnapshot(
        long id,
        long projectId,
        String title,
        int position,
        Integer limit) {
}
