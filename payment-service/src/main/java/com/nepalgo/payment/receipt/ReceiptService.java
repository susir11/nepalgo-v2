package com.nepalgo.payment.receipt;

import com.nepalgo.payment.PaymentExceptions;
import com.nepalgo.payment.bill.Bill;
import com.nepalgo.payment.bill.BillService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    static final long MAX_BYTES = 10L * 1024 * 1024;

    private final PaymentReceiptRepository repository;
    private final ReceiptStorage storage;
    private final BillService billService;

    public record Stored(Long receiptId, String receiptCode) {}

    public record ReceiptView(Long id, Long bookingId, String receiptCode, String status, String contentType,
                              long sizeBytes, Instant submittedAt, String viewUrl, String viewUrlExpiresIn,
                              List<Long> duplicateCodeOnBookings, List<Long> duplicateImageOnBookings) {}

    // Called by booking-service AFTER it checked owner / status / consent.
    // We still re-check what we own: the bill must be this customer's, issued, unpaid.
    public Stored store(Long bookingId, Long customerId, String receiptCode, MultipartFile file) {
        Bill bill = billService.currentForBooking(bookingId);
        if (!bill.getCustomerId().equals(customerId) || !bill.isIssued()) {
            throw new PaymentExceptions.BillNotFound("booking " + bookingId);
        }
        if (bill.getStatus() == Bill.Status.LOCKED) {
            throw new PaymentExceptions.InvalidBillState("This bill is already paid");
        }
        String code = receiptCode == null ? "" : receiptCode.trim();
        if (PaymentReceipt.normalize(code).isEmpty()) {
            throw new PaymentExceptions.BadBillRequest("Receipt / transaction code is required");
        }

        byte[] bytes = read(file);
        String type = detectImageType(bytes);   // throws if not a real image
        String key = "receipts/" + bookingId + "/" + UUID.randomUUID() + "." + type.substring("image/".length());

        // Object first, then row. If the row insert fails we delete the object,
        // so storage never holds a file the database doesn't know about.
        storage.put(key, bytes, type);
        try {
            PaymentReceipt r = new PaymentReceipt();
            r.setBookingId(bookingId);
            r.setCustomerId(customerId);
            r.setBillId(bill.getId());
            r.setReceiptCode(code);
            r.setReceiptCodeNormalized(PaymentReceipt.normalize(code));
            r.setObjectKey(key);
            r.setContentType(type);
            r.setSizeBytes(bytes.length);
            r.setSha256(sha256(bytes));
            r.setStatus(PaymentReceipt.Status.SUBMITTED);
            repository.save(r);
            return new Stored(r.getId(), code);
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
    }

    // Spec 8, Step 4: BEFORE "Payment Verified" is accepted, block if the latest
    // receipt's code — or its exact image — was already used on another booking.
    @Transactional(readOnly = true)
    public void assertNoDoubleClaim(Long bookingId) {
        PaymentReceipt latest = repository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)
                .orElseThrow(() -> new PaymentExceptions.InvalidBillState("No payment receipt was submitted for this booking"));
        Set<Long> codeHits = bookingIds(repository.findByReceiptCodeNormalizedAndBookingIdNot(
                latest.getReceiptCodeNormalized(), bookingId));
        Set<Long> imageHits = bookingIds(repository.findBySha256AndBookingIdNot(latest.getSha256(), bookingId));
        if (!codeHits.isEmpty() || !imageHits.isEmpty()) {
            throw new PaymentExceptions.DuplicateReceipt(latest.getReceiptCode(), codeHits, imageHits);
        }
    }

    @Transactional
    public void markLatestAccepted(Long bookingId) {
        repository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId).ifPresent(r -> {
            r.setStatus(PaymentReceipt.Status.ACCEPTED);
            repository.save(r);
        });
    }

    // Admin review screen: image (via 5-minute link) + typed code side by side,
    // with any double-claim warnings already worked out.
    @Transactional(readOnly = true)
    public List<ReceiptView> forAdmin(Long bookingId) {
        return repository.findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                .map(r -> new ReceiptView(r.getId(), r.getBookingId(), r.getReceiptCode(), r.getStatus().name(),
                        r.getContentType(), r.getSizeBytes(), r.getCreatedAt(),
                        storage.presignedViewUrl(r.getObjectKey()), "5 minutes",
                        List.copyOf(bookingIds(repository.findByReceiptCodeNormalizedAndBookingIdNot(
                                r.getReceiptCodeNormalized(), bookingId))),
                        List.copyOf(bookingIds(repository.findBySha256AndBookingIdNot(r.getSha256(), bookingId)))))
                .toList();
    }

    // ---- helpers

    private static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PaymentExceptions.BadBillRequest("A screenshot/photo of the payment confirmation is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new PaymentExceptions.BadBillRequest("Image is larger than 10 MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new PaymentExceptions.BadBillRequest("Could not read the uploaded file");
        }
    }

    // Trust the BYTES, not the file name or the client's Content-Type header —
    // both are just text the client typed. "Magic numbers" at the start of the
    // file identify the real format.
    static String detectImageType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        throw new PaymentExceptions.BadBillRequest("Only JPEG, PNG or WebP images are accepted");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<Long> bookingIds(List<PaymentReceipt> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        rows.forEach(r -> ids.add(r.getBookingId()));
        return ids;
    }
}
