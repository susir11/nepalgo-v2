package com.nepalgo.booking.review;

import com.nepalgo.booking.review.ReviewDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** HTTP doors for reviews. Three audiences, three security chains (see BookingSecurityConfig). */
public final class ReviewControllers {

    private ReviewControllers() {}

    /** Customer token (aud nepalgo-customer). Sub-resource of the booking it reviews. */
    @RestController
    @RequestMapping("/api/bookings/{bookingId}/review")
    @RequiredArgsConstructor
    public static class Customer {
        private final ReviewService reviewService;

        @GetMapping
        public MyReviewResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
            return reviewService.myReview(Long.parseLong(jwt.getSubject()), bookingId);
        }

        @PutMapping
        public OwnReview put(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId,
                             @Valid @RequestBody WriteReviewRequest request) {
            return reviewService.write(Long.parseLong(jwt.getSubject()), bookingId, request);
        }
    }

    /** No token: anyone browsing a destination can read its published reviews. */
    @RestController
    @RequestMapping("/api/reviews")
    @RequiredArgsConstructor
    public static class Public {
        private final ReviewService reviewService;

        @GetMapping("/destinations/{destinationId}")
        public DestinationReviewsResponse forDestination(@PathVariable Long destinationId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
            return reviewService.forDestination(destinationId, page, size);
        }
    }

    /** Staff token (SUPER_ADMIN or OPS): moderation queue. */
    @RestController
    @RequestMapping("/api/admin/reviews")
    @RequiredArgsConstructor
    public static class Admin {
        private final ReviewService reviewService;

        @GetMapping
        public AdminReviewPage list(@RequestParam(defaultValue = "PENDING") String status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
            return reviewService.adminList(status, page, size);
        }

        @PostMapping("/{id}/moderate")
        public AdminReview moderate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                    @Valid @RequestBody ModerateRequest request) {
            return reviewService.moderate(Long.parseLong(jwt.getSubject()), id, request);
        }
    }
}
