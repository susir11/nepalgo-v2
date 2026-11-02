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

    // fixedDelay (not fixedRate): the next run starts 60s AFTER the previous
    // one finishes, so a slow run can never overlap itself.
    @Scheduled(fixedDelayString = "${nepalgo.booking.expiry-check-interval-ms:60000}")
    public void run() {
        int expired = bookingService.expireOverdueBookings();
        if (expired > 0) {
            log.info("Expired {} booking(s) past the payment window", expired);
        }
    }
}