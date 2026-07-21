package com.massimotter.weave.backend.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "weave.agent-runtime.state-store")
public class AgentRuntimeStateStoreProperties {
    private boolean enabled;
    private Path wrappingKeyRoot;
    private DataSize chunkSize = DataSize.ofMegabytes(1);
    private DataSize maximumGenerationSize = DataSize.ofMegabytes(64);

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path wrappingKeyRoot() {
        return wrappingKeyRoot;
    }

    public void setWrappingKeyRoot(Path wrappingKeyRoot) {
        this.wrappingKeyRoot = wrappingKeyRoot;
    }

    public DataSize chunkSize() {
        return chunkSize;
    }

    public void setChunkSize(DataSize chunkSize) {
        this.chunkSize = chunkSize;
    }

    public DataSize maximumGenerationSize() {
        return maximumGenerationSize;
    }

    public void setMaximumGenerationSize(DataSize maximumGenerationSize) {
        this.maximumGenerationSize = maximumGenerationSize;
    }

    public Path requiredWrappingKeyRoot() {
        if (wrappingKeyRoot == null) {
            throw new IllegalStateException(
                    "RuntimeStateStore requires an explicit operator-mounted wrapping-key SecretRef root");
        }
        return wrappingKeyRoot;
    }

    public int requiredChunkBytes() {
        long value = chunkSize == null ? 0 : chunkSize.toBytes();
        if (value < 4_096 || value > 4L * 1024L * 1024L) {
            throw new IllegalStateException("RuntimeStateStore chunk size must be between 4KiB and 4MiB");
        }
        return Math.toIntExact(value);
    }

    public long requiredMaximumGenerationBytes() {
        long value = maximumGenerationSize == null ? 0 : maximumGenerationSize.toBytes();
        if (value < requiredChunkBytes() || value > 1024L * 1024L * 1024L) {
            throw new IllegalStateException(
                    "RuntimeStateStore maximum generation size must be at least one chunk and at most 1GiB");
        }
        return value;
    }
}
