package com.massimotter.weave.backend.config;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.context.authz.ContextMembership;
import com.massimotter.weave.backend.context.authz.ContextRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict loader for the three-record, private isolated Context seed contract. */
final class ContextAuthorizationSeedFileLoader {

    private static final long MAXIMUM_BYTES = 32_768;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "memberships");
    private static final Set<String> MEMBERSHIP_FIELDS =
            Set.of("tenantId", "contextId", "principalRef", "role", "source");
    private static final Set<PosixFilePermission> OWNER_ONLY =
            PosixFilePermissions.fromString("rw-------");

    private ContextAuthorizationSeedFileLoader() {
    }

    static List<ContextMembership> load(
            ContextAuthorizationSeedFileProperties seed,
            List<ContextMembership> inline) {
        if (seed.membershipsFile().isBlank()) {
            return List.copyOf(inline);
        }
        if (!inline.isEmpty()) {
            throw new IllegalStateException(
                    "Context memberships must not be configured both inline and through a seed file.");
        }
        Path path = Path.of(seed.membershipsFile()).toAbsolutePath().normalize();
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(path) > MAXIMUM_BYTES) {
                throw invalid();
            }
            try {
                if (!Files.getPosixFilePermissions(path).equals(OWNER_ONLY)) {
                    throw invalid();
                }
            } catch (UnsupportedOperationException ignored) {
                // The deployment preflight enforces the private source on non-POSIX hosts.
            }
            JsonNode root = OBJECT_MAPPER.readTree(Files.readAllBytes(path));
            if (root == null
                    || !root.isObject()
                    || !fieldNames(root).equals(ROOT_FIELDS)
                    || !"weave.context-authorization-seed/v1".equals(root.path("schemaVersion").asString())
                    || !root.path("memberships").isArray()
                    || root.path("memberships").size() != 3) {
                throw invalid();
            }
            List<ContextMembership> result = new ArrayList<>();
            Set<String> exactTuples = new HashSet<>();
            for (JsonNode item : root.path("memberships")) {
                if (!item.isObject() || !fieldNames(item).equals(MEMBERSHIP_FIELDS)) {
                    throw invalid();
                }
                String tenant = required(item, "tenantId", 160);
                String context = required(item, "contextId", 160);
                String principal = required(item, "principalRef", 255);
                String source = required(item, "source", 160);
                if (!principal.matches("user:[A-Za-z0-9._:@/-]{1,240}")) {
                    throw invalid();
                }
                ContextRole role;
                try {
                    role = ContextRole.valueOf(required(item, "role", 32));
                } catch (IllegalArgumentException exception) {
                    throw invalid();
                }
                if (!exactTuples.add(tenant + "\u0000" + context + "\u0000" + principal)) {
                    throw invalid();
                }
                result.add(new ContextMembership(tenant, context, principal, role, source));
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            throw new IllegalStateException("The private Context authorization seed is invalid.", exception);
        }
    }

    private static Set<String> fieldNames(JsonNode value) {
        Set<String> names = new HashSet<>();
        value.properties().forEach(entry -> names.add(entry.getKey()));
        return Set.copyOf(names);
    }

    private static String required(JsonNode item, String field, int maximum) {
        String value = item.path(field).asString("");
        if (value.isBlank()
                || value.length() > maximum
                || value.chars().anyMatch(Character::isISOControl)) {
            throw invalid();
        }
        return value.trim();
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("The private Context authorization seed is invalid.");
    }
}
