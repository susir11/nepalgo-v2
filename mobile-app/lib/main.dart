import 'dart:async';

import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'core/providers.dart';
import 'core/push.dart';
import 'core/theme.dart';
import 'features/inbox.dart' show inboxProvider;
import 'router.dart';

final _messenger = GlobalKey<ScaffoldMessengerState>();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await FirebaseSetup.init();   // optional: app runs without it (no push / Google button)
  final prefs = await SharedPreferences.getInstance();
  runApp(ProviderScope(overrides: [prefsProvider.overrideWithValue(prefs)], child: const NepalGoApp()));
}

class NepalGoApp extends ConsumerStatefulWidget {
  const NepalGoApp({super.key});
  @override
  ConsumerState<NepalGoApp> createState() => _NepalGoAppState();
}

class _NepalGoAppState extends ConsumerState<NepalGoApp> {
  final _subs = <StreamSubscription<dynamic>>[];

  @override
  void initState() {
    super.initState();
    if (!FirebaseSetup.ready) return;
    // Tap on a notification while the app was in the background...
    _subs.add(FirebaseMessaging.onMessageOpenedApp.listen(_openFromPush));
    // ...or one that launched the app from terminated.
    FirebaseMessaging.instance.getInitialMessage().then((m) {
      if (m != null) _openFromPush(m);
    });
    // App in the foreground: Android shows nothing by itself, so show a banner
    // and refresh the inbox badge.
    _subs.add(FirebaseMessaging.onMessage.listen((m) {
      ref.invalidate(inboxProvider);
      final title = m.notification?.title;
      if (title == null) return;
      _messenger.currentState?.showSnackBar(SnackBar(
        content: Text(title),
        action: _bookingId(m) == null ? null : SnackBarAction(label: 'Open', onPressed: () => _openFromPush(m)),
      ));
    }));
  }

  @override
  void dispose() {
    for (final s in _subs) {
      s.cancel();
    }
    super.dispose();
  }

  static int? _bookingId(RemoteMessage m) => int.tryParse(m.data['bookingId']?.toString() ?? '');

  /// notification-service puts bookingId in the data payload: deep-link to that trip.
  void _openFromPush(RemoteMessage m) {
    ref.invalidate(inboxProvider);
    final id = _bookingId(m);
    ref.read(routerProvider).push(id == null ? '/inbox' : '/trip/$id');
  }

  @override
  Widget build(BuildContext context) {
    // Every transition to signed-in (login, sign-up, Google, restored session)
    // re-registers this device if notifications are already allowed.
    ref.listen(authProvider, (prev, next) {
      if (next.status == AuthStatus.signedIn && prev?.status != AuthStatus.signedIn) {
        ref.read(pushProvider).registerIfAllowed();
      }
    });
    return MaterialApp.router(
      title: 'NepalGo',
      debugShowCheckedModeBanner: false,
      scaffoldMessengerKey: _messenger,
      theme: buildTheme(Brightness.light),
      darkTheme: buildTheme(Brightness.dark),
      themeMode: ref.watch(themeModeProvider), // applied at the ROOT (16.1a), so every screen follows it
      scrollBehavior: const NgScrollBehavior(),
      routerConfig: ref.watch(routerProvider),
    );
  }
}
