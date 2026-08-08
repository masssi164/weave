package com.massimotter.weave.backend;

import com.massimotter.weave.backend.agentruntime.operator.RuntimeProfileSigningKeyCli;
import com.massimotter.weave.backend.agentruntime.operator.RuntimeStateWrappingKeyCli;
import com.massimotter.weave.backend.config.CalendarCalDavProperties;
import com.massimotter.weave.backend.config.AgentRuntimeEntitlementProperties;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import com.massimotter.weave.backend.config.ConnectorRuntimeProperties;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.FilesRuntimeProperties;
import com.massimotter.weave.backend.config.GuestAccessProperties;
import com.massimotter.weave.backend.config.InteropGatewayProperties;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.config.LiveKitSfuProviderProperties;
import com.massimotter.weave.backend.config.MatrixChatProperties;
import com.massimotter.weave.backend.config.MigrationToolkitProperties;
import com.massimotter.weave.backend.config.NextcloudFilesProperties;
import com.massimotter.weave.backend.config.PlatformContractProperties;
import com.massimotter.weave.backend.config.ProviderHealthProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.identity.migration.KeycloakRealmMigrationCli;
import com.massimotter.weave.backend.identity.migration.KeycloakRealmMigrationReceiptVerifierCli;
import com.massimotter.weave.backend.schema.SchemaAuthorityInitializer;
import com.massimotter.weave.backend.schema.SchemaReceiptVerifier;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        CalendarCalDavProperties.class,
        AgentRuntimeEntitlementProperties.class,
        ChatRuntimeProperties.class,
        ConnectorRuntimeProperties.class,
        ContextAuthorizationProperties.class,
        FilesRuntimeProperties.class,
        GuestAccessProperties.class,
        InteropGatewayProperties.class,
        IdentityInvitationProperties.class,
        LiveKitSfuProviderProperties.class,
        MatrixChatProperties.class,
        MigrationToolkitProperties.class,
        NextcloudFilesProperties.class,
        PlatformContractProperties.class,
        ProviderHealthProperties.class,
        WeaveSecurityProperties.class,
        WeaveNativeFilesProperties.class,
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
