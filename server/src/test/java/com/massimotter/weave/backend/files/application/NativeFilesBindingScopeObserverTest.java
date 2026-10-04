package com.massimotter.weave.backend.files.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding.State;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeFilesBindingScopeObserverTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Test
    void provisionsOnlyTheDefaultScopeOfAReconciledNativeBinding() {
        List<Provisioning> calls = new ArrayList<>();
        NativeFilesBindingScopeObserver observer = new NativeFilesBindingScopeObserver(
                (scope, provisionedAt) -> calls.add(new Provisioning(scope, provisionedAt)));

        observer.reconciled(binding("nextcloud-webdav"), NOW.minusSeconds(1));
        observer.reconciled(binding("weave-native"), NOW);

        assertThat(calls).containsExactly(new Provisioning(
                new FilesScope("org:bootstrap", NativeFilesScopeProvisioner.DEFAULT_SPACE_REF),
                NOW));
    }

    private ProviderBinding binding(String adapterKey) {
        return new ProviderBinding(
                "org:bootstrap",
                "files",
                1,
                adapterKey,
                "profile:" + adapterKey,
                State.ACTIVE,
                NOW);
    }

    private record Provisioning(FilesScope scope, Instant provisionedAt) {
    }
}
