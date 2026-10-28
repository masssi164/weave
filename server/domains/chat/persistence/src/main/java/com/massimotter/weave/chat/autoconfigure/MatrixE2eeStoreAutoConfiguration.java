package com.massimotter.weave.chat.autoconfigure;

import com.massimotter.weave.backend.chat.persistence.MatrixE2eeRelationalStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/** Serving persistence adapter; schema/operator processes only discover the mappings. */
@AutoConfiguration(after = ChatPersistenceAutoConfiguration.class,
        afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(MatrixE2eeRelationalStore.class)
public class MatrixE2eeStoreAutoConfiguration {}
