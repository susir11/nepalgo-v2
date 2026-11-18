package com.nepalgo.auth.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

// Same two-builder pattern as booking-service: a plain @Primary builder (Eureka's
// own registration client needs a literal host:port) and a @LoadBalanced one that
// only our code asks for by @Qualifier, resolving "http://booking-service/..."
// through the Eureka registry.
@Configuration
public class RestClientConfig {

    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
