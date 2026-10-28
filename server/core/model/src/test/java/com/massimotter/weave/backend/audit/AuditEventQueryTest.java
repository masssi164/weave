package com.massimotter.weave.backend.audit;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEventQueryTest {
    @Test
    void auditQueriesCannotBecomeUnscopedOrUnbounded() {
        for (var organization : new String[] {null, "", " "}) {
            assertThatThrownBy(() -> AuditEventQuery.recent(organization))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (int size : new int[] {0, -1, 201, Integer.MAX_VALUE}) {
            assertThatThrownBy(() -> new AuditEventQuery("tenant-a", List.of(), List.of(), 0, size))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new AuditEventQuery("tenant-a", List.of(), List.of(), -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
