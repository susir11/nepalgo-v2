import 'dart:async';
import 'dart:io' show Platform;

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'providers.dart';

/// Firebase (native SDK) is OPTIONAL at runtime. With no google-services.json /
/// GoogleService-Info.plist (CI, a fresh checkout, the dev-only web build) the
/// app still runs: e-mail login uses the Firebase REST API, and push + Google
/// sign-in simply stay hidden.
class FirebaseSetup {
  static bool ready = false;

  static Future<void> init() async {
    if (kIsWeb) return;
    try {
      // No options argument: Android/iOS read the platform config files.
      await Firebase.initializeApp();
      ready = true;
    } catch (e) {
      debugPrint('Firebase not configured, push + Google sign-in disabled: $e');
    }
  }
}

final pushProvider = Provider<PushService>((ref) => PushService(ref));

/// FCM device registration (backend: notification-service /api/notifications/devices).
///
/// Like enrolling a phone for card-transaction alerts: the bank (notification-service)
/// keeps "customer -> device tokens" and pushes to all of them; the phone must
/// re-enroll when its token rotates and un-enroll when the customer signs out,
/// or the next person using that phone would get someone else's alerts.
class PushService {
  PushService(this._ref);
  final Ref _ref;
  String? _registeredToken;
  StreamSubscription<String>? _refreshSub;

  static bool _granted(NotificationSettings s) =>
      s.authorizationStatus == AuthorizationStatus.authorized ||
      s.authorizationStatus == AuthorizationStatus.provisional;

  /// After login: register only if the OS already allows notifications
  /// (Android < 13 always does). Never shows a permission prompt.
  Future<void> registerIfAllowed() async {
    if (!FirebaseSetup.ready) return;
    try {
      if (_granted(await FirebaseMessaging.instance.getNotificationSettings())) await _register();
    } catch (e) {
      debugPrint('push registration skipped: $e');
    }
  }

  /// After the first trip request (store checklist: ask when the value is obvious,
  /// not at first launch). Shows the OS prompt once; later calls are no-ops.
  Future<void> askAndRegister() async {
    if (!FirebaseSetup.ready) return;
    try {
      if (_granted(await FirebaseMessaging.instance.requestPermission())) await _register();
    } catch (e) {
      debugPrint('push permission/registration failed: $e');
    }
  }

  /// Before sign-out (while the access token is still valid).
  Future<void> unregister() async {
    final token = _registeredToken;
    _registeredToken = null;
    await _refreshSub?.cancel();
    _refreshSub = null;
    if (!FirebaseSetup.ready || token == null) return;
    try {
      await _ref.read(apiProvider).delete('/api/notifications/devices/${Uri.encodeComponent(token)}');
    } catch (_) {/* best effort: the server also drops tokens FCM reports as invalid */}
  }

  // Deliberately the CLASSIC registration-token API (deprecated in firebase_messaging 17
  // in favour of register()/onRegistered, which yield Firebase Installation IDs for
  // the newer "direct send" model). notification-service sends with the Admin SDK's
  // sendEachForMulticast(registration tokens); migrate both sides together, later.
  Future<void> _register() async {
    // ignore: deprecated_member_use
    final token = await FirebaseMessaging.instance.getToken();
    if (token != null) await _send(token);
    // ignore: deprecated_member_use
    _refreshSub ??= FirebaseMessaging.instance.onTokenRefresh.listen(_send);
  }

  Future<void> _send(String token) async {
    if (token == _registeredToken) return;
    try {
      await _ref.read(apiProvider).post('/api/notifications/devices',
          {'token': token, 'platform': Platform.isIOS ? 'IOS' : 'ANDROID'});
      _registeredToken = token;
    } catch (e) {
      debugPrint('device token not saved: $e');
    }
  }
}
