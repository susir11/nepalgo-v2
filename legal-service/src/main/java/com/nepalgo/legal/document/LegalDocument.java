package com.nepalgo.legal.document;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

// One versioned legal document (spec 10). Terms and Privacy share this model;
// only Terms uses the three card sections for the gated Policy Review (spec 9).
@Entity
@Table(name = "legal_documents")
@Getter
@Setter
@NoArgsConstructor
public class LegalDocument {

    public enum Type { TERMS_AND_CONDITIONS, PRIVACY_POLICY }
    public enum Status { DRAFT, PUBLISHED }

    // Record separator between fields: the hash covers every field without any
    // ambiguity about where one field ends and the next begins. MUST match the
    // SQL in V2__seed_v1_terms_and_privacy.sql.
    private static final String SEP = "\n\u001E\n";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private Type documentType;

    @Column(name = "version_label", nullable = false, length = 20)
    private String versionLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "full_text", nullable = false, columnDefinition = "TEXT")
    private String fullText;

    @Column(name = "cancellation_matrix", columnDefinition = "TEXT")
    private String cancellationMatrix;

    @Column(name = "force_majeure_weather", columnDefinition = "TEXT")
    private String forceMajeureWeather;

    @Column(name = "identity_liability", columnDefinition = "TEXT")
    private String identityLiability;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    // Fingerprint of exactly what the customer was shown. Stored on publish and
    // copied into every ConsentRecord: years later we can prove which text was
    // agreed to, the way a signed payment instruction carries a checksum.
    public String computeSha256() {
        String canonical = String.join(SEP, nz(title), nz(fullText), nz(cancellationMatrix),
                nz(forceMajeureWeather), nz(identityLiability));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
