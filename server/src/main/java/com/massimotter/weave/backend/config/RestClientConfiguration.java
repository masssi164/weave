package com.massimotter.weave.backend.config;

import java.net.http.HttpClient;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
public class RestClientConfiguration {

    @Bean
    RestClientCustomizer jdkRestClientRequestFactoryCustomizer() {
        HttpClient httpClient = HttpClient.newHttpClient();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        return builder -> builder.requestFactory(requestFactory);
    }
}
