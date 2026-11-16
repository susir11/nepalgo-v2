-- Onboarding carousel (spec 16.8) and company profile (spec 4a), version 1.
-- Both are admin-editable via draft -> publish; the app also ships a built-in
-- fallback copy in case this fetch fails (spec 4a).

INSERT INTO site_content (content_key, version_no, status, is_current, payload, change_summary, published_at)
VALUES ('ONBOARDING', 1, 'PUBLISHED', true, $json$
{
  "slides": [
    {
      "eyebrow": "Welcome to NepalGo",
      "headline": "Your companion for Nepal.",
      "body": "Every itinerary is planned by hand by our team in Kathmandu — real people who know the trails, the hotels and the weather.",
      "imageUrl": null
    },
    {
      "eyebrow": "Built around you",
      "headline": "Tell us your dates. We do the rest.",
      "body": "Send a trip request with your party size and wishes. We check availability with local partners and send you an itemised bill — no surprises.",
      "imageUrl": null
    },
    {
      "eyebrow": "Real people, real help",
      "headline": "We're one message away.",
      "body": "Questions before or during your trip? Our Kathmandu team answers on WhatsApp.",
      "imageUrl": null
    }
  ],
  "ctaLabel": "Get Started"
}
$json$::jsonb, 'Initial copy (spec 16.8)', now());

INSERT INTO site_content (content_key, version_no, status, is_current, payload, change_summary, published_at)
VALUES ('COMPANY_PROFILE', 1, 'PUBLISHED', true, $json$
{
  "legalName": "[Company Legal Name]",
  "registrationNumber": "[number]",
  "registeredAddress": "[address], Kathmandu, Nepal",
  "supportEmail": "[support email]",
  "supportWhatsApp": "[+977 WhatsApp number]",
  "website": "[https://website]",
  "about": "NepalGo is a Kathmandu-based team that hand-builds trips across Nepal with trusted local hotels, drivers and guides."
}
$json$::jsonb, 'Placeholders — fill in real company details before launch', now());
