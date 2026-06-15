package com.massimotter.weave.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.model.admin.AttachExistingPortabilityPlanResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AttachExistingPortabilityPlanLoader {
    static final String RESOURCE_PATH = "admin/portability/attach-existing-files-portability-plan-mvp.json";
    static final String CONTRACT_VERSION = "admin-attach-existing-portability-plan-v1";
    static final String STATUS = "inspection-ready-read-only";

    private final ObjectMapper objectMapper;
    private final Resource resource;

    @Autowired
    public AttachExistingPortabilityPlanLoader(ObjectMapper objectMapper) {
        this(objectMapper, new ClassPathResource(RESOURCE_PATH));
    }

    AttachExistingPortabilityPlanLoader(ObjectMapper objectMapper, Resource resource) {
        this.objectMapper = objectMapper;
        this.resource = resource;
    }

    AttachExistingPortabilityPlanResponse load(String inspectionAuditRef) {
        try (var input = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            AttachExistingPortabilityPlanResponse response = toResponse(root, inspectionAuditRef);
            validate(response);
            return response;
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to load attach-existing Files portability plan resource", ex);
        }
    }

    private AttachExistingPortabilityPlanResponse toResponse(JsonNode root, String inspectionAuditRef) {
        JsonNode mapper = required(root, "adapterMapper");
        JsonNode reports = required(root, "reports");
        List<AttachExistingPortabilityPlanResponse.AdapterBinding> bindings = stream(required(root, "adapterBindings"))
                .map(binding -> new AttachExistingPortabilityPlanResponse.AdapterBinding(
                        text(binding, "adapterKey"),
                        textList(required(binding, "domainKeys")),
                        text(binding, "providerPosture"),
                        text(binding, "activeBindingStatus"),
                        text(binding, "discoveryMode"),
                        "active".equals(text(binding, "activeBindingStatus")),
                        bool(binding, "providerMutationPerformed"),
                        bool(binding, "memberVisibleProviderInternals"),
                        text(binding, "auditRef")))
                .toList();
        List<String> auditRefs = new java.util.ArrayList<>(textList(required(mapper, "auditRefs")));
        auditRefs.add(inspectionAuditRef);
        return new AttachExistingPortabilityPlanResponse(
                text(root, "planId"),
                CONTRACT_VERSION,
                text(root, "mode"),
                text(root, "domainKey"),
                STATUS,
                text(root, "claimBoundary"),
                "support_safe".equals(text(root, "redaction")),
                bool(root, "adminOnlyProviderDetails"),
                false,
                bindings.stream().anyMatch(AttachExistingPortabilityPlanResponse.AdapterBinding::providerMutationPerformed),
                bindings.stream().anyMatch(AttachExistingPortabilityPlanResponse.AdapterBinding::memberVisibleProviderInternals),
                stream(required(mapper, "capabilityMap"))
                        .map(item -> new AttachExistingPortabilityPlanResponse.CapabilityMapItem(text(item, "canonicalCapability"), text(item, "sourceProviderCapability"), text(item, "targetProviderCapability"), text(item, "memberState")))
                        .toList(),
                bindings,
                text(mapper, "permissionImpactRef"),
                reportItems(required(reports, "permissionImpact")),
                text(mapper, "lossReportRef"),
                reportItems(required(reports, "loss")),
                text(mapper, "conflictReportRef"),
                reportItems(required(reports, "conflicts")),
                auditRefs,
                new AttachExistingPortabilityPlanResponse.RecommendedTarget(text(required(mapper, "recommendedTarget"), "providerKey"), text(required(mapper, "recommendedTarget"), "reason")),
                new AttachExistingPortabilityPlanResponse.NextSteps(textList(required(required(mapper, "nextSteps"), "cutover")), textList(required(required(mapper, "nextSteps"), "rollback"))),
                textList(required(root, "memberCapabilityStates")),
                new AttachExistingPortabilityPlanResponse.NegativeChecks(
                        bool(required(root, "negativeChecks"), "noDestructiveActionInDiscoveryMode"),
                        bool(required(root, "negativeChecks"), "noMemberVisibleProviderInternals"),
                        exactlyOneActiveBindingPerDomain(bindings)));
    }

    private void validate(AttachExistingPortabilityPlanResponse response) {
        if (!"attach_existing".equals(response.mode()) || !"files".equals(response.domainKey()) || !response.supportSafe() || !response.adminOnlyProviderDetails()) {
            throw new IllegalStateException("Attach-existing Files portability plan violates support-safe discovery invariants");
        }
        if (response.destructiveActionAllowed() || response.providerMutationPerformed() || response.memberVisibleProviderInternals()) {
            throw new IllegalStateException("Attach-existing Files portability plan must remain read-only and member-neutral");
        }
        if (!response.negativeChecks().noDestructiveActionInDiscoveryMode() || !response.negativeChecks().noMemberVisibleProviderInternals() || !response.negativeChecks().exactlyOneActiveBindingPerDomain()) {
            throw new IllegalStateException("Attach-existing Files portability plan negative checks failed");
        }
    }

    private static boolean exactlyOneActiveBindingPerDomain(List<AttachExistingPortabilityPlanResponse.AdapterBinding> bindings) {
        Map<String, Long> activeCounts = bindings.stream()
                .filter(AttachExistingPortabilityPlanResponse.AdapterBinding::activeBinding)
                .flatMap(binding -> binding.domainKeys().stream())
                .collect(java.util.stream.Collectors.groupingBy(domain -> domain, LinkedHashMap::new, java.util.stream.Collectors.counting()));
        return activeCounts.values().stream().allMatch(count -> count == 1L);
    }

    private static List<AttachExistingPortabilityPlanResponse.ReportItem> reportItems(JsonNode node) {
        return stream(node).map(item -> new AttachExistingPortabilityPlanResponse.ReportItem(optionalText(item, "canonicalObject"), optionalText(item, "field"), optionalText(item, "fieldClass"), optionalText(item, "impact"), optionalText(item, "reason"))).toList();
    }

    private static List<String> textList(JsonNode node) {
        if (!node.isArray()) throw new IllegalStateException("Expected JSON array");
        return stream(node).map(JsonNode::asText).toList();
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode node) {
        if (!node.isArray()) throw new IllegalStateException("Expected JSON array");
        return StreamSupport.stream(node.spliterator(), false);
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isMissingNode() || value.isNull()) throw new IllegalStateException("Missing portability plan field: " + field);
        return value;
    }

    private static String text(JsonNode node, String field) { return required(node, field).asText(); }
    private static String optionalText(JsonNode node, String field) { JsonNode value = node.get(field); return value == null || value.isNull() ? null : value.asText(); }
    private static boolean bool(JsonNode node, String field) { return required(node, field).asBoolean(); }
}
