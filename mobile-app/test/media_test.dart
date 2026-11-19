import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:nepalgo/core/env.dart';
import 'package:nepalgo/features/photo_carousel.dart';

void main() {
  group('mediaUrl', () {
    test('relative gateway path resolves against API_BASE', () {
      expect(mediaUrl('/media/destination-images/d/1-thumb.jpg'), '${Env.apiBase}/media/destination-images/d/1-thumb.jpg');
    });
    test('absolute CDN URL is kept as is', () {
      expect(mediaUrl('https://cdn.example.com/destination-images/x.jpg'), 'https://cdn.example.com/destination-images/x.jpg');
    });
    test('null/empty -> null', () {
      expect(mediaUrl(null), isNull);
      expect(mediaUrl(''), isNull);
    });
  });

  // Network images fail in tests -> errorBuilder placeholder; we only test the slideshow logic.
  testWidgets('carousel advances every 5 s, loops back, and a touch restarts the countdown', (tester) async {
    int page() => (tester.widget<PageView>(find.byType(PageView)).controller!.page ?? 0).round();
    await tester.pumpWidget(const MaterialApp(home: SizedBox(height: 300, child: PhotoCarousel(urls: ['/a.jpg', '/b.jpg', '/c.jpg']))));
    expect(page(), 0);
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
    expect(page(), 1);
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
    expect(page(), 2);
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
    expect(page(), 0, reason: 'wraps around to the first photo');
    // touch at 4 s restarts the 5 s timer: no move at the 5 s mark
    await tester.pump(const Duration(seconds: 4));
    await tester.tapAt(const Offset(100, 100));
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();
    expect(page(), 0);
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();
    expect(page(), 1);
  });

  testWidgets('single photo: no dots and no auto-advance', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: SizedBox(height: 300, child: PhotoCarousel(urls: ['/a.jpg']))));
    await tester.pump(const Duration(seconds: 12));
    expect(find.byType(AnimatedContainer), findsNothing);
  });
}
