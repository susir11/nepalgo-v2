-- Seed v1 legal documents (spec section 10), extracted VERBATIM from the spec
-- (only hard line-wraps re-flowed into paragraphs for mobile display).
--
-- !! LEGAL REVIEW REQUIRED BEFORE PRODUCTION !! These texts fix a binding
-- cancellation schedule and shift identity liability to the customer, and the
-- privacy policy covers passport data of (likely) EU customers. Placeholders
-- such as [Company Legal Name] must be filled in by publishing a v2 from the
-- admin Policy Content Manager — published versions are never edited in place.

INSERT INTO legal_documents (document_type, version_label, status, is_current, title, full_text,
                             cancellation_matrix, force_majeure_weather, identity_liability,
                             change_summary, effective_from, published_at, content_sha256)
SELECT *, encode(sha256(convert_to(coalesce(title, '') || E'\n\x1E\n' || coalesce(full_text, '') || E'\n\x1E\n' || coalesce(cancellation_matrix, '') || E'\n\x1E\n' || coalesce(force_majeure_weather, '') || E'\n\x1E\n' || coalesce(identity_liability, ''), 'UTF8')), 'hex') FROM (VALUES ('TERMS_AND_CONDITIONS', 'v1', 'PUBLISHED', true, 'NepalGo — Terms and Conditions',
$nepalgo$NEPALGO — TERMS AND CONDITIONS

These Terms and Conditions ("Terms") govern your use of the NepalGo mobile application and booking service ("Service"), operated by [Company Legal Name], a company registered in Nepal under registration number [number], with its registered office at [address] ("NepalGo," "we," "us"). By creating an account or submitting a booking request, you agree to these Terms.

1. ELIGIBILITY. You must be at least 18 years old and have the legal capacity to enter a binding contract to use the Service.

2. WHAT NEPALGO DOES. NepalGo is a booking platform that connects travellers with hotels, transport providers, and tourist guides operating in Nepal, and facilitates payment for those bookings. NepalGo does not itself provide accommodation, transport, or guiding services — these are provided by independent third-party vendors. NepalGo does not arrange, purchase, or guarantee government permits, visas, or entry authorizations of any kind; permit or route-restriction information shown in the app is for general guidance only and does not substitute for your own verification with the relevant Nepali government authority.

3. HOW A BOOKING WORKS.
3.1 You submit a trip request specifying your travel party size, arrival date, and any special requests (including an optional guide or porter). This does not create a confirmed booking and you cannot pay at this stage. Status: "Pending Approval."
3.2 Our team verifies real-world availability with the relevant vendor(s) and prices your itemized bill. Once ready, you will be shown the finalized itinerary for your review, followed by an itemized bill in U.S. dollars using the exchange rate applicable at that time. Status: "Awaiting Payment."
3.3 Before paying, you will be asked to confirm your legal name (as it appears on your passport), passport number, and email address, and to review and agree to these Terms. You then complete payment via the provided link and upload proof of payment (a screenshot or transaction reference). Status: "Verifying Payment" while our team confirms receipt of funds.
3.4 Once confirmed, your booking is CONFIRMED and you receive a voucher code and QR code, and a copy of your bill is sent to the email you provided. We target a 5-30 minute review turnaround during business hours once a payment is submitted; this is not a guaranteed timeframe, and initial availability review may take longer.
3.5 A rejected request or an unverifiable payment submission will be communicated to you with a reason and, where applicable, an opportunity to resubmit.

4. PRICING, CURRENCY, AND PAYMENT.
4.1 Your bill is itemized — each service (accommodation, transport, meals, guide, porter, and any other item) is listed with its own cost — rather than shown as a single lump sum, and is payable in U.S. dollars (or another currency we explicitly offer). The exchange rate applicable when your bill is issued is locked to your booking and will not change once issued.
4.2 Your itemized bill may include a subtotal, a service charge, and — if NepalGo is VAT-registered at the time of your booking — Nepal VAT, each shown separately along with the grand total.
4.3 Payments are processed via Payoneer, governed also by Payoneer's own terms of service. NepalGo does not store your payment card details.
4.4 Booking confirmation is subject to manual verification and is not automatic or instantaneous. Your bill's invoice number is fixed once issued; corrections made after you have paid are recorded as a linked correction to the original bill rather than an edit to it.

5. IDENTITY AND TRAVEL DETAIL ACCURACY.
5.1 Before payment, you are required to provide your exact legal name as it appears on your passport, your passport number, and your email address. NepalGo relies on this information as provided and does not, in the current version of the Service, independently verify it against a physical passport or visa.
5.2 You are solely and fully responsible for the accuracy of this information. If a typographical error, omission, or inaccuracy results in a checkpoint rejection, denied entry, permit refusal, or any other financial or logistical consequence, that consequence is your sole responsibility. NepalGo will not issue a refund, voucher, or other compensation in connection with a consequence caused by inaccurate information you provided, and this is separate from and not covered by the cancellation schedule in section 6.
5.3 You remain solely responsible for holding all travel documents, visas, and permits required for your trip and for every member of your travel party, independent of any information NepalGo provides.

6. CANCELLATION AND REFUND POLICY.
6.1 If you cancel a confirmed booking, the amount refunded or credited depends on how far in advance of your trip start date you cancel:
(a) 60+ days before your trip: a cash refund of 90% of the amount paid, with the remaining 10% retained as an administration fee.
(b) 30 to 59 days before your trip: 30% is retained as a cancellation penalty; the remaining 70% is issued as a NepalGo Travel Credit, valid 365 days from issue and non-transferable.
(c) 15 to 29 days before your trip: 50% is retained as a cancellation penalty; the remaining 50% is issued as a Travel Credit on the same terms as (b).
(d) 0 to 14 days before your trip: your booking is 100% non-refundable.
6.2 Where a cash refund applies under 6.1(a), it will be reinitiated as soon as reasonably possible; the time for it to reach you depends on international banking and payment-processor transaction times outside our control, and we do not guarantee a specific number of days.
6.3 Regardless of the schedule in 6.1, and where required by applicable law in your jurisdiction, you are entitled to a full cash refund with no fee and without regard to the above tiers where: (a) NepalGo cancels your booking or fails to deliver a confirmed booking through no fault of yours; or (b) you cancel because of unavoidable and extraordinary circumstances at your destination that significantly affect your trip (natural disaster, government-imposed travel restriction, civil unrest). NepalGo will provide such a refund without undue delay to your original payment method.
6.4 Weather and transport delays (including mountain flight delays common on routes such as Lukla) are not a cancellation event under this policy. NepalGo will provide reasonable rebooking assistance but is not responsible for incidental costs resulting from the delay. We strongly recommend travel insurance covering trip interruption and delay.

7. THIRD-PARTY SERVICES. Hotels, transport operators, and guides booked through NepalGo are independent businesses, not NepalGo employees or agents. Payoneer is an independent payment processor. NepalGo's liability in connection with services provided by these third parties is limited to what is required by applicable law.

8. LIMITATION OF LIABILITY. To the maximum extent permitted by applicable law, NepalGo's total liability to you arising from your use of the Service is limited to the amount you paid for the relevant booking. NepalGo is not liable for indirect, incidental, or consequential losses. Nothing in these Terms excludes or limits liability that cannot lawfully be excluded or limited under the law applicable to you.

9. DATA PRIVACY. Our collection and use of your personal data is described in our separate Privacy Policy, which forms part of these Terms by reference.

10. INTELLECTUAL PROPERTY. The NepalGo name, logo, and app content are the property of [Company Legal Name] and may not be used without permission.

11. PROHIBITED CONDUCT. You agree not to submit false information or use the Service for any unlawful purpose.

12. GOVERNING LAW AND DISPUTES. These Terms are governed by the laws of Nepal, and any dispute is subject to the exclusive jurisdiction of the courts of Kathmandu, Nepal, except where mandatory consumer protection law of your country of residence grants rights that cannot be limited by this clause, in which case those rights remain available to you.

13. CHANGES TO THESE TERMS. We may update these Terms from time to time. Material changes will be notified to you in-app before they take effect.

14. CONTACT. Questions about these Terms can be directed to [support email/contact].$nepalgo$,
$nepalgo$6. CANCELLATION AND REFUND POLICY.
6.1 If you cancel a confirmed booking, the amount refunded or credited depends on how far in advance of your trip start date you cancel:
(a) 60+ days before your trip: a cash refund of 90% of the amount paid, with the remaining 10% retained as an administration fee.
(b) 30 to 59 days before your trip: 30% is retained as a cancellation penalty; the remaining 70% is issued as a NepalGo Travel Credit, valid 365 days from issue and non-transferable.
(c) 15 to 29 days before your trip: 50% is retained as a cancellation penalty; the remaining 50% is issued as a Travel Credit on the same terms as (b).
(d) 0 to 14 days before your trip: your booking is 100% non-refundable.
6.2 Where a cash refund applies under 6.1(a), it will be reinitiated as soon as reasonably possible; the time for it to reach you depends on international banking and payment-processor transaction times outside our control, and we do not guarantee a specific number of days.$nepalgo$,
$nepalgo$6.3 Regardless of the schedule in 6.1, and where required by applicable law in your jurisdiction, you are entitled to a full cash refund with no fee and without regard to the above tiers where: (a) NepalGo cancels your booking or fails to deliver a confirmed booking through no fault of yours; or (b) you cancel because of unavoidable and extraordinary circumstances at your destination that significantly affect your trip (natural disaster, government-imposed travel restriction, civil unrest). NepalGo will provide such a refund without undue delay to your original payment method.
6.4 Weather and transport delays (including mountain flight delays common on routes such as Lukla) are not a cancellation event under this policy. NepalGo will provide reasonable rebooking assistance but is not responsible for incidental costs resulting from the delay. We strongly recommend travel insurance covering trip interruption and delay.$nepalgo$,
$nepalgo$5. IDENTITY AND TRAVEL DETAIL ACCURACY.
5.1 Before payment, you are required to provide your exact legal name as it appears on your passport, your passport number, and your email address. NepalGo relies on this information as provided and does not, in the current version of the Service, independently verify it against a physical passport or visa.
5.2 You are solely and fully responsible for the accuracy of this information. If a typographical error, omission, or inaccuracy results in a checkpoint rejection, denied entry, permit refusal, or any other financial or logistical consequence, that consequence is your sole responsibility. NepalGo will not issue a refund, voucher, or other compensation in connection with a consequence caused by inaccurate information you provided, and this is separate from and not covered by the cancellation schedule in section 6.
5.3 You remain solely responsible for holding all travel documents, visas, and permits required for your trip and for every member of your travel party, independent of any information NepalGo provides.$nepalgo$,
'Initial version seeded from spec v15 (pending legal review)', now(), now()))
  AS v(document_type, version_label, status, is_current, title, full_text, cancellation_matrix, force_majeure_weather, identity_liability, change_summary, effective_from, published_at);

