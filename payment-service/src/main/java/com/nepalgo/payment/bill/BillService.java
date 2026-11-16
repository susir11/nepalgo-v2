package com.nepalgo.payment.bill;

import com.nepalgo.payment.PaymentDtos;
import com.nepalgo.payment.PaymentExceptions;
import com.nepalgo.payment.audit.AuditService;
import com.nepalgo.payment.client.BookingClient;
import com.nepalgo.payment.credit.StoreCredit;
import com.nepalgo.payment.credit.StoreCreditRepository;
import com.nepalgo.payment.fx.FxRate;
import com.nepalgo.payment.fx.FxRateService;
import com.nepalgo.payment.settings.BillingSettings;
import com.nepalgo.payment.settings.BillingSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// Bill lifecycle (spec section 21):
//
//   create (DRAFT, no number) --edit--> amendments logged
//        |
//   issue  ("Approve & Issue Bill")  -> invoice number assigned, customer can see it
//        |                              still DRAFT: edits allowed until the customer pays
//   lock   (payment verified)        -> LOCKED, never edited again
//        |
//   edit on LOCKED                   -> BillCorrection: brand-new bill, new number, original kept
@Service
@RequiredArgsConstructor
public class BillService {

    public static final String CURRENCY_PAIR = "USD/NPR";
    // Booking statuses in which a bill may be created or amended (customer hasn't paid yet).
    private static final Set<String> EDITABLE_BOOKING_STATUSES = Set.of("PENDING_APPROVAL", "AWAITING_PAYMENT");

    private final BillRepository billRepository;
    private final BillAmendmentRepository amendmentRepository;
    private final BillCorrectionRepository correctionRepository;
    private final InvoiceNumberService invoiceNumberService;
    private final BillingSettingsService billingSettingsService;
    private final FxRateService fxRateService;
    private final BookingClient bookingClient;
    private final AuditService auditService;
    private final StoreCreditRepository storeCreditRepository;

    // ------------------------------------------------------------------ create

    @Transactional
    public Bill create(Long adminId, PaymentDtos.CreateBillRequest req) {
        BookingClient.BookingSummary booking = bookingClient.getBooking(req.bookingId());
        if (!EDITABLE_BOOKING_STATUSES.contains(booking.status())) {
            throw new PaymentExceptions.InvalidBillState(
                    "Cannot create a bill for a booking in status " + booking.status());
        }
        if (billRepository.findByBookingIdAndSupersededFalse(req.bookingId()).isPresent()) {
            throw new PaymentExceptions.BillAlreadyExists(req.bookingId());
        }

        BillingSettings settings = billingSettingsService.get();
        FxRate fx = fxRateService.current(CURRENCY_PAIR);   // 404 NO_APPROVED_FX_RATE if none yet

        Bill bill = new Bill();
        bill.setBookingId(booking.id());
        bill.setCustomerId(booking.customerId());
        bill.setStatus(Bill.Status.DRAFT);
        // Frozen copies (spec: "a frozen value copied ... at creation time").
        bill.setSellerLegalName(settings.getSellerLegalName());
        bill.setSellerPanVatNumber(settings.getSellerPanVatNumber());
        bill.setSellerAddress(settings.getSellerAddress());
        bill.setVatRegistered(settings.isVatRegistered());
        bill.setVatPercent(settings.getVatPercent());
        bill.setServiceChargePercent(settings.getServiceChargePercent());
        bill.setCurrencyPair(fx.getCurrencyPair());
        bill.setFxRate(fx.getRate());
        bill.setBuyerPan(blankToNull(req.buyerPan()));
        bill.setPayoneerLink(blankToNull(req.payoneerLink()));
        bill.setCreatedBy(adminId);
        bill.replaceLineItems(toEntities(req.lineItems()));
        billRepository.save(bill);

        auditService.log(adminId, "BILL_CREATED", "BILL", bill.getId(),
                "booking " + bill.getBookingId() + ", fx " + fx.getRate().toPlainString());
        return bill;
    }

