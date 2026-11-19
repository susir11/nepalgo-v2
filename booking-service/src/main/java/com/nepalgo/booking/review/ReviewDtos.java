package com.nepalgo.booking.review;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Request/response shapes for reviews (records are fine nested; repositories are not). */
public final class ReviewDtos {

    private ReviewDtos() {}

    public record WriteReviewRequest(
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 80) String title,
            @NotBlank @Size(min = ReviewPolicy.MIN_BODY, max = ReviewPolicy.MAX_BODY) String body) {}

    /** The customer's own view: includes status and the reason if staff rejected it. */
    public record OwnReview(Long id, int rating, String title, String body, String status,
                            String moderationNote, Instant updatedAt) {}

    /** GET /api/bookings/{id}/review — tells the app whether to show "Write a review". */
    public record MyReviewResponse(boolean eligible, String ineligibleReason, LocalDate eligibleFrom, OwnReview review) {}

    /** Public card: no ids of customers or bookings, only a short display name. */
    public record PublicReview(Long id, int rating, String title, String body, String authorName,
                               String tripMonth, Instant publishedAt) {}

    public record ReviewSummary(double averageRating, long reviewCount, Map<Integer, Long> ratingCounts) {}

    public record DestinationReviewsResponse(ReviewSummary summary, List<PublicReview> items, int page, int totalPages) {}

    public record ModerateRequest(@NotNull Action action, @Size(max = 500) String note) {
        public enum Action { PUBLISH, REJECT, HIDE }
    }

    public record AdminReview(Long id, Long bookingId, String voucherCode, Long customerId, Long destinationId,
                              String destinationName, int rating, String title, String body, String authorName,
                              String tripMonth, String status, String moderationNote, Long moderatedBy,
                              Instant moderatedAt, Instant createdAt, Instant updatedAt) {}

    public record AdminReviewPage(List<AdminReview> items, int page, int totalPages, long totalItems, long pendingCount) {}
}
