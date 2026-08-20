package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;

/** Stable append-only audit projection shared by request execution and crash recovery. */
public final class NativeFilesOperationAudit {

    public static final String DEFAULT_CONTEXT_ID = "workspace-default";

    private NativeFilesOperationAudit() {
    }

    public static String publish(AuditEventPublisher publisher, OperationIntent intent) {
        Objects.requireNonNull(publisher, "publisher must not be null");
        Objects.requireNonNull(intent, "intent must not be null");
        if (!(intent.projection() instanceof OperationIntent.ProtocolProjection projection)) {
            throw new IllegalArgumentException(
                    "native Files mutation requires one protocol projection");
        }
        String auditRef = "files-operation-intent:" + intent.operationRef();
        publisher.publish(new AuditEvent(
                intent.organizationRef(),
                DEFAULT_CONTEXT_ID,
                intent.actor().personRef(),
                "files:operation-intent",
                AuditAction.FILES_OPERATION_INTENT_RECORDED,
                intent.createdAt().truncatedTo(ChronoUnit.MICROS),
                auditRef,
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "domain", "files",
                        "operation", projection.operation(),
                        "operationRef", intent.operationRef(),
                        "providerBindingRevision", Long.toString(intent.providerBindingRevision()),
                        "result", "recorded",
                        "supportSafe", true)));
        return auditRef;
    }
}
