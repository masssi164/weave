package com.massimotter.weave.backend.files.spaces;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.files.port.FilesUserResourceRepository;
import com.massimotter.weave.backend.service.files.FilesUserApiService;
import com.massimotter.weave.backend.spaces.relationship.SpaceRelationshipContribution;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class FilesSpaceRelationshipContribution implements SpaceRelationshipContribution {
    private final FilesUserResourceRepository resources;
    private final FilesUserApiService files;
    public FilesSpaceRelationshipContribution(FilesUserResourceRepository resources, FilesUserApiService files) {
        this.resources = resources; this.files = files;
    }
    @Override public String domain() { return "files"; }
    @Override public String relationPrefix() { return "relation:file:"; }
    @Override public String targetKind() { return "FILE"; }
    @Override public boolean validTargetId(String value) {
        return value != null && value.startsWith("file:") && value.length() <= 255 && value.chars().noneMatch(Character::isISOControl);
    }

    @Override
    public List<Target> list(Context context, String afterTargetId, int limit) {
        var candidates = resources.activeInSpace(context.organizationId(), context.spaceRef(), context.principalRef(), afterTargetId, limit);
        if (candidates == null || candidates.size() > limit) throw new Unavailable();
        var visible = new ArrayList<Target>();
        String after = afterTargetId;
        for (var candidate : candidates) {
            if (candidate == null || !validTargetId(candidate.fileId()) || candidate.fileId().compareTo(after) <= 0) throw new Unavailable();
            after = candidate.fileId();
            try {
                var checked = files.inspect(context.jwt(), candidate.fileId());
                if (checked == null || !candidate.fileId().equals(checked.fileId()) || !checked.allowedActions().contains("inspect")) throw new Unavailable();
            } catch (ApiErrorException denied) {
                if (denied.status() == HttpStatus.FORBIDDEN) break;
                throw new Unavailable();
            }
            visible.add(new Target(candidate.fileId(), candidate.fileId()));
        }
        return List.copyOf(visible);
    }
}
