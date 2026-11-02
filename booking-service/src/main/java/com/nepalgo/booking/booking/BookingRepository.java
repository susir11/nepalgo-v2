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
    boolean existsByVoucherCode(String voucherCode);
    List<Booking> findByStatusAndAwaitingPaymentSinceBefore(BookingStatus status, Instant cutoff);
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

    // Every filter optional, same CAST(...) pattern as catalog-service's
// search query — avoids the Postgres "could not determine parameter type"
// issue on a NULL bind value.
    @Query(value = """
    SELECT * FROM bookings
    WHERE (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
      AND (CAST(:customerId AS bigint) IS NULL OR customer_id = CAST(:customerId AS bigint))
    ORDER BY created_at DESC
    """,
            countQuery = """
    SELECT COUNT(*) FROM bookings
    WHERE (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
      AND (CAST(:customerId AS bigint) IS NULL OR customer_id = CAST(:customerId AS bigint))
    """,
            nativeQuery = true)
    Page<Booking> adminSearch(@Param("status") String status, @Param("customerId") Long customerId, Pageable pageable);
}