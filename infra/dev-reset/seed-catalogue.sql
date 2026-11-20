-- Curated starter catalogue: Everest Base Camp, Annapurna Base Camp, Pokhara.
-- Runs as catalog_svc inside one transaction. Replaces ALL destinations (images,
-- itinerary versions and days cascade). Photos are not seeded: add licensed photos in
-- the admin portal (Destinations & itineraries -> Photos).
-- Prices are starting points ("from"), not quotes; each real trip is priced by hand.
-- Coordinates: the destination point drives the weather card; day points drive the maps.

TRUNCATE catalog.destination_images, catalog.itinerary_days, catalog.itinerary_versions, catalog.destinations
    RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------- 1. Everest Base Camp
WITH d AS (
  INSERT INTO catalog.destinations (name, region, type, description, best_season, base_price_usd, permit_notes, latitude, longitude)
  VALUES ('Everest Base Camp Trek', 'Khumbu', 'Trek',
          'The classic trek to the foot of the world''s highest mountain: Sherpa villages, monasteries and two acclimatisation days built in, with sunrise over Everest from Kala Patthar. Flights to and from Lukla, lodges, a licensed guide and permits are arranged for you.',
          'Mar-May, Sep-Nov', 1200.00,
          'Sagarmatha National Park entry permit and the Khumbu Pasang Lhamu Rural Municipality permit are required. Rules for guides and permits change - check the current requirements before you travel.',
          28.002600, 86.852800)
  RETURNING id
), v AS (
  INSERT INTO catalog.itinerary_versions (destination_id, tier) SELECT id, 'DEFAULT' FROM d RETURNING id
)
INSERT INTO catalog.itinerary_days (itinerary_version_id, day_number, narrative, hotel_name, hotel_lat, hotel_lng)
SELECT v.id, x.day, x.narrative, x.hotel, x.lat, x.lng FROM v, (VALUES
  (1,  'Morning flight Kathmandu - Lukla (2,860 m), then an easy walk down the Dudh Koshi valley to Phakding (2,610 m).', 'Teahouse lodge, Phakding', 27.740600, 86.713000),
  (2,  'Cross the Hillary suspension bridges and climb to Namche Bazaar (3,440 m), the Sherpa capital.', 'Teahouse lodge, Namche Bazaar', 27.806900, 86.714000),
  (3,  'Acclimatisation day: hike up to the Everest viewpoint above Namche and back down to sleep low.', 'Teahouse lodge, Namche Bazaar', 27.806900, 86.714000),
  (4,  'Contour path with Ama Dablam views, then up to Tengboche (3,860 m) and its monastery.', 'Teahouse lodge, Tengboche', 27.836200, 86.764600),
  (5,  'Through Pangboche to Dingboche (4,410 m) in the Imja valley.', 'Teahouse lodge, Dingboche', 27.892500, 86.831100),
  (6,  'Acclimatisation day: climb Nangkartshang ridge for views of Makalu and Lhotse.', 'Teahouse lodge, Dingboche', 27.892500, 86.831100),
  (7,  'Past the climbers'' memorials at Thukla Pass to Lobuche (4,940 m).', 'Teahouse lodge, Lobuche', 27.948300, 86.810300),
  (8,  'To Gorak Shep (5,164 m), then on to Everest Base Camp (5,364 m) and the Khumbu Icefall.', 'Teahouse lodge, Gorak Shep', 27.981000, 86.828700),
  (9,  'Pre-dawn climb of Kala Patthar (5,545 m) for sunrise on Everest, then descend to Pheriche (4,371 m).', 'Teahouse lodge, Pheriche', 27.895000, 86.819000),
  (10, 'Long descent back to Namche Bazaar.', 'Teahouse lodge, Namche Bazaar', 27.806900, 86.714000),
  (11, 'Final trekking day down to Lukla; farewell dinner with your crew.', 'Teahouse lodge, Lukla', 27.687800, 86.731400),
  (12, 'Morning flight Lukla - Kathmandu (weather permitting; a spare day is recommended).', NULL, NULL, NULL)
) AS x(day, narrative, hotel, lat, lng);

