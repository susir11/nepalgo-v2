package com.nepalgo.legal.content;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.nepalgo.legal.common.LegalAudit;
import com.nepalgo.legal.common.LegalErrors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// Onboarding & Company Profile content managers (spec 15): same draft -> publish
// lifecycle as legal documents, with light structural validation per key so a
// typo in the admin form can't publish something the app can't render.
@Service
@RequiredArgsConstructor
public class SiteContentService {

    private final SiteContentRepository repository;
    private final JsonMapper objectMapper;   // Jackson 3 (Boot 4 default, same one MVC uses)
    private final LegalAudit.Log audit;

    public record ContentView(Long id, String key, int versionNo, String status, boolean current, JsonNode payload,
                              String changeSummary, Instant publishedAt, Long publishedBy, Instant createdAt, Long version) {}

    @Transactional(readOnly = true)
    public ContentView current(SiteContent.Key key) {
        return view(repository.findByContentKeyAndCurrentTrue(key)
                .orElseThrow(() -> LegalErrors.notFound("NO_CURRENT_CONTENT", "No published " + key + " content")));
    }

    @Transactional(readOnly = true)
    public List<ContentView> history(SiteContent.Key key) {
        return repository.findByContentKeyOrderByVersionNoDesc(key).stream().map(this::view).toList();
    }

    @Transactional
    public ContentView createDraft(Long adminId, SiteContent.Key key, JsonNode payload, String changeSummary) {
        validate(key, payload);
        SiteContent c = new SiteContent();
        c.setContentKey(key);
        c.setVersionNo(repository.maxVersion(key) + 1);
        c.setStatus(SiteContent.Status.DRAFT);
        c.setPayload(write(payload));
        c.setChangeSummary(changeSummary);
        c.setCreatedBy(adminId);
        repository.save(c);
        audit.log(adminId, "CONTENT_DRAFT_CREATED", "SITE_CONTENT", c.getId(), key + " v" + c.getVersionNo());
        return view(c);
    }

    @Transactional
    public ContentView updateDraft(Long adminId, Long id, JsonNode payload, String changeSummary) {
        SiteContent c = get(id);
        requireDraft(c);
        validate(c.getContentKey(), payload);
        c.setPayload(write(payload));
        if (changeSummary != null) c.setChangeSummary(changeSummary);
        repository.saveAndFlush(c);
        audit.log(adminId, "CONTENT_DRAFT_UPDATED", "SITE_CONTENT", c.getId(), c.getContentKey() + " v" + c.getVersionNo());
        return view(c);
    }

    @Transactional
    public ContentView publish(Long adminId, Long id) {
        SiteContent c = get(id);
        requireDraft(c);
        repository.findByContentKeyAndCurrentTrue(c.getContentKey()).ifPresent(old -> {
            old.setCurrent(false);
            repository.saveAndFlush(old);   // retire first: one-current-per-key unique index
        });
        c.setStatus(SiteContent.Status.PUBLISHED);
        c.setCurrent(true);
        c.setPublishedAt(Instant.now());
        c.setPublishedBy(adminId);
        repository.saveAndFlush(c);
        audit.log(adminId, "CONTENT_PUBLISHED", "SITE_CONTENT", c.getId(), c.getContentKey() + " v" + c.getVersionNo());
        return view(c);
    }

    // Minimal schema checks: enough to stop a broken carousel / profile going live.
    static void validate(SiteContent.Key key, JsonNode p) {
        if (p == null || !p.isObject()) {
            throw LegalErrors.badRequest("INVALID_CONTENT", "payload must be a JSON object");
        }
        if (key == SiteContent.Key.ONBOARDING) {
            JsonNode slides = p.get("slides");
            if (slides == null || !slides.isArray() || slides.isEmpty() || slides.size() > 5) {
                throw LegalErrors.badRequest("INVALID_CONTENT", "onboarding needs 1-5 slides");
            }
            for (JsonNode s : slides) {
                if (!s.hasNonNull("headline") || s.get("headline").asString().isBlank() || !s.hasNonNull("body")) {
                    throw LegalErrors.badRequest("INVALID_CONTENT", "every slide needs a headline and a body");
                }
            }
        } else {
            for (String f : List.of("legalName", "registrationNumber", "registeredAddress", "supportEmail", "website")) {
                if (!p.hasNonNull(f) || p.get(f).asString().isBlank()) {
                    throw LegalErrors.badRequest("INVALID_CONTENT", "company profile field '" + f + "' is required");
                }
            }
        }
    }

    private void requireDraft(SiteContent c) {
        if (c.getStatus() != SiteContent.Status.DRAFT) {
            throw LegalErrors.conflict("ALREADY_PUBLISHED", "Published content can't be edited; create a new draft");
        }
    }

    private SiteContent get(Long id) {
        return repository.findById(id).orElseThrow(() -> LegalErrors.notFound("CONTENT_NOT_FOUND", "Content not found: " + id));
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw LegalErrors.badRequest("INVALID_CONTENT", "payload is not valid JSON");
        }
    }

    private ContentView view(SiteContent c) {
        try {
            return new ContentView(c.getId(), c.getContentKey().name(), c.getVersionNo(), c.getStatus().name(), c.isCurrent(),
                    objectMapper.readTree(c.getPayload()), c.getChangeSummary(), c.getPublishedAt(), c.getPublishedBy(),
                    c.getCreatedAt(), c.getVersion());
        } catch (Exception e) {
            throw new IllegalStateException("Stored content is not valid JSON: " + c.getId(), e);
        }
    }
}
