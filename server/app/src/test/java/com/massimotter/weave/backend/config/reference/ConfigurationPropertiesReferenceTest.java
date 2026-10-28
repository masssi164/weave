package com.massimotter.weave.backend.config.reference;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.validation.annotation.Validated;

class ConfigurationPropertiesReferenceTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(InfrastructureComposition.class);

    @Test
    void explicitlyRegistersOneTypedRecordAndBindsDurationWithSafeDefaults() {
        runner.withPropertyValues(
                "weave.reference.infrastructure.instance-name=test-instance",
                "weave.reference.infrastructure.connect-timeout=750ms").run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(InfrastructureProperties.class);
                    assertThat(context.getBean(InfrastructureProperties.class))
                            .isEqualTo(new InfrastructureProperties("test-instance", Duration.ofMillis(750), 16));
                });
    }

    @Test
    void missingMandatoryInfrastructureSettingFailsAtStartup() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"instance-name=", "max-in-flight=0"})
    void invalidInfrastructureSettingFailsAtStartup(String invalid) {
        runner.withPropertyValues("weave.reference.infrastructure.instance-name=test-instance",
                "weave.reference.infrastructure." + invalid).run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
                });
    }

    @Validated
    @ConfigurationProperties("weave.reference.infrastructure")
    record InfrastructureProperties(
            @NotBlank String instanceName,
            @NotNull @DefaultValue("2s") Duration connectTimeout,
            @Min(1) @DefaultValue("16") int maxInFlight) {}

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(InfrastructureProperties.class)
    static class InfrastructureComposition {}
}
