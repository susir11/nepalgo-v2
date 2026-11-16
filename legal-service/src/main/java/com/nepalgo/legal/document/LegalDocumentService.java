package com.nepalgo.legal.document;

import com.nepalgo.legal.common.LegalAudit;
import com.nepalgo.legal.common.LegalErrors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// Policy Content Manager (spec 10 + 15): draft -> preview -> publish.
// Published versions are never edited in place (also enforced by a DB trigger).
@Service
@RequiredArgsConstructor
public class LegalDocumentService {

    private final LegalDocumentRepository repository;
    private final LegalAudit.Log audit;

    public record DocumentView(Long id, String documentType, String versionLabel, String status, boolean current,
                               String title, String fullText, String cancellationMatrix, String forceMajeureWeather,
                               String identityLiability, String changeSummary, String contentSha256,
                               Instant effectiveFrom, Instant publishedAt, Long publishedBy, Long createdBy,
                               Instant createdAt, Instant updatedAt, Long version) {
        public static DocumentView of(LegalDocument d) {
            return new DocumentView(d.getId(), d.getDocumentType().name(), d.getVersionLabel(), d.getStatus().name(),
                    d.isCurrent(), d.getTitle(), d.getFullText(), d.getCancellationMatrix(), d.getForceMajeureWeather(),
                    d.getIdentityLiability(), d.getChangeSummary(), d.getContentSha256(), d.getEffectiveFrom(),
                    d.getPublishedAt(), d.getPublishedBy(), d.getCreatedBy(), d.getCreatedAt(), d.getUpdatedAt(), d.getVersion());
        }
    }

    // What the app's Policy Review screen renders: the three cards + the version id
    // it must send back with the consent (so we know exactly what was shown).
    public record PolicyReview(Long termsDocumentId, String versionLabel, String contentSha256, String title,
                               List<Card> cards, String fullText) {}

    public record Card(String key, String title, String text) {}

    public record DraftInput(String title, String fullText, String cancellationMatrix, String forceMajeureWeather,
                             String identityLiability, String changeSummary) {}

    // ---------------------------------------------------------------- public

    @Transactional(readOnly = true)
    public DocumentView current(LegalDocument.Type type) {
        return DocumentView.of(currentOrThrow(type));
    }

    @Transactional(readOnly = true)
    public PolicyReview policyReview() {
        LegalDocument t = currentOrThrow(LegalDocument.Type.TERMS_AND_CONDITIONS);
        return new PolicyReview(t.getId(), t.getVersionLabel(), t.getContentSha256(), t.getTitle(), List.of(
                new Card("CANCELLATION_MATRIX", "Tiered Cancellation Matrix", t.getCancellationMatrix()),
                new Card("FORCE_MAJEURE_WEATHER", "Force-Majeure & Himalayan Weather Protocol", t.getForceMajeureWeather()),
                new Card("IDENTITY_LIABILITY", "Absolute Identity Liability", t.getIdentityLiability())),
                t.getFullText());
    }

    // Anyone may re-read a PUBLISHED version (e.g. "show me what I agreed to"); drafts stay private.
    @Transactional(readOnly = true)
    public DocumentView publishedById(Long id) {
        LegalDocument d = get(id);
        if (d.getStatus() != LegalDocument.Status.PUBLISHED) {
            throw LegalErrors.notFound("DOCUMENT_NOT_FOUND", "Legal document not found: " + id);
        }
        return DocumentView.of(d);
    }

