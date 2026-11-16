package com.nepalgo.legal;

import tools.jackson.databind.JsonNode;
import com.nepalgo.legal.common.LegalErrors;
import com.nepalgo.legal.consent.ConsentService;
import com.nepalgo.legal.content.SiteContent;
import com.nepalgo.legal.content.SiteContentService;
import com.nepalgo.legal.document.LegalDocument;
import com.nepalgo.legal.document.LegalDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

// All HTTP entry points of legal-service, grouped by caller.
public final class LegalControllers {

    private LegalControllers() {}

    static Long me(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    // URL slugs -> enums ("terms" / "privacy", "onboarding" / "company-profile").
    static LegalDocument.Type docType(String slug) {
        return switch (slug.toLowerCase(Locale.ROOT)) {
            case "terms" -> LegalDocument.Type.TERMS_AND_CONDITIONS;
            case "privacy" -> LegalDocument.Type.PRIVACY_POLICY;
            default -> throw LegalErrors.notFound("UNKNOWN_DOCUMENT_TYPE", "Use 'terms' or 'privacy'");
        };
    }

    static SiteContent.Key contentKey(String slug) {
        return switch (slug.toLowerCase(Locale.ROOT)) {
            case "onboarding" -> SiteContent.Key.ONBOARDING;
            case "company-profile" -> SiteContent.Key.COMPANY_PROFILE;
            default -> throw LegalErrors.notFound("UNKNOWN_CONTENT", "Use 'onboarding' or 'company-profile'");
        };
    }

    // ------------------------------------------------------------ public (no login)
    @RestController
    @RequiredArgsConstructor
    public static class Public {
        private final LegalDocumentService documents;
        private final SiteContentService content;

        @GetMapping("/api/legal/{type}/current")
        public LegalDocumentService.DocumentView current(@PathVariable String type) {
            return documents.current(docType(type));
        }

        // What the gated Policy Review screen renders (3 cards + version id to echo back).
        @GetMapping("/api/legal/policy-review")
        public LegalDocumentService.PolicyReview policyReview() {
            return documents.policyReview();
        }

        @GetMapping("/api/legal/documents/{id}")
        public LegalDocumentService.DocumentView published(@PathVariable Long id) {
            return documents.publishedById(id);
        }

        @GetMapping("/api/content/{key}")
        public SiteContentService.ContentView content(@PathVariable String key) {
            return content.current(contentKey(key));
        }
    }

    // ------------------------------------------------------------ customer
    public record ConsentRequest(@NotNull Long bookingId, @NotNull Long termsDocumentId,
                                 @NotNull ConsentService.SectionTimes cancellationMatrix,
                                 @NotNull ConsentService.SectionTimes forceMajeureWeather,
                                 @NotNull ConsentService.SectionTimes identityLiability) {}

    @RestController
    @RequestMapping("/api/legal/consents")
    @RequiredArgsConstructor
    public static class Customer {
        private final ConsentService consents;

        @PostMapping
        public ResponseEntity<ConsentService.ConsentView> consent(@AuthenticationPrincipal Jwt jwt,
                                                                  @Valid @RequestBody ConsentRequest req,
                                                                  HttpServletRequest http) {
            var in = new ConsentService.ConsentInput(req.bookingId(), req.termsDocumentId(), req.cancellationMatrix(),
                    req.forceMajeureWeather(), req.identityLiability());
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    consents.record(me(jwt), in, clientIp(http), http.getHeader("User-Agent")));
        }

        @GetMapping
        public List<ConsentService.ConsentView> mine(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(required = false) Long bookingId) {
            return consents.forCustomer(me(jwt), bookingId);
        }

        // Behind the gateway the real client IP arrives in X-Forwarded-For (first hop).
        private static String clientIp(HttpServletRequest http) {
            String fwd = http.getHeader("X-Forwarded-For");
            return fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : http.getRemoteAddr();
        }
    }

    // ------------------------------------------------------------ admin: Policy Content Manager
    @RestController
    @RequestMapping("/api/admin/legal")
    @RequiredArgsConstructor
    public static class AdminLegal {
        private final LegalDocumentService documents;
        private final ConsentService consents;

        @GetMapping("/{type}/versions")
        public List<LegalDocumentService.DocumentView> history(@PathVariable String type) {
            return documents.history(docType(type));
        }

        @GetMapping("/documents/{id}")
        public LegalDocumentService.DocumentView preview(@PathVariable Long id) {
            return documents.adminGet(id);
        }

        @PostMapping("/{type}/drafts")
        public ResponseEntity<LegalDocumentService.DocumentView> createDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable String type,
                                                                             @RequestBody LegalDocumentService.DraftInput in) {
            return ResponseEntity.status(HttpStatus.CREATED).body(documents.createDraft(me(jwt), docType(type), in));
        }

        @PutMapping("/documents/{id}")
        public LegalDocumentService.DocumentView updateDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                             @RequestBody LegalDocumentService.DraftInput in) {
            return documents.updateDraft(me(jwt), id, in);
        }

        @DeleteMapping("/documents/{id}")
        public ResponseEntity<Void> deleteDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
            documents.deleteDraft(me(jwt), id);
            return ResponseEntity.noContent().build();
        }

        @PostMapping("/documents/{id}/publish")
        public LegalDocumentService.DocumentView publish(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
            return documents.publish(me(jwt), id);
        }

        // Booking detail screen: which Terms version did this customer agree to, card by card.
        @GetMapping("/consents")
        public List<ConsentService.ConsentView> consents(@RequestParam Long bookingId) {
            return consents.forBooking(bookingId);
        }
    }

    // ------------------------------------------------------------ admin: onboarding & company profile
    public record ContentDraftRequest(@NotNull JsonNode payload, String changeSummary) {}

    @RestController
    @RequestMapping("/api/admin/content")
    @RequiredArgsConstructor
    public static class AdminContent {
        private final SiteContentService content;

        @GetMapping("/{key}/versions")
        public List<SiteContentService.ContentView> history(@PathVariable String key) {
            return content.history(contentKey(key));
        }

        @PostMapping("/{key}/drafts")
        public ResponseEntity<SiteContentService.ContentView> createDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable String key,
                                                                          @Valid @RequestBody ContentDraftRequest req) {
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    content.createDraft(me(jwt), contentKey(key), req.payload(), req.changeSummary()));
        }

        @PutMapping("/versions/{id}")
        public SiteContentService.ContentView updateDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                          @Valid @RequestBody ContentDraftRequest req) {
            return content.updateDraft(me(jwt), id, req.payload(), req.changeSummary());
        }

        @PostMapping("/versions/{id}/publish")
        public SiteContentService.ContentView publish(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
            return content.publish(me(jwt), id);
        }
    }

    // ------------------------------------------------------------ internal (booking-service)
    @RestController
    @RequestMapping("/internal/consents")
    @RequiredArgsConstructor
    public static class Internal {
        private final ConsentService consents;

        @GetMapping("/check")
        public ConsentService.CheckResult check(@RequestParam Long bookingId, @RequestParam Long customerId) {
            return consents.check(bookingId, customerId);
        }
    }
}
