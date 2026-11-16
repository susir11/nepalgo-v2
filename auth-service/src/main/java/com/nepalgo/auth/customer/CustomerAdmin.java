package com.nepalgo.auth.customer;

import com.nepalgo.auth.dto.PageResponse;
import com.nepalgo.auth.staff.AdminAuditLog;
import com.nepalgo.auth.staff.AdminAuditLogRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

// Customer Management (spec 15.2): search, profile, suspend/reactivate,
// plus a tiny internal endpoint other services use to check status.
public final class CustomerAdmin {

    private CustomerAdmin() {}

    public record CustomerView(Long id, String nepalgoId, String nepalgoIdDisplay, String email, String displayName,
                               String status, Instant suspendedAt, String suspensionReason, Instant createdAt) {
        static CustomerView of(Customer c) {
            String raw = c.getNepalgoId();
            // "48291057" -> "4829 1057" (section 5a display format)
            String display = raw != null && raw.length() == 8 ? raw.substring(0, 4) + " " + raw.substring(4) : raw;
            return new CustomerView(c.getId(), raw, display, c.getEmail(), c.getDisplayName(), c.getStatus(),
                    c.getSuspendedAt(), c.getSuspensionReason(), c.getCreatedAt());
        }
    }

    public record AuditEntry(Long id, Long actorStaffId, String action, String details, Instant createdAt) {}

    public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record InternalCustomer(Long id, String nepalgoId, String email, String displayName, String status) {}

    public static class CustomerNotFound extends RuntimeException {
        CustomerNotFound(String what) { super("Customer not found: " + what); }
    }

    public static class CustomerStateConflict extends RuntimeException {
        CustomerStateConflict(String msg) { super(msg); }
    }

    @Service
    @RequiredArgsConstructor
    public static class AdminService {
        private final CustomerRepository customers;
        private final AdminAuditLogRepository audit;

        @Transactional(readOnly = true)
        public PageResponse<CustomerView> search(String q, String status, int page, int size) {
            String query = q == null || q.isBlank() ? null : q.trim();
            String st = status == null || status.isBlank() ? null : status.trim().toUpperCase();
            Page<Customer> p = customers.adminSearch(query, st, PageRequest.of(page, Math.min(size, 100)));
            return new PageResponse<>(p.getContent().stream().map(CustomerView::of).toList(), page, size,
                    p.getTotalElements(), p.getTotalPages());
        }

        @Transactional(readOnly = true)
        public CustomerView get(Long id) {
            return CustomerView.of(find(id));
        }

        @Transactional(readOnly = true)
        public CustomerView byNepalgoId(String nepalgoId) {
            String raw = nepalgoId.replace(" ", "");
            return CustomerView.of(customers.findByNepalgoId(raw).orElseThrow(() -> new CustomerNotFound("NepalGo ID " + raw)));
        }

        @Transactional(readOnly = true)
        public List<AuditEntry> history(Long id) {
            find(id);
            return audit.findByTargetCustomerIdOrderByCreatedAtAsc(id).stream()
                    .map(a -> new AuditEntry(a.getId(), a.getActorStaffId(), a.getAction(), a.getDetails(), a.getCreatedAt()))
                    .toList();
        }

        @Transactional
        public CustomerView suspend(Long actorId, Long id, String reason) {
            Customer c = find(id);
            if ("SUSPENDED".equals(c.getStatus())) {
                throw new CustomerStateConflict("Customer is already suspended");
            }
            c.setStatus("SUSPENDED");
            c.setSuspendedAt(Instant.now());
            c.setSuspensionReason(reason.trim());
            customers.save(c);
            log(actorId, "CUSTOMER_SUSPENDED", id, reason.trim());
            return CustomerView.of(c);
        }

        @Transactional
        public CustomerView reactivate(Long actorId, Long id, String reason) {
            Customer c = find(id);
            if (!"SUSPENDED".equals(c.getStatus())) {
                throw new CustomerStateConflict("Customer is not suspended");
            }
            c.setStatus("ACTIVE");
            c.setSuspendedAt(null);
            c.setSuspensionReason(null);
            customers.save(c);
            log(actorId, "CUSTOMER_REACTIVATED", id, reason.trim());
            return CustomerView.of(c);
        }

        @Transactional(readOnly = true)
        public InternalCustomer internal(Long id) {
            Customer c = find(id);
            return new InternalCustomer(c.getId(), c.getNepalgoId(), c.getEmail(), c.getDisplayName(), c.getStatus());
        }

        private Customer find(Long id) {
            return customers.findById(id).orElseThrow(() -> new CustomerNotFound("id " + id));
        }

        private void log(Long actorId, String action, Long customerId, String details) {
            AdminAuditLog a = new AdminAuditLog();
            a.setActorStaffId(actorId);
            a.setAction(action);
            a.setTargetCustomerId(customerId);
            a.setDetails(details);
            audit.save(a);
        }
    }

    // OPS + SUPER_ADMIN (the staff chain authenticates all /api/v1/admin/**).
    @RestController
    @RequestMapping("/api/v1/admin/customers")
    @RequiredArgsConstructor
    public static class AdminController {
        private final AdminService service;

        @GetMapping
        public PageResponse<CustomerView> search(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
            return service.search(q, status, page, size);
        }

        @GetMapping("/{id}")
        public CustomerView get(@PathVariable Long id) {
            return service.get(id);
        }

        @GetMapping("/by-nepalgo-id/{nepalgoId}")
        public CustomerView byNepalgoId(@PathVariable String nepalgoId) {
            return service.byNepalgoId(nepalgoId);
        }

        @GetMapping("/{id}/history")
        public List<AuditEntry> history(@PathVariable Long id) {
            return service.history(id);
        }

        @PostMapping("/{id}/suspend")
        public CustomerView suspend(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
            return service.suspend(Long.parseLong(jwt.getSubject()), id, req.reason());
        }

        @PostMapping("/{id}/reactivate")
        public CustomerView reactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
            return service.reactivate(Long.parseLong(jwt.getSubject()), id, req.reason());
        }
    }

    @RestController
    @RequestMapping("/internal/customers")
    @RequiredArgsConstructor
    public static class InternalController {
        private final AdminService service;

        @GetMapping("/{id}")
        public InternalCustomer get(@PathVariable Long id) {
            return service.internal(id);
        }
    }

    @RestControllerAdvice(assignableTypes = {AdminController.class, InternalController.class})
    public static class Errors {
        @ExceptionHandler(CustomerNotFound.class)
        public ProblemDetail notFound(CustomerNotFound e) {
            ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
            p.setProperty("code", "CUSTOMER_NOT_FOUND");
            return p;
        }

        @ExceptionHandler(CustomerStateConflict.class)
        public ProblemDetail conflict(CustomerStateConflict e) {
            ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
            p.setProperty("code", "INVALID_CUSTOMER_STATE");
            return p;
        }
    }
}
