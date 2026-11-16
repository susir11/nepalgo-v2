package com.nepalgo.payment.credit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreCreditAmendmentRepository extends JpaRepository<CreditEntities.StoreCreditAmendment, Long> {
    List<CreditEntities.StoreCreditAmendment> findByCreditIdOrderByCreatedAtAsc(Long creditId);
}
