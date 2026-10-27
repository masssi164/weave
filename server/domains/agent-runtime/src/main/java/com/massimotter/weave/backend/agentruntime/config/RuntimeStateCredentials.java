package com.massimotter.weave.backend.agentruntime.config;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

/** Resolves the mounted state credentials once, at serving readiness or first provider use. */
final class RuntimeStateCredentials implements AwsCredentialsProvider {
    private final AgentRuntimeStateStoreProperties properties;
    private AwsCredentials credentials;

    RuntimeStateCredentials(AgentRuntimeStateStoreProperties properties) {
        this.properties = properties;
    }

    @Override
    public synchronized AwsCredentials resolveCredentials() {
        if (credentials == null) {
            credentials = AwsBasicCredentials.create(properties.readAccessKey(), properties.readSecretKey());
        }
        return credentials;
    }
}
