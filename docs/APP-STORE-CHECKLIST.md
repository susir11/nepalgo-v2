# NepalGo — App store submission checklist

Status legend: ✅ done · ⚠️ needs work · ❌ blocker for store review. Re-check store policies at submission time; they change.

## ❌ Blockers

| Item | Why | What to build |
|---|---|---|
| **Sign in with Apple** (iOS) | If the iOS app offers Google/Facebook login, Apple requires Sign in with Apple too (Guideline 4.8). | Add when social login is enabled (FlutterFire config files needed). |
| **Public privacy-policy URL** | Both stores need a public web URL, not an in-app screen. | Serve the current Privacy Policy as a public HTML page (e.g. `https://nepalgo.com/privacy`, rendered from `GET /api/legal/privacy/current`). |

## ⚠️ Before first build

- [ ] **Android Studio + SDK** installed; `flutter doctor` green for Android. Xcode on a Mac for iOS.
- [ ] App id / bundle id: `com.nepalgo.app` (Android `applicationId`, iOS `PRODUCT_BUNDLE_IDENTIFIER`) — currently the Flutter default `com.nepalgo.nepalgo`.
- [ ] App name "NepalGo", launcher icons + splash (use `flutter_launcher_icons` / `flutter_native_splash` with brand colours).
- [ ] Versioning: `pubspec.yaml` `version: 1.0.0+1` — bump the build number for every upload.
- [ ] **Release signing**: Android upload keystore (store it in a password manager, NOT the repo; enable Play App Signing). iOS distribution certificate + provisioning profile.
- [ ] `API_BASE` and `FIREBASE_API_KEY` passed with `--dart-define` for the production build; production Firebase project.
- [ ] Android `targetSdk` = current Play requirement; iOS minimum version decided.
- [ ] FlutterFire: `google-services.json` (Android) + `GoogleService-Info.plist` (iOS) for push (FCM/APNs key uploaded to Firebase) and social login.
- [ ] Register the FCM device token after login: `POST /api/notifications/devices` (endpoint exists; app wiring pending with firebase_messaging).

## Permissions (only what we use)

| Permission | Where | Usage text (iOS `Info.plist` / Play declaration) |
|---|---|---|
| Photo library | Receipt upload (image_picker) | "NepalGo needs access to your photos so you can upload your payment receipt." (`NSPhotoLibraryUsageDescription`) |
| Camera (if offered) | Receipt photo | "Take a photo of your payment confirmation." (`NSCameraUsageDescription`) |
| Notifications | Booking updates | Ask after the first trip request, not at first launch. |
| Internet | everything | — |

No location, contacts, microphone or tracking. → iOS App Tracking Transparency prompt NOT needed.

## Store listing data declarations

**Google Play "Data safety" / Apple "App Privacy" — data collected and why (must match the Privacy Policy):**

| Data | Purpose | Linked to user | Shared |
|---|---|---|---|
| Name, e-mail | Account, booking, invoices | yes | Firebase (auth) |
| Passport number, legal name | Booking identity (Terms §5) | yes | Local vendors for the confirmed trip only |
| Payment receipt image + transaction id | Payment verification | yes | No (payment itself happens on Payoneer) |
| Device push token | Notifications | yes | Firebase (FCM) |
| Approximate trip dates, party size, special requests | Booking | yes | Local vendors for the confirmed trip |

No card data is collected (Payoneer). No advertising, no data sale. Data encrypted in transit (HTTPS).

## Review notes

- **Payments**: the app sells real-world travel services (hotels, transport, guides) consumed outside the app → external payment (Payoneer) is permitted (Apple 3.1.3(e)/3.1.5, Play payments policy exemption for physical goods/services). State this in the review notes.
- **Demo account** for reviewers: a test customer with one CONFIRMED trip (voucher + QR) and one AWAITING_PAYMENT trip. Provide e-mail + password in the review notes (not in the repo).
- **Age rating**: Terms require 18+ to book; content itself has no mature themes → set eligibility in the description and choose the appropriate questionnaire answers.
- **Export compliance (iOS)**: only standard HTTPS/TLS → exempt (`ITSAppUsesNonExemptEncryption = NO`).

## ✅ Already in place

- **In-app account deletion** (Profile → Delete account, typed DELETE confirmation): open requests withdrawn, personal data erased from bookings, inbox/devices erased, Firebase login deleted, NepalGo ID retired; invoices kept. Google Play also wants a **web** deletion link: point it at a support form / e-mail until a web page exists.

- Terms + Privacy versioned, consent recorded per booking (legal-service).
- Light / Dark / System appearance; accessible labels on nav and icon buttons; customer copy never shows raw status codes.
- Error, empty and offline states on every screen; unknown deep links show a friendly page.
- Customer sees USD only; invoices in PDF by e-mail after payment.
