package com.massimotter.weave.backend.boards.domain;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;

final class BoardsContract {

    private BoardsContract() {
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    static URI optionalUri(URI value) {
        return value;
    }

    static Instant optionalInstant(Instant value) {
        return value;
    }

    static <T> List<T> immutableList(List<T> value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return List.copyOf(value);
    }

    static <T> Set<T> immutableSet(Set<T> value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return Set.copyOf(value);
    }
}
