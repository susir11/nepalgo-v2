package com.nepalgo.booking.booking;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);
    private final BookingService bookingService;
    private final com.nepalgo.booking.client.PaymentClient paymentClient;
    private final PaymentDeadline paymentDeadline;

    // fixedDelay (not fixedRate): the next run starts 60s AFTER the previous
    // one finishes, so a slow run can never overlap itself.
    @Scheduled(fixedDelayString = "${nepalgo.booking.expiry-check-interval-ms:60000}")
    public void run() {
        int windowHours;
        try {
            windowHours = paymentClient.paymentWindowHours();
        } catch (com.nepalgo.booking.client.PaymentClient.PaymentUnavailableException e) {
            // Fail safe toward the customer: without the real setting we expire
            // NOTHING this round (a hold that lasts a minute longer is harmless;
            // cancelling someone's trip on a guessed value is not). Next run retries.
            log.warn("Expiry check skipped: payment window unavailable ({})", e.getMessage());
            return;
        }
        paymentDeadline.update(windowHours);   // customer "pay by" times use the same value
        int expired = bookingService.expireOverdueBookings(windowHours);
        if (expired > 0) {
            log.info("Expired {} booking(s) past the payment window", expired);
        }
    }
}