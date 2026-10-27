package com.massimotter.weave.backend.boards.port;

public record BoardQuery(String cursor, int limit) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 250;

    public BoardQuery {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    public static BoardQuery firstPage() {
        return new BoardQuery(null, DEFAULT_LIMIT);
    }
}
