package com.nepalgo.booking.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByBookingId(Long bookingId);

    Page<Review> findByDestinationIdAndStatusOrderByCreatedAtDesc(Long destinationId, ReviewStatus status, Pageable pageable);

    Page<Review> findByStatusOrderByCreatedAtAsc(ReviewStatus status, Pageable pageable);   // oldest first = fair queue

    Page<Review> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Rating histogram of PUBLISHED reviews: rows of [rating, count]. Average is derived from it. */
    @Query("select r.rating, count(r) from Review r where r.destinationId = :destinationId "
            + "and r.status = com.nepalgo.booking.review.ReviewStatus.PUBLISHED group by r.rating")
    List<Object[]> ratingHistogram(Long destinationId);

    long countByStatus(ReviewStatus status);

    @Modifying
    @Query("delete from Review r where r.customerId = :customerId")
    int deleteByCustomerId(Long customerId);
}
