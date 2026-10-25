package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.security.device.DeviceCredentialDomain;
import com.massimotter.weave.backend.security.device.DeviceCredentialService;
import com.massimotter.weave.backend.security.device.JpaDeviceCredentialRepository;
import com.massimotter.weave.backend.security.persistence.DeviceCredentialJpaRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/** Shared credential hashing/storage; only installed domains declare permitted issuance scopes. */
@AutoConfiguration(after = CorePersistenceAutoConfiguration.class,
        afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CoreDeviceSecurityAutoConfiguration {
    @Bean
    JpaDeviceCredentialRepository jpaDeviceCredentialRepository(
            DeviceCredentialJpaRepository repository, ObjectMapper mapper) {
        return new JpaDeviceCredentialRepository(repository, mapper);
    }

    @Bean
    DeviceCredentialService deviceCredentialService(JpaDeviceCredentialRepository repository,
            List<DeviceCredentialDomain> domains) {
        return new DeviceCredentialService(repository, domains);
    }
}
