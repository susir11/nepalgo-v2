package com.nepalgo.booking.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    // Plain, unqualified builder — @Primary makes this the one any
    // unqualified injection point gets by default, including Eureka's own
    // internal registration client, which needs to hit a literal
    // host:port (localhost:8761) rather than resolve a service name.
    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    // Only OUR OWN code should ever get this one — by explicit @Qualifier,
    // never left as the sole/default candidate for anything else to
    // accidentally pick up.
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}