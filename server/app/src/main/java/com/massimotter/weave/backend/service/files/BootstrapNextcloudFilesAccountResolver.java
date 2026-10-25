package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.NextcloudFilesProperties;
import com.massimotter.weave.backend.config.FilesProviderAccountsProperties;
import com.massimotter.weave.backend.config.FilesProviderAccountsProperties.NextcloudAccount;
import com.massimotter.weave.backend.providerbinding.application.ProviderBindingBootstrapProperties;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Restricts the legacy single Nextcloud credential to its explicit bootstrap binding only. */
@Component
public final class BootstrapNextcloudFilesAccountResolver implements NextcloudFilesAccountResolver {

    private final ProviderBindingBootstrapProperties bootstrap;
    private final NextcloudFilesProperties account;
    private final Path secretRoot;
    private final Map<AccountKey, NextcloudAccount> provisioned;

    @Autowired
    public BootstrapNextcloudFilesAccountResolver(
            ProviderBindingBootstrapProperties bootstrap,
            NextcloudFilesProperties account,
            FilesProviderAccountsProperties accounts) {
        this.bootstrap = bootstrap;
        this.account = account;
        this.secretRoot = requiredRoot(accounts.secretRoot());
        Map<AccountKey, NextcloudAccount> byKey = new HashMap<>();
        for (NextcloudAccount candidate : accounts.nextcloud()) {
            AccountKey key = new AccountKey(candidate.organizationRef(), candidate.configurationRef());
            validate(candidate);
            if (byKey.putIfAbsent(key, candidate) != null) {
                throw new IllegalArgumentException("Duplicate Nextcloud Files account configuration");
            }
            if (matchesBootstrap(key)) {
                throw new IllegalArgumentException("Provisioned Files account conflicts with the bootstrap binding");
            }
        }
        this.provisioned = Map.copyOf(byKey);
    }

    public BootstrapNextcloudFilesAccountResolver(
            ProviderBindingBootstrapProperties bootstrap,
            NextcloudFilesProperties account) {
        this(bootstrap, account, new FilesProviderAccountsProperties("/run/secrets/weave-files", null));
    }

    @Override
    public Optional<NextcloudFilesProperties> resolve(String organizationRef, String configurationRef) {
        NextcloudAccount candidate = provisioned.get(new AccountKey(organizationRef, configurationRef));
        if (candidate != null) {
            return mountedCredential(candidate)
                    .map(secret -> new NextcloudFilesProperties(
                            candidate.baseUrl(), candidate.webdavRootPath(),
                            "backend-service-account", candidate.actorUsername(), secret));
        }
        if (!available()
                || !matchesBootstrap(new AccountKey(organizationRef, configurationRef))) {
            return Optional.empty();
        }
        return Optional.of(account);
    }

    @Override
    public boolean available() {
        return !provisioned.isEmpty() || bootstrapAvailable();
    }

    private boolean bootstrapAvailable() {
        return bootstrap.enabled()
                && "nextcloud-webdav".equals(bootstrap.adapterKey())
                && bootstrap.organizationRef() != null
                && !bootstrap.organizationRef().isBlank()
                && bootstrap.configurationRef() != null
                && !bootstrap.configurationRef().isBlank()
                && account.isConfigured();
    }

    private boolean matchesBootstrap(AccountKey key) {
        return bootstrapAvailable()
                && bootstrap.organizationRef().trim().equals(key.organizationRef())
                && bootstrap.configurationRef().trim().equals(key.configurationRef());
    }

    private static Path requiredRoot(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Files provider SecretRef root is required");
        }
        Path root = Path.of(value);
        if (!root.isAbsolute()) {
            throw new IllegalArgumentException("Files provider SecretRef root must be absolute");
        }
        return root.normalize();
    }

    private static void validate(NextcloudAccount candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("Nextcloud Files account is required");
        }
        new AccountKey(candidate.organizationRef(), candidate.configurationRef());
        required(candidate.actorUsername());
        URI endpoint = URI.create(required(candidate.baseUrl()));
        if (!endpoint.isAbsolute() || endpoint.getHost() == null
                || !("https".equalsIgnoreCase(endpoint.getScheme())
                        || "http".equalsIgnoreCase(endpoint.getScheme()))
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("Nextcloud Files endpoint is invalid");
        }
        if (!required(candidate.webdavRootPath()).startsWith("/")) {
            throw new IllegalArgumentException("Nextcloud Files DAV root must be absolute");
        }
        credentialName(candidate.credentialRef());
    }

    private Optional<String> mountedCredential(NextcloudAccount candidate) {
        String name = credentialName(candidate.credentialRef());
        Path file = secretRoot.resolve(name).normalize();
        if (!file.getParent().equals(secretRoot)) {
            return Optional.empty();
        }
        try {
            if (Files.isSymbolicLink(secretRoot)
                    || !Files.isDirectory(secretRoot, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(file)
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(file) < 1 || Files.size(file) > 64 * 1024) {
                return Optional.empty();
            }
            if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
                if (!permissions.equals(Set.of(PosixFilePermission.OWNER_READ))
                        && !permissions.equals(Set.of(
                                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))) {
                    return Optional.empty();
                }
            }
            String secret = Files.readString(file, StandardCharsets.UTF_8).replaceFirst("[\\r\\n]+$", "");
            return secret.isBlank() || secret.chars().anyMatch(Character::isISOControl)
                    ? Optional.empty() : Optional.of(secret);
        } catch (IOException | SecurityException failure) {
            return Optional.empty();
        }
    }

    private static String credentialName(String credentialRef) {
        String prefix = "secretref://weave/files/";
        if (credentialRef == null || !credentialRef.startsWith(prefix)) {
            throw new IllegalArgumentException("Nextcloud Files credential reference is invalid");
        }
        String name = credentialRef.substring(prefix.length());
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}") || name.equals("..")) {
            throw new IllegalArgumentException("Nextcloud Files credential reference is invalid");
        }
        return name;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Nextcloud Files account property is required");
        }
        return value.trim();
    }

    private record AccountKey(String organizationRef, String configurationRef) {
        private AccountKey {
            organizationRef = required(organizationRef);
            configurationRef = required(configurationRef);
        }
    }
}
