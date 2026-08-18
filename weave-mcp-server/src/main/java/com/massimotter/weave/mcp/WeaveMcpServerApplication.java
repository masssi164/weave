package com.massimotter.weave.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(McpWorkloadProperties.class)
public class WeaveMcpServerApplication {
  public static void main(String[] args) {
    SpringApplication.run(WeaveMcpServerApplication.class, args);
  }
}
