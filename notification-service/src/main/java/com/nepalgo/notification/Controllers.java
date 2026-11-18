package com.nepalgo.notification;

import com.nepalgo.notification.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class Controllers {

    private Controllers() {}

    public record View(Long id, String type, String title, String body, Long bookingId, Instant createdAt, boolean read) {
        static View of(Notification n) {
            return new View(n.getId(), n.getEventType(), n.getTitle(), n.getBody(), n.getBookingId(), n.getCreatedAt(), n.getReadAt() != null);
        }
    }

    public record Inbox(List<View> content, long unread, int page, long totalElements) {}

    // ---------------------------------------------------------------- internal: producers post events here
    @RestController
    @RequestMapping("/internal/notifications")
    @RequiredArgsConstructor
    public static class Internal {
        private final EventIntake intake;

        @PostMapping("/events")
        public ResponseEntity<Map<String, Object>> event(@RequestBody EventIntake.Event e) {
            if (e.type() == null || e.idempotencyKey() == null) {
                return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "detail", "type and idempotencyKey are required"));
            }
            Notification n = intake.accept(e);
            return ResponseEntity.accepted().body(Map.of("notificationId", n.getId()));
        }
    }

    // ---------------------------------------------------------------- customer: in-app inbox + device registration
    public record DeviceRequest(@NotBlank String token, @Pattern(regexp = "ANDROID|IOS|WEB") String platform) {}

    @RestController
    @RequestMapping("/api/notifications")
    @RequiredArgsConstructor
    public static class Customer {
        private final NotificationRepository notifications;
        private final DeviceTokenRepository devices;

        @GetMapping
        public Inbox inbox(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
            Long me = Long.parseLong(jwt.getSubject());
            Page<Notification> p = notifications.findByAudienceAndCustomerIdOrderByCreatedAtDesc(Notification.Audience.CUSTOMER, me, PageRequest.of(page, 30));
            return new Inbox(p.map(View::of).getContent(),
                    notifications.countByAudienceAndCustomerIdAndReadAtIsNull(Notification.Audience.CUSTOMER, me), page, p.getTotalElements());
        }

        @PostMapping("/{id}/read")
        @Transactional
        public ResponseEntity<Void> read(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
            Long me = Long.parseLong(jwt.getSubject());
            notifications.findById(id).filter(n -> me.equals(n.getCustomerId())).ifPresent(n -> {
                if (n.getReadAt() == null) n.setReadAt(Instant.now());
            });
            return ResponseEntity.noContent().build();
        }

        // The app calls this after FCM gives it a token. A token moving to a
        // different account (shared phone) is re-assigned, not duplicated.
        @PostMapping("/devices")
        @Transactional
        public ResponseEntity<Void> register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeviceRequest r) {
            Long me = Long.parseLong(jwt.getSubject());
            DeviceToken t = devices.findByToken(r.token()).orElseGet(DeviceToken::new);
            t.setToken(r.token());
            t.setCustomerId(me);
            t.setPlatform(r.platform() == null ? "ANDROID" : r.platform());
            t.setLastSeenAt(Instant.now());
            devices.save(t);
            return ResponseEntity.noContent().build();
        }

        @DeleteMapping("/devices/{token}")
        @Transactional
        public ResponseEntity<Void> unregister(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
            Long me = Long.parseLong(jwt.getSubject());
            devices.findByToken(token).filter(t -> me.equals(t.getCustomerId())).ifPresent(devices::delete);
            return ResponseEntity.noContent().build();
        }
    }

    // ---------------------------------------------------------------- admin: in-app alerts + delivery status
    public record DeliveryView(Long id, String channel, String recipient, String status, int attempts, String lastError, Instant sentAt) {}

    @RestController
    @RequestMapping("/api/admin/notifications")
    @RequiredArgsConstructor
    public static class Admin {
        private final NotificationRepository notifications;
        private final DeliveryRepository deliveries;

        @GetMapping
        public Inbox alerts(@RequestParam(defaultValue = "0") int page) {
            Page<Notification> p = notifications.findByAudienceOrderByCreatedAtDesc(Notification.Audience.ADMIN, PageRequest.of(page, 30));
            return new Inbox(p.map(View::of).getContent(), notifications.countByAudienceAndReadAtIsNull(Notification.Audience.ADMIN),
                    page, p.getTotalElements());
        }

        @PostMapping("/{id}/read")
        @Transactional
        public ResponseEntity<Void> read(@PathVariable Long id) {
            notifications.findById(id).filter(n -> n.getAudience() == Notification.Audience.ADMIN).ifPresent(n -> {
                if (n.getReadAt() == null) n.setReadAt(Instant.now());
            });
            return ResponseEntity.noContent().build();
        }

        // Support/debug view: did the customer actually get told? On which channel?
        @GetMapping("/{id}/deliveries")
        public List<DeliveryView> deliveries(@PathVariable Long id) {
            return deliveries.findByNotificationIdOrderById(id).stream().map(d -> new DeliveryView(d.getId(), d.getChannel().name(),
                    d.getRecipient(), d.getStatus().name(), d.getAttempts(), d.getLastError(), d.getSentAt())).toList();
        }
    }

    @RestControllerAdvice
    public static class Errors {
        @ExceptionHandler(IllegalArgumentException.class)
        public ProblemDetail bad(IllegalArgumentException e) {
            ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
            p.setProperty("code", "BAD_EVENT");
            return p;
        }
    }
}
