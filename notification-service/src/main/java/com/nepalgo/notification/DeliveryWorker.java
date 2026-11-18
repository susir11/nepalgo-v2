package com.nepalgo.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.nepalgo.notification.model.*;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Works the delivery outbox. Each poll claims a batch of due rows
// (FOR UPDATE SKIP LOCKED), tries to send, and records the outcome:
//   SENT | SKIPPED (nothing to send to) | retry later with exponential backoff
//   (30s, 1m, 2m, 4m, 8m) | FAILED after max attempts.
// Like a remittance payout queue: a failed bank call is retried on a schedule,
// never silently dropped, and never sent twice.
@Component
@RequiredArgsConstructor
public class DeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private final DeliveryRepository deliveries;
    private final DeviceTokenRepository devices;
    private final JavaMailSender mail;
    private final Clients clients;

    @Value("${nepalgo.notification.mail-from}")
    private String mailFrom;
    @Value("${nepalgo.notification.max-attempts}")
    private int maxAttempts;
    @Value("${nepalgo.notification.firebase-credentials-path}")
    private String firebaseCredentials;

    private FirebaseMessaging fcm;   // null => push disabled (no credentials)

    @PostConstruct
    void initFirebase() {
        try (FileInputStream in = new FileInputStream(firebaseCredentials)) {
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(in)).build())
                    : FirebaseApp.getInstance();
            fcm = FirebaseMessaging.getInstance(app);
            log.info("FCM push enabled");
        } catch (Exception e) {
            log.warn("FCM push DISABLED ({}): in-app inbox and e-mail still work", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${nepalgo.notification.poll-interval-ms}")
    @Transactional
    public void work() {
        List<Delivery> due = deliveries.claimDue(25);
        for (Delivery d : due) {
            try {
                boolean sentSomething = d.getChannel() == Delivery.Channel.EMAIL ? sendEmail(d) : sendPush(d);
                d.setStatus(sentSomething ? Delivery.Status.SENT : Delivery.Status.SKIPPED);
                d.setSentAt(sentSomething ? Instant.now() : null);
                d.setAttempts(d.getAttempts() + 1);
            } catch (Exception e) {
                d.setAttempts(d.getAttempts() + 1);
                d.setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
                if (d.getAttempts() >= maxAttempts) {
                    d.setStatus(Delivery.Status.FAILED);
                    log.error("Delivery {} FAILED permanently after {} attempts: {}", d.getId(), d.getAttempts(), d.getLastError());
                } else {
                    long backoff = 30L << Math.min(d.getAttempts() - 1, 8);   // 30s, 60s, 120s, ...
                    d.setNextAttemptAt(Instant.now().plus(Duration.ofSeconds(backoff)));
                    log.warn("Delivery {} attempt {} failed, retry in {}s: {}", d.getId(), d.getAttempts(), backoff, d.getLastError());
                }
            }
            deliveries.save(d);
        }
    }

    private boolean sendEmail(Delivery d) throws Exception {
        if (d.getRecipient() == null) return false;
        Notification n = d.getNotification();
        MimeMessage msg = mail.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, d.isAttachInvoice());
        h.setFrom(mailFrom);
        h.setTo(d.getRecipient());
        h.setSubject(n.getTitle());
        h.setText(n.getBody() + "\n\n— The NepalGo team, Kathmandu");
        if (d.isAttachInvoice() && n.getBookingId() != null) {
            // Spec 19: the confirmation email carries the bill / invoice PDF.
            byte[] pdf = clients.invoicePdf(n.getBookingId());
            h.addAttachment("NepalGo-invoice.pdf", new ByteArrayResource(pdf), "application/pdf");
        }
        mail.send(msg);
        return true;
    }

    private boolean sendPush(Delivery d) throws Exception {
        Notification n = d.getNotification();
        if (fcm == null || n.getCustomerId() == null) {
            d.setLastError(fcm == null ? "push disabled (no Firebase credentials)" : "no customer");
            return false;
        }
        List<DeviceToken> tokens = devices.findByCustomerId(n.getCustomerId());
        if (tokens.isEmpty()) {
            d.setLastError("customer has no registered device (in-app inbox still shows it)");
            return false;
        }
        MulticastMessage m = MulticastMessage.builder()
                .addAllTokens(tokens.stream().map(DeviceToken::getToken).toList())
                .setNotification(com.google.firebase.messaging.Notification.builder().setTitle(n.getTitle()).setBody(n.getBody()).build())
                .putData("bookingId", n.getBookingId() == null ? "" : n.getBookingId().toString())   // app deep-links to the trip
                .putData("type", n.getEventType())
                .build();
        BatchResponse r = fcm.sendEachForMulticast(m);
        // Prune tokens FCM says are dead (app uninstalled / token rotated).
        for (int i = 0; i < r.getResponses().size(); i++) {
            var resp = r.getResponses().get(i);
            if (!resp.isSuccessful() && resp.getException() != null
                    && resp.getException().getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                devices.delete(tokens.get(i));
            }
        }
        if (r.getSuccessCount() == 0) throw new IllegalStateException("FCM rejected all " + tokens.size() + " token(s)");
        return true;
    }
}
