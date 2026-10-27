package com.massimotter.weave.backend.calendar.spaces;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.calendar.application.CalendarUserApiService;
import com.massimotter.weave.backend.spaces.relationship.SpaceRelationshipContribution;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;

public class CalendarSpaceRelationshipContribution implements SpaceRelationshipContribution {
    private final CalendarUserApiService calendar;
    public CalendarSpaceRelationshipContribution(CalendarUserApiService calendar) { this.calendar = calendar; }
    @Override public String domain() { return "calendar"; }
    @Override public String relationPrefix() { return "relation:event:"; }
    @Override public String targetKind() { return "EVENT"; }
    @Override public boolean validTargetId(String value) { return value != null && value.matches("event:[0-9a-f]{64}"); }

    @Override
    public List<Target> list(Context context, String afterTargetId, int limit) {
        var visible = new ArrayList<Target>();
        String after = afterTargetId;
        int scanned = 0;
        while (visible.size() < limit) {
            List<String> candidates;
            try {
                candidates = calendar.materializedEventRefsInSpace(context.jwt(), context.spaceRef(), after, 100);
            } catch (ApiErrorException denied) {
                if (denied.status() == HttpStatus.FORBIDDEN) break;
                throw new Unavailable();
            }
            if (candidates == null || candidates.size() > 100) throw new Unavailable();
            for (String eventId : candidates) {
                if (!validTargetId(eventId) || eventId.compareTo(after) <= 0) throw new Unavailable();
                after = eventId;
                try {
                    var checked = calendar.readMaterializedEventInSpace(context.jwt(), context.spaceRef(), eventId);
                    if (checked == null || !eventId.equals(checked.id())) throw new Unavailable();
                } catch (ApiErrorException unavailable) {
                    if (unavailable.status() == HttpStatus.NOT_FOUND) continue;
                    throw new Unavailable();
                }
                visible.add(new Target(eventId, eventId));
                if (visible.size() == limit) return List.copyOf(visible);
            }
            scanned += candidates.size();
            if (candidates.size() < 100) break;
            if (scanned >= 1000) throw new Unavailable();
        }
        return List.copyOf(visible);
    }
}
