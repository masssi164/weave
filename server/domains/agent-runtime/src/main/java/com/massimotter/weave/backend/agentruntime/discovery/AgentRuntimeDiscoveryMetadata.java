package com.massimotter.weave.backend.agentruntime.discovery;

import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryContract;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Concrete discovery declarations owned by agent-runtime; shared policy contains no feature catalog. */
public final class AgentRuntimeDiscoveryMetadata {
    private AgentRuntimeDiscoveryMetadata() {}

    public static ProviderCategoryFragment modelCategory() {
        return new ProviderCategoryFragment("agent-runtime", 10, "model", "model provider",
                null, true, "Model provider selection is admin-owned and exposed only through support-safe aliases to entitled Agent Runtime Control workloads.",
                ProviderRealityLevel.CONFIGURED, Set.of(),
                new ProviderCategoryContract(
                    List.of("model.chat_completion", "model.embedding", "model.admin_select"),
                    List.of("lmstudio", "lmstudio-openai-compatible"),
                    List.of("ollama-openai-compatible", "generic-openai-compatible", "openai", "anthropic"),
                    List.of("ModelProvider", "ModelAlias", "CompletionRequest", "CompletionResponse", "ProviderCredentialRef"),
                    "Admin-selected model provider owns inference; Weave owns support-safe aliases, routing, credentials by reference, and member impact states",
                    List.of("model behavior drift", "context-window differences", "tool-call support", "data residency", "rate limits"),
                    "export model alias policy and audit receipts; revoke credential refs through Credential Broker",
                    "model provider replacement requires readiness proof, credential-ref validation, and support-safe live completion evidence"));
    }

    public static ProviderCategoryFragment agentRuntimeControlCategory() {
        return new ProviderCategoryFragment("agent-runtime", 11, "agent-runtime-control", "Agent Runtime Control",
                "agent-runtime", true, null,
                ProviderRealityLevel.CONTRACT_ONLY, Set.of(),
                new ProviderCategoryContract(
                    List.of("agent-runtime.entitled", "agent-runtime.profile.read", "agent-runtime.lifecycle.write", "agent-runtime.wake", "agent-runtime.approval.attest"),
                    List.of("weaver-openclaw"),
                    List.of(),
                    List.of("RuntimeEntitlementRef", "RuntimeProfile", "ApprovalChallenge", "RuntimeCell", "WorkspaceRevision", "RuntimeRevocation", "RuntimeAuditCorrelation"),
                    "Keycloak owns entitlement; ARC owns profile/cell bindings and external-state lifecycle; collaboration domains retain content and authorization",
                    List.of("cross-cell state", "stale entitlement/profile", "workload credential exposure", "durable cell residue", "unreconciled restore"),
                    "export support-safe ARC bindings/evidence; delete only through explicit retention-scoped operations",
                    "runtime-provider replacement requires current entitlement, signed profile trust, external-state portability, kill/recreate, and cross-cell denial proof"));
    }

    public static CanonicalDomainContribution agentRuntimeControlDomain() {
        return new CanonicalDomainContribution("agent-runtime", 11, CanonicalDomainRegistry.entry(
                "agent-runtime-control",
                "Agent Runtime Control",
                "Optional entitlement-bound control plane for signed RuntimeProfiles, disposable runtime cells, external state, revocation, and runtime-provider mapping; it owns no collaboration content or domain authorization.",
                List.of("RuntimeEntitlementRef", "RuntimeProfile", "ApprovalChallenge", "RuntimeCell", "WorkspaceRevision", "RuntimeRevocation", "RuntimeAuditCorrelation"),
                List.of(),
                List.of("agent-runtime.entitled", "agent-runtime.profile.read", "agent-runtime.lifecycle.write", "agent-runtime.wake", "agent-runtime.approval.attest", "agent-runtime.admin"),
                Map.of("weaver-openclaw", "contract_only")));
    }
}
