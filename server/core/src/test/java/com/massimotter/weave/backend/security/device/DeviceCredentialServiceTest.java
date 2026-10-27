package com.massimotter.weave.backend.security.device;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceCredentialServiceTest {

    @Test
    void absentDomainCannotIssueCredentialsAndDuplicateOwnershipFails() {
        var repository = new InMemoryDeviceCredentialRepository();
        var service = new DeviceCredentialService(repository, java.util.List.of());
        assertThatThrownBy(() -> service.issue("files", "tenant", "principal", "subject", "username",
                "webdav", "label", Set.of("files.read")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not supported");
        assertThatThrownBy(() -> new DeviceCredentialService(repository, java.util.List.of(
                new DeviceCredentialDomain("files"), new DeviceCredentialDomain("files"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicated");
    }

    @Test
    void storesOnlyAdaptiveHashAndAuthenticatesExactDomainSecret() {
        InMemoryDeviceCredentialRepository repository = new InMemoryDeviceCredentialRepository();
        DeviceCredentialService service = com.massimotter.weave.backend.security.device.DeviceCredentialTestSupport.service(repository);

        IssuedDeviceCredential issued = service.issue(
                "files",
                "tenant-1",
                "user:1",
                "subject-1",
                "massimo",
                "webdav",
                "Finder",
                Set.of("files.read", "files.upload"));
        DeviceCredential stored = repository.findById(issued.credential().credentialId()).orElseThrow();
        assertThat(issued.toString()).doesNotContain(issued.secret()).isEqualTo("IssuedDeviceCredential[redacted]");
        assertThat(stored.toString()).doesNotContain(stored.secretHash()).isEqualTo("DeviceCredential[redacted]");

        assertThat(issued.secret()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(stored.secretHash())
                .isNotEqualTo(issued.secret())
                .doesNotContain(issued.secret())
                .hasSizeGreaterThan(40);
        assertThat(service.authenticate("files", stored.credentialId(), issued.secret()))
                .isEqualTo(stored);
        assertThatThrownBy(() -> service.authenticate("calendar", stored.credentialId(), issued.secret()))
                .isInstanceOf(DeviceCredentialException.class);
        assertThatThrownBy(() -> service.authenticate("files", stored.credentialId(), "wrong-secret"))
                .isInstanceOf(DeviceCredentialException.class);
    }

    @Test
    void revocationIsIndependentAndImmediatelyBlocksAuthentication() {
        InMemoryDeviceCredentialRepository repository = new InMemoryDeviceCredentialRepository();
        DeviceCredentialService service = com.massimotter.weave.backend.security.device.DeviceCredentialTestSupport.service(repository);
        IssuedDeviceCredential first = service.issue(
                "calendar", "tenant-1", "user:1", "subject-1", "massimo", "caldav", "Laptop",
                Set.of("calendar.read"));
        IssuedDeviceCredential second = service.issue(
                "calendar", "tenant-1", "user:1", "subject-1", "massimo", "caldav", "Phone",
                Set.of("calendar.read"));

        service.revoke("calendar", first.credential().credentialId(), "user:1");

        assertThatThrownBy(() -> service.authenticate(
                "calendar", first.credential().credentialId(), first.secret()))
                .isInstanceOf(DeviceCredentialException.class);
        assertThat(service.authenticate("calendar", second.credential().credentialId(), second.secret()).label())
                .isEqualTo("Phone");
        assertThat(service.list("calendar", "user:1")).hasSize(2);
        assertThat(service.list("calendar", "user:other")).isEmpty();
    }
}
