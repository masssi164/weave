package com.massimotter.weave.backend.notifications.discovery;

import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NotificationsDiscoveryConfiguration {

    @Bean
    CanonicalDomainContribution notificationsNotificationsDomain() {
        return NotificationsDiscoveryMetadata.notificationsDomain();
    }
}
