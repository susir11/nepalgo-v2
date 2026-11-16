package com.nepalgo.payment.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final PaymentAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    // details is a jsonb column, so a bare string is invalid JSON and Postgres
    // would reject the insert (and roll back the whole business transaction —
    // the exact bug we hit in booking-service). Wrapping as {"detail": "..."}
    // makes every caller safe, and ObjectMapper escapes quotes in free text.
    public void log(Long adminId, String actionType, String subjectType, Long subjectId, String detail) {
        PaymentAuditLog row = new PaymentAuditLog();
        row.setAdminUserId(adminId);
        row.setActionType(actionType);
        row.setSubjectType(subjectType);
        row.setSubjectId(subjectId);
        if (detail != null) {
            try {
                row.setDetails(objectMapper.writeValueAsString(Map.of("detail", detail)));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize audit details", e);
            }
        }
        repository.save(row);
    }
}