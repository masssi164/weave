package com.massimotter.weave.mcp;

import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ApprovalReceiptRef;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationRequest;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ToolInvocationStatus;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpContentBlock;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mcp")
public class McpJsonRpcController {
    private final WeaveServerClient client;
    public McpJsonRpcController(WeaveServerClient client) { this.client = client; }

    @GetMapping
    ResponseEntity<Map<String, Object>> getNotSupported() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, "POST")
                .body(Map.of("supportSafe", true, "message", "Use JSON-RPC POST /mcp."));
    }

    @PostMapping
    Map<String, Object> jsonRpc(@RequestBody Map<String, Object> request, @RequestHeader HttpHeaders headers) {
        Object id = request.get("id");
        String method = String.valueOf(request.getOrDefault("method", ""));
        if ("initialize".equals(method)) return ok(id, Map.of("protocolVersion", "2024-11-05", "capabilities", Map.of("tools", Map.of("listChanged", false)), "serverInfo", Map.of("name", "weave-mcp", "version", "0.1.0")));
        RuntimeHeaders runtime = runtime(headers);
        if (!runtime.valid()) return error(id, -32001, "missing runtime authorization context");
        if ("tools/list".equals(method)) return ok(id, Map.of("tools", governedTools(runtime)));
        if ("tools/call".equals(method)) return ok(id, call(request, runtime));
        return error(id, -32601, "method not found");
    }

    private List<Map<String, Object>> governedTools(RuntimeHeaders runtime) {
        var governed = client.discover(runtime.runtimeProfile(), runtime);
        return governed.catalog().tools().stream().map(this::descriptor).toList();
    }

    private Map<String, Object> descriptor(WeaveMcpToolDefinition tool) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("name", tool.name());
        descriptor.put("description", tool.description());
        descriptor.put("inputSchema", tool.inputSchema());
        descriptor.put("annotations", Map.of(
                "readOnlyHint", tool.annotations().readOnlyHint(),
                "destructiveHint", tool.annotations().destructiveHint(),
                "openWorldHint", tool.annotations().openWorldHint()));
        return Map.copyOf(descriptor);
    }

    private Map<String, Object> call(Map<String, Object> request, RuntimeHeaders runtime) {
        Map<?, ?> params = request.get("params") instanceof Map<?, ?> p ? p : Map.of();
        String name = String.valueOf(params.containsKey("name") ? params.get("name") : "");
        Map<String, Object> args = params.get("arguments") instanceof Map<?, ?> a ? copy(a) : Map.of();
        BridgeInvocationResponse response = client.invoke(new BridgeInvocationRequest(name, args, runtimeContext(runtime)), runtime);
        return Map.of(
                "content", response.content().stream().map(this::contentBlock).toList(),
                "structuredContent", structuredContent(response),
                "isError", response.status() != ToolInvocationStatus.SUCCESS);
    }

    private Map<String, Object> contentBlock(WeaveMcpContentBlock block) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", block.type());
        out.put("text", block.text());
        if (block.ref() != null) out.put("ref", block.ref().value());
        if (!block.metadata().isEmpty()) out.put("metadata", block.metadata());
        return Map.copyOf(out);
    }

    private Map<String, Object> structuredContent(BridgeInvocationResponse response) {
        Map<String, Object> out = new LinkedHashMap<>(response.structuredContent());
        out.putIfAbsent("toolName", response.toolName());
        out.putIfAbsent("auditRef", response.auditRef());
        out.putIfAbsent("supportSafe", response.supportSafe());
        out.putIfAbsent("status", statusValue(response));
        return Map.copyOf(out);
    }

    private String statusValue(BridgeInvocationResponse response) {
        if (response.status() == ToolInvocationStatus.DENIED && approvalRequired(response)) return "approval_required";
        return response.status().name().toLowerCase();
    }

    private boolean approvalRequired(BridgeInvocationResponse response) {
        return response.structuredContent().containsKey("approvalPolicy")
                || response.content().stream().map(WeaveMcpContentBlock::metadata).anyMatch(metadata -> "approval_required".equals(metadata.get("status")));
    }

    private com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.RuntimeInvocationContext runtimeContext(RuntimeHeaders runtime) {
        return new com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.RuntimeInvocationContext(
                new com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpRef(runtime.orgId() == null || runtime.orgId().isBlank() ? "org:workspace" : runtime.orgId()),
                new com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpRef(runtime.userRef() == null || runtime.userRef().isBlank() ? "user:mcp" : runtime.userRef()),
                new com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpRef("weave-runtime-profile://" + runtime.runtimeProfile()),
                runtime.runtimeProfile(),
                new com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpRef("credentialref://weave/runtime/short-lived/mcp"),
                "audit://mcp/" + runtime.runtimeProfile(),
                runtime.approvalReceiptRef() == null || runtime.approvalReceiptRef().isBlank() ? null : new ApprovalReceiptRef(runtime.approvalReceiptRef()),
                null,
                List.of(),
                List.of());
    }

    private RuntimeHeaders runtime(HttpHeaders h) { return new RuntimeHeaders(h.getFirst(HttpHeaders.AUTHORIZATION), h.getFirst("X-Weave-Org-Id"), h.getFirst("X-Weave-User-Ref"), h.getFirst("X-Weave-Runtime-Profile"), h.getFirst("X-Weave-Approval-Receipt")); }
    private Map<String, Object> ok(Object id, Map<String, Object> result) { return Map.of("jsonrpc", "2.0", "id", id == null ? "" : id, "result", result); }
    private Map<String, Object> error(Object id, int code, String message) { return Map.of("jsonrpc", "2.0", "id", id == null ? "" : id, "error", Map.of("code", code, "message", message)); }
    private Map<String, Object> copy(Map<?, ?> raw) { Map<String, Object> out = new LinkedHashMap<>(); raw.forEach((k, v) -> out.put(String.valueOf(k), v)); return out; }
}
