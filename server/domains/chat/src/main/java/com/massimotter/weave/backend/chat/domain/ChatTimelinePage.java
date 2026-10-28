package com.massimotter.weave.backend.chat.domain;

/** A bounded canonical history page with an opaque earlier-event cursor. */
public record ChatTimelinePage(ChatTimeline timeline, String nextBackwardCursor, boolean hasEarlier) {
    public ChatTimelinePage {
        if (timeline == null || nextBackwardCursor == null
                || !nextBackwardCursor.matches("timeline-revision-[0-9]+")) {
            throw new IllegalArgumentException("canonical Chat timeline page is invalid");
        }
    }
}
