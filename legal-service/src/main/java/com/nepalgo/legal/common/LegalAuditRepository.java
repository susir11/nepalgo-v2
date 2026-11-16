package com.nepalgo.legal.common;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalAuditRepository extends JpaRepository<LegalAudit.Entry, Long> {
}
