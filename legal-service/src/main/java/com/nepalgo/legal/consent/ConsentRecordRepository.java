package com.nepalgo.legal.consent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    Optional<ConsentRecord> findByBookingIdAndLegalDocumentId(Long bookingId, Long legalDocumentId);

    List<ConsentRecord> findByBookingIdOrderByConsentedAtDesc(Long bookingId);

    List<ConsentRecord> findByCustomerIdOrderByConsentedAtDesc(Long customerId);
}
