package com.nepalgo.auth.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByFirebaseUid(String firebaseUid);
    boolean existsByNepalgoId(String nepalgoId);

    java.util.Optional<Customer> findByNepalgoId(String nepalgoId);

    // Admin customer search: NepalGo ID (exact, spaces ignored), or name/email
    // contains (case-insensitive). Optional status filter. CAST(...) pattern for
    // nullable params (pitfall from Phase 2).
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT * FROM customers
            WHERE (CAST(:q AS text) IS NULL
                   OR nepalgo_id = replace(CAST(:q AS text), ' ', '')
                   OR lower(email) LIKE '%' || lower(CAST(:q AS text)) || '%'
                   OR lower(display_name) LIKE '%' || lower(CAST(:q AS text)) || '%')
              AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
            ORDER BY created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM customers
            WHERE (CAST(:q AS text) IS NULL
                   OR nepalgo_id = replace(CAST(:q AS text), ' ', '')
                   OR lower(email) LIKE '%' || lower(CAST(:q AS text)) || '%'
                   OR lower(display_name) LIKE '%' || lower(CAST(:q AS text)) || '%')
              AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
            """,
            nativeQuery = true)
    org.springframework.data.domain.Page<Customer> adminSearch(
            @org.springframework.data.repository.query.Param("q") String q,
            @org.springframework.data.repository.query.Param("status") String status,
            org.springframework.data.domain.Pageable pageable);
}