import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import 'core/providers.dart';
import 'core/theme.dart';
import 'features/auth_screens.dart';
import 'features/destination.dart';
import 'features/explore.dart';
import 'features/onboarding.dart';
import 'features/profile.dart';
import 'features/support.dart';
import 'features/trip_detail.dart';
import 'features/trips.dart';

// Bridges Riverpod state -> go_router: whenever auth or onboarding state
// changes, the router re-runs its redirect logic.
class _RouterRefresh extends ChangeNotifier {
  _RouterRefresh(Ref ref) {
    ref.listen(authProvider, (_, _) => notifyListeners());
    ref.listen(onboardingSeenProvider, (_, _) => notifyListeners());
  }
}

final routerProvider = Provider<GoRouter>((ref) {
  final refresh = _RouterRefresh(ref);
  return GoRouter(
    initialLocation: '/splash',
    refreshListenable: refresh,
    redirect: (context, state) {
      final auth = ref.read(authProvider).status;
      final seen = ref.read(onboardingSeenProvider);
      final loc = state.matchedLocation;
      const authPages = ['/login', '/signup', '/forgot'];
      if (auth == AuthStatus.unknown) return loc == '/splash' ? null : '/splash';
      if (auth == AuthStatus.signedOut) {
        if (!seen) return loc == '/onboarding' ? null : '/onboarding';
        return authPages.contains(loc) || loc == '/onboarding' ? null : '/login';
      }
      // signed in
      if (loc == '/splash' || loc == '/onboarding' || authPages.contains(loc)) return '/explore';
      return null;
    },
    routes: [
      GoRoute(path: '/splash', builder: (_, _) => const SplashScreen()),
      GoRoute(path: '/onboarding', builder: (_, _) => const OnboardingScreen()),
      GoRoute(path: '/login', builder: (_, _) => const LoginScreen()),
      GoRoute(path: '/signup', builder: (_, _) => const SignUpScreen()),
      GoRoute(path: '/forgot', builder: (_, _) => const ForgotPasswordScreen()),
      GoRoute(path: '/destination/:id', builder: (_, s) => DestinationScreen(id: int.parse(s.pathParameters['id']!))),
      GoRoute(path: '/trip/:id', builder: (_, s) => TripDetailScreen(id: int.parse(s.pathParameters['id']!))),
      StatefulShellRoute.indexedStack(
        builder: (_, _, shell) => AppShell(shell: shell),
        branches: [
          StatefulShellBranch(routes: [GoRoute(path: '/explore', builder: (_, _) => const ExploreScreen())]),
          StatefulShellBranch(routes: [GoRoute(path: '/trips', builder: (_, _) => const TripsScreen())]),
          StatefulShellBranch(routes: [GoRoute(path: '/support', builder: (_, _) => const SupportScreen())]),
          StatefulShellBranch(routes: [GoRoute(path: '/profile', builder: (_, _) => const ProfileScreen())]),
        ],
      ),
    ],
  );
});

/// Tabs: Explore | My Trips | Support | Profile, as a FLOATING pill bar in deep
/// black — the one surface that stays dark in both Light and Dark mode (spec 3).
class AppShell extends StatelessWidget {
  final StatefulNavigationShell shell;
  const AppShell({super.key, required this.shell});

  @override
  Widget build(BuildContext context) {
    const items = [
      (Icons.explore_outlined, Icons.explore, 'Explore'),
      (Icons.luggage_outlined, Icons.luggage, 'My Trips'),
      (Icons.support_agent_outlined, Icons.support_agent, 'Support'),
      (Icons.person_outline, Icons.person, 'Profile'),
    ];
    return Scaffold(
      extendBody: true,
      body: shell,
      bottomNavigationBar: SafeArea(
        minimum: const EdgeInsets.fromLTRB(20, 0, 20, 14),
        child: Container(
          height: 64,
          decoration: BoxDecoration(color: context.ng.nav, borderRadius: BorderRadius.circular(NgRadius.pill)),
          child: Row(
            children: [
              for (var i = 0; i < items.length; i++)
                Expanded(
                  child: Semantics(
                    selected: shell.currentIndex == i,
                    button: true,
                    label: items[i].$3,
                    child: InkWell(
                      borderRadius: BorderRadius.circular(NgRadius.pill),
                      onTap: () => shell.goBranch(i, initialLocation: i == shell.currentIndex),
                      child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                        Icon(shell.currentIndex == i ? items[i].$2 : items[i].$1,
                            color: shell.currentIndex == i ? context.ng.secondary : Colors.white70, size: 24),
                        const SizedBox(height: 2),
                        Text(items[i].$3, style: TextStyle(fontSize: 11, color: shell.currentIndex == i ? Colors.white : Colors.white60)),
                      ]),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
