package com.nepalgo.booking.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nepalgo.booking.booking.*;
import com.nepalgo.booking.review.ReviewDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Verified-traveller reviews with pre-moderation.
 * Every staff decision lands in booking_audit_log (same trail as booking actions),
 * so "who published this review and when" is always answerable.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final BookingAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    // ---------------- customer ----------------

    @Transactional(readOnly = true)
    public MyReviewResponse myReview(Long customerId, Long bookingId) {
        Booking b = ownedBooking(customerId, bookingId);
        String reason = ReviewPolicy.ineligibleReason(b, today());
        OwnReview own = reviewRepository.findByBookingId(bookingId).map(this::toOwn).orElse(null);
        return new MyReviewResponse(reason == null, reason, ReviewPolicy.tripLastDay(b), own);
    }

    /** Create or edit (PUT = idempotent upsert). Any edit goes back to PENDING for re-moderation. */
    @Transactional
    public OwnReview write(Long customerId, Long bookingId, WriteReviewRequest req) {
        Booking b = ownedBooking(customerId, bookingId);
        String reason = ReviewPolicy.ineligibleReason(b, today());
        if (reason != null) {
            throw new ReviewException(HttpStatus.CONFLICT, reason,
                    "TRIP_NOT_FINISHED".equals(reason)
                            ? "You can review this trip from " + ReviewPolicy.tripLastDay(b) + "."
                            : "Only confirmed trips can be reviewed.");
        }
        Review r = reviewRepository.findByBookingId(bookingId).orElseGet(() -> {
            Review n = new Review();
            n.setBookingId(b.getId());
            n.setCustomerId(customerId);
            n.setDestinationId(b.getDestinationId());
            n.setTripMonth(b.getArrivalDate().withDayOfMonth(1));
            return n;
        });
        boolean isNew = r.getId() == null;
        // Snapshot the public name at write time; erased bookings fall back to "NepalGo traveller".
        r.setAuthorDisplayName(ReviewPolicy.displayName(b.getLeadBookerLegalName()));
        r.setRating(req.rating().shortValue());
        r.setTitle(blankToNull(req.title()));
        r.setBody(req.body().trim());
        r.setStatus(ReviewStatus.PENDING);
        r.setModerationNote(null);
        r.setModeratedBy(null);
        r.setModeratedAt(null);
        reviewRepository.save(r);
        audit(null, isNew ? "REVIEW_SUBMITTED" : "REVIEW_EDITED", b.getId(), customerId, "rating " + req.rating());
        return toOwn(r);
    }

    // ---------------- public ----------------

    @Transactional(readOnly = true)
    public DestinationReviewsResponse forDestination(Long destinationId, int page, int size) {
        Page<Review> p = reviewRepository.findByDestinationIdAndStatusOrderByCreatedAtDesc(
                destinationId, ReviewStatus.PUBLISHED, PageRequest.of(Math.max(page, 0), clamp(size, 1, 50)));
        return new DestinationReviewsResponse(summary(destinationId),
                p.getContent().stream().map(this::toPublic).toList(), p.getNumber(), p.getTotalPages());
    }

    ReviewSummary summary(Long destinationId) {
        // 5 stars first, like every store page; every bucket present so the app can draw bars.
        Map<Integer, Long> counts = new TreeMap<>(Comparator.reverseOrder());
        for (int s = 5; s >= 1; s--) counts.put(s, 0L);
        long total = 0, weighted = 0;
        for (Object[] row : reviewRepository.ratingHistogram(destinationId)) {
            int stars = ((Number) row[0]).intValue();
            long n = ((Number) row[1]).longValue();
            counts.put(stars, n);
            total += n;
            weighted += stars * n;
        }
        return new ReviewSummary(average(weighted, total), total, counts);
    }

    /** BigDecimal for a stable 1-decimal average (4.25 -> 4.3), not binary-float rounding. */
    static double average(long weighted, long total) {
        return total == 0 ? 0.0
                : BigDecimal.valueOf(weighted).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).doubleValue();
    }

    // ---------------- staff ----------------

    @Transactional(readOnly = true)
    public AdminReviewPage adminList(String status, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), clamp(size, 1, 100));
        Page<Review> p = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? reviewRepository.findAllByOrderByCreatedAtDesc(pr)
                : reviewRepository.findByStatusOrderByCreatedAtAsc(parseStatus(status), pr);
        Map<Long, Booking> bookings = bookingRepository.findAllById(
                        p.getContent().stream().map(Review::getBookingId).toList())
                .stream().collect(Collectors.toMap(Booking::getId, Function.identity()));
        return new AdminReviewPage(
                p.getContent().stream().map(r -> toAdmin(r, bookings.get(r.getBookingId()))).toList(),
                p.getNumber(), p.getTotalPages(), p.getTotalElements(),
                reviewRepository.countByStatus(ReviewStatus.PENDING));
    }

    /**
     * State machine (like a payment's maker-checker states, every move is explicit):
     *   PUBLISH: PENDING | HIDDEN -> PUBLISHED
     *   REJECT : PENDING          -> REJECTED  (note required: the customer sees why)
     *   HIDE   : PUBLISHED        -> HIDDEN    (note required: internal reason for the take-down)
     */
    @Transactional
    public AdminReview moderate(Long staffId, Long reviewId, ModerateRequest req) {
        Review r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Review not found."));
        String note = blankToNull(req.note());
        ReviewStatus from = r.getStatus();
        ReviewStatus to = nextStatus(from, req.action(), note);
        r.setStatus(to);
        r.setModerationNote(note);
        r.setModeratedBy(staffId);
        r.setModeratedAt(Instant.now());
        reviewRepository.save(r);
        audit(staffId, "REVIEW_" + to.name(), r.getBookingId(), r.getCustomerId(),
                from + " -> " + to + (note == null ? "" : ": " + note));
        Booking b = bookingRepository.findById(r.getBookingId()).orElse(null);
        return toAdmin(r, b);   // built inside the transaction (open-in-view is off)
    }

    /** Pure transition rule, unit-tested. */
    static ReviewStatus nextStatus(ReviewStatus from, ModerateRequest.Action action, String note) {
        return switch (action) {
            case PUBLISH -> {
                requireFrom(from, action, ReviewStatus.PENDING, ReviewStatus.HIDDEN);
                yield ReviewStatus.PUBLISHED;
            }
            case REJECT -> {
                requireFrom(from, action, ReviewStatus.PENDING);
                requireNote(note, "Tell the traveller why the review was not published.");
                yield ReviewStatus.REJECTED;
            }
            case HIDE -> {
                requireFrom(from, action, ReviewStatus.PUBLISHED);
                requireNote(note, "Record why this review is being taken down.");
                yield ReviewStatus.HIDDEN;
            }
        };
    }

    /** Account deletion (spec 15.2): reviews are personal content, so they go with the account. */
    @Transactional
    public int eraseCustomer(Long customerId) {
        return reviewRepository.deleteByCustomerId(customerId);
    }

    // ---------------- helpers ----------------

    private Booking ownedBooking(Long customerId, Long bookingId) {
        Booking b = bookingRepository.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
        // Same rule as BookingService: a non-owner gets 404, never "exists but not yours".
        if (!b.getCustomerId().equals(customerId)) throw new BookingNotFoundException(bookingId);
        return b;
    }

    private static void requireFrom(ReviewStatus from, ModerateRequest.Action action, ReviewStatus... allowed) {
        if (!Arrays.asList(allowed).contains(from)) {
            throw new ReviewException(HttpStatus.CONFLICT, "INVALID_REVIEW_STATE",
                    "Cannot " + action.name().toLowerCase() + " a review that is " + from + ".");
        }
    }

    private static void requireNote(String note, String message) {
        if (note == null) throw new ReviewException(HttpStatus.BAD_REQUEST, "NOTE_REQUIRED", message);
    }

    private static ReviewStatus parseStatus(String s) {
        try {
            return ReviewStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ReviewException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown review status: " + s);
        }
    }

    private void audit(Long staffId, String action, Long bookingId, Long customerId, String detail) {
        BookingAuditLog log = new BookingAuditLog();
        log.setAdminUserId(staffId);
        log.setActionType(action);
        log.setBookingId(bookingId);
        log.setCustomerId(customerId);
        try {
            log.setDetails(objectMapper.writeValueAsString(Map.of("detail", detail)));   // jsonb needs valid JSON
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize audit details", e);
        }
        auditLogRepository.save(log);
    }

    private static LocalDate today() { return LocalDate.now(ZoneOffset.UTC); }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static String month(LocalDate d) { return d.toString().substring(0, 7); }   // "2026-10"

    private OwnReview toOwn(Review r) {
        return new OwnReview(r.getId(), r.getRating(), r.getTitle(), r.getBody(), r.getStatus().name(),
                r.getStatus() == ReviewStatus.REJECTED ? r.getModerationNote() : null,   // HIDE notes stay internal
                r.getUpdatedAt());
    }

    private PublicReview toPublic(Review r) {
        return new PublicReview(r.getId(), r.getRating(), r.getTitle(), r.getBody(), r.getAuthorDisplayName(),
                month(r.getTripMonth()), r.getModeratedAt() != null ? r.getModeratedAt() : r.getCreatedAt());
    }

    private AdminReview toAdmin(Review r, Booking b) {
        return new AdminReview(r.getId(), r.getBookingId(), b == null ? null : b.getVoucherCode(), r.getCustomerId(),
                r.getDestinationId(), b == null ? null : b.getDestinationNameSnapshot(), r.getRating(), r.getTitle(),
                r.getBody(), r.getAuthorDisplayName(), month(r.getTripMonth()), r.getStatus().name(),
                r.getModerationNote(), r.getModeratedBy(), r.getModeratedAt(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
