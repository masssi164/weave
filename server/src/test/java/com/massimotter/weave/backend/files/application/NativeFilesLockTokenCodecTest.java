package com.massimotter.weave.backend.files.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeFilesLockTokenCodecTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void derivesRestartStablePurposeBoundAcquireTokensFromMountedHmacAuthority() throws Exception {
        Path firstSecret = secret("first", "0123456789abcdef0123456789abcdef");
        Path secondSecret = secret("second", "abcdef0123456789abcdef0123456789");
        NativeFilesLockTokenCodec first = codec(firstSecret);
        NativeFilesLockTokenCodec restarted = codec(firstSecret);
        NativeFilesLockTokenCodec rotated = codec(secondSecret);

        String token = first.acquireToken("org:alpha", "operation:lock:one");

        assertThat(token)
                .startsWith("opaquelocktoken:cur_")
                .isEqualTo(restarted.acquireToken("org:alpha", "operation:lock:one"))
                .isNotEqualTo(first.acquireToken("org:alpha", "operation:lock:two"))
                .isNotEqualTo(first.acquireToken("org:beta", "operation:lock:one"))
                .isNotEqualTo(rotated.acquireToken("org:alpha", "operation:lock:one"))
                .doesNotContain("operation:lock:one");
        assertThat(first.digest(token)).matches("sha256:[a-f0-9]{64}");
    }

    private Path secret(String name, String value) throws Exception {
        Path path = temporaryDirectory.resolve(name + "-hmac-key");
        Files.writeString(path, value);
        return path;
    }

    private NativeFilesLockTokenCodec codec(Path secret) {
        IdentityInvitationProperties properties = new IdentityInvitationProperties();
        properties.keycloak().setReferenceHmacSecretFile(secret.toString());
        return new NativeFilesLockTokenCodec(new IdentityOpaqueReferenceCodec(properties));
    }
}
