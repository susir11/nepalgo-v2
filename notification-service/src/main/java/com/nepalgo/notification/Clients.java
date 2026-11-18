package com.nepalgo.notification;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

// Calls to other services (via Eureka, internal API key).
// Unlike booking/payment, notification-service does NOT fail closed: a missing
// e-mail address only means one channel is skipped — never a lost booking.
@Component
public class Clients {

    private final RestClient.Builder lb;
    private final String key;

    public Clients(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lb, @Value("${nepalgo.internal.api-key}") String key) {
        this.lb = lb;
        this.key = key;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> customer(Long id) {
        return lb.build().get().uri("http://auth-service/internal/customers/{id}", id)
                .header("X-Internal-Api-Key", key).retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> booking(Long id) {
        return lb.build().get().uri("http://booking-service/internal/bookings/{id}", id)
                .header("X-Internal-Api-Key", key).retrieve().body(Map.class);
    }

    public byte[] invoicePdf(Long bookingId) {
        return lb.build().get().uri("http://payment-service/internal/bills/pdf?bookingId={id}", bookingId)
                .header("X-Internal-Api-Key", key).retrieve().body(byte[].class);
    }

    @Configuration
    public static class Config {
        @Bean
        @Primary
        public RestClient.Builder restClientBuilder() { return RestClient.builder(); }

        @Bean
        @LoadBalanced
        public RestClient.Builder loadBalancedRestClientBuilder() { return RestClient.builder(); }
    }
}
