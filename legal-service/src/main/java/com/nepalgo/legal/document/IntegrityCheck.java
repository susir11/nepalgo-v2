package com.nepalgo.legal.document;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Tamper check at startup: re-hash every PUBLISHED document and compare with the
// fingerprint stored at publish time. The DB trigger already blocks edits, but a
// superuser can disable triggers — this catches that, the same way a bank
// reconciles its ledger against independently stored totals.
@Component
@RequiredArgsConstructor
public class IntegrityCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IntegrityCheck.class);
    private final LegalDocumentRepository repository;

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        int ok = 0, bad = 0;
        for (LegalDocument d : repository.findAll()) {
            if (d.getStatus() != LegalDocument.Status.PUBLISHED) continue;
            if (d.computeSha256().equals(d.getContentSha256())) {
                ok++;
            } else {
                bad++;
                log.error("LEGAL INTEGRITY FAILURE: {} {} (id {}) content no longer matches its published fingerprint",
                        d.getDocumentType(), d.getVersionLabel(), d.getId());
            }
        }
        log.info("Legal integrity check: {} published document(s) verified, {} mismatch(es)", ok, bad);
    }
}