    public LegalDocument currentOrThrow(LegalDocument.Type type) {
        return repository.findByDocumentTypeAndCurrentTrue(type)
                .orElseThrow(() -> LegalErrors.notFound("NO_CURRENT_VERSION", "No published " + type + " yet"));
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<DocumentView> history(LegalDocument.Type type) {
        return repository.findByDocumentTypeOrderByCreatedAtDesc(type).stream().map(DocumentView::of).toList();
    }

    @Transactional(readOnly = true)
    public DocumentView adminGet(Long id) {
        return DocumentView.of(get(id));   // includes drafts = the "preview"
    }

    // New draft. Fields left null are copied from the current version, so an admin
    // fixing one clause doesn't have to re-paste the whole document.
    @Transactional
    public DocumentView createDraft(Long adminId, LegalDocument.Type type, DraftInput in) {
        LegalDocument base = repository.findByDocumentTypeAndCurrentTrue(type).orElse(null);
        LegalDocument d = new LegalDocument();
        d.setDocumentType(type);
        d.setVersionLabel(nextLabel(type));
        d.setStatus(LegalDocument.Status.DRAFT);
        d.setCurrent(false);
        d.setCreatedBy(adminId);
        apply(d, in, base);
        if (d.getTitle() == null || d.getFullText() == null) {
            throw LegalErrors.badRequest("MISSING_CONTENT", "title and fullText are required for the first version");
        }
        repository.save(d);
        audit.log(adminId, "LEGAL_DRAFT_CREATED", "LEGAL_DOCUMENT", d.getId(), type + " " + d.getVersionLabel());
        return DocumentView.of(d);
    }

    @Transactional
    public DocumentView updateDraft(Long adminId, Long id, DraftInput in) {
        LegalDocument d = get(id);
        requireDraft(d);
        apply(d, in, d);
        repository.saveAndFlush(d);
        audit.log(adminId, "LEGAL_DRAFT_UPDATED", "LEGAL_DOCUMENT", d.getId(), d.getDocumentType() + " " + d.getVersionLabel());
        return DocumentView.of(d);
    }

    @Transactional
    public void deleteDraft(Long adminId, Long id) {
        LegalDocument d = get(id);
        requireDraft(d);
        repository.delete(d);
        audit.log(adminId, "LEGAL_DRAFT_DELETED", "LEGAL_DOCUMENT", id, d.getDocumentType() + " " + d.getVersionLabel());
    }

    // Publish = freeze + fingerprint + make current. The old current version is
    // retired FIRST and flushed, because the partial unique index allows only one
    // current row per type at any instant (same ordering rule as FX approval).
    @Transactional
    public DocumentView publish(Long adminId, Long id) {
        LegalDocument d = get(id);
        requireDraft(d);
        if (d.getDocumentType() == LegalDocument.Type.TERMS_AND_CONDITIONS
                && (blank(d.getCancellationMatrix()) || blank(d.getForceMajeureWeather()) || blank(d.getIdentityLiability()))) {
            throw LegalErrors.badRequest("MISSING_SECTIONS",
                    "Terms need all three Policy Review sections (cancellation, force majeure/weather, identity liability)");
        }
        repository.findByDocumentTypeAndCurrentTrue(d.getDocumentType()).ifPresent(old -> {
            old.setCurrent(false);
            repository.saveAndFlush(old);
        });
        Instant now = Instant.now();
        d.setContentSha256(d.computeSha256());
        d.setStatus(LegalDocument.Status.PUBLISHED);
        d.setCurrent(true);
        d.setPublishedAt(now);
        d.setEffectiveFrom(now);
        d.setPublishedBy(adminId);
        repository.saveAndFlush(d);
        // TODO Phase 8: notify customers in-app about material Terms changes (Terms clause 13).
        audit.log(adminId, "LEGAL_PUBLISHED", "LEGAL_DOCUMENT", d.getId(),
                d.getDocumentType() + " " + d.getVersionLabel() + " sha256=" + d.getContentSha256());
        return DocumentView.of(d);
    }

    // ---------------------------------------------------------------- helpers

    private void apply(LegalDocument d, DraftInput in, LegalDocument base) {
        d.setTitle(pick(in.title(), base == null ? null : base.getTitle()));
        d.setFullText(pick(in.fullText(), base == null ? null : base.getFullText()));
        d.setChangeSummary(in.changeSummary() != null ? in.changeSummary() : (base == d ? d.getChangeSummary() : null));
        if (d.getDocumentType() == LegalDocument.Type.TERMS_AND_CONDITIONS) {
            d.setCancellationMatrix(pick(in.cancellationMatrix(), base == null ? null : base.getCancellationMatrix()));
            d.setForceMajeureWeather(pick(in.forceMajeureWeather(), base == null ? null : base.getForceMajeureWeather()));
            d.setIdentityLiability(pick(in.identityLiability(), base == null ? null : base.getIdentityLiability()));
        } else if (in.cancellationMatrix() != null || in.forceMajeureWeather() != null || in.identityLiability() != null) {
            throw LegalErrors.badRequest("NOT_SECTIONED", "The Privacy Policy is a single full-text document (no card sections)");
        }
    }

    private String nextLabel(LegalDocument.Type type) {
        long n = repository.countByDocumentType(type) + 1;
        while (repository.existsByDocumentTypeAndVersionLabel(type, "v" + n)) n++;
        return "v" + n;
    }

    private static void requireDraft(LegalDocument d) {
        if (d.getStatus() != LegalDocument.Status.DRAFT) {
            throw LegalErrors.conflict("ALREADY_PUBLISHED",
                    d.getDocumentType() + " " + d.getVersionLabel() + " is published and can never be edited; create a new draft");
        }
    }

    private LegalDocument get(Long id) {
        return repository.findById(id).orElseThrow(() -> LegalErrors.notFound("DOCUMENT_NOT_FOUND", "Legal document not found: " + id));
    }

    private static String pick(String v, String fallback) {
        return blank(v) ? fallback : v.strip();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
