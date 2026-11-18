import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api.dart';
import 'env.dart';

// ---------------------------------------------------------------- infrastructure
final tokenStoreProvider = Provider<TokenStore>((_) => TokenStore());

final apiProvider = Provider<Api>((ref) => Api(ref.read(tokenStoreProvider),
    onSessionExpired: () => ref.read(authProvider.notifier).sessionExpired()));

final prefsProvider = Provider<SharedPreferences>((_) => throw UnimplementedError('overridden in main()'));

// ---------------------------------------------------------------- appearance (16.1a)
// Light by default; Light / Dark / System chosen in Profile. A UI preference,
// so it lives in SharedPreferences, deliberately NOT in secure storage.
class ThemeModeController extends Notifier<ThemeMode> {
  static const _key = 'appearance';
  @override
  ThemeMode build() => ThemeMode.values.firstWhere((m) => m.name == ref.read(prefsProvider).getString(_key), orElse: () => ThemeMode.light);

  void set(ThemeMode m) {
    ref.read(prefsProvider).setString(_key, m.name);
    state = m;
  }
}

final themeModeProvider = NotifierProvider<ThemeModeController, ThemeMode>(ThemeModeController.new);

final onboardingSeenProvider = NotifierProvider<OnboardingSeen, bool>(OnboardingSeen.new);

class OnboardingSeen extends Notifier<bool> {
  @override
  bool build() => ref.read(prefsProvider).getBool('onboarding_seen') ?? false;
  void markSeen() {
    ref.read(prefsProvider).setBool('onboarding_seen', true);
    state = true;
  }
}

// ---------------------------------------------------------------- auth
enum AuthStatus { unknown, signedOut, signedIn }

class AuthState {
  final AuthStatus status;
  final Map<String, dynamic>? profile; // {nepalgoId, email, displayName}
  const AuthState(this.status, [this.profile]);
}

/// Customer sign-in = two hops, exactly as the backend expects:
///  1. Firebase Auth proves who the person is (email/password via Firebase's
///     REST API here; Google/Apple/Facebook via FlutterFire later).
///  2. auth-service verifies that Firebase ID token server-side and issues OUR
///     short-lived access token + refresh token (and the NepalGo ID on first signup).
class AuthController extends Notifier<AuthState> {
  final _firebase = Dio(BaseOptions(baseUrl: 'https://identitytoolkit.googleapis.com/v1/'));

  @override
  AuthState build() {
    _restore();
    return const AuthState(AuthStatus.unknown);
  }

  Api get _api => ref.read(apiProvider);

  Future<void> _restore() async {
    final store = ref.read(tokenStoreProvider);
    await store.load();
    if (store.access == null && await store.refreshToken() == null) {
      state = const AuthState(AuthStatus.signedOut);
      return;
    }
    try {
      await _loadProfile();
    } catch (_) {
      await store.clear();
      state = const AuthState(AuthStatus.signedOut);
    }
  }

  Future<void> _loadProfile() async {
    final me = await _api.get<Map<String, dynamic>>('/api/v1/auth/me');
    state = AuthState(AuthStatus.signedIn, me);
  }

  Future<void> signIn(String email, String password) =>
      _firebaseThenExchange('accounts:signInWithPassword', {'email': email, 'password': password, 'returnSecureToken': true});

  Future<void> signUp(String name, String email, String password) async {
    final r = await _firebaseCall('accounts:signUp', {'email': email, 'password': password, 'returnSecureToken': true});
    // Store the display name on the Firebase account so it flows into our profile.
    await _firebaseCall('accounts:update', {'idToken': r['idToken'], 'displayName': name, 'returnSecureToken': true}).then((u) => _exchange(u['idToken']));
  }

  // Spec 5: Firebase sends the reset e-mail; we only provide the entry point.
  Future<void> sendPasswordReset(String email) => _firebaseCall('accounts:sendOobCode', {'requestType': 'PASSWORD_RESET', 'email': email});

  Future<void> signOut() async {
    final store = ref.read(tokenStoreProvider);
    final rt = await store.refreshToken();
    if (rt != null) {
      try {
        await _api.postNoAuth('/api/v1/auth/logout', {'refreshToken': rt});
      } catch (_) {/* best effort: we clear locally regardless */}
    }
    await store.clear();
    state = const AuthState(AuthStatus.signedOut);
  }

  /// Spec 15.2: permanently deletes the account on the server (bookings are
  /// anonymised, open requests withdrawn, tax records kept), then forgets the
  /// session locally. Throws ApiException (409 = blocked for now, 503 = retry).
  Future<void> deleteAccount() async {
    await _api.delete('/api/v1/auth/me', {'confirmation': 'DELETE'});
    await ref.read(tokenStoreProvider).clear();
    state = const AuthState(AuthStatus.signedOut);
  }

  void sessionExpired() => state = const AuthState(AuthStatus.signedOut);

  Future<void> refreshProfile() => _loadProfile();

  Future<void> _firebaseThenExchange(String op, Map<String, dynamic> body) async {
    final r = await _firebaseCall(op, body);
    await _exchange(r['idToken']);
  }

  Future<void> _exchange(String idToken) async {
    final t = await _api.postNoAuth<Map<String, dynamic>>('/api/v1/auth/firebase/exchange', {'idToken': idToken});
    await ref.read(tokenStoreProvider).save(t['accessToken'], t['refreshToken']);
    await _loadProfile();
  }

  Future<Map<String, dynamic>> _firebaseCall(String op, Map<String, dynamic> body) async {
    if (Env.firebaseApiKey.isEmpty) {
      throw ApiException(null, 'CONFIG', 'App is missing FIREBASE_API_KEY (run with --dart-define).');
    }
    try {
      final r = await _firebase.post(op, queryParameters: {'key': Env.firebaseApiKey}, data: body);
      return Map<String, dynamic>.from(r.data);
    } on DioException catch (e) {
      final msg = (e.response?.data is Map) ? (e.response!.data['error']?['message'] ?? '') as String : '';
      throw ApiException(e.response?.statusCode, msg, _friendly(msg));
    }
  }

  // Firebase error codes -> human sentences. Unknown email and wrong password
  // read the same on purpose (don't help someone probe which emails exist).
  static String _friendly(String code) => switch (code) {
        'EMAIL_EXISTS' => 'An account with this email already exists. Try signing in.',
        'EMAIL_NOT_FOUND' || 'INVALID_PASSWORD' || 'INVALID_LOGIN_CREDENTIALS' => 'Email or password is incorrect.',
        'USER_DISABLED' => 'This account has been disabled. Please contact support.',
        'TOO_MANY_ATTEMPTS_TRY_LATER' => 'Too many attempts. Please wait a moment and try again.',
        _ when code.startsWith('WEAK_PASSWORD') => 'Please choose a stronger password (at least 6 characters).',
        'INVALID_EMAIL' => 'That email address doesn\'t look right.',
        _ => 'Sign-in failed. Please try again.',
      };
}

final authProvider = NotifierProvider<AuthController, AuthState>(AuthController.new);

// ---------------------------------------------------------------- shared data
final companyProfileProvider = FutureProvider<Map<String, dynamic>>((ref) async {
  try {
    final r = await ref.read(apiProvider).get<Map<String, dynamic>>('/api/content/company-profile');
    return Map<String, dynamic>.from(r['payload']);
  } catch (_) {
    // Spec 4a: fetched live WITH a built-in fallback if the fetch fails.
    return {'legalName': 'NepalGo', 'supportWhatsApp': Env.supportWhatsApp, 'about': 'A Kathmandu-based team that hand-builds trips across Nepal.'};
  }
});
