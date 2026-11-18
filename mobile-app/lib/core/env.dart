// Build-time configuration, passed with --dart-define so no environment value
// is hard-coded in source:
//   flutter run -d chrome \
//     --dart-define=API_BASE=http://localhost:8080 \
//     --dart-define=FIREBASE_API_KEY=<web api key>
//
// Android emulator: API_BASE=http://10.0.2.2:8080 (10.0.2.2 = the host PC).
class Env {
  static const apiBase = String.fromEnvironment('API_BASE', defaultValue: 'http://localhost:8080');

  // Firebase *Web API key*. Not a secret (it only identifies the Firebase
  // project; access is controlled by Firebase Auth rules), but kept out of
  // source anyway so each environment can point at its own project.
  static const firebaseApiKey = String.fromEnvironment('FIREBASE_API_KEY');

  // Fallback WhatsApp number if the company profile can't be fetched.
  static const supportWhatsApp = String.fromEnvironment('SUPPORT_WHATSAPP', defaultValue: '9779800000000');
}
