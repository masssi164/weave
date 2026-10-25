package com.massimotter.weave.backend;

import com.massimotter.weave.backend.agentruntime.operator.RuntimeProfileSigningKeyCli;
import com.massimotter.weave.backend.agentruntime.operator.RuntimeStateWrappingKeyCli;
import com.massimotter.weave.backend.security.workload.config.WorkloadEntitlementProperties;
import com.massimotter.weave.backend.config.CalendarCalDavProperties;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import com.massimotter.weave.backend.config.ConnectorRuntimeProperties;
import com.massimotter.weave.backend.security.config.DeploymentOrganizationProperties;
import com.massimotter.weave.backend.config.DefaultSpaceBootstrapProperties;
import com.massimotter.weave.backend.config.GuestAccessProperties;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.config.InteropGatewayProperties;
import com.massimotter.weave.backend.config.LiveKitSfuProviderProperties;
import com.massimotter.weave.backend.config.MatrixChatProperties;
import com.massimotter.weave.backend.config.MigrationToolkitProperties;
import com.massimotter.weave.backend.discovery.config.PlatformContractProperties;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.capability.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.identity.migration.KeycloakRealmMigrationCli;
import com.massimotter.weave.backend.identity.migration.KeycloakRealmMigrationReceiptVerifierCli;
import com.massimotter.weave.backend.schema.SchemaAuthorityInitializer;
import com.massimotter.weave.backend.schema.SchemaReceiptVerifier;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Product runtime. Flyway is the sole schema authority; Hibernate validates the migrated schema. */
@SpringBootApplication
@org.springframework.context.annotation.ComponentScan(
        basePackages = "com.massimotter.weave.backend",
        excludeFilters = {
                @org.springframework.context.annotation.ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.CUSTOM,
                        classes = org.springframework.boot.context.TypeExcludeFilter.class),
                @org.springframework.context.annotation.ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.CUSTOM,
                        classes = org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter.class),
                @org.springframework.context.annotation.ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.REGEX,
                        pattern = "com\\.massimotter\\.weave\\.backend\\.files\\..*")
        })
@EnableScheduling
@EnableConfigurationProperties({
        CalendarCalDavProperties.class,
        WorkloadEntitlementProperties.class,
        ChatRuntimeProperties.class,
        ConnectorRuntimeProperties.class,
        DeploymentOrganizationProperties.class,
        DefaultSpaceBootstrapProperties.class,
        GuestAccessProperties.class,
        InteropGatewayProperties.class,
        IdentityInvitationProperties.class,
        LiveKitSfuProviderProperties.class,
        MatrixChatProperties.class,
        MigrationToolkitProperties.class,
        PlatformContractProperties.class,
        WeaveSecurityProperties.class,
        WorkspaceCapabilityProperties.class
})
public class WeaveBackendApplication {

    public static void main(String[] args) {
        if (args != null && args.length > 0) {
            String[] operatorArguments = Arrays.copyOfRange(args, 1, args.length);
            if ("runtime-profile-signing-keys".equals(args[0])) {
                RuntimeProfileSigningKeyCli.main(operatorArguments);
                return;
            }
            if ("runtime-state-wrapping-keys".equals(args[0])) {
                RuntimeStateWrappingKeyCli.main(operatorArguments);
                return;
            }
            if ("dogfood-prepare-members".equals(args[0])) {
                com.massimotter.weave.backend.dogfood.DogfoodPreparationCli.main(operatorArguments);
                return;
            }
            if ("schema-init".equals(args[0])) {
                SchemaAuthorityInitializer.main(operatorArguments);
                return;
            }
            if ("schema-receipt-check".equals(args[0])) {
                SchemaReceiptVerifier.main(operatorArguments);
                return;
            }
            if ("keycloak-realm-migration".equals(args[0])) {
                KeycloakRealmMigrationCli.main(operatorArguments);
                return;
            }
            if ("keycloak-realm-migration-receipt-check".equals(args[0])) {
                KeycloakRealmMigrationReceiptVerifierCli.main(operatorArguments);
                return;
            }
        }
        SpringApplication.run(WeaveBackendApplication.class, args);
    }
}
