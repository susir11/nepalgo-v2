package com.nepalgo.notification.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByIdempotencyKey(String key);
    Page<Notification> findByAudienceAndCustomerIdOrderByCreatedAtDesc(Notification.Audience a, Long customerId, Pageable p);
    Page<Notification> findByAudienceOrderByCreatedAtDesc(Notification.Audience a, Pageable p);
    long countByAudienceAndCustomerIdAndReadAtIsNull(Notification.Audience a, Long customerId);
    long countByAudienceAndReadAtIsNull(Notification.Audience a);
}
