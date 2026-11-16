package com.nepalgo.legal.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, Long> {

    Optional<LegalDocument> findByDocumentTypeAndCurrentTrue(LegalDocument.Type type);

    List<LegalDocument> findByDocumentTypeOrderByCreatedAtDesc(LegalDocument.Type type);

    boolean existsByDocumentTypeAndVersionLabel(LegalDocument.Type type, String versionLabel);

    long countByDocumentType(LegalDocument.Type type);
}
