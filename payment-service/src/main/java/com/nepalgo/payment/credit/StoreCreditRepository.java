package com.nepalgo.payment.credit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreCreditRepository extends JpaRepository<StoreCredit, Long> {

    Optional<StoreCredit> findByCode(String code);

    boolean existsByCode(String code);

    List<StoreCredit> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<StoreCredit> findByReservedBookingIdAndStatus(Long bookingId, StoreCredit.Status status);

    Optional<StoreCredit> findBySourceAndSourceBookingId(StoreCredit.Source source, Long sourceBookingId);

    // Ledger search; null filters are ignored.
    @Query("""
            SELECT c FROM StoreCredit c
            WHERE (:customerId IS NULL OR c.customerId = :customerId)
              AND (:status IS NULL OR c.status = :status)
            ORDER BY c.createdAt DESC
            """)
    Page<StoreCredit> search(@Param("customerId") Long customerId, @Param("status") StoreCredit.Status status,
                             Pageable pageable);
}
