package com.nepalgo.payment.credit;

import com.nepalgo.payment.PaymentExceptions;
import com.nepalgo.payment.audit.AuditService;
import com.nepalgo.payment.bill.Bill;
import com.nepalgo.payment.bill.BillAmendmentRepository;
import com.nepalgo.payment.bill.BillHistory;
import com.nepalgo.payment.bill.BillRepository;
import com.nepalgo.payment.bill.BillService;
import com.nepalgo.payment.bill.InvoiceNumberService;
import com.nepalgo.payment.client.BookingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoreCreditService {

    public static final int DEFAULT_VALIDITY_DAYS = 365;          // spec 8.7: "365-day NepalGo Travel Credit"
    // Booking statuses in which a reserved credit is clearly orphaned (trip will never be paid).
    private static final Set<String> DEAD_BOOKING_STATUSES =
            Set.of("REJECTED", "EXPIRED", "WITHDRAWN", "CANCELLED_TIERED", "CANCELLED_FORCE_MAJEURE");
    // No 0/O/1/I: codes get read aloud over WhatsApp.
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StoreCreditRepository creditRepository;
    private final StoreCreditAmendmentRepository amendmentRepository;
    private final BillRepository billRepository;
    private final BillAmendmentRepository billAmendmentRepository;
    private final BookingClient bookingClient;
    private final AuditService auditService;
    private final com.nepalgo.payment.client.NotificationPublisher notifications;

    // ================================================================ views

    public record CreditView(Long id, String code, Long customerId, BigDecimal amountUsd, LocalDate expiresOn,
                             String status, String source, Long sourceBookingId, Long reservedBookingId,
                             Boolean accepted, String rejectionReason, Instant redeemedAt, String voidReason,
                             String note, Long issuedBy, Instant createdAt) {
        static CreditView of(StoreCredit c) {
            return new CreditView(c.getId(), c.getCode(), c.getCustomerId(), c.getAmountUsd(), c.getExpiresOn(),
                    c.displayStatus(), c.getSource().name(), c.getSourceBookingId(), c.getReservedBookingId(),
                    c.getAccepted(), c.getRejectionReason(), c.getRedeemedAt(), c.getVoidReason(), c.getNote(),
                    c.getIssuedBy(), c.getCreatedAt());
        }
    }

    // Wallet card (spec 6c): code, USD value, expiry. Never NPR.
    public record WalletCard(String code, BigDecimal amountUsd, LocalDate expiresOn, String status,
                             Long appliedToBookingId) {}

    public record CreditDetail(CreditView credit, List<CreditEntities.StoreCreditAmendment> history) {}

    public record LedgerPage(List<CreditView> content, int page, int size, long totalElements, int totalPages) {}

    // ================================================================ customer

    @Transactional(readOnly = true)
    public List<WalletCard> wallet(Long customerId) {
        return creditRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .filter(c -> c.getStatus() != StoreCredit.Status.VOID)
                .map(c -> new WalletCard(c.getCode(), c.getAmountUsd(), c.getExpiresOn(), c.displayStatus(),
                        c.getStatus() == StoreCredit.Status.RESERVED ? c.getReservedBookingId() : null))
                .toList();
    }

    // Spec 6b: the customer flags credits on their trip request. Nothing is
    // deducted yet — the credits become RESERVED so admin sees them while pricing.
    @Transactional
    public List<WalletCard> applyToBooking(Long customerId, Long bookingId, List<String> codes) {
        BookingClient.BookingSummary booking = bookingClient.getBooking(bookingId);
        if (!booking.customerId().equals(customerId)) {
            throw new PaymentExceptions.BookingNotFound(bookingId);       // don't reveal other people's bookings
        }
        if (!"PENDING_APPROVAL".equals(booking.status())) {
            throw new PaymentExceptions.InvalidBillState("Credits can only be applied while the trip request is awaiting approval");
        }
        billRepository.findByBookingIdAndSupersededFalse(bookingId).filter(Bill::isIssued).ifPresent(b -> {
            throw new PaymentExceptions.InvalidBillState("The bill was already issued; ask our team to adjust it");
        });
        for (String raw : codes) {
            String code = raw.trim().toUpperCase();
            StoreCredit c = creditRepository.findByCode(code)
                    .filter(x -> x.getCustomerId().equals(customerId))
                    .orElseThrow(() -> new PaymentExceptions.BadBillRequest("Unknown travel credit " + code));
            if (c.getStatus() == StoreCredit.Status.RESERVED && bookingId.equals(c.getReservedBookingId())) {
                continue;   // already applied here: idempotent
            }
            if (c.getStatus() == StoreCredit.Status.RESERVED && isOrphaned(c)) {
                release(c);   // self-heal: its old booking died without telling us
            }
            if (!c.isSpendable()) {
                throw new PaymentExceptions.BadBillRequest("Travel credit " + code + " is " + c.displayStatus().toLowerCase());
            }
            c.setStatus(StoreCredit.Status.RESERVED);
            c.setReservedBookingId(bookingId);
            c.setAccepted(null);
            c.setRejectionReason(null);
            creditRepository.save(c);
            auditService.log(null, "CREDIT_APPLIED_BY_CUSTOMER", "STORE_CREDIT", c.getId(), code + " -> booking " + bookingId);
        }
        return wallet(customerId);
    }

    private boolean isOrphaned(StoreCredit c) {
        try {
            return DEAD_BOOKING_STATUSES.contains(bookingClient.getBooking(c.getReservedBookingId()).status());
        } catch (PaymentExceptions.BookingNotFound e) {
            return true;
        }
    }

    // ================================================================ admin: bill pricing (spec 21)

    public record AppliedCredit(Long creditId, String code, BigDecimal amountUsd, LocalDate expiresOn,
                                boolean expired, Boolean accepted, String rejectionReason) {}

    @Transactional(readOnly = true)
    public List<AppliedCredit> appliedToBooking(Long bookingId) {
        return creditRepository.findByReservedBookingIdAndStatus(bookingId, StoreCredit.Status.RESERVED).stream()
                .map(c -> new AppliedCredit(c.getId(), c.getCode(), c.getAmountUsd(), c.getExpiresOn(), c.isExpired(),
                        c.getAccepted(), c.getRejectionReason()))
                .toList();
    }

    // Accept: the credit nets against the bill total (bill.applied_credit_amount).
    @Transactional
    public void accept(Long adminId, Long billId, Long creditId) {
        Bill bill = editableBill(billId);
        StoreCredit c = reservedFor(creditId, bill.getBookingId());
        if (c.isExpired()) {
            throw new PaymentExceptions.InvalidBillState("Credit " + c.getCode() + " expired on " + c.getExpiresOn() + "; reject it with a reason");
        }
        c.setAccepted(true);
        c.setRejectionReason(null);
        creditRepository.saveAndFlush(c);
        BigDecimal newTotal = acceptedTotal(bill.getBookingId());
        // Spec 6b: combined credits may not exceed the bill total.
        BigDecimal grand = BillService.totals(bill).grandTotalUsd();
        if (newTotal.compareTo(grand) > 0) {
            throw new PaymentExceptions.InvalidBillState("Accepted credits ($" + newTotal + ") would exceed the bill total ($"
                    + grand + "). Price the bill first or reject a credit.");
        }
        setBillCredit(adminId, bill, newTotal);
        auditService.log(adminId, "CREDIT_ACCEPTED", "STORE_CREDIT", c.getId(), c.getCode() + " on bill " + billId);
    }

    // Reject (e.g. expired since the customer picked it): credit goes back to the wallet.
    @Transactional
    public void reject(Long adminId, Long billId, Long creditId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new PaymentExceptions.BadBillRequest("A reason is required when rejecting a travel credit");
        }
        Bill bill = editableBill(billId);
        StoreCredit c = reservedFor(creditId, bill.getBookingId());
        c.setRejectionReason(reason.trim());
        release(c);
        setBillCredit(adminId, bill, acceptedTotal(bill.getBookingId()));
        logCredit(adminId, c, "rejectedForBooking", String.valueOf(bill.getBookingId()), reason.trim());
        auditService.log(adminId, "CREDIT_REJECTED", "STORE_CREDIT", c.getId(), c.getCode() + ": " + reason.trim());
    }

    // ================================================================ lifecycle hooks

    // Bill locked (payment verified): accepted credits are consumed; any credit the
    // admin never accepted goes back to the wallet.
    @Transactional
    public void redeemForBooking(Long bookingId) {
        for (StoreCredit c : creditRepository.findByReservedBookingIdAndStatus(bookingId, StoreCredit.Status.RESERVED)) {
            if (Boolean.TRUE.equals(c.getAccepted())) {
                c.setStatus(StoreCredit.Status.REDEEMED);
                c.setRedeemedAt(Instant.now());
                creditRepository.save(c);
            } else {
                release(c);
            }
        }
    }

    // Booking withdrawn / rejected / expired: give reserved credits back.
    @Transactional
    public int releaseForBooking(Long bookingId) {
        List<StoreCredit> reserved = creditRepository.findByReservedBookingIdAndStatus(bookingId, StoreCredit.Status.RESERVED);
        reserved.forEach(this::release);
        return reserved.size();
    }

    // Issued by the system on cancellation (8.7). Idempotent per booking.
    @Transactional
    public StoreCredit issueForCancellation(Long customerId, Long bookingId, BigDecimal amountUsd, String note) {
        return creditRepository.findBySourceAndSourceBookingId(StoreCredit.Source.CANCELLATION, bookingId)
                .orElseGet(() -> {
                    StoreCredit c = newCredit(customerId, amountUsd, InvoiceNumberService.today().plusDays(DEFAULT_VALIDITY_DAYS));
                    c.setSource(StoreCredit.Source.CANCELLATION);
                    c.setSourceBookingId(bookingId);
                    c.setNote(note);
                    creditRepository.save(c);
                    auditService.log(null, "CREDIT_ISSUED", "STORE_CREDIT", c.getId(), c.getCode() + " $" + amountUsd + " for cancelled booking " + bookingId);
                    notifications.publish("CREDIT_ISSUED", "CREDIT_ISSUED:" + c.getCode(), c.getCustomerId(), bookingId,
                            java.util.Map.of("amountUsd", c.getAmountUsd(), "creditCode", c.getCode(), "expiresOn", c.getExpiresOn().toString()));
                    return c;
                });
    }

    // ================================================================ admin: Wallet & Card Management

    @Transactional(readOnly = true)
    public LedgerPage ledger(Long customerId, String status, int page, int size) {
        StoreCredit.Status st = (status == null || status.isBlank()) ? null : StoreCredit.Status.valueOf(status.toUpperCase());
        Page<StoreCredit> p = creditRepository.search(customerId, st, PageRequest.of(page, Math.min(size, 100)));
        return new LedgerPage(p.getContent().stream().map(CreditView::of).toList(), page, size, p.getTotalElements(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CreditDetail detail(Long id) {
        StoreCredit c = get(id);
        return new CreditDetail(CreditView.of(c), amendmentRepository.findByCreditIdOrderByCreatedAtAsc(id));
    }

    // Goodwill credit outside the cancellation flow (e.g. after a complaint).
    @Transactional
    public CreditView issueManual(Long adminId, Long customerId, BigDecimal amountUsd, LocalDate expiresOn, String note) {
        LocalDate expiry = expiresOn != null ? expiresOn : InvoiceNumberService.today().plusDays(DEFAULT_VALIDITY_DAYS);
        if (!expiry.isAfter(InvoiceNumberService.today())) {
            throw new PaymentExceptions.BadBillRequest("Expiry date must be in the future");
        }
        StoreCredit c = newCredit(customerId, amountUsd, expiry);
        c.setSource(StoreCredit.Source.MANUAL);
        c.setIssuedBy(adminId);
        c.setNote(note);
        creditRepository.save(c);
        auditService.log(adminId, "CREDIT_ISSUED_MANUALLY", "STORE_CREDIT", c.getId(), c.getCode() + " $" + c.getAmountUsd() + " customer " + customerId);
        notifications.publish("CREDIT_ISSUED", "CREDIT_ISSUED:" + c.getCode(), c.getCustomerId(), null,
                java.util.Map.of("amountUsd", c.getAmountUsd(), "creditCode", c.getCode(), "expiresOn", c.getExpiresOn().toString()));
        return CreditView.of(c);
    }

    // Edit amount / expiry — logged field by field, only while still spendable.
    @Transactional
    public CreditView edit(Long adminId, Long id, BigDecimal amountUsd, LocalDate expiresOn) {
        StoreCredit c = get(id);
        if (c.getStatus() != StoreCredit.Status.ACTIVE) {
            throw new PaymentExceptions.InvalidBillState("Only ACTIVE credits can be edited (this one is " + c.getStatus() + ")");
        }
        if (amountUsd != null && amountUsd.compareTo(c.getAmountUsd()) != 0) {
            BigDecimal v = amountUsd.setScale(2, RoundingMode.HALF_UP);
            logCredit(adminId, c, "amountUsd", c.getAmountUsd().toPlainString(), v.toPlainString());
            c.setAmountUsd(v);
        }
        if (expiresOn != null && !expiresOn.equals(c.getExpiresOn())) {
            logCredit(adminId, c, "expiresOn", c.getExpiresOn().toString(), expiresOn.toString());
            c.setExpiresOn(expiresOn);
        }
        creditRepository.save(c);
        return CreditView.of(c);
    }

    @Transactional
    public CreditView voidCredit(Long adminId, Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new PaymentExceptions.BadBillRequest("A reason is required to void a credit");
        }
        StoreCredit c = get(id);
        if (c.getStatus() != StoreCredit.Status.ACTIVE) {
            throw new PaymentExceptions.InvalidBillState("Only ACTIVE credits can be voided (this one is " + c.getStatus() + ")");
        }
        logCredit(adminId, c, "status", c.getStatus().name(), "VOID: " + reason.trim());
        c.setStatus(StoreCredit.Status.VOID);
        c.setVoidReason(reason.trim());
        creditRepository.save(c);
        auditService.log(adminId, "CREDIT_VOIDED", "STORE_CREDIT", c.getId(), c.getCode() + ": " + reason.trim());
        return CreditView.of(c);
    }

    // ================================================================ helpers

    private StoreCredit newCredit(Long customerId, BigDecimal amountUsd, LocalDate expiresOn) {
        if (amountUsd == null || amountUsd.signum() <= 0) {
            throw new PaymentExceptions.BadBillRequest("Credit amount must be positive");
        }
        StoreCredit c = new StoreCredit();
        c.setCode(uniqueCode());
        c.setCustomerId(customerId);
        c.setAmountUsd(amountUsd.setScale(2, RoundingMode.HALF_UP));
        c.setExpiresOn(expiresOn);
        c.setStatus(StoreCredit.Status.ACTIVE);
        return c;
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("NG-CR-");
            for (int i = 0; i < 6; i++) sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
            if (!creditRepository.existsByCode(sb.toString())) return sb.toString();
        }
        throw new IllegalStateException("Could not generate a unique credit code");
    }

    private void release(StoreCredit c) {
        c.setStatus(StoreCredit.Status.ACTIVE);
        c.setReservedBookingId(null);
        c.setAccepted(null);
        creditRepository.save(c);
    }

    private Bill editableBill(Long billId) {
        Bill bill = billRepository.findById(billId).orElseThrow(() -> new PaymentExceptions.BillNotFound("id " + billId));
        if (bill.isSuperseded() || bill.isIssued()) {
            throw new PaymentExceptions.InvalidBillState("Credits must be decided before the bill is issued");
        }
        return bill;
    }

    private StoreCredit reservedFor(Long creditId, Long bookingId) {
        StoreCredit c = get(creditId);
        if (c.getStatus() != StoreCredit.Status.RESERVED || !bookingId.equals(c.getReservedBookingId())) {
            throw new PaymentExceptions.InvalidBillState("Credit " + c.getCode() + " is not applied to this booking");
        }
        return c;
    }

    private BigDecimal acceptedTotal(Long bookingId) {
        return creditRepository.findByReservedBookingIdAndStatus(bookingId, StoreCredit.Status.RESERVED).stream()
                .filter(c -> Boolean.TRUE.equals(c.getAccepted()))
                .map(StoreCredit::getAmountUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // Writes the netted amount onto the bill and records it in the bill's amend log.
    private void setBillCredit(Long adminId, Bill bill, BigDecimal total) {
        BigDecimal newValue = total.signum() == 0 ? null : total;
        if (Objects.equals(bill.getAppliedCreditAmount(), newValue)) {
            return;
        }
        BillHistory.BillAmendment a = new BillHistory.BillAmendment();
        a.setBillId(bill.getId());
        a.setAdminUserId(adminId);
        a.setFieldChanged("appliedCreditAmount");
        a.setOldValue(bill.getAppliedCreditAmount() == null ? null : bill.getAppliedCreditAmount().toPlainString());
        a.setNewValue(newValue == null ? null : newValue.toPlainString());
        billAmendmentRepository.save(a);
        bill.setAppliedCreditAmount(newValue);
        billRepository.save(bill);
    }

    private void logCredit(Long adminId, StoreCredit c, String field, String oldV, String newV) {
        CreditEntities.StoreCreditAmendment a = new CreditEntities.StoreCreditAmendment();
        a.setCreditId(c.getId());
        a.setAdminUserId(adminId);
        a.setFieldChanged(field);
        a.setOldValue(oldV);
        a.setNewValue(newV);
        amendmentRepository.save(a);
    }

    private StoreCredit get(Long id) {
        return creditRepository.findById(id).orElseThrow(() -> new PaymentExceptions.CreditNotFound(id));
    }
}
