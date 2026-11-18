package com.nepalgo.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// support-service (spec 12): in-app chat + callback requests + shared admin inbox.
// Dormant: in_app_support_chat_enabled = OFF (spec 20). WhatsApp stays the default path.
public final class Support {

    private Support() {}

    // ================================================================ feature-flag gate
    // Runs BEFORE Spring Security (highest precedence): while OFF, every chat and
    // callback route is a 404 for everyone — unreachable, not merely hidden in the app.
    @Configuration
    public static class FlagGate {
        @Bean
        FilterRegistrationBean<OncePerRequestFilter> supportFlagFilter(@Value("${nepalgo.flags.in-app-support-chat-enabled}") boolean enabled) {
            OncePerRequestFilter f = new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                        throws ServletException, IOException {
                    String p = req.getRequestURI();
                    boolean gated = p.startsWith("/api/support/") || p.startsWith("/api/admin/support/");
                    if (gated && !enabled) {
                        // Write the 404 ourselves: sendError() would forward to /error,
                        // which the default-deny security chain turns into a 403 —
                        // and a 403 would reveal "something exists here".
                        res.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        res.setContentType("application/problem+json");
                        res.getWriter().write("{\"status\":404,\"title\":\"Not Found\",\"code\":\"NOT_FOUND\"}");
                        return;
                    }
                    chain.doFilter(req, res);
                }
            };
            FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(f);
            reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return reg;
        }
    }

    // ================================================================ DTOs
    public record ThreadView(Long id, Long customerId, Long bookingId, String subject, String status, Long assignedStaffId,
                             Instant lastMessageAt, Instant createdAt) {
        static ThreadView of(Model.ChatThread t) {
            return new ThreadView(t.getId(), t.getCustomerId(), t.getBookingId(), t.getSubject(), t.getStatus().name(),
                    t.getAssignedStaffId(), t.getLastMessageAt(), t.getCreatedAt());
        }
    }

    public record MessageView(Long id, String senderType, Long senderId, String body, Instant createdAt) {
        static MessageView of(Model.ChatMessage m) {
            return new MessageView(m.getId(), m.getSenderType().name(), m.getSenderId(), m.getBody(), m.getCreatedAt());
        }
    }

    public record ThreadDetail(ThreadView thread, List<MessageView> messages) {}

    public record NewThread(@NotBlank @Size(max = 200) String subject, Long bookingId, @NotBlank @Size(max = 4000) String message) {}
    public record NewMessage(@NotBlank @Size(max = 4000) String body) {}
    public record NewCallback(@NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{7,25}$", message = "enter a valid phone number") String phoneNumber,
                              @NotBlank @Size(max = 100) String preferredTime, Long bookingId) {}

    public static class NotFound extends RuntimeException { NotFound(String m) { super(m); } }
    public static class Conflict extends RuntimeException { Conflict(String m) { super(m); } }

    // ================================================================ service
    @Service
    @RequiredArgsConstructor
    public static class SupportService {
        private final ThreadRepository threads;
        private final MessageRepository messages;
        private final CallbackRepository callbacks;

        @Transactional
        public ThreadDetail open(Long customerId, NewThread r) {
            Model.ChatThread t = new Model.ChatThread();
            t.setCustomerId(customerId);
            t.setBookingId(r.bookingId());
            t.setSubject(r.subject().strip());
            threads.save(t);
            post(t, Model.ChatMessage.Sender.CUSTOMER, customerId, r.message());
            return detail(t);
        }

        @Transactional(readOnly = true)
        public List<ThreadView> mine(Long customerId) {
            return threads.findByCustomerIdOrderByLastMessageAtDesc(customerId).stream().map(ThreadView::of).toList();
        }

        @Transactional(readOnly = true)
        public ThreadDetail customerThread(Long customerId, Long id) {
            return detail(owned(customerId, id));
        }

        @Transactional
        public ThreadDetail customerReply(Long customerId, Long id, String body) {
            Model.ChatThread t = owned(customerId, id);
            t.setStatus(Model.ChatThread.Status.OPEN);       // a customer reply re-opens a closed thread
            post(t, Model.ChatMessage.Sender.CUSTOMER, customerId, body);
            return detail(t);
        }

        // ---- admin: shared inbox
        @Transactional(readOnly = true)
        public List<ThreadView> inbox(String status, int page) {
            var st = status == null ? Model.ChatThread.Status.OPEN : Model.ChatThread.Status.valueOf(status.toUpperCase());
            return threads.findByStatusOrderByLastMessageAtDesc(st, PageRequest.of(page, 50)).map(ThreadView::of).getContent();
        }

        @Transactional(readOnly = true)
        public ThreadDetail staffThread(Long id) {
            return detail(get(id));
        }

        // "Pick up": optimistic locking (@Version) means two staff clicking at the
        // same moment can't both claim it — the second gets a 409.
        @Transactional
        public ThreadView pickUp(Long staffId, Long id) {
            Model.ChatThread t = get(id);
            if (t.getAssignedStaffId() != null && !t.getAssignedStaffId().equals(staffId)) {
                throw new Conflict("Already picked up by staff #" + t.getAssignedStaffId());
            }
            t.setAssignedStaffId(staffId);
            threads.saveAndFlush(t);
            return ThreadView.of(t);
        }

        @Transactional
        public ThreadDetail staffReply(Long staffId, Long id, String body) {
            Model.ChatThread t = get(id);
            if (t.getAssignedStaffId() == null) t.setAssignedStaffId(staffId);   // replying = picking up
            post(t, Model.ChatMessage.Sender.STAFF, staffId, body);
            return detail(t);
        }

        @Transactional
        public ThreadView close(Long id) {
            Model.ChatThread t = get(id);
            t.setStatus(Model.ChatThread.Status.CLOSED);
            threads.save(t);
            return ThreadView.of(t);
        }

        // ---- callbacks (spec 12 data model)
        @Transactional
        public Model.CallbackRequest requestCallback(Long customerId, NewCallback r) {
            Model.CallbackRequest c = new Model.CallbackRequest();
            c.setCustomerId(customerId);
            c.setBookingId(r.bookingId());
            c.setPhoneNumber(r.phoneNumber().strip());
            c.setPreferredTime(r.preferredTime().strip());
            return callbacks.save(c);
        }

        @Transactional(readOnly = true)
        public List<Model.CallbackRequest> myCallbacks(Long customerId) {
            return callbacks.findByCustomerIdOrderByCreatedAtDesc(customerId);
        }

        @Transactional(readOnly = true)
        public List<Model.CallbackRequest> callbackQueue(String status) {
            return callbacks.findByStatusOrderByCreatedAtAsc(status == null ? Model.CallbackRequest.Status.PENDING
                    : Model.CallbackRequest.Status.valueOf(status.toUpperCase()));
        }

        @Transactional
        public Model.CallbackRequest completeCallback(Long staffId, Long id) {
            Model.CallbackRequest c = callbacks.findById(id).orElseThrow(() -> new NotFound("Callback request not found"));
            if (c.getStatus() == Model.CallbackRequest.Status.COMPLETED) throw new Conflict("Already completed");
            c.setStatus(Model.CallbackRequest.Status.COMPLETED);
            c.setCompletedBy(staffId);
            c.setCompletedAt(Instant.now());
            return callbacks.save(c);
        }

        private void post(Model.ChatThread t, Model.ChatMessage.Sender who, Long id, String body) {
            Model.ChatMessage m = new Model.ChatMessage();
            m.setThreadId(t.getId());
            m.setSenderType(who);
            m.setSenderId(id);
            m.setBody(body.strip());
            messages.save(m);
            t.setLastMessageAt(Instant.now());
            threads.save(t);
        }

        private ThreadDetail detail(Model.ChatThread t) {
            return new ThreadDetail(ThreadView.of(t), messages.findByThreadIdOrderByIdAsc(t.getId()).stream().map(MessageView::of).toList());
        }

        private Model.ChatThread owned(Long customerId, Long id) {
            // Someone else's thread is reported as "not found", never "forbidden".
            return threads.findById(id).filter(t -> t.getCustomerId().equals(customerId)).orElseThrow(() -> new NotFound("Thread not found"));
        }

        private Model.ChatThread get(Long id) {
            return threads.findById(id).orElseThrow(() -> new NotFound("Thread not found"));
        }
    }

    private static Long me(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }

    // ================================================================ controllers
    @RestController
    public static class Status {
        @Value("${nepalgo.flags.in-app-support-chat-enabled}")
        private boolean enabled;

        // Public: the app asks this to decide whether to show the chat entry point at all.
        @GetMapping("/api/support-status")
        public Map<String, Boolean> status() { return Map.of("inAppChatEnabled", enabled); }

        // Admin feature-flag viewer (spec 20), reachable even while the feature is OFF.
        @GetMapping("/api/admin/feature-flags/support")
        public List<Map<String, Object>> flags() {
            return List.of(Map.of("key", "in_app_support_chat_enabled", "enabled", enabled, "owner", "support-service",
                    "description", "Native in-app support chat + callback requests instead of WhatsApp. Fully built; unreachable while off."));
        }
    }

    @RestController
    @RequestMapping("/api/support")
    @RequiredArgsConstructor
    public static class CustomerApi {
        private final SupportService s;

        @GetMapping("/threads") public List<ThreadView> mine(@AuthenticationPrincipal Jwt jwt) { return s.mine(me(jwt)); }
        @PostMapping("/threads") public ResponseEntity<ThreadDetail> open(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewThread r) {
            return ResponseEntity.status(HttpStatus.CREATED).body(s.open(me(jwt), r));
        }
        @GetMapping("/threads/{id}") public ThreadDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) { return s.customerThread(me(jwt), id); }
        @PostMapping("/threads/{id}/messages") public ThreadDetail reply(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody NewMessage r) {
            return s.customerReply(me(jwt), id, r.body());
        }
        @PostMapping("/callbacks") public ResponseEntity<Model.CallbackRequest> callback(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewCallback r) {
            return ResponseEntity.status(HttpStatus.CREATED).body(s.requestCallback(me(jwt), r));
        }
        @GetMapping("/callbacks") public List<Model.CallbackRequest> myCallbacks(@AuthenticationPrincipal Jwt jwt) { return s.myCallbacks(me(jwt)); }
    }

    @RestController
    @RequestMapping("/api/admin/support")
    @RequiredArgsConstructor
    public static class AdminApi {
        private final SupportService s;

        @GetMapping("/threads") public List<ThreadView> inbox(@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page) {
            return s.inbox(status, page);
        }
        @GetMapping("/threads/{id}") public ThreadDetail get(@PathVariable Long id) { return s.staffThread(id); }
        @PostMapping("/threads/{id}/pick-up") public ThreadView pickUp(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) { return s.pickUp(me(jwt), id); }
        @PostMapping("/threads/{id}/messages") public ThreadDetail reply(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody NewMessage r) {
            return s.staffReply(me(jwt), id, r.body());
        }
        @PostMapping("/threads/{id}/close") public ThreadView close(@PathVariable Long id) { return s.close(id); }
        @GetMapping("/callbacks") public List<Model.CallbackRequest> callbacks(@RequestParam(required = false) String status) { return s.callbackQueue(status); }
        @PostMapping("/callbacks/{id}/complete") public Model.CallbackRequest complete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
            return s.completeCallback(me(jwt), id);
        }
    }

    @RestControllerAdvice
    public static class Errors {
        private static ProblemDetail p(HttpStatus s, String code, String msg) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(s, msg);
            pd.setProperty("code", code);
            return pd;
        }
        @ExceptionHandler(NotFound.class) public ProblemDetail nf(NotFound e) { return p(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage()); }
        @ExceptionHandler(Conflict.class) public ProblemDetail cf(Conflict e) { return p(HttpStatus.CONFLICT, "CONFLICT", e.getMessage()); }
        @ExceptionHandler(ObjectOptimisticLockingFailureException.class) public ProblemDetail race(ObjectOptimisticLockingFailureException e) {
            return p(HttpStatus.CONFLICT, "CONCURRENT_EDIT", "Someone else just picked this up; reload.");
        }
        @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
        public ProblemDetail invalid(org.springframework.web.bind.MethodArgumentNotValidException e) {
            ProblemDetail pd = p(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid");
            pd.setProperty("fieldErrors", e.getBindingResult().getFieldErrors().stream()
                    .collect(java.util.stream.Collectors.toMap(f -> f.getField(), f -> String.valueOf(f.getDefaultMessage()), (a, b) -> a)));
            return pd;
        }
    }
}
