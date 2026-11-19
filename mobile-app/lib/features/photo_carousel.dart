import 'dart:async';

import 'package:material_ui/material_ui.dart';

import '../core/env.dart';

/// Destination hero slideshow: advances every 5 s and loops; a swipe by the user
/// restarts the 5 s countdown so it never jumps away mid-look. Dots show position.
/// One photo = no timer, no dots. A photo that fails to load shows the mountain
/// placeholder instead of an error box.
class PhotoCarousel extends StatefulWidget {
  final List<String> urls;   // already in display order, cover first
  final Duration interval;
  const PhotoCarousel({super.key, required this.urls, this.interval = const Duration(seconds: 5)});

  @override
  State<PhotoCarousel> createState() => _PhotoCarouselState();
}

class _PhotoCarouselState extends State<PhotoCarousel> {
  final _pages = PageController();
  Timer? _timer;
  int _index = 0;

  @override
  void initState() {
    super.initState();
    _restartTimer();
  }

  @override
  void didUpdateWidget(PhotoCarousel old) {
    super.didUpdateWidget(old);
    if (old.urls.length != widget.urls.length) {
      _index = 0;
      _restartTimer();
    }
  }

  void _restartTimer() {
    _timer?.cancel();
    if (widget.urls.length < 2) return;
    _timer = Timer.periodic(widget.interval, (_) {
      if (!mounted || !_pages.hasClients) return;
      final next = (_index + 1) % widget.urls.length;
      // Wrap-around jumps back to the first photo instead of scrolling backwards through all of them.
      if (next == 0) {
        _pages.jumpToPage(0);
      } else {
        _pages.animateToPage(next, duration: const Duration(milliseconds: 600), curve: Curves.easeInOut);
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _pages.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final urls = widget.urls;
    return Stack(fit: StackFit.expand, children: [
      PageView.builder(
        controller: _pages,
        itemCount: urls.length,
        onPageChanged: (i) => setState(() => _index = i),
        itemBuilder: (_, i) => Semantics(
          image: true,
          label: 'Destination photo ${i + 1} of ${urls.length}',
          child: Image.network(mediaUrl(urls[i]) ?? '', fit: BoxFit.cover,
              errorBuilder: (_, _, _) => const PhotoPlaceholder()),
        ),
      ),
      // Any touch on the photos restarts the countdown (user is looking).
      Positioned.fill(child: Listener(behavior: HitTestBehavior.translucent, onPointerDown: (_) => _restartTimer())),
      if (urls.length > 1)
        Positioned(
          bottom: 14,
          left: 0,
          right: 0,
          child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            for (var i = 0; i < urls.length; i++)
              AnimatedContainer(
                duration: const Duration(milliseconds: 250),
                margin: const EdgeInsets.symmetric(horizontal: 3),
                width: i == _index ? 18 : 7,
                height: 7,
                decoration: BoxDecoration(
                  color: i == _index ? Colors.white : Colors.white54,
                  borderRadius: BorderRadius.circular(4),
                  boxShadow: const [BoxShadow(color: Color(0x55000000), blurRadius: 3)],
                ),
              ),
          ]),
        ),
    ]);
  }
}

class PhotoPlaceholder extends StatelessWidget {
  const PhotoPlaceholder({super.key});
  @override
  Widget build(BuildContext context) => const DecoratedBox(
        decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight,
            colors: [Color(0xFF9DB9C8), Color(0xFF4C6B57)])),
        child: Center(child: Icon(Icons.terrain_rounded, color: Colors.white70, size: 48)),
      );
}
