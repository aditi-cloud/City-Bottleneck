package com.citybottleneck.api.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {

    @Bean("nominatimRestClient")
    RestClient nominatimClient(
            RestClient.Builder builder,
            @Value("${traffic.nominatim.base-url}") String baseUrl,
            @Value("${traffic.user-agent}") String userAgent,
            @Value("${traffic.upstream.connect-timeout}") Duration connectTimeout,
            @Value("${traffic.upstream.read-timeout}") Duration readTimeout) {
        return buildClient(builder, baseUrl, userAgent, connectTimeout, readTimeout);
    }

    @Bean("osrmRestClient")
    RestClient osrmClient(
            RestClient.Builder builder,
            @Value("${traffic.osrm.base-url}") String baseUrl,
            @Value("${traffic.user-agent}") String userAgent,
            @Value("${traffic.upstream.connect-timeout}") Duration connectTimeout,
            @Value("${traffic.upstream.read-timeout}") Duration readTimeout) {
        return buildClient(builder, baseUrl, userAgent, connectTimeout, readTimeout);
    }

    private RestClient buildClient(
            RestClient.Builder builder,
            String baseUrl,
            String userAgent,
            Duration connectTimeout,
            Duration readTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", userAgent)
                .requestFactory(requestFactory)
                .build();
    }
}