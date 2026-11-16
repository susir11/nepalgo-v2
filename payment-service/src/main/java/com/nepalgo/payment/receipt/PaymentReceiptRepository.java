package com.nepalgo.payment.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    List<PaymentReceipt> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    Optional<PaymentReceipt> findFirstByBookingIdOrderByCreatedAtDesc(Long bookingId);

    // Double-claim checks: same code or same image on ANY OTHER booking.
    List<PaymentReceipt> findByReceiptCodeNormalizedAndBookingIdNot(String code, Long bookingId);

    List<PaymentReceipt> findBySha256AndBookingIdNot(String sha256, Long bookingId);
}
