package com.massimotter.weave.backend.agentruntime.config;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.readiness.RuntimeStateReadiness;
import com.massimotter.weave.backend.agentruntime.security.FileRuntimeStateKeyWrapper;
import com.massimotter.weave.backend.agentruntime.security.FileSecretStoreAccess;
import com.massimotter.weave.backend.agentruntime.persistence.S3EncryptedRuntimeStateStore;
import com.massimotter.weave.backend.agentruntime.persistence.RuntimeStateJpaAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateKeyWrapper;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentRuntimeStateStoreProperties.class)
@ConditionalOnProperty(name = "weave.agent-runtime.state-store.enabled", havingValue = "true")
public class AgentRuntimeStateStoreConfiguration {

    @Bean
    FileRuntimeStateKeyWrapper fileRuntimeStateKeyWrapper(
            AgentRuntimeStateStoreProperties properties,
            ObjectMapper objectMapper) {
        return new FileRuntimeStateKeyWrapper(
                properties.requiredWrappingKeyRoot(),
                objectMapper,
                Clock.systemUTC(),
                new SecureRandom(),
                FileSecretStoreAccess.READ_ONLY);
    }

    @Bean
    RuntimeStateCredentials runtimeStateCredentials(AgentRuntimeStateStoreProperties properties) {
        return new RuntimeStateCredentials(properties);
    }

    @Bean
    AgentRuntimeSecretReadiness runtimeStateSecretReadiness(
            FileRuntimeStateKeyWrapper keys,
            RuntimeStateCredentials credentials) {
        return new AgentRuntimeSecretReadiness(() -> {
            keys.requireReady();
            credentials.resolveCredentials();
        });
    }

    @Bean(destroyMethod = "close")
    S3Client runtimeStateS3Client(
            AgentRuntimeStateStoreProperties properties,
            RuntimeStateCredentials credentials) {
        properties.requiredCredentialRef();
        return S3Client.builder()
                .endpointOverride(properties.requiredEndpoint())
                .region(Region.of(properties.requiredRegion()))
                .credentialsProvider(credentials)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        .build())
                .build();
    }

    @Bean
    RuntimeStateReadiness runtimeStateReadiness(S3EncryptedRuntimeStateStore store) {
        return new RuntimeStateReadiness(store);
    }

    @Bean
    S3EncryptedRuntimeStateStore s3EncryptedRuntimeStateStore(
            RuntimeStateJpaAuthority authority,
            PlatformTransactionManager transactionManager,
            S3Client runtimeStateS3Client,
            RuntimeStateKeyWrapper keyWrapper,
            AgentRuntimeStateStoreProperties properties) {
        return new S3EncryptedRuntimeStateStore(
                authority,
                transactionManager,
                runtimeStateS3Client,
                properties.requiredBucket(),
                keyWrapper,
                new SecureRandom(),
                Clock.systemUTC(),
                properties.requiredMaximumGenerationBytes());
    }
}
