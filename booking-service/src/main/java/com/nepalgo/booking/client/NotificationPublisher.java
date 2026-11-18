package com.nepalgo.booking.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Publishes business events to notification-service (spec 19).
//
// Three deliberate choices (interview notes):
//  1. AFTER COMMIT: the event is sent only once our DB transaction committed,
//     so a rolled-back approval never tells the customer "your bill is ready".
//  2. ASYNC on a virtual thread: the admin's click never waits on notifications.
//  3. BEST EFFORT: a failure is logged, never thrown — a notification outage must
//     not undo or block a money action. Trade-off: if notification-service is
//     down at that instant the event is lost (logged). The upgrade path is a
//     transactional outbox table in this service, relayed by a poller.
@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);
    private static final ExecutorService EXEC = Executors.newVirtualThreadPerTaskExecutor();

    private final RestClient.Builder lb;
    private final String key;

    public NotificationPublisher(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lb,
                                 @Value("${nepalgo.internal.api-key}") String key) {
        this.lb = lb;
        this.key = key;
    }

    public void publish(String type, String idempotencyKey, Long customerId, Long bookingId, Map<String, ?> data) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("idempotencyKey", idempotencyKey);
        body.put("customerId", customerId);
        body.put("bookingId", bookingId);
        body.put("data", data == null ? Map.of() : data);
        Runnable send = () -> EXEC.submit(() -> {
            try {
                lb.build().post().uri("http://notification-service/internal/notifications/events")
                        .header("X-Internal-Api-Key", key).body(body).retrieve().toBodilessEntity();
            } catch (Exception e) {
                log.warn("Notification {} not delivered to notification-service: {}", idempotencyKey, e.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }
}
