# NepalGo — App store submission checklist

Status legend: ✅ done · ⚠️ needs work · ❌ blocker for store review. Re-check store policies at submission time; they change.

## ❌ Blockers

| Item | Why | What to build |
|---|---|---|
| **Sign in with Apple** (iOS) | The app now offers Google login, so Apple requires Sign in with Apple too (Guideline 4.8) before any iOS release. | Needs the paid Apple Developer account (Services ID + key), then enable Apple in Firebase Auth. Android release is NOT blocked by this. |

## ⚠️ Before first build

- [ ] **Android Studio + SDK** installed; `flutter doctor` green for Android. Xcode on a Mac for iOS.
- [x] App id / bundle id `com.nepalgo.app` (Android `applicationId` + namespace, iOS `PRODUCT_BUNDLE_IDENTIFIER`). Permanent after the first upload.
- [x] App name "NepalGo" (Android label, iOS display name, web manifest). Brand launcher icons (incl. Android adaptive) + native splash in theme green: source images from `mobile-app/tool/make_brand_icons.py`, regenerate with `dart run flutter_launcher_icons` and `dart run flutter_native_splash:create`.
- [x] iOS `Info.plist`: photo-library + camera usage texts (image_picker crashes without them), `ITSAppUsesNonExemptEncryption = NO`.
- [ ] Versioning: `pubspec.yaml` `version: 1.0.0+1` — bump the build number for every upload.
- [ ] **Release signing**: Gradle is wired to `android/key.properties` (gitignored; falls back to the debug key when absent, which Play rejects). Create the upload key once and keep the `.jks` + passwords in a password manager, outside the repo:
  `keytool -genkey -v -keystore C:/keys/nepalgo-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`
  then `android/key.properties`: `storePassword=…`, `keyPassword=…`, `keyAlias=upload`, `storeFile=C:/keys/nepalgo-upload.jks`; build with `flutter build appbundle --release --dart-define-from-file=dart_defines.json`. Verified 2026-11-19: the release bundle builds (54 MB .aab; Play serves smaller per-device APKs) and, without `key.properties`, is signed `CN=Android Debug` as intended — check `jarsigner -verify -verbose -certs` shows YOUR certificate before uploading. Enable Play App Signing. iOS: distribution certificate + provisioning profile (needs a Mac).
- [ ] `API_BASE` and `FIREBASE_API_KEY` passed with `--dart-define` for the production build; production Firebase project.
- [ ] Android `targetSdk` = current Play requirement; iOS minimum version decided.
- [x] FlutterFire Android: `google-services.json` generated (gitignored), Google sign-in + FCM wired; Gradle applies the Google Services plugin only when the file exists.
- [ ] FlutterFire iOS: download `GoogleService-Info.plist` from the Firebase console (Windows CLI skipped it) + upload an APNs key for iOS push.
- [ ] Add the **upload key** and **Play App Signing** SHA-1/SHA-256 to the Firebase Android app, or Google sign-in fails in Play builds (only the debug key is registered now).
- [x] FCM device token registered after login when notifications are allowed; permission asked after the first trip request; un-registered on sign-out; tapping a push opens that trip.

- [x] Store listing prepared: texts, 512 px icon, feature graphic, review-notes template in [`store-listing/`](store-listing/README.md). Screenshots still to capture (after replacing the Everest cover photo).
- [x] `mobile-app\build-android.ps1 -Release` builds the store bundle from `dart_defines.prod.json` and refuses emulator/http APIs, a missing `key.properties` or a debug signature.

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

- **Public legal pages** served by legal-service through the gateway, always the current published version: `https://<api domain>/privacy` (store "Privacy Policy URL"), `/terms`, `/delete-account` (Google Play "account deletion web link"). No JavaScript, strict CSP, mobile-friendly, light/dark.

- **In-app account deletion** (Profile → Delete account, typed DELETE confirmation): open requests withdrawn, personal data erased from bookings, inbox/devices erased, Firebase login deleted, NepalGo ID retired; invoices kept. Web deletion link for Google Play: `/delete-account`.

- **User-generated content (Apple 1.2)**: destination reviews are pre-moderated (staff publish/reject/take down in the admin portal), verified travellers only, reviews deleted with the account.
- Terms + Privacy versioned, consent recorded per booking (legal-service).
- Light / Dark / System appearance; accessible labels on nav and icon buttons; customer copy never shows raw status codes.
- Error, empty and offline states on every screen; unknown deep links show a friendly page.
- Customer sees USD only; invoices in PDF by e-mail after payment.
