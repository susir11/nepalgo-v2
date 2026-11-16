package com.nepalgo.payment.bill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillCorrectionRepository extends JpaRepository<BillHistory.BillCorrection, Long> {
    Optional<BillHistory.BillCorrection> findByNewBillId(Long newBillId);
    List<BillHistory.BillCorrection> findByOriginalBillIdIn(List<Long> originalBillIds);
}
