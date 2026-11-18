import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'core/providers.dart';
import 'core/theme.dart';
import 'router.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  runApp(ProviderScope(overrides: [prefsProvider.overrideWithValue(prefs)], child: const NepalGoApp()));
}

class NepalGoApp extends ConsumerWidget {
  const NepalGoApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MaterialApp.router(
      title: 'NepalGo',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(Brightness.light),
      darkTheme: buildTheme(Brightness.dark),
      themeMode: ref.watch(themeModeProvider), // applied at the ROOT (16.1a), so every screen follows it
      scrollBehavior: const NgScrollBehavior(),
      routerConfig: ref.watch(routerProvider),
    );
  }
}
