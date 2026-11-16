package com.nepalgo.payment.bill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillAmendmentRepository extends JpaRepository<BillHistory.BillAmendment, Long> {
    List<BillHistory.BillAmendment> findByBillIdOrderByCreatedAtAsc(Long billId);
}
