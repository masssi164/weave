package com.massimotter.weave.backend.security.workload.port;

import com.massimotter.weave.backend.security.workload.domain.ReleaseMcpBinding;
import java.util.Optional;

/** Re-reads protected binding state for each invocation; unavailable state fails closed. */
public interface ReleaseMcpBindingRepository {
    Optional<ReleaseMcpBinding> findByWorkload(String issuer, String subject);
}
