package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.operation.adapter.JpaOperationIntentRepository;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Shared durable idempotency and outbox transactions; domains own their use cases. */
@AutoConfiguration(after = CorePersistenceAutoConfiguration.class,
        afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({JpaOperationIntentRepository.class, com.massimotter.weave.backend.operation.idempotency.IdempotencyKeyService.class})
public class CoreOperationAutoConfiguration {
    @Bean
    OperationIntentService operationIntentService(JpaOperationIntentRepository repository) {
        return new OperationIntentService(repository, Clock.systemUTC());
    }
}
