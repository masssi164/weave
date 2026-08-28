package com.massimotter.weave.backend.runner.http;

import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.CapabilityContract;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityDescriptor;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityEffect;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Recomputes every public Runner digest from canonical agent-visible metadata. */
public final class RunnerPublicCapabilityBundleVerifier {

    private static final String PUBLIC_BUNDLE_SCHEMA =
            "weave.runner.public-capability-bundle/v1";
    private static final String CAPABILITY_CONTRACT_SCHEMA =
            "weave.runner.public-capability-contract/v1";
    private static final String BUNDLE_CONTRACT_SCHEMA =
            "weave.runner.public-capability-bundle-contract/v1";

    private final ObjectMapper objectMapper;

    public RunnerPublicCapabilityBundleVerifier(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public VerifiedPublicBundle verify(PublicBundleRequest request) {
        PublicBundleRequest value = Objects.requireNonNull(request, "request");
        if (!PUBLIC_BUNDLE_SCHEMA.equals(value.schemaVersion())) {
            throw new IllegalArgumentException("unsupported public capability bundle schemaVersion");
        }
        List<VerifiedCapability> verified = new ArrayList<>(value.capabilities().size());
        for (PublicCapabilityRequest capability : value.capabilities()) {
            verified.add(verifyCapability(capability));
        }
        verified.sort(Comparator
                .comparing((VerifiedCapability entry) -> entry.contract().capability().id().value())
                .thenComparing(entry -> entry.contract().capability().version()));
        for (int index = 1; index < verified.size(); index++) {
            if (verified.get(index - 1).contract().capability().coordinate()
                    .equals(verified.get(index).contract().capability().coordinate())) {
                throw new IllegalArgumentException("duplicate public capability coordinate");
            }
        }

        List<Map<String, Object>> identities = verified.stream()
                .map(entry -> Map.<String, Object>of(
                        "id", entry.contract().capability().id().value(),
                        "version", entry.contract().capability().version(),
                        "contractDigest", entry.contract().contractDigest()))
                .toList();
        LinkedHashMap<String, Object> bundleContract = new LinkedHashMap<>();
        bundleContract.put("schemaVersion", BUNDLE_CONTRACT_SCHEMA);
        bundleContract.put("bundleId", value.bundleId());
        bundleContract.put("bundleVersion", value.bundleVersion());
        bundleContract.put("capabilities", identities);
        String computedBundleDigest = digest(canonicalBytes(bundleContract));
        requireDigest("bundleDigest", value.bundleDigest(), computedBundleDigest);

        return new VerifiedPublicBundle(
                value.bundleId(),
                value.bundleVersion(),
                computedBundleDigest,
                verified.stream().map(VerifiedCapability::contract).toList());
    }

    private VerifiedCapability verifyCapability(PublicCapabilityRequest value) {
        byte[] canonicalInput = canonicalJson(value.inputSchema());
        byte[] canonicalOutput = canonicalJson(value.outputSchema());
        String inputDigest = digest(canonicalInput);
        String outputDigest = digest(canonicalOutput);
        requireDigest("inputSchemaDigest", value.inputSchemaDigest(), inputDigest);
        requireDigest("outputSchemaDigest", value.outputSchemaDigest(), outputDigest);

        TreeSet<String> artifactTypes = new TreeSet<>(value.artifactTypes());
        if (artifactTypes.size() != value.artifactTypes().size()) {
            throw new IllegalArgumentException("artifactTypes must be unique");
        }
        LinkedHashMap<String, Object> contract = new LinkedHashMap<>();
        contract.put("schemaVersion", CAPABILITY_CONTRACT_SCHEMA);
        contract.put("id", value.id());
        contract.put("version", value.version());
        contract.put("title", value.title());
        contract.put("description", value.description());
        contract.put("effect", value.effect().name());
        contract.put("inputSchemaDigest", inputDigest);
        contract.put("outputSchemaDigest", outputDigest);
        contract.put("timeoutSeconds", value.timeoutSeconds());
        contract.put("maxOutputBytes", value.maxOutputBytes());
        contract.put("artifactTypes", List.copyOf(artifactTypes));
        String contractDigest = digest(canonicalBytes(contract));
        requireDigest("contractDigest", value.contractDigest(), contractDigest);

        CapabilityDescriptor descriptor = new CapabilityDescriptor(
                new CapabilityRef(new CapabilityId(value.id()), value.version()),
                value.title(),
                value.description(),
                value.effect(),
                new String(canonicalInput, StandardCharsets.UTF_8),
                inputDigest,
                new String(canonicalOutput, StandardCharsets.UTF_8),
                outputDigest,
                Duration.ofSeconds(value.timeoutSeconds()),
                value.maxOutputBytes(),
                Set.copyOf(artifactTypes));
        return new VerifiedCapability(new CapabilityContract(descriptor, contractDigest));
    }

    private byte[] canonicalJson(JsonNode value) {
        if (value == null || (!value.isObject() && !value.isBoolean())) {
            throw new IllegalArgumentException("public capability schemas must be JSON objects or booleans");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        appendCanonical(value, output);
        return output.toByteArray();
    }

    private byte[] canonicalBytes(Object value) {
        try {
            JsonNode tree = objectMapper.valueToTree(value);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            appendCanonical(tree, output);
            return output.toByteArray();
        } catch (IllegalArgumentException failure) {
            throw failure;
        }
    }

    private void appendCanonical(JsonNode node, ByteArrayOutputStream output) {
        if (node.isObject()) {
            output.write('{');
            List<Map.Entry<String, JsonNode>> properties = new ArrayList<>(node.properties());
            properties.sort(Map.Entry.comparingByKey());
            for (int index = 0; index < properties.size(); index++) {
                if (index > 0) {
                    output.write(',');
                }
                Map.Entry<String, JsonNode> property = properties.get(index);
                writeJsonString(property.getKey(), output);
                output.write(':');
                appendCanonical(property.getValue(), output);
            }
            output.write('}');
            return;
        }
        if (node.isArray()) {
            output.write('[');
            int index = 0;
            for (JsonNode child : node) {
                if (index++ > 0) {
                    output.write(',');
                }
                appendCanonical(child, output);
            }
            output.write(']');
            return;
        }
        if (node.isString()) {
            writeJsonString(node.asText(), output);
            return;
        }
        byte[] scalar = node.toString().getBytes(StandardCharsets.UTF_8);
        output.writeBytes(scalar);
    }

    private void writeJsonString(String value, ByteArrayOutputStream output) {
        try {
            output.writeBytes(objectMapper.writeValueAsBytes(value));
        } catch (JacksonException failure) {
            throw new IllegalStateException("could not canonicalize JSON string", failure);
        }
    }

    private static String digest(byte[] value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void requireDigest(String field, String supplied, String computed) {
        if (!computed.equals(supplied)) {
            throw new IllegalArgumentException(field + " does not match canonical public content");
        }
    }

    public record VerifiedPublicBundle(
            String bundleId,
            String bundleVersion,
            String bundleDigest,
            List<CapabilityContract> capabilities) {
        public VerifiedPublicBundle {
            capabilities = List.copyOf(capabilities);
        }
    }

    private record VerifiedCapability(CapabilityContract contract) {}

    public record PublicBundleRequest(
            String schemaVersion,
            String bundleId,
            String bundleVersion,
            String bundleDigest,
            List<PublicCapabilityRequest> capabilities) {
        public PublicBundleRequest {
            schemaVersion = required(schemaVersion, "schemaVersion", 128);
            bundleId = required(bundleId, "bundleId", 128);
            bundleVersion = required(bundleVersion, "bundleVersion", 96);
            bundleDigest = required(bundleDigest, "bundleDigest", 71);
            capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
            if (capabilities.isEmpty() || capabilities.size() > 128) {
                throw new IllegalArgumentException("capabilities must contain between one and 128 values");
            }
        }
    }

    public record PublicCapabilityRequest(
            String id,
            String version,
            String title,
            String description,
            CapabilityEffect effect,
            JsonNode inputSchema,
            String inputSchemaDigest,
            JsonNode outputSchema,
            String outputSchemaDigest,
            int timeoutSeconds,
            long maxOutputBytes,
            List<String> artifactTypes,
            String contractDigest) {
        public PublicCapabilityRequest {
            id = required(id, "id", 128);
            version = required(version, "version", 96);
            title = required(title, "title", 160);
            description = Objects.requireNonNull(description, "description");
            if (description.length() > 1000) {
                throw new IllegalArgumentException("description exceeds the supported bound");
            }
            effect = Objects.requireNonNull(effect, "effect");
            inputSchema = Objects.requireNonNull(inputSchema, "inputSchema");
            inputSchemaDigest = required(inputSchemaDigest, "inputSchemaDigest", 71);
            outputSchema = Objects.requireNonNull(outputSchema, "outputSchema");
            outputSchemaDigest = required(outputSchemaDigest, "outputSchemaDigest", 71);
            if (timeoutSeconds < 1 || timeoutSeconds > 3600) {
                throw new IllegalArgumentException("timeoutSeconds is outside the supported bound");
            }
            if (maxOutputBytes < 1024 || maxOutputBytes > 16L * 1024 * 1024) {
                throw new IllegalArgumentException("maxOutputBytes is outside the supported bound");
            }
            artifactTypes = List.copyOf(artifactTypes == null ? List.of() : artifactTypes);
            if (artifactTypes.size() > 32
                    || artifactTypes.stream().anyMatch(value -> value == null
                            || value.isBlank()
                            || !value.equals(value.strip())
                            || value.length() > 160)) {
                throw new IllegalArgumentException("artifactTypes are invalid");
            }
            contractDigest = required(contractDigest, "contractDigest", 71);
        }
    }

    private static String required(String value, String field, int maximum) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not be blank or padded");
        }
        if (value.length() > maximum) {
            throw new IllegalArgumentException(field + " exceeds the supported bound");
        }
        return value;
    }
}
