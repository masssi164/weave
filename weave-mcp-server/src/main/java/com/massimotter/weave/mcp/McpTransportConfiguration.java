package com.massimotter.weave.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.ServerRequest;
import tools.jackson.databind.json.JsonMapper;

@Configuration
class McpTransportConfiguration {

    @Bean
    WebMvcStreamableServerTransportProvider mcpStreamableTransport(
            JsonMapper jsonMapper,
            @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}") String endpoint) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(endpoint)
                .contextExtractor(request -> McpTransportContext.create(contextValues(request)))
                .build();
    }

    private Map<String, Object> contextValues(ServerRequest request) {
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put(RuntimeHeaders.RUNTIME_PROFILE, request.headers().firstHeader("X-Weave-Runtime-Profile"));
        Map<String, Object> context = new LinkedHashMap<>();
        candidates.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                context.put(key, value);
            }
        });
        return Map.copyOf(context);
    }
}
