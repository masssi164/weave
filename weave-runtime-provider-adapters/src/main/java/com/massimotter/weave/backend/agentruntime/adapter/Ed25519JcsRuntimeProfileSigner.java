package com.massimotter.weave.backend.agentruntime.adapter;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigner;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigningKeyProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.erdtman.jcs.JsonCanonicalizer;

public final class Ed25519JcsRuntimeProfileSigner implements RuntimeProfileSigner {
    public static final String TYPE = "weave.runtime-profile+jws";

    private final ObjectMapper objectMapper;
    private final RuntimeProfileSigningKeyProvider keys;

    public Ed25519JcsRuntimeProfileSigner(ObjectMapper objectMapper, RuntimeProfileSigningKeyProvider keys) {
        if (objectMapper == null || keys == null) {
            throw new IllegalArgumentException("RuntimeProfile signer dependencies are required");
        }
        this.objectMapper = objectMapper;
        this.keys = keys;
    }

    @Override
    public SignedRuntimeProfile sign(RuntimeProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("RuntimeProfile is required");
        }
        RuntimeProfileSigningKeyProvider.SigningKey key = keys.activeKey();
        try {
            byte[] payloadBytes = canonicalJson(profileProjection(profile));
            byte[] headerBytes = canonicalJson(Map.of(
                    "alg", "EdDSA",
                    "contractVersion", RuntimeProfile.VERSION,
                    "kid", key.keyId(),
                    "typ", TYPE));
            String encodedHeader = base64Url(headerBytes);
            String encodedPayload = base64Url(payloadBytes);
            byte[] signingInput = (encodedHeader + "." + encodedPayload).getBytes(StandardCharsets.US_ASCII);
            byte[] signature = sign(key, signingInput);
            verifyConfiguredKeyPair(key, signingInput, signature);
            return new SignedRuntimeProfile(
                    encodedHeader,
                    encodedPayload,
                    base64Url(signature),
                    "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payloadBytes)),
                    profile.profileId(),
                    profile.cellRef(),
                    key.keyId(),
                    profile.issuedAt(),
                    profile.expiresAt());
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("RuntimeProfile signing failed", exception);
        }
    }

    private byte[] canonicalJson(Object value) throws IOException {
        String json = objectMapper.writeValueAsString(value);
        return new JsonCanonicalizer(json).getEncodedUTF8();
    }

    private static byte[] sign(RuntimeProfileSigningKeyProvider.SigningKey key, byte[] signingInput)
            throws GeneralSecurityException {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key.privateKey());
        signer.update(signingInput);
        return signer.sign();
    }

    private static void verifyConfiguredKeyPair(
            RuntimeProfileSigningKeyProvider.SigningKey key, byte[] signingInput, byte[] signature)
            throws GeneralSecurityException {
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key.publicKey());
        verifier.update(signingInput);
        if (!verifier.verify(signature)) {
            throw new GeneralSecurityException("active RuntimeProfile signing key pair does not match");
        }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static Map<String, Object> profileProjection(RuntimeProfile profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profileVersion", profile.profileVersion());
        result.put("profileId", profile.profileId());
        result.put("organizationRef", profile.organizationRef());
        result.put("personRef", profile.personRef());
        result.put("memberBinding", Map.of(
                "issuer", profile.memberBinding().issuer(),
                "subject", profile.memberBinding().subject()));
        result.put("cellRef", profile.cellRef());
        result.put("workloadIdentity", Map.of(
                "issuer", profile.workloadIdentity().issuer(),
                "subject", profile.workloadIdentity().subject(),
                "clientId", profile.workloadIdentity().clientId(),
                "role", profile.workloadIdentity().role(),
                "authenticationMethod", profile.workloadIdentity().authenticationMethod().wireValue()));
        result.put("issuedAt", profile.issuedAt().toString());
        result.put("expiresAt", profile.expiresAt().toString());
        result.put("entitlementRevision", profile.entitlementRevision());
        result.put("workspaceRevision", profile.workspaceRevision());
        result.put("workspaceManifestRef", profile.workspaceManifestRef());
        result.put("runtimeStateStoreRef", profile.runtimeStateStoreRef());
        result.put("zeroDurableCellBytes", true);
        result.put("modelPolicy", modelPolicy(profile.modelPolicy()));
        result.put("matrix", matrixPolicy(profile.matrix()));
        result.put("mcp", mcpPolicy(profile.mcp()));
        result.put("approvals", approvalPolicy(profile.approvals()));
        result.put("sandbox", sandboxPolicy(profile.sandbox()));
        result.put("automation", Map.of(
                "heartbeatEnabled", profile.automation().heartbeatEnabled(),
                "schedulePolicy", profile.automation().schedulePolicy().wireValue()));
        return result;
    }

    private static Map<String, Object> modelPolicy(RuntimeProfile.ModelPolicy policy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allowedProviders", policy.allowedProviders());
        result.put("allowedModels", policy.allowedModels());
        result.put("fallback", policy.fallback());
        putIfPresent(result, "maximumContextTokens", policy.maximumContextTokens());
        putIfPresent(result, "dataRegion", policy.dataRegion());
        return result;
    }

    private static Map<String, Object> matrixPolicy(RuntimeProfile.MatrixPolicy policy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountRef", policy.accountRef());
        result.put("homeserverRef", policy.homeserverRef());
        putIfPresent(result, "credentialRef", policy.credentialRef());
        result.put("allowedRooms", policy.allowedRooms());
        if (policy.autoJoin() != null) {
            result.put("autoJoin", policy.autoJoin().wireValue());
        }
        result.put("encryptionRequired", true);
        return result;
    }

    private static Map<String, Object> mcpPolicy(RuntimeProfile.McpPolicy policy) {
        List<Map<String, Object>> servers = new ArrayList<>();
        for (RuntimeProfile.McpServer server : policy.servers()) {
            Map<String, Object> projection = new LinkedHashMap<>();
            projection.put("serverRef", server.serverRef());
            projection.put("endpoint", server.endpoint());
            projection.put("requestedResource", server.requestedResource());
            projection.put("extensionId", server.extensionId());
            projection.put("grantType", server.grantType());
            projection.put("requiredScopes", server.requiredScopes());
            projection.put("credentialRef", server.credentialRef());
            putIfPresent(projection, "allowedToolClasses", server.allowedToolClasses());
            servers.add(projection);
        }
        return Map.of("servers", servers, "visibleToolClasses", policy.visibleToolClasses());
    }

    private static Map<String, Object> approvalPolicy(RuntimeProfile.ApprovalPolicy policy) {
        Map<String, Object> routing = new LinkedHashMap<>();
        routing.put("enabled", policy.pluginRouting().enabled());
        routing.put("mode", policy.pluginRouting().mode().wireValue());
        putIfPresent(routing, "targetRefs", policy.pluginRouting().targetRefs());
        return Map.of(
                "owner", policy.owner(),
                "pluginRouting", routing,
                "execMode", policy.execMode().wireValue(),
                "persistentTrustPolicy", policy.persistentTrustPolicy().wireValue());
    }

    private static Map<String, Object> sandboxPolicy(RuntimeProfile.SandboxPolicy policy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", policy.mode().wireValue());
        result.put("networkPolicy", policy.networkPolicy().wireValue());
        putIfPresent(result, "allowedNetworkTargets", policy.allowedNetworkTargets());
        result.put("filesystemPolicy", policy.filesystemPolicy().wireValue());
        putIfPresent(result, "approvedMountRefs", policy.approvedMountRefs());
        return result;
    }

    private static void putIfPresent(Map<String, Object> target, String name, Object value) {
        if (value != null) {
            target.put(name, value);
        }
    }
}
