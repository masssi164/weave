package com.massimotter.weave.backend.context.authz;

/**
 * Context graph edge relations that can project membership across Context/Space templates.
 */
public enum ContextGraphRelation {
    CONTAINS,
    LINKED_TO,
    CALENDAR_FOR,
    BOARD_FOR,
    FILES_FOR,
    THREAD_FOR,
    IMPORTS_FROM
}
