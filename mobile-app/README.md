# NepalGo traveller app (Flutter)

Flutter 3.50 · Riverpod 3 · go_router · dio · flutter_secure_storage · flutter_map (OSM) · `material_ui`
(Material moved out of the SDK in Flutter 3.47+, so screens import `package:material_ui/material_ui.dart`).

## Run (web, against the local stack)

```powershell
cd mobile-app
flutter run -d chrome `
  --dart-define=API_BASE=http://localhost:8080 `
  --dart-define=FIREBASE_API_KEY=<firebase web api key>
```

Android emulator (one-time: `copy dart_defines.example.json dart_defines.json` and paste the Firebase web API key; the file is gitignored):

```powershell
.uild-android.ps1          # build + install + launch on the running emulator
.uild-android.ps1 -Fresh   # same, but wipe app data (shows onboarding again)
```

## Firebase (push notifications + Google sign-in) — optional per machine

The native Firebase config files are **gitignored**. Without them the app still builds and
runs (e-mail login works through the Firebase REST API); the Google button and push simply
stay hidden. To enable them on a machine:

```powershell
npm install -g firebase-tools; firebase login
dart pub global activate flutterfire_cli
cd mobile-app
flutterfire configure --platforms=android,ios --android-package-name=com.nepalgo.app --ios-bundle-id=com.nepalgo.app
```

Pick the SAME Firebase project auth-service uses (`nepalgo-4ff63`). Creates
`android/app/google-services.json`, `ios/Runner/GoogleService-Info.plist` (download it from the
console if the CLI skips it on Windows) and `lib/firebase_options.dart` (unused by the code).
Google sign-in on Android also needs this machine's signing SHA-1/SHA-256 added to the Android
app in Firebase (debug key: `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore
-alias androiddebugkey -storepass android -keypass android`), then re-run `flutterfire configure`.

## Structure

```
lib/
  core/      env (dart-defines) · theme (spec 16.1 tokens, light+dark) · api (dio, single-flight refresh)
             providers (auth, appearance, onboarding-seen, company profile) · widgets · format
  features/  onboarding · auth_screens · explore · destination · trips · trip_detail · support · profile
  router.dart  redirects driven by auth state; floating pill tab bar
```

## Flows
- Sign up / sign in / forgot password: Firebase Auth (REST, email+password) → `POST /api/v1/auth/firebase/exchange`
  → our own JWTs in secure storage. Google / Apple / Facebook need the FlutterFire config files
  (`google-services.json`, `GoogleService-Info.plist`) — not in the repo yet.
- Trip request → Research in progress → Final itinerary review → bill (USD only) → passport details →
  gated Policy Review (each card unlocks its checkbox only after it was opened; consent stored with the
  exact Terms version) → Payoneer → receipt photo + code → Payment under verification → voucher + QR.
- Travel credits applied at trip request; wallet cards in Profile; tiered cancellation.
