package com.nepalgo.booking.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByVoucherCode(String voucherCode);

    List<Booking> findByCustomerId(Long customerId);
    boolean existsByVoucherCode(String voucherCode);
    List<Booking> findByStatusAndAwaitingPaymentSinceBeforeAndInvoiceNumberIsNotNull(BookingStatus status, Instant cutoff);
    Page<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    // Native query: Postgres date arithmetic uses "date + integer * interval",
    // not a date_add() function (that's MySQL/SQL Server syntax, which
    // JPQL's FUNCTION() doesn't automatically translate). This mirrors the
    // same category of fix as catalog-service's :param::type casting issue —
    // JPQL's abstraction layer doesn't always match Postgres's real syntax,
    // so a native query sidesteps the mismatch entirely.
    @Query(value = """
        SELECT * FROM bookings
        WHERE destination_id = :destinationId
          AND status IN ('PENDING_APPROVAL', 'AWAITING_PAYMENT', 'VERIFYING_PAYMENT')
          AND arrival_date <= :newEndDate
          AND :newArrivalDate <= (arrival_date + (duration_days || ' days')::interval)
        """, nativeQuery = true)
    List<Booking> findOverlappingHolds(
            @Param("destinationId") Long destinationId,
            @Param("newArrivalDate") LocalDate newArrivalDate,
            @Param("newEndDate") LocalDate newEndDate
    );

    // Booking Queue search (spec 15.2). Every filter optional; CAST(...) on each
    // nullable bind avoids Postgres "could not determine parameter type".
    // q matches voucher code, invoice number, lead-booker name or email.
    // sort: "urgency" = what needs a human first (receipts to verify, then new
    // requests, then unpaid bills; oldest first), "arrival" = trip date, default newest.
    @Query(value = """
    SELECT * FROM bookings
    WHERE (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
      AND (CAST(:customerId AS bigint) IS NULL OR customer_id = CAST(:customerId AS bigint))
      AND (CAST(:destinationId AS bigint) IS NULL OR destination_id = CAST(:destinationId AS bigint))
      AND (CAST(:origin AS text) IS NULL OR origin = CAST(:origin AS text))
      AND (CAST(:arrivalFrom AS date) IS NULL OR arrival_date >= CAST(:arrivalFrom AS date))
      AND (CAST(:arrivalTo AS date) IS NULL OR arrival_date <= CAST(:arrivalTo AS date))
      AND (CAST(:q AS text) IS NULL
           OR upper(voucher_code) LIKE '%' || upper(CAST(:q AS text)) || '%'
           OR upper(coalesce(invoice_number, '')) LIKE '%' || upper(CAST(:q AS text)) || '%'
           OR lower(coalesce(lead_booker_legal_name, '')) LIKE '%' || lower(CAST(:q AS text)) || '%'
           OR lower(coalesce(lead_booker_email, '')) LIKE '%' || lower(CAST(:q AS text)) || '%')
    ORDER BY
      CASE WHEN CAST(:sort AS text) = 'urgency' THEN
        CASE status WHEN 'VERIFYING_PAYMENT' THEN 0 WHEN 'PENDING_APPROVAL' THEN 1 WHEN 'AWAITING_PAYMENT' THEN 2 ELSE 3 END
      ELSE 0 END,
      CASE WHEN CAST(:sort AS text) = 'arrival' THEN arrival_date END ASC,
      CASE WHEN CAST(:sort AS text) = 'urgency' THEN created_at END ASC,
      created_at DESC
    """,
            countQuery = """
    SELECT COUNT(*) FROM bookings
    WHERE (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
      AND (CAST(:customerId AS bigint) IS NULL OR customer_id = CAST(:customerId AS bigint))
      AND (CAST(:destinationId AS bigint) IS NULL OR destination_id = CAST(:destinationId AS bigint))
      AND (CAST(:origin AS text) IS NULL OR origin = CAST(:origin AS text))
      AND (CAST(:arrivalFrom AS date) IS NULL OR arrival_date >= CAST(:arrivalFrom AS date))
      AND (CAST(:arrivalTo AS date) IS NULL OR arrival_date <= CAST(:arrivalTo AS date))
      AND (CAST(:q AS text) IS NULL
           OR upper(voucher_code) LIKE '%' || upper(CAST(:q AS text)) || '%'
           OR upper(coalesce(invoice_number, '')) LIKE '%' || upper(CAST(:q AS text)) || '%'
           OR lower(coalesce(lead_booker_legal_name, '')) LIKE '%' || lower(CAST(:q AS text)) || '%'
           OR lower(coalesce(lead_booker_email, '')) LIKE '%' || lower(CAST(:q AS text)) || '%')
    """,
            nativeQuery = true)
    Page<Booking> adminSearch(@Param("status") String status, @Param("customerId") Long customerId,
                              @Param("destinationId") Long destinationId, @Param("origin") String origin,
                              @Param("arrivalFrom") LocalDate arrivalFrom, @Param("arrivalTo") LocalDate arrivalTo,
                              @Param("q") String q, @Param("sort") String sort, Pageable pageable);

    // Dashboard counters (spec 15.1): one GROUP BY instead of N count queries.
    @Query(value = "SELECT status AS status, COUNT(*) AS cnt FROM bookings GROUP BY status", nativeQuery = true)
    List<Object[]> countByStatus();

    // Manual bookings still waiting for their bill to be issued.
    long countByStatusAndInvoiceNumberIsNull(BookingStatus status);
}