package com.massimotter.weave.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimeStateKeyWrapper;
import com.massimotter.weave.backend.agentruntime.adapter.S3EncryptedRuntimeStateStore;
import com.massimotter.weave.backend.agentruntime.adapter.RuntimeStateJpaAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateKeyWrapper;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
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
                new SecureRandom());
    }

    @Bean(destroyMethod = "close")
    S3Client runtimeStateS3Client(
            AgentRuntimeStateStoreProperties properties,
            Environment environment) {
        rejectReleaseActivation(environment);
        properties.requiredCredentialRef();
        return S3Client.builder()
                .endpointOverride(properties.requiredEndpoint())
                .region(Region.of(properties.requiredRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        properties.readAccessKey(), properties.readSecretKey())))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        .build())
                .build();
    }

    static void rejectReleaseActivation(Environment environment) {
        java.util.Set<String> releaseProfiles = java.util.Set.of("dogfood", "main", "prod");
        boolean releaseProfile = java.util.Arrays.stream(environment.getActiveProfiles())
                .anyMatch(releaseProfiles::contains);
        String declaredProfile = environment.getProperty("weave.deployment.profile", "");
        if (releaseProfile || releaseProfiles.contains(declaredProfile)) {
            throw new IllegalStateException(
                    "RuntimeStateStore remains Guarded until durable cross-store reconciliation evidence passes");
        }
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
