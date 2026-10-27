package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.api.error.ApiErrorResponseWriter;
import com.massimotter.weave.backend.api.error.ApiExceptionHandler;
import com.massimotter.weave.backend.http.request.RequestIdFilter;
import com.massimotter.weave.backend.security.http.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.security.http.ApiAuthenticationEntryPoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/** Shared request correlation and API denials, with optional domain-owned protocol envelopes. */
@AutoConfiguration(afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({ApiErrorResponseWriter.class, ApiExceptionHandler.class, RequestIdFilter.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
public class CoreHttpSecurityAutoConfiguration {}
