package com.massimotter.weave.mcp;

import com.massimotter.weave.contract.mcp.MemberMcpToolCatalog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mcp")
public class McpJsonRpcController {
    private final WeaveServerClient client;
    public McpJsonRpcController(WeaveServerClient client) { this.client = client; }

    @PostMapping
    Map<String, Object> jsonRpc(@RequestBody Map<String, Object> request, @RequestHeader HttpHeaders headers) {
        Object id = request.get("id");
        String method = String.valueOf(request.getOrDefault("method", ""));
        if ("initialize".equals(method)) return ok(id, Map.of("protocolVersion", "2024-11-05", "capabilities", Map.of("tools", Map.of("listChanged", false)), "serverInfo", Map.of("name", "weave-mcp", "version", "0.1.0")));
        RuntimeHeaders runtime = runtime(headers);
        if (!runtime.valid()) return error(id, -32001, "missing runtime authorization context");
        if ("tools/list".equals(method)) return ok(id, Map.of("tools", governedTools(runtime)));
        if ("tools/call".equals(method)) return ok(id, Map.of("content", java.util.List.of(Map.of("type", "text", "text", call(request, runtime).toString())), "isError", false));
        return error(id, -32601, "method not found");
    }

    private List<Map<String, Object>> governedTools(RuntimeHeaders runtime) {
        var contractByName = MemberMcpToolCatalog.byName();
        var governed = client.discover(runtime.runtimeProfile(), runtime);
        return governed.stream().map(tool -> {
            String name = String.valueOf(tool.get("name"));
            var contract = contractByName.get(name);
            Map<String, Object> descriptor = new LinkedHashMap<>();
            descriptor.put("name", name);
            descriptor.put("description", contract == null ? String.valueOf(tool.getOrDefault("supportSafeDescription", "Governed Weave MCP tool.")) : contract.description());
            descriptor.put("inputSchema", contract == null ? tool.getOrDefault("inputSchema", Map.of("type", "object")) : contract.inputSchema());
            descriptor.put("annotations", Map.of("readOnlyHint", contract != null && !contract.writeLike(), "destructiveHint", contract != null && contract.approvalRequired()));
            return Map.copyOf(descriptor);
        }).toList();
    }

    private Map<String, Object> call(Map<String, Object> request, RuntimeHeaders runtime) {
        Map<?, ?> params = request.get("params") instanceof Map<?, ?> p ? p : Map.of();
        String name = String.valueOf(params.get("name"));
        var tool = MemberMcpToolCatalog.byName().get(name);
        if (tool == null) return Map.of("status", "blocked", "reason", "unknown_tool");
        Map<String, Object> args = params.get("arguments") instanceof Map<?, ?> a ? copy(a) : Map.of();
        if (tool.approvalRequired() && !args.containsKey("approvalReceiptRef") && runtime.approvalReceiptRef() == null) return Map.of("status", "approval_required", "tool", name, "supportSafe", true);
        return client.invoke(tool, args, runtime);
    }

    private RuntimeHeaders runtime(HttpHeaders h) { return new RuntimeHeaders(h.getFirst(HttpHeaders.AUTHORIZATION), h.getFirst("X-Weave-Org-Id"), h.getFirst("X-Weave-User-Ref"), h.getFirst("X-Weave-Runtime-Profile"), h.getFirst("X-Weave-Approval-Receipt")); }
    private Map<String, Object> ok(Object id, Map<String, Object> result) { return Map.of("jsonrpc", "2.0", "id", id == null ? "" : id, "result", result); }
    private Map<String, Object> error(Object id, int code, String message) { return Map.of("jsonrpc", "2.0", "id", id == null ? "" : id, "error", Map.of("code", code, "message", message)); }
    private Map<String, Object> copy(Map<?, ?> raw) { Map<String, Object> out = new LinkedHashMap<>(); raw.forEach((k, v) -> out.put(String.valueOf(k), v)); return out; }
}
