package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.COPIED;
import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import com.massimotter.weave.backend.files.port.FilesMutationPlan;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FilesMutationTargetCodecTest {

    @Test
    void matchesTheCanonicalCorpusRfc8785DigestVector() {
        Target target = new Target(
                0,
                COPIED,
                "file:source-42",
                "file:copy-84",
                "/reports/source.txt",
                "/archive/source.txt",
                Kind.FILE,
                ACTIVE,
                "blob:source-42-v7",
                12L,
                "text/plain",
                "sha256:" + "a".repeat(64),
                "file-version:7",
                "\"weave-source-7\"",
                Instant.parse("2026-08-20T08:00:00Z"),
                false,
                Instant.parse("2026-08-20T08:00:01Z"),
                ACTIVE,
                "blob:copy-84-v1",
                12,
                "text/plain",
                "sha256:" + "a".repeat(64),
                "file-version:1",
                "\"weave-copy-1\"",
                Instant.parse("2026-08-20T08:05:00Z"),
                false,
                Instant.parse("2026-08-20T08:05:00Z"));
        FilesMutationTargetCodec codec = new FilesMutationTargetCodec(new ObjectMapper());

        assertThat(codec.targetsDigest(List.of(target)))
                .isEqualTo("sha256:dd0360ac4b5c6aae844a90d59b771162ddb88c7aae9670b917e6ff5f79c4cb1b");
        assertThat(new String(codec.canonicalTargets(List.of(target)), StandardCharsets.UTF_8))
                .contains("\"resultObservedAt\":\"2026-08-20T08:05:00.000000Z\"")
                .contains("\"sourceModifiedAt\":\"2026-08-20T08:00:00.000000Z\"");
    }

    @Test
    void fenceDigestUsesTheExactRfc8785SnapshotAndOrderedArray() {
        var fence = FilesMutationPlan.Fence.present(
                0,
                FilesMutationPlan.FenceRole.REQUEST_TARGET,
                "/reports/ä.txt",
                "file:source-42",
                Kind.FILE,
                ACTIVE,
                9,
                "\"weave-source-7\"",
                FilesMutationPlan.subtreeMembershipDigest(List.of(
                        new FilesMutationPlan.Membership("/reports/ä.txt", "file:source-42"))));
        FilesMutationTargetCodec codec = new FilesMutationTargetCodec(new ObjectMapper());

        assertThat(codec.fencesDigest(List.of(fence))).startsWith("sha256:").hasSize(71);
        assertThat(codec.canonicalFencesJson(List.of(fence)))
                .contains("\"fenceVersion\":\"weave.files-mutation-fence/v1\"")
                .contains("\"expectedRowVersion\":9")
                .contains("\"snapshotDigest\":\"" + fence.snapshotDigest() + "\"");
    }
}
