package com.massimotter.weave.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SchemaAuthorityInitializerConfigurationTest {

    @Test
    void oneShotInitializerPinsEveryFailClosedFlywaySetting() {
        var configuration = SchemaAuthorityInitializer.configuredFlyway(
                        "jdbc:postgresql://127.0.0.1:1/weave",
                        "weave_backend_migrator",
                        "not-used")
                .getConfiguration();

        assertThat(configuration.isBaselineOnMigrate()).isFalse();
        assertThat(configuration.isCleanDisabled()).isTrue();
        assertThat(configuration.isOutOfOrder()).isFalse();
        assertThat(configuration.isValidateOnMigrate()).isTrue();
        assertThat(configuration.isValidateMigrationNaming()).isTrue();
    }
}
