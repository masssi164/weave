package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.chat.adapter.WeaveCanonicalChatAdapter;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Test-classpath-only Chat adapter.
 *
 * <p>The shipped application has no in-memory persistence or provider fallback. Full-context tests
 * can explicitly select this isolated adapter without adding test behavior to the production
 * composition root.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "weave.chat.provider", havingValue = "in-memory-test")
public class IsolatedChatTestAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(ChatProviderPort.class)
  ChatProviderPort isolatedChatProviderPort() {
    return new WeaveCanonicalChatAdapter();
  }
}
