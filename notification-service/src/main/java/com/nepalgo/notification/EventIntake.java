package com.nepalgo.notification;

import com.nepalgo.notification.model.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

// Turns one business event into a stored notification + one outbox row per
// channel. Sending happens later, in DeliveryWorker — so the producer's HTTP
// call returns in milliseconds and never waits on SMTP or FCM.
@Service
@RequiredArgsConstructor
public class EventIntake {

    private static final Logger log = LoggerFactory.getLogger(EventIntake.class);

    public record Event(String type, String idempotencyKey, Long customerId, Long bookingId, Map<String, Object> data) {}

    private final NotificationRepository notifications;
    private final DeliveryRepository deliveries;
    private final Clients clients;
    private final JsonMapper json;

    @Value("${nepalgo.notification.ops-email}")
    private String opsEmail;

    @Transactional
    public Notification accept(Event e) {
        EventCatalog.Template t = EventCatalog.EVENTS.get(e.type());
        if (t == null) {
            throw new IllegalArgumentException("Unknown event type: " + e.type());
        }
        // Idempotent intake: a producer retry returns the existing notification.
        var existing = notifications.findByIdempotencyKey(e.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }
        Map<String, Object> data = new HashMap<>(e.data() == null ? Map.of() : e.data());
        String[] copy = EventCatalog.render(e.type(), data);

        Notification n = new Notification();
        n.setIdempotencyKey(e.idempotencyKey());
        n.setEventType(e.type());
        n.setAudience(t.audience());
        n.setCustomerId(e.customerId());
        n.setBookingId(e.bookingId());
        n.setTitle(copy[0]);
        n.setBody(copy[1]);
        n.setData(json.writeValueAsString(data));
        notifications.save(n);

        for (Delivery.Channel ch : t.channels()) {
            Delivery d = new Delivery();
            d.setNotification(n);
            d.setChannel(ch);
            d.setAttachInvoice(t.attachInvoice() && ch == Delivery.Channel.EMAIL);
            if (ch == Delivery.Channel.EMAIL) {
                d.setRecipient(t.audience() == Notification.Audience.ADMIN ? opsEmail : customerEmail(e));
                if (d.getRecipient() == null) {
                    d.setStatus(Delivery.Status.SKIPPED);
                    d.setLastError("no e-mail address on file");
                }
            }
            deliveries.save(d);
        }
        return n;
    }

    // Spec 19: the confirmation email goes to the address captured in the
    // Step 2 identity form; fall back to the account e-mail.
    private String customerEmail(Event e) {
        try {
            if (e.bookingId() != null) {
                Object lead = clients.booking(e.bookingId()).get("leadBookerEmail");
                if (lead != null && !lead.toString().isBlank()) return lead.toString();
            }
            if (e.customerId() != null) {
                Object acct = clients.customer(e.customerId()).get("email");
                if (acct != null && !acct.toString().isBlank()) return acct.toString();
            }
        } catch (RuntimeException ex) {
            log.warn("Could not resolve e-mail for event {}: {}", e.idempotencyKey(), ex.getMessage());
        }
        return null;
    }
}