INSERT INTO legal_documents (document_type, version_label, status, is_current, title, full_text,
                             change_summary, effective_from, published_at, content_sha256)
SELECT *, encode(sha256(convert_to(coalesce(title, '') || E'\n\x1E\n' || coalesce(full_text, '') || E'\n\x1E\n' || coalesce(NULL, '') || E'\n\x1E\n' || coalesce(NULL, '') || E'\n\x1E\n' || coalesce(NULL, ''), 'UTF8')), 'hex') FROM (VALUES ('PRIVACY_POLICY', 'v1', 'PUBLISHED', true, 'NepalGo — Privacy Policy',
$nepalgo$NEPALGO — PRIVACY POLICY

This Privacy Policy explains what personal data NepalGo ("we," "us") collects through the NepalGo mobile application and booking service ("Service"), why we collect it, and how it's handled. It applies alongside our Terms and Conditions.

1. INFORMATION WE COLLECT
- Account information: your name, email address, and the authentication identifier provided by your chosen sign-in method (Google, Apple, Facebook, or email/password), plus the permanent NepalGo ID we assign to your account.
- Booking and identity information: when you book, we collect your party size, arrival date, any special requests, and — before payment — your legal name as it appears on your passport, your passport number, and your email address.
- Payment-related information: a screenshot or transaction ID you submit as proof of payment. We do not collect or store your card or bank account details — payment itself is handled entirely by Payoneer, governed by Payoneer's own privacy practices.
- Communications: if you contact us via WhatsApp, that conversation is governed by WhatsApp's own privacy practices, not this policy, since it takes place outside the Service.

2. HOW WE USE THIS INFORMATION To create and maintain your account; to process, price, and confirm your booking; to generate and send your itemized bill/invoice, including for our own tax record-keeping obligations in Nepal; to verify your payment; to communicate with you about your booking; and to comply with applicable law.

3. WHO WE SHARE IT WITH We share the minimum necessary information with: Firebase/Google, for authentication; Payoneer, for payment processing; and local hotels, transport providers, or guides, limited to what's needed to fulfill a confirmed booking (such as a guest name and dates for a hotel reservation). We do not sell your personal information, and we do not share it with third parties for their own marketing purposes.

4. HOW WE STORE AND PROTECT IT Identity information is stored with encryption at rest and access limited to authorized personnel. We retain personal data only for as long as necessary for the purposes described above, or as required by applicable law (including Nepal's tax record-keeping requirements), after which it is deleted.

5. INTERNATIONAL DATA TRANSFERS NepalGo is based in Nepal. If you are located elsewhere, including the European Union or United Kingdom, your information will be processed in Nepal and by the third-party processors listed in section 3, which may be located in other countries. We take reasonable steps consistent with this policy to protect your information wherever it is processed.

6. YOUR RIGHTS Depending on where you live, you may have rights to access, correct, or request deletion of your personal information, or to object to certain processing. To exercise these rights, contact us at [privacy contact email].

7. CHILDREN The Service is not directed to, and we do not knowingly collect information from, anyone under 18, consistent with the eligibility requirement in our Terms and Conditions.

8. CHANGES TO THIS POLICY We may update this Privacy Policy from time to time. Material changes will be reflected here with an updated effective date; continued use of the Service after a change constitutes acceptance of the revised policy.

9. CONTACT Questions about this Privacy Policy can be directed to [privacy contact email].$nepalgo$,
'Initial version seeded from spec v15 (pending legal review)', now(), now()))
  AS v(document_type, version_label, status, is_current, title, full_text, change_summary, effective_from, published_at);
