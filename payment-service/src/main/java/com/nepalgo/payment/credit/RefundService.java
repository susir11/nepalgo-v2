package com.nepalgo.payment.credit;

import com.nepalgo.payment.PaymentExceptions;
import com.nepalgo.payment.audit.AuditService;
import com.nepalgo.payment.bill.Bill;
import com.nepalgo.payment.bill.BillCalculator;
import com.nepalgo.payment.bill.BillService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

// Executes the money side of a cancellation (spec 8.7) and runs the refund queue (spec 21).
//
//   60+ days        -> 90% back as CASH  (RefundRequest, paid manually via Payoneer)
//   30-59 days      -> 70% back as TRAVEL CREDIT (365 days)
//   15-29 days      -> 50% back as TRAVEL CREDIT
//   0-14 days       -> nothing
//   force majeure   -> 100% back as CASH, no fee
//
// Integrity rule: CASH refunds only ever return CASH that was paid. If part of
// the bill was paid with a travel credit, that part comes back as a new credit,
// never as cash — otherwise "credit in, cancel, cash out" would turn a
// non-cash promise into withdrawable money (a classic laundering loop).
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final RefundRequestRepository refundRepository;
    private final StoreCreditService storeCreditService;
    private final BillService billService;
    private final AuditService auditService;
    private final com.nepalgo.payment.client.NotificationPublisher notifications;

    public record CancellationOutcome(Long bookingId, String tier, BigDecimal refundPercent,
                                      Long refundRequestId, BigDecimal cashRefundUsd,
                                      String creditCode, BigDecimal creditUsd) {}

    public record RefundView(Long id, Long bookingId, Long customerId, Long billId, String tier,
                             BigDecimal refundPercent, BigDecimal amountUsd, String status, String payoutReference,
                             Long completedBy, Instant completedAt, Instant createdAt) {
        static RefundView of(CreditEntities.RefundRequest r) {
            return new RefundView(r.getId(), r.getBookingId(), r.getCustomerId(), r.getBillId(), r.getTier(),
                    r.getRefundPercent(), r.getAmountUsd(), r.getStatus().name(), r.getPayoutReference(),
                    r.getCompletedBy(), r.getCompletedAt(), r.getCreatedAt());
        }
    }

    // Idempotent: booking-service may retry after a timeout; the unique indexes
    // (one refund / one cancellation credit per booking) plus the look-ups below
    // guarantee a retry returns the same outcome instead of paying twice.
    @Transactional
    public CancellationOutcome cancel(Long bookingId, String tier, BigDecimal refundPercent, boolean forceMajeure) {
        Bill bill = billService.currentForBooking(bookingId);
        if (bill.getStatus() != Bill.Status.LOCKED) {
            throw new PaymentExceptions.InvalidBillState("Only paid (locked) bills can be refunded");
        }
        BillCalculator.Totals t = BillService.totals(bill);
        BigDecimal pct = forceMajeure ? HUNDRED : refundPercent;
        boolean cashTier = forceMajeure || "60_PLUS_DAYS".equals(tier);

        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal credit;
        if (cashTier) {
            cash = percent(t.payableUsd(), pct);               // cash paid -> cash back
            credit = percent(t.appliedCreditUsd(), pct);       // credit paid -> credit back
        } else {
            credit = percent(t.grandTotalUsd(), pct);          // whole value -> credit
        }

        CreditEntities.RefundRequest refund = null;
        if (cash.signum() > 0) {
            refund = refundRepository.findByBookingId(bookingId).orElseGet(() -> null);
            if (refund == null) {
                refund = new CreditEntities.RefundRequest();
                refund.setBookingId(bookingId);
                refund.setCustomerId(bill.getCustomerId());
                refund.setBillId(bill.getId());
                refund.setTier(forceMajeure ? "FORCE_MAJEURE" : tier);
                refund.setRefundPercent(pct);
                refund.setAmountUsd(cash);
                refund.setStatus(CreditEntities.RefundRequest.Status.PENDING);
                refundRepository.save(refund);
                auditService.log(null, "REFUND_REQUESTED", "REFUND_REQUEST", refund.getId(),
                        "booking " + bookingId + " $" + cash + " (" + refund.getTier() + ")");
            }
        }
        StoreCredit issued = null;
        if (credit.signum() > 0) {
            issued = storeCreditService.issueForCancellation(bill.getCustomerId(), bookingId, credit,
                    (forceMajeure ? "FORCE_MAJEURE" : tier) + " cancellation of booking " + bookingId
                            + " (invoice " + bill.getInvoiceNumber() + ")");
        }
        return new CancellationOutcome(bookingId, forceMajeure ? "FORCE_MAJEURE" : tier, pct,
                refund == null ? null : refund.getId(), refund == null ? BigDecimal.ZERO.setScale(2) : refund.getAmountUsd(),
                issued == null ? null : issued.getCode(), issued == null ? BigDecimal.ZERO.setScale(2) : issued.getAmountUsd());
    }

    @Transactional(readOnly = true)
    public List<RefundView> queue(String status) {
        List<CreditEntities.RefundRequest> rows = (status == null || status.isBlank())
                ? refundRepository.findAllByOrderByCreatedAtDesc()
                : refundRepository.findByStatusOrderByCreatedAtAsc(CreditEntities.RefundRequest.Status.valueOf(status.toUpperCase()));
        return rows.stream().map(RefundView::of).toList();
    }

    // "Mark as Sent": admin already sent the money from their own Payoneer
    // dashboard; this records it. Human-initiated, human-confirmed (no webhook).
    @Transactional
    public RefundView complete(Long adminId, Long id, String payoutReference) {
        if (payoutReference == null || payoutReference.isBlank()) {
            throw new PaymentExceptions.BadBillRequest("Enter the Payoneer transaction reference of the refund you sent");
        }
        CreditEntities.RefundRequest r = refundRepository.findById(id).orElseThrow(() -> new PaymentExceptions.RefundNotFound(id));
        if (r.getStatus() == CreditEntities.RefundRequest.Status.COMPLETED) {
            throw new PaymentExceptions.InvalidBillState("Refund already marked as sent (" + r.getPayoutReference() + ")");
        }
        r.setStatus(CreditEntities.RefundRequest.Status.COMPLETED);
        r.setPayoutReference(payoutReference.trim());
        r.setCompletedBy(adminId);
        r.setCompletedAt(Instant.now());
        refundRepository.save(r);
        // Spec 19: cash refund issued -> customer push + email.
        notifications.publish("REFUND_SENT", "REFUND_SENT:refund:" + r.getId(), r.getCustomerId(), r.getBookingId(),
                java.util.Map.of("amountUsd", r.getAmountUsd(), "payoutReference", r.getPayoutReference()));
        auditService.log(adminId, "REFUND_COMPLETED", "REFUND_REQUEST", r.getId(),
                "$" + r.getAmountUsd() + " ref " + r.getPayoutReference());
        return RefundView.of(r);
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal pct) {
        return amount.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
