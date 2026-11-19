-- Store the object KEY of each image size, not a full URL.
--
-- Before: "http://localhost:9000/destination-images/destinations/1/<uuid>-full.jpg"
-- was frozen into the row at upload time. A phone can't reach "localhost", and a
-- later move to a CDN/domain would have broken every existing image. Now the row
-- holds "destinations/1/<uuid>-full.jpg" and the API builds the public URL from
-- config (nepalgo.storage.public-url-base) on every read. Like storing an IBAN
-- rather than a printed bank statement: the presentation can change, the
-- identifier doesn't.
ALTER TABLE destination_images RENAME COLUMN thumbnail_url TO thumbnail_key;
ALTER TABLE destination_images RENAME COLUMN medium_url    TO medium_key;
ALTER TABLE destination_images RENAME COLUMN full_url      TO full_key;

-- Strip "<scheme>://<host>/<bucket>/" (or any leading path up to the bucket) from existing rows.
UPDATE destination_images SET
    thumbnail_key = regexp_replace(thumbnail_key, '^.*?/destination-images/', ''),
    medium_key    = regexp_replace(medium_key,    '^.*?/destination-images/', ''),
    full_key      = regexp_replace(full_key,      '^.*?/destination-images/', '');

CREATE INDEX IF NOT EXISTS idx_destination_images_dest_order ON destination_images (destination_id, display_order);