    // ------------------------------------------------------------------ edit (one button)

    @Transactional
    public Bill edit(Long adminId, Long billId, PaymentDtos.UpdateBillRequest req) {
        Bill bill = getOrThrow(billId);
        if (bill.isSuperseded()) {
            throw new PaymentExceptions.InvalidBillState(
                    "Bill " + billId + " was replaced by a correction; edit the current bill instead");
        }
        return bill.getStatus() == Bill.Status.DRAFT
                ? amend(adminId, bill, req)
                : correct(adminId, bill, req);
    }

    // DRAFT: change in place, invoice number untouched, every change logged.
    private Bill amend(Long adminId, Bill bill, PaymentDtos.UpdateBillRequest req) {
        // Once the customer has submitted payment, the amount must not move under them.
        BookingClient.BookingSummary booking = bookingClient.getBooking(bill.getBookingId());
        if (!EDITABLE_BOOKING_STATUSES.contains(booking.status())) {
            throw new PaymentExceptions.InvalidBillState("Bill cannot be edited while the booking is "
                    + booking.status() + " (customer has submitted payment)");
        }

        BigDecimal oldTotal = totals(bill).grandTotalNpr();
        List<String[]> changes = new ArrayList<>();
        diff(changes, "buyerPan", bill.getBuyerPan(), blankToNull(req.buyerPan()));
        diff(changes, "payoneerLink", bill.getPayoneerLink(), blankToNull(req.payoneerLink()));

        List<BillLineItem> newLines = toEntities(req.lineItems());
        String oldLinesText = describe(bill.getLineItems());
        String newLinesText = describe(newLines);
        boolean linesChanged = !oldLinesText.equals(newLinesText);
        diff(changes, "lineItems", oldLinesText, newLinesText);

        if (changes.isEmpty()) {
            return bill;   // nothing to do, nothing to log
        }

        bill.setBuyerPan(blankToNull(req.buyerPan()));
        bill.setPayoneerLink(blankToNull(req.payoneerLink()));
        if (linesChanged) {
            bill.replaceLineItems(newLines);
        }
        billRepository.saveAndFlush(bill);   // flush now so a stale @Version fails HERE (409), before we log

        BigDecimal newTotal = totals(bill).grandTotalNpr();
        diff(changes, "grandTotalNpr", oldTotal.toPlainString(), newTotal.toPlainString());
        for (String[] c : changes) {
            BillHistory.BillAmendment a = new BillHistory.BillAmendment();
            a.setBillId(bill.getId());
            a.setAdminUserId(adminId);
            a.setFieldChanged(c[0]);
            a.setOldValue(c[1]);
            a.setNewValue(c[2]);
            amendmentRepository.save(a);
        }
        auditService.log(adminId, "BILL_AMENDED", "BILL", bill.getId(),
                changes.stream().map(c -> c[0]).collect(Collectors.joining(", ")));
        return bill;
    }

    // LOCKED: never touch the original. Build a new bill (new number) linked to it.
    private Bill correct(Long adminId, Bill original, PaymentDtos.UpdateBillRequest req) {
        if (req.reason() == null || req.reason().isBlank()) {
            throw new PaymentExceptions.BadBillRequest(
                    "This bill is locked (paid). A reason is required; your edit will be saved as a correction");
        }
        List<BillLineItem> newLines = toEntities(req.lineItems());
        boolean unchanged = describe(original.getLineItems()).equals(describe(newLines))
                && Objects.equals(original.getBuyerPan(), blankToNull(req.buyerPan()))
                && Objects.equals(original.getPayoneerLink(), blankToNull(req.payoneerLink()));
        if (unchanged) {
            throw new PaymentExceptions.BadBillRequest("Nothing changed, so no correction was created");
        }

        // Retire the original FIRST and flush: the partial unique index allows only
        // one current bill per booking (same ordering rule as the FX approve step).
        original.setSuperseded(true);
        billRepository.saveAndFlush(original);

        Bill fix = new Bill();
        fix.setBookingId(original.getBookingId());
        fix.setCustomerId(original.getCustomerId());
        // Same frozen commercial terms as the transaction being corrected.
        fix.setSellerLegalName(original.getSellerLegalName());
        fix.setSellerPanVatNumber(original.getSellerPanVatNumber());
        fix.setSellerAddress(original.getSellerAddress());
        fix.setVatRegistered(original.isVatRegistered());
        fix.setVatPercent(original.getVatPercent());
        fix.setServiceChargePercent(original.getServiceChargePercent());
        fix.setCurrencyPair(original.getCurrencyPair());
        fix.setFxRate(original.getFxRate());
        fix.setAppliedCreditAmount(original.getAppliedCreditAmount());
        fix.setBuyerName(original.getBuyerName());
        fix.setBuyerPan(blankToNull(req.buyerPan()));
        fix.setPayoneerLink(blankToNull(req.payoneerLink()));
        fix.setCreatedBy(adminId);
        fix.replaceLineItems(newLines);
        // The payment already happened, so the correction is born issued AND locked.
        LocalDate today = InvoiceNumberService.today();
        fix.setInvoiceNumber(invoiceNumberService.next(today));
        fix.setIssueDate(today);
        fix.setIssuedAt(Instant.now());
        fix.setStatus(Bill.Status.LOCKED);
        fix.setLockedAt(Instant.now());
        billRepository.save(fix);

        BillHistory.BillCorrection link = new BillHistory.BillCorrection();
        link.setOriginalBillId(original.getId());
        link.setNewBillId(fix.getId());
        link.setReason(req.reason().trim());
        link.setCreatedBy(adminId);
        correctionRepository.save(link);

        auditService.log(adminId, "BILL_CORRECTED", "BILL", fix.getId(),
                original.getInvoiceNumber() + " corrected by " + fix.getInvoiceNumber() + ": " + req.reason().trim());
        return fix;
    }

    // ------------------------------------------------------------------ issue / lock

    // "Approve & Issue Bill": assigns the fixed invoice number. Idempotent: if a
    // retry arrives after a timeout, the same number comes back, never a second one.
    @Transactional
    public Bill issue(Long adminId, Long bookingId) {
        Bill bill = currentForBooking(bookingId);
        if (bill.isIssued()) {
            return bill;
        }
        if (bill.getLineItems().isEmpty()) {
            throw new PaymentExceptions.InvalidBillState("Bill has no line items; price it before issuing");
        }
        if (bill.getPayoneerLink() == null) {
            throw new PaymentExceptions.InvalidBillState("Paste the Payoneer payment link before issuing the bill");
        }
        // Spec 21: never apply (or ignore) a customer's travel credit silently —
        // every credit flagged on the trip request needs an explicit admin decision.
        List<String> undecided = storeCreditRepository
                .findByReservedBookingIdAndStatus(bookingId, StoreCredit.Status.RESERVED).stream()
                .filter(c -> c.getAccepted() == null).map(StoreCredit::getCode).toList();
        if (!undecided.isEmpty()) {
            throw new PaymentExceptions.InvalidBillState("Accept or reject the customer's travel credit(s) first: " + undecided);
        }
        if (totals(bill).grandTotalUsd().signum() <= 0) {
            throw new PaymentExceptions.InvalidBillState("Bill total must be greater than zero");
        }
        LocalDate today = InvoiceNumberService.today();
        bill.setInvoiceNumber(invoiceNumberService.next(today));
        bill.setIssueDate(today);
        bill.setIssuedAt(Instant.now());
        billRepository.save(bill);
        auditService.log(adminId, "BILL_ISSUED", "BILL", bill.getId(), bill.getInvoiceNumber());
        return bill;
    }

    // Payment verified: the bill becomes a final tax document. Idempotent too.
    @Transactional
    public Bill lock(Long adminId, Long bookingId, String buyerName) {
        Bill bill = currentForBooking(bookingId);
        if (bill.getStatus() == Bill.Status.LOCKED) {
            return bill;
        }
        if (!bill.isIssued()) {
            throw new PaymentExceptions.InvalidBillState("Bill was never issued, so it cannot be locked");
        }
        bill.setBuyerName(blankToNull(buyerName));
        bill.setStatus(Bill.Status.LOCKED);
        bill.setLockedAt(Instant.now());
        billRepository.save(bill);
        auditService.log(adminId, "BILL_LOCKED", "BILL", bill.getId(), bill.getInvoiceNumber());
        return bill;
    }

    // Internal-API variants: same operations, but the response DTO is built while
    // the transaction (and Hibernate session) is still open.
    @Transactional
    public BillControllers.InternalBillStatus issueForBooking(Long adminId, Long bookingId) {
        return internalStatus(issue(adminId, bookingId));
    }

    @Transactional
    public BillControllers.InternalBillStatus lockForBooking(Long adminId, Long bookingId, String buyerName) {
        return internalStatus(lock(adminId, bookingId, buyerName));
    }

    @Transactional(readOnly = true)
    public BillControllers.InternalBillStatus statusForBooking(Long bookingId) {
        return internalStatus(currentForBooking(bookingId));
    }

    private static BillControllers.InternalBillStatus internalStatus(Bill b) {
        return new BillControllers.InternalBillStatus(b.getId(), b.getBookingId(), b.getInvoiceNumber(),
                b.getStatus().name(), b.isIssued(), totals(b).payableUsd());
    }

    // ------------------------------------------------------------------ CSV export (spec 15)

