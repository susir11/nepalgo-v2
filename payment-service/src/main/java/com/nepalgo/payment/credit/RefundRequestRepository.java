package com.nepalgo.payment.credit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<CreditEntities.RefundRequest, Long> {
    Optional<CreditEntities.RefundRequest> findByBookingId(Long bookingId);
    List<CreditEntities.RefundRequest> findByStatusOrderByCreatedAtAsc(CreditEntities.RefundRequest.Status status);
    List<CreditEntities.RefundRequest> findAllByOrderByCreatedAtDesc();
}
