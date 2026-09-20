package com.meridian.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// Without timeouts a price provider that accepts the connection and then never answers
// blocks the calling thread for as long as the operating system keeps the socket (many
// minutes, or forever). Every RestClient built from Spring's builder gets these limits.
@Configuration
public class MarketDataHttpConfig {

    @Bean
    public RestClientCustomizer providerTimeouts(
            @Value("${marketdata.http.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${marketdata.http.read-timeout-ms:10000}") long readTimeoutMs) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        return builder -> builder.requestFactory(ClientHttpRequestFactories.get(settings));
    }
}
