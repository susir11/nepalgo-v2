-- Needed for reliable weather lookups (city-name search is unreliable for
-- regions/districts, as opposed to named cities) and will also be useful
-- later for map-based browsing.
ALTER TABLE destinations
    ADD COLUMN latitude  NUMERIC(9, 6),
    ADD COLUMN longitude NUMERIC(9, 6);