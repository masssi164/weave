package com.massimotter.weave.backend;

import com.massimotter.weave.backend.agentruntime.operator.RuntimeProfileSigningKeyCli;
import com.massimotter.weave.backend.agentruntime.operator.RuntimeStateWrappingKeyCli;
import com.massimotter.weave.backend.config.CalendarCalDavProperties;
import com.massimotter.weave.backend.config.AgentRuntimeEntitlementProperties;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import com.massimotter.weave.backend.config.ConnectorRuntimeProperties;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
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
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import java.util.Arrays;
import com.massimotter.weave.shared.persistence.SharedSchemaReadinessConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableScheduling
@Import(SharedSchemaReadinessConfiguration.class)
@EnableConfigurationProperties({
        CalendarCalDavProperties.class,
        AgentRuntimeEntitlementProperties.class,
        ChatRuntimeProperties.class,
        ConnectorRuntimeProperties.class,
        ContextAuthorizationProperties.class,
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
        }
        SpringApplication.run(WeaveBackendApplication.class, args);
    }
}
