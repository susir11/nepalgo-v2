package com.nepalgo.legal.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SiteContentRepository extends JpaRepository<SiteContent, Long> {

    Optional<SiteContent> findByContentKeyAndCurrentTrue(SiteContent.Key key);

    List<SiteContent> findByContentKeyOrderByVersionNoDesc(SiteContent.Key key);

    @Query("SELECT COALESCE(MAX(s.versionNo), 0) FROM SiteContent s WHERE s.contentKey = :key")
    int maxVersion(@Param("key") SiteContent.Key key);
}