-- ---------------------------------------------------------------- 2. Annapurna Base Camp
WITH d AS (
  INSERT INTO catalog.destinations (name, region, type, description, best_season, base_price_usd, permit_notes, latitude, longitude)
  VALUES ('Annapurna Base Camp Trek', 'Annapurna', 'Trek',
          'A shorter, greener trek into the Annapurna Sanctuary: Gurung villages, rhododendron forest and a ring of 7,000 m+ peaks around base camp, finishing with a soak at the Jhinu hot springs. Starts and ends in Pokhara.',
          'Mar-May, Oct-Nov', 850.00,
          'Annapurna Conservation Area Permit (ACAP) required. Rules for guides and trekker registration change - check the current requirements before you travel.',
          28.530600, 83.878100)
  RETURNING id
), v AS (
  INSERT INTO catalog.itinerary_versions (destination_id, tier) SELECT id, 'DEFAULT' FROM d RETURNING id
)
INSERT INTO catalog.itinerary_days (itinerary_version_id, day_number, narrative, hotel_name, hotel_lat, hotel_lng)
SELECT v.id, x.day, x.narrative, x.hotel, x.lat, x.lng FROM v, (VALUES
  (1, 'Drive from Pokhara to the trailhead and walk up to the Gurung village of Ghandruk (1,940 m).', 'Teahouse lodge, Ghandruk', 28.375700, 83.808500),
  (2, 'Over Komrong ridge and down to Chhomrong (2,170 m), gateway to the Sanctuary.', 'Teahouse lodge, Chhomrong', 28.414600, 83.820300),
  (3, 'Stone steps down and up through forest to Bamboo (2,310 m).', 'Teahouse lodge, Bamboo', NULL, NULL),
  (4, 'Along the Modi Khola gorge to Deurali (3,230 m).', 'Teahouse lodge, Deurali', NULL, NULL),
  (5, 'Via Machhapuchhre Base Camp (3,700 m) to Annapurna Base Camp (4,130 m).', 'Teahouse lodge, Annapurna Base Camp', 28.530600, 83.878100),
  (6, 'Sunrise on Annapurna I and Machhapuchhre, then descend to Bamboo.', 'Teahouse lodge, Bamboo', NULL, NULL),
  (7, 'Down to Jhinu Danda (1,780 m) and the natural hot springs by the river.', 'Teahouse lodge, Jhinu Danda', NULL, NULL),
  (8, 'Short walk to the road and drive back to Pokhara.', 'Lakeside hotel, Pokhara', 28.209600, 83.958600),
  (9, 'Spare day in Pokhara for weather delays, or a rest by Phewa Lake.', 'Lakeside hotel, Pokhara', 28.209600, 83.958600)
) AS x(day, narrative, hotel, lat, lng);

-- ---------------------------------------------------------------- 3. Pokhara
WITH d AS (
  INSERT INTO catalog.destinations (name, region, type, description, best_season, base_price_usd, permit_notes, latitude, longitude)
  VALUES ('Pokhara Lakes & Sunrise', 'Pokhara', 'City & lakes',
          'Nepal''s lake city at the foot of the Annapurnas: lakeside cafes, a rowing boat to the Tal Barahi temple and sunrise over Machhapuchhre from Sarangkot. An easy add-on before or after a trek.',
          'Sep-May', 320.00,
          'No trekking permit is needed for Pokhara or Sarangkot.',
          28.209600, 83.958600)
  RETURNING id
), v AS (
  INSERT INTO catalog.itinerary_versions (destination_id, tier) SELECT id, 'DEFAULT' FROM d RETURNING id
)
INSERT INTO catalog.itinerary_days (itinerary_version_id, day_number, narrative, hotel_name, hotel_lat, hotel_lng)
SELECT v.id, x.day, x.narrative, x.hotel, x.lat, x.lng FROM v, (VALUES
  (1, 'Arrive in Pokhara (short flight or scenic drive from Kathmandu) and stroll the Phewa Lake shore at sunset.', 'Lakeside hotel, Pokhara', 28.209600, 83.958600),
  (2, 'Early drive to Sarangkot for sunrise over the Annapurnas, boat to Tal Barahi temple, afternoon at the World Peace Pagoda.', 'Lakeside hotel, Pokhara', 28.209600, 83.958600),
  (3, 'Davis Falls, Gupteshwor Cave and the International Mountain Museum, then onward travel.', NULL, NULL, NULL)
) AS x(day, narrative, hotel, lat, lng);
