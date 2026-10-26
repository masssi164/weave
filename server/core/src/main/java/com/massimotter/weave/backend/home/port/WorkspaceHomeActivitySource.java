package com.massimotter.weave.backend.home.port;

import com.massimotter.weave.backend.home.api.WorkspaceHomeRecentActivityResponse;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

/** A domain projects only completed, resource-authorized, support-safe activity for this caller. */
public interface WorkspaceHomeActivitySource {
    String domainId();
    List<WorkspaceHomeRecentActivityResponse> recentActivity(Jwt jwt);
}
