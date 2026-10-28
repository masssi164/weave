package com.massimotter.weave.backend.boards.port;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record BoardPage<T>(List<T> items, String nextCursor) {

    public BoardPage {
        items = List.copyOf(requireNonNull(items, "items must not be null"));
    }

    public static <T> BoardPage<T> singlePage(List<T> items) {
        return new BoardPage<>(items, null);
    }
}
