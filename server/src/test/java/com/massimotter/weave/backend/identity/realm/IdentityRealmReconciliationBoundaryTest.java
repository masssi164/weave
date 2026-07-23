package com.massimotter.weave.backend.identity.realm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class IdentityRealmReconciliationBoundaryTest {

    @Test
    void serverReviewPolicyCarriesNoKeycloakAdminLocationOrCredential() {
        assertThat(Arrays.stream(IdentityRealmApplyProperties.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName))
                .containsExactly("dryRunFreshnessSeconds")
                .noneMatch(name -> name.toLowerCase().contains("token")
                        || name.toLowerCase().contains("admin")
                        || name.toLowerCase().contains("url"));
    }

    @Test
    void removedLiveMutationClassesCannotBeLoaded() {
        for (String className : java.util.List.of(
                "com.massimotter.weave.backend.identity.realm.KeycloakRealmLiveApplyAdapter",
                "com.massimotter.weave.backend.identity.realm.HttpKeycloakRealmAdminClient",
                "com.massimotter.weave.backend.identity.realm.KeycloakRealmAdminClient")) {
            assertThat(loadable(className)).isFalse();
        }
    }

    private static boolean loadable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException expected) {
            return false;
        }
    }
}
