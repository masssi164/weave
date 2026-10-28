package com.massimotter.weave.backend.files.spaces;

import com.massimotter.weave.backend.capability.api.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.identity.OrganizationIdentityContext;
import com.massimotter.weave.backend.spaces.preparation.DefaultSpacePreparationContribution;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;

@Order(10)
public class FilesDefaultSpacePreparationContribution implements DefaultSpacePreparationContribution {
    @Override public String domain() { return "files"; }
    @Override public List<String> requiredCapabilities() { return List.of("files.read", "files.upload"); }
    @Override public boolean ready(WorkspaceCapabilitiesResponse snapshot) {
        return snapshot != null && DefaultSpacePreparationContribution.ready(snapshot.domains().get("files"));
    }
    @Override public Optional<Runnable> captureAuthorityRecheck(OrganizationIdentityContext owner) {
        // Files retains its existing capability-snapshot precondition; no provider mutation is performed here.
        return Optional.empty();
    }
}
