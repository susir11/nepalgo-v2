package com.nepalgo.auth.staff;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    java.util.List<AdminAuditLog> findByTargetCustomerIdOrderByCreatedAtAsc(Long customerId);
}