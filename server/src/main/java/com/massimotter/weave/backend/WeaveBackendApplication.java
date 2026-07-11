package com.massimotter.weave.backend;

import com.massimotter.weave.backend.config.CalendarCalDavProperties;
import com.massimotter.weave.backend.config.ConnectorRuntimeProperties;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.GuestAccessProperties;
import com.massimotter.weave.backend.config.InteropGatewayProperties;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.config.LiveKitMeetingsProviderProperties;
import com.massimotter.weave.backend.config.MatrixChatProperties;
import com.massimotter.weave.backend.config.MigrationToolkitProperties;
import com.massimotter.weave.backend.config.NextcloudFilesProperties;
import com.massimotter.weave.backend.config.PlatformContractProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        CalendarCalDavProperties.class,
        ConnectorRuntimeProperties.class,
        ContextAuthorizationProperties.class,
        GuestAccessProperties.class,
        InteropGatewayProperties.class,
        IdentityInvitationProperties.class,
        LiveKitMeetingsProviderProperties.class,
        MatrixChatProperties.class,
        MigrationToolkitProperties.class,
        NextcloudFilesProperties.class,
        PlatformContractProperties.class,
        WeaveSecurityProperties.class,
        WeaverRuntimeProperties.class,
        WorkspaceCapabilityProperties.class
})
public class WeaveBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeaveBackendApplication.class, args);
    }
}
