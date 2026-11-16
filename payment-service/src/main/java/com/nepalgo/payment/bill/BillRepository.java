package com.nepalgo.payment.bill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {

    // "Current" bill = the one not replaced by a correction (DB index guarantees at most one).
    Optional<Bill> findByBookingIdAndSupersededFalse(Long bookingId);

    // Full chain for the activity timeline: original + every correction, oldest first.
    List<Bill> findByBookingIdOrderByCreatedAtAsc(Long bookingId);

    // Accountant export: every ISSUED bill (incl. superseded originals — they are
    // real tax documents too) whose invoice number was assigned in the range.
    List<Bill> findByIssueDateBetweenOrderByInvoiceNumberAsc(java.time.LocalDate from, java.time.LocalDate to);
}