    // The accountant's INPUT data, not a filed VAT return (the government
    // format is different). One row per line item; bill fields repeated so the
    // sheet can be pivoted in Excel without joins.
    @Transactional(readOnly = true)
    public String exportCsv(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new PaymentExceptions.BadBillRequest("'to' must not be before 'from'");
        }
        StringBuilder csv = new StringBuilder(
                "invoice_number,issue_date,status,superseded,corrects_invoice,booking_id,buyer_name,buyer_pan,"
                        + "seller_pan_vat,vat_registered,vat_percent,service_charge_percent,fx_rate,"
                        + "line_no,line_description,line_category,itinerary_day,quantity,unit_price_npr,line_total_npr,"
                        + "subtotal_npr,service_charge_npr,vat_npr,grand_total_npr,grand_total_usd,applied_credit_usd,payable_usd\n");
        for (Bill b : billRepository.findByIssueDateBetweenOrderByInvoiceNumberAsc(from, to)) {
            BillCalculator.Totals t = totals(b);
            String corrects = correctionRepository.findByNewBillId(b.getId())
                    .flatMap(c -> billRepository.findById(c.getOriginalBillId()))
                    .map(Bill::getInvoiceNumber).orElse("");
            for (BillLineItem l : b.getLineItems()) {
                csv.append(String.join(",",
                        esc(b.getInvoiceNumber()), b.getIssueDate().toString(), b.getStatus().name(),
                        String.valueOf(b.isSuperseded()), esc(corrects), b.getBookingId().toString(),
                        esc(b.getBuyerName()), esc(b.getBuyerPan()), esc(b.getSellerPanVatNumber()),
                        String.valueOf(b.isVatRegistered()), b.getVatPercent().toPlainString(),
                        b.getServiceChargePercent().toPlainString(), b.getFxRate().toPlainString(),
                        String.valueOf(l.getPosition()), esc(l.getDescription()), l.getCategory().name(),
                        l.getItineraryDay() == null ? "" : l.getItineraryDay().toString(),
                        l.getQuantity().toPlainString(), l.getUnitPriceNpr().toPlainString(), l.getLineTotalNpr().toPlainString(),
                        t.subtotalNpr().toPlainString(), t.serviceChargeNpr().toPlainString(), t.vatNpr().toPlainString(),
                        t.grandTotalNpr().toPlainString(), t.grandTotalUsd().toPlainString(),
                        t.appliedCreditUsd().toPlainString(), t.payableUsd().toPlainString())).append('\n');
            }
        }
        return csv.toString();
    }

    // RFC 4180 quoting, plus a guard against CSV/formula injection: a cell that
    // starts with = + - @ would be executed as a formula when opened in Excel.
    static String esc(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("'")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ------------------------------------------------------------------ PDF

    public record Pdf(String filename, byte[] bytes) {}

    @Transactional(readOnly = true)
    public Pdf adminPdf(Long billId) {
        return pdf(getOrThrow(billId));
    }

    // Customer: same ownership + "issued" rules as the JSON view.
    @Transactional(readOnly = true)
    public Pdf customerPdf(Long customerId, Long bookingId) {
        Bill bill = billRepository.findByBookingIdAndSupersededFalse(bookingId)
                .filter(b -> b.getCustomerId().equals(customerId) && b.isIssued())
                .orElseThrow(() -> new PaymentExceptions.BillNotFound("booking " + bookingId));
        return pdf(bill);
    }

    private Pdf pdf(Bill bill) {
        if (!bill.isIssued()) {
            throw new PaymentExceptions.InvalidBillState("Bill has not been issued yet, so there is no invoice to print");
        }
        String corrects = correctionRepository.findByNewBillId(bill.getId())
                .flatMap(c -> billRepository.findById(c.getOriginalBillId()))
                .map(Bill::getInvoiceNumber).orElse(null);
        String supersededBy = bill.isSuperseded()
                ? correctionRepository.findByOriginalBillIdIn(List.of(bill.getId())).stream().findFirst()
                    .flatMap(c -> billRepository.findById(c.getNewBillId()))
                    .map(Bill::getInvoiceNumber).orElse(null)
                : null;
        return new Pdf(bill.getInvoiceNumber() + ".pdf", InvoicePdfRenderer.render(bill, corrects, supersededBy));
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public PaymentDtos.AdminBillResponse adminView(Long billId) {
        return toAdmin(getOrThrow(billId));
    }

    @Transactional(readOnly = true)
    public PaymentDtos.AdminBillResponse adminViewForBooking(Long bookingId) {
        return toAdmin(currentForBooking(bookingId));
    }

    // Customer: own booking only, only once issued, USD only. A bill that is
    // still being priced is reported as "not found" — the customer shouldn't see
    // half-built numbers, and a non-owner shouldn't learn the bill exists.
    @Transactional(readOnly = true)
    public PaymentDtos.CustomerBillResponse customerView(Long customerId, Long bookingId) {
        Bill bill = billRepository.findByBookingIdAndSupersededFalse(bookingId)
                .filter(b -> b.getCustomerId().equals(customerId) && b.isIssued())
                .orElseThrow(() -> new PaymentExceptions.BillNotFound("booking " + bookingId));
        BigDecimal fx = bill.getFxRate();
        BillCalculator.Totals t = totals(bill);
        List<PaymentDtos.CustomerLineItem> lines = bill.getLineItems().stream()
                .map(l -> new PaymentDtos.CustomerLineItem(l.getDescription(), l.getCategory().name(),
                        l.getItineraryDay(), l.getQuantity(),
                        BillCalculator.toUsd(l.getUnitPriceNpr(), fx), BillCalculator.toUsd(l.getLineTotalNpr(), fx)))
                .toList();
        return new PaymentDtos.CustomerBillResponse(bill.getBookingId(), bill.getInvoiceNumber(), bill.getStatus().name(),
                bill.getIssueDate(), "USD", lines,
                BillCalculator.toUsd(t.subtotalNpr(), fx), bill.getServiceChargePercent(),
                BillCalculator.toUsd(t.serviceChargeNpr(), fx),
                bill.isVatRegistered(), bill.isVatRegistered() ? bill.getVatPercent() : null,
                bill.isVatRegistered() ? BillCalculator.toUsd(t.vatNpr(), fx) : null,
                t.grandTotalUsd(), t.appliedCreditUsd(), t.payableUsd(), bill.getPayoneerLink());
    }

    // Activity timeline: creation, amendments, issue, lock, corrections (with links).
    @Transactional(readOnly = true)
    public List<PaymentDtos.BillTimelineEvent> timeline(Long bookingId) {
        List<Bill> chain = billRepository.findByBookingIdOrderByCreatedAtAsc(bookingId);
        if (chain.isEmpty()) {
            throw new PaymentExceptions.BillNotFound("booking " + bookingId);
        }
        List<PaymentDtos.BillTimelineEvent> events = new ArrayList<>();
        for (Bill b : chain) {
            events.add(new PaymentDtos.BillTimelineEvent("BILL_CREATED", b.getCreatedAt(), b.getCreatedBy(), b.getId(),
                    b.getInvoiceNumber(), null, null, null, null, null, null));
            for (BillHistory.BillAmendment a : amendmentRepository.findByBillIdOrderByCreatedAtAsc(b.getId())) {
                events.add(new PaymentDtos.BillTimelineEvent("BILL_AMENDED", a.getCreatedAt(), a.getAdminUserId(),
                        b.getId(), b.getInvoiceNumber(), a.getFieldChanged(), a.getOldValue(), a.getNewValue(),
                        null, null, null));
            }
            if (b.getIssuedAt() != null && correctionRepository.findByNewBillId(b.getId()).isEmpty()) {
                events.add(new PaymentDtos.BillTimelineEvent("BILL_ISSUED", b.getIssuedAt(), null, b.getId(),
                        b.getInvoiceNumber(), null, null, null, null, null, null));
            }
            if (b.getLockedAt() != null && correctionRepository.findByNewBillId(b.getId()).isEmpty()) {
                events.add(new PaymentDtos.BillTimelineEvent("BILL_LOCKED", b.getLockedAt(), null, b.getId(),
                        b.getInvoiceNumber(), null, null, null, null, null, null));
            }
        }
        List<Long> ids = chain.stream().map(Bill::getId).toList();
        for (BillHistory.BillCorrection c : correctionRepository.findByOriginalBillIdIn(ids)) {
            Bill orig = chain.stream().filter(b -> b.getId().equals(c.getOriginalBillId())).findFirst().orElseThrow();
            Bill fix = chain.stream().filter(b -> b.getId().equals(c.getNewBillId())).findFirst().orElseThrow();
            // The correction entry visibly links back to the bill it corrects (spec 21).
            events.add(new PaymentDtos.BillTimelineEvent("BILL_CORRECTED", c.getCreatedAt(), c.getCreatedBy(),
                    fix.getId(), fix.getInvoiceNumber(), null, null, null, c.getReason(),
                    orig.getId(), orig.getInvoiceNumber()));
        }
        events.sort(Comparator.comparing(PaymentDtos.BillTimelineEvent::at));
        return events;
    }

    // ------------------------------------------------------------------ helpers

    public Bill currentForBooking(Long bookingId) {
        return billRepository.findByBookingIdAndSupersededFalse(bookingId)
                .orElseThrow(() -> new PaymentExceptions.BillNotFound("booking " + bookingId));
    }

    private Bill getOrThrow(Long billId) {
        return billRepository.findById(billId)
                .orElseThrow(() -> new PaymentExceptions.BillNotFound("id " + billId));
    }

    public static BillCalculator.Totals totals(Bill bill) {
        List<BillCalculator.Line> lines = bill.getLineItems().stream()
                .map(l -> new BillCalculator.Line(l.getQuantity(), l.getUnitPriceNpr()))
                .toList();
        return BillCalculator.compute(lines, bill.getServiceChargePercent(), bill.isVatRegistered(),
                bill.getVatPercent(), bill.getFxRate(), bill.getAppliedCreditAmount());
    }

    private PaymentDtos.AdminBillResponse toAdmin(Bill bill) {
        BillCalculator.Totals t = totals(bill);
        List<PaymentDtos.AdminLineItem> lines = bill.getLineItems().stream()
                .map(l -> new PaymentDtos.AdminLineItem(l.getId(), l.getPosition(), l.getDescription(),
                        l.getCategory().name(), l.getItineraryDay(), l.getQuantity(), l.getUnitPriceNpr(),
                        l.getLineTotalNpr(), BillCalculator.toUsd(l.getLineTotalNpr(), bill.getFxRate())))
                .toList();
        Long correctsBillId = correctionRepository.findByNewBillId(bill.getId())
                .map(BillHistory.BillCorrection::getOriginalBillId).orElse(null);
        // Spec 21: "Customer has applied a $45 Travel Credit (NG-CR-...)" must be
        // plainly visible to whoever is pricing the bill.
        List<PaymentDtos.AppliedCreditInfo> credits = storeCreditRepository
                .findByReservedBookingIdAndStatus(bill.getBookingId(), StoreCredit.Status.RESERVED).stream()
                .map(c -> new PaymentDtos.AppliedCreditInfo(c.getId(), c.getCode(), c.getAmountUsd(), c.getExpiresOn(),
                        c.isExpired(), c.getAccepted() == null ? "PENDING_DECISION" : c.getAccepted() ? "ACCEPTED" : "REJECTED"))
                .toList();
        return new PaymentDtos.AdminBillResponse(bill.getId(), bill.getBookingId(), bill.getCustomerId(),
                bill.getInvoiceNumber(), bill.getStatus().name(), bill.isIssued(), documentTitle(bill),
                bill.getIssuedAt(), bill.getIssueDate(), bill.getLockedAt(), bill.getSellerLegalName(),
                bill.getSellerPanVatNumber(), bill.getSellerAddress(), bill.getBuyerName(), bill.getBuyerPan(),
                bill.getCurrencyPair(), bill.getFxRate(), bill.isVatRegistered(), bill.getVatPercent(),
                bill.getServiceChargePercent(), bill.getPayoneerLink(), bill.isSuperseded(), correctsBillId, lines, t,
                AmountInWords.format(t.grandTotalNpr()), credits, bill.getVersion());
    }

    // VAT on  -> formal "Tax Invoice" (VAT Rules 2053, Rule 17)
    // VAT off -> simpler PAN-style "Invoice"
    public static String documentTitle(Bill bill) {
        return bill.isVatRegistered() ? "Tax Invoice" : "Invoice";
    }

    private static List<BillLineItem> toEntities(List<PaymentDtos.LineItemInput> inputs) {
        List<BillLineItem> out = new ArrayList<>();
        for (PaymentDtos.LineItemInput in : inputs) {
            BillLineItem li = new BillLineItem();
            li.setDescription(in.description().trim());
            li.setCategory(in.category());
            li.setItineraryDay(in.itineraryDay());
            li.setQuantity(in.quantity().setScale(2, java.math.RoundingMode.HALF_UP));
            li.setUnitPriceNpr(in.unitPriceNpr().setScale(2, java.math.RoundingMode.HALF_UP));
            li.setLineTotalNpr(BillCalculator.lineTotal(li.getQuantity(), li.getUnitPriceNpr()));
            out.add(li);
        }
        return out;
    }

    // Human-readable snapshot of the lines, used both to detect a change and as
    // the old/new value in the amendment log (readable on the admin timeline).
    private static String describe(List<BillLineItem> items) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (BillLineItem l : items) {
            sb.append(i++).append(". [").append(l.getCategory()).append("] ")
                    .append(l.getItineraryDay() == null ? "" : "Day " + l.getItineraryDay() + " - ")
                    .append(l.getDescription()).append(" x").append(l.getQuantity().toPlainString())
                    .append(" @ NPR ").append(l.getUnitPriceNpr().toPlainString())
                    .append(" = ").append(l.getLineTotalNpr().toPlainString()).append('\n');
        }
        return sb.toString().trim();
    }

    private static void diff(List<String[]> changes, String field, String oldV, String newV) {
        if (!Objects.equals(oldV, newV)) {
            changes.add(new String[]{field, oldV, newV});
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
