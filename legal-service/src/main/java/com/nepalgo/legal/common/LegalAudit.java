package com.nepalgo.legal.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

// legal-service's own audit log (each service logs its own domain, spec 15.1).
public final class LegalAudit {

    private LegalAudit() {}

    @Entity
    @Table(name = "legal_audit_log")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Entry {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "admin_user_id")
        private Long adminUserId;

        @Column(name = "action_type", nullable = false, length = 50)
        private String actionType;

        @Column(name = "subject_type", nullable = false, length = 30)
        private String subjectType;

        @Column(name = "subject_id")
        private Long subjectId;

        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        private String details;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); }
    }

    @Service
    @RequiredArgsConstructor
    public static class Log {
        private final LegalAuditRepository repository;
        private final ObjectMapper objectMapper;

        // Wrap as {"detail": "..."}: a bare string is not valid jsonb (Phase 3 pitfall).
        public void log(Long adminId, String action, String subjectType, Long subjectId, String detail) {
            Entry e = new Entry();
            e.setAdminUserId(adminId);
            e.setActionType(action);
            e.setSubjectType(subjectType);
            e.setSubjectId(subjectId);
            if (detail != null) {
                try {
                    e.setDetails(objectMapper.writeValueAsString(Map.of("detail", detail)));
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }
            repository.save(e);
        }
    }
}
