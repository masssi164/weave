package com.massimotter.weave.mcp;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.springframework.ai.mcp.customizer.McpAsyncServerCustomizer;
import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

@Configuration
class McpTransportConfiguration {

  @Bean
  JsonMapperBuilderCustomizer weaveMcpCapabilitiesJsonCustomizer() {
    return builder -> builder.addModule(capabilitiesModule());
  }

  @Bean
  JacksonMcpJsonMapper weaveMcpProtocolJsonMapper(JsonMapper jsonMapper) {
    return new JacksonMcpJsonMapper(jsonMapper);
  }

  private static SimpleModule capabilitiesModule() {
    SimpleModule module = new SimpleModule("weave-mcp-capabilities");
    module.addSerializer(McpSchema.ServerCapabilities.class, new ServerCapabilitiesSerializer());
    return module;
  }

  @Bean
  @Primary
  McpSyncServerCustomizer weaveMcpSyncServerCustomizer(JacksonMcpJsonMapper protocolMapper) {
    return server -> server.jsonMapper(protocolMapper).immediateExecution(true);
  }

  @Bean
  McpAsyncServerCustomizer weaveMcpAsyncServerCustomizer(JacksonMcpJsonMapper protocolMapper) {
    return server -> server.jsonMapper(protocolMapper);
  }

  @Bean
  WebMvcStreamableServerTransportProvider mcpStreamableTransport(
      JacksonMcpJsonMapper protocolMapper,
      @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}") String endpoint) {
    return WebMvcStreamableServerTransportProvider.builder()
        .jsonMapper(protocolMapper)
        .mcpEndpoint(endpoint)
        .build();
  }

  private static final class ServerCapabilitiesSerializer
      extends StdSerializer<McpSchema.ServerCapabilities> {
    private ServerCapabilitiesSerializer() {
      super(McpSchema.ServerCapabilities.class);
    }

    @Override
    public void serialize(
        McpSchema.ServerCapabilities capabilities,
        JsonGenerator generator,
        SerializationContext context)
        throws JacksonException {
      generator.writeStartObject(capabilities);
      property(context, generator, "completions", capabilities.completions());
      property(context, generator, "experimental", capabilities.experimental());
      generator.writePOJOProperty(
          "extensions", Map.of(McpWorkloadProperties.CLIENT_CREDENTIALS_EXTENSION, Map.of()));
      property(context, generator, "logging", capabilities.logging());
      property(context, generator, "prompts", capabilities.prompts());
      property(context, generator, "resources", capabilities.resources());
      property(context, generator, "tools", capabilities.tools());
      generator.writeEndObject();
    }

    private static void property(
        SerializationContext context, JsonGenerator generator, String name, Object value)
        throws JacksonException {
      if (value != null) {
        context.defaultSerializeProperty(name, value, generator);
      }
    }
  }
}
