package com.massimotter.weave.backend.discovery.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanonicalDomainRegistryTest {
    @Test
    void onlySuppliedOwnersAppearInStableOrder() {
        var registry = new CanonicalDomainRegistry(List.of(domain("second", 2, List.of("old-second")),
                domain("first", 1, List.of())));
        assertThat(registry.domains()).extracting(value -> value.key()).containsExactly("first", "second");
        assertThat(registry.snapshot().compatibilityAliases()).containsExactlyEntriesOf(Map.of("old-second", "second"));
        assertThat(new CanonicalDomainRegistry(List.of()).snapshot().domains()).isEmpty();
    }

    @Test
    void duplicateKeysAliasesAndShadowingFailClosed() {
        var one = domain("one", 0, List.of("old"));
        assertThatThrownBy(() -> new CanonicalDomainRegistry(List.of(one, one)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate canonical domain");
        assertThatThrownBy(() -> new CanonicalDomainRegistry(List.of(one, domain("two", 1, List.of("old")))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate canonical alias");
        assertThatThrownBy(() -> new CanonicalDomainRegistry(List.of(one, domain("old", 1, List.of()))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("shadow");
    }

    private static CanonicalDomainContribution domain(String key, int order, List<String> aliases) {
        return new CanonicalDomainContribution(key, order, CanonicalDomainRegistry.entry(key, key, "Declared purpose",
                List.of("Object"), aliases, CanonicalDomainRegistry.standardCapabilities(key), Map.of()));
    }
}
