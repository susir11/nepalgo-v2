import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../core/env.dart';
import '../core/providers.dart';
import '../core/theme.dart';

class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});
  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: context.ng.nav,
        body: Center(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Text('NepalGo', style: Theme.of(context).textTheme.displaySmall?.copyWith(color: Colors.white)),
            const SizedBox(height: 8),
            Text('Your companion for Nepal.', style: TextStyle(color: Colors.white.withValues(alpha: .7))),
          ]),
        ),
      );
}

// Built-in fallback (spec 4a): used if the live content can't be fetched.
const _fallbackSlides = [
  {'eyebrow': 'Welcome to NepalGo', 'headline': 'Your companion for Nepal.',
    'body': 'Every itinerary is planned by hand by our team in Kathmandu — real people who know the trails, the hotels and the weather.'},
  {'eyebrow': 'Built around you', 'headline': 'Tell us your dates. We do the rest.',
    'body': 'Send a trip request. We check availability with local partners and send you an itemised bill — no surprises.'},
  {'eyebrow': 'Real people, real help', 'headline': 'We\'re one message away.', 'body': 'Questions? Our Kathmandu team answers on WhatsApp.'},
];

final onboardingContentProvider = FutureProvider<Map<String, dynamic>>((ref) async {
  try {
    final r = await ref.read(apiProvider).get<Map<String, dynamic>>('/api/content/onboarding');
    return Map<String, dynamic>.from(r['payload']);
  } catch (_) {
    return {'slides': _fallbackSlides, 'ctaLabel': 'Get Started'};
  }
});

/// Spec 16.8: full-bleed Himalayan photo, dark gradient scrim (0.2 -> 0.85),
/// 32pt padding, bottom-aligned copy, 3-dot indicator whose active dot
/// stretches into a pill, full-width pill "Get Started ->" button. No blur.
class OnboardingScreen extends ConsumerStatefulWidget {
  const OnboardingScreen({super.key});
  @override
  ConsumerState<OnboardingScreen> createState() => _OnboardingState();
}

class _OnboardingState extends ConsumerState<OnboardingScreen> {
  final _pages = PageController();
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final content = ref.watch(onboardingContentProvider).value ?? {'slides': _fallbackSlides, 'ctaLabel': 'Get Started'};
    final slides = List<Map<String, dynamic>>.from((content['slides'] as List).map((e) => Map<String, dynamic>.from(e)));
    final last = _index == slides.length - 1;
    return Scaffold(
      body: Stack(fit: StackFit.expand, children: [
        // Photography: admin can set imageUrl per slide; otherwise a mountain-toned gradient placeholder.
        _Backdrop(url: slides[_index]['imageUrl'] as String?),
        const DecoratedBox(
          decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter,
              colors: [Color(0x33000000), Color(0xD9000000)])),
        ),
        SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Expanded(
                child: PageView.builder(
                  controller: _pages,
                  itemCount: slides.length,
                  onPageChanged: (i) => setState(() => _index = i),
                  itemBuilder: (_, i) => Column(mainAxisAlignment: MainAxisAlignment.end, crossAxisAlignment: CrossAxisAlignment.start, children: [
                    if ((slides[i]['eyebrow'] ?? '').toString().isNotEmpty)
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                        decoration: BoxDecoration(color: Colors.white.withValues(alpha: .16), borderRadius: BorderRadius.circular(NgRadius.pill)),
                        child: Text(slides[i]['eyebrow'], style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600)),
                      ),
                    const SizedBox(height: 14),
                    Text(slides[i]['headline'] ?? '', style: Theme.of(context).textTheme.displaySmall?.copyWith(color: Colors.white, fontSize: 32)),
                    const SizedBox(height: 10),
                    Text(slides[i]['body'] ?? '', style: TextStyle(color: Colors.white.withValues(alpha: .85), fontSize: 16, height: 1.4)),
                    const SizedBox(height: 28),
                  ]),
                ),
              ),
              Row(children: [
                for (var i = 0; i < slides.length; i++)
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 250),
                    margin: const EdgeInsets.only(right: 6),
                    width: i == _index ? 24 : 8,
                    height: 8,
                    decoration: BoxDecoration(color: i == _index ? Colors.white : Colors.white38, borderRadius: BorderRadius.circular(NgRadius.pill)),
                  ),
              ]),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: () => last
                      ? ref.read(onboardingSeenProvider.notifier).markSeen()
                      : _pages.nextPage(duration: const Duration(milliseconds: 300), curve: Curves.easeOut),
                  child: Text(last ? '${content['ctaLabel'] ?? 'Get Started'} →' : 'Next →'),
                ),
              ),
            ]),
          ),
        ),
      ]),
    );
  }
}

class _Backdrop extends StatelessWidget {
  final String? url;
  const _Backdrop({this.url});
  @override
  Widget build(BuildContext context) {
    const placeholder = DecoratedBox(
      decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter,
          colors: [Color(0xFF8FB3C9), Color(0xFF3E5B4C), Color(0xFF1D2B22)])),
    );
    if (url == null || url!.isEmpty) return placeholder;
    return Image.network(mediaUrl(url) ?? '', fit: BoxFit.cover, errorBuilder: (_, _, _) => placeholder);
  }
}
