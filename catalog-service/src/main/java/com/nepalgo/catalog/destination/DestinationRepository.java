package com.nepalgo.catalog.destination;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;


public interface DestinationRepository extends JpaRepository<Destination, Long> {

    // Every filter is optional — a NULL parameter means "don't filter on
    // this." The ::text / ::numeric / ::int casts on each parameter avoid
    // a known Postgres/Hibernate native-query gotcha ("could not determine
    // data type of parameter") that shows up when a bind value is NULL and
    // Postgres can't infer its type from context alone.
    //
    // Duration isn't a stored column — it's derived by counting DEFAULT
    // itinerary days per destination, per the spec's own definition of
    // "duration" as itinerary day-count, not a separately maintained field.
    @Query(value = """
    SELECT d.* FROM destinations d
    WHERE (CAST(:q AS text) IS NULL OR d.search_vector @@ plainto_tsquery('english', CAST(:q AS text)))
      AND (CAST(:region AS text) IS NULL OR d.region = CAST(:region AS text))
      AND (CAST(:type AS text) IS NULL OR d.type = CAST(:type AS text))
      AND (CAST(:minPrice AS numeric) IS NULL OR d.base_price_usd >= CAST(:minPrice AS numeric))
      AND (CAST(:maxPrice AS numeric) IS NULL OR d.base_price_usd <= CAST(:maxPrice AS numeric))
      AND (
        (CAST(:minDuration AS integer) IS NULL AND CAST(:maxDuration AS integer) IS NULL) OR
        d.id IN (
          SELECT iv.destination_id FROM itinerary_versions iv
          JOIN itinerary_days idd ON idd.itinerary_version_id = iv.id
          WHERE iv.tier = 'DEFAULT'
          GROUP BY iv.destination_id
          HAVING (CAST(:minDuration AS integer) IS NULL OR COUNT(*) >= CAST(:minDuration AS integer))
             AND (CAST(:maxDuration AS integer) IS NULL OR COUNT(*) <= CAST(:maxDuration AS integer))
        )
      )
    ORDER BY
      CASE WHEN CAST(:q AS text) IS NOT NULL THEN ts_rank(d.search_vector, plainto_tsquery('english', CAST(:q AS text))) END DESC NULLS LAST,
      d.created_at DESC
    """,
            countQuery = """
    SELECT COUNT(*) FROM destinations d
    WHERE (CAST(:q AS text) IS NULL OR d.search_vector @@ plainto_tsquery('english', CAST(:q AS text)))
      AND (CAST(:region AS text) IS NULL OR d.region = CAST(:region AS text))
      AND (CAST(:type AS text) IS NULL OR d.type = CAST(:type AS text))
      AND (CAST(:minPrice AS numeric) IS NULL OR d.base_price_usd >= CAST(:minPrice AS numeric))
      AND (CAST(:maxPrice AS numeric) IS NULL OR d.base_price_usd <= CAST(:maxPrice AS numeric))
      AND (
        (CAST(:minDuration AS integer) IS NULL AND CAST(:maxDuration AS integer) IS NULL) OR
        d.id IN (
          SELECT iv.destination_id FROM itinerary_versions iv
          JOIN itinerary_days idd ON idd.itinerary_version_id = iv.id
          WHERE iv.tier = 'DEFAULT'
          GROUP BY iv.destination_id
          HAVING (CAST(:minDuration AS integer) IS NULL OR COUNT(*) >= CAST(:minDuration AS integer))
             AND (CAST(:maxDuration AS integer) IS NULL OR COUNT(*) <= CAST(:maxDuration AS integer))
        )
      )
    """,
            nativeQuery = true)
    Page<Destination> search(
            @Param("q") String q,
            @Param("region") String region,
            @Param("type") String type,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("minDuration") Integer minDuration,
            @Param("maxDuration") Integer maxDuration,
            Pageable pageable
    );
}