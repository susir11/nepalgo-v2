import 'package:google_fonts/google_fonts.dart';
import 'package:material_ui/material_ui.dart';

// Design system tokens (spec 16.1 / 16.1a). Same names as the admin portal's
// Tailwind tokens, so both apps speak one visual language.
class NgColors extends ThemeExtension<NgColors> {
  final Color primary, primaryPressed, primaryTint, secondary, background, card, nav, ink, textSecondary, divider, success, warning, error;

  const NgColors({
    required this.primary, required this.primaryPressed, required this.primaryTint, required this.secondary,
    required this.background, required this.card, required this.nav, required this.ink, required this.textSecondary,
    required this.divider, required this.success, required this.warning, required this.error,
  });

  static const light = NgColors(
    primary: Color(0xFF387B53), primaryPressed: Color(0xFF2C6242), primaryTint: Color(0xFFE4EFE8),
    secondary: Color(0xFFE2B338), background: Color(0xFFE5E7E5), card: Color(0xFFFFFFFF),
    nav: Color(0xFF111111), ink: Color(0xFF181818), textSecondary: Color(0xFF737373),
    divider: Color(0xFFEDEDEB), success: Color(0xFF2E9E5B), warning: Color(0xFFD97706), error: Color(0xFFD32F2F),
  );

  // 16.1a: same zero-shadow model in dark mode — a card just reads lighter than the background.
  static const dark = NgColors(
    primary: Color(0xFF4F9E6E), primaryPressed: Color(0xFF63B682), primaryTint: Color(0xFF24352C),
    secondary: Color(0xFFE8C25A), background: Color(0xFF121412), card: Color(0xFF1E211F),
    nav: Color(0xFF111111), ink: Color(0xFFF2F2F0), textSecondary: Color(0xFFA3A3A3),
    divider: Color(0xFF2A2D2B), success: Color(0xFF4CC27E), warning: Color(0xFFF59E0B), error: Color(0xFFEF5350),
  );

  @override
  NgColors copyWith() => this;

  @override
  NgColors lerp(ThemeExtension<NgColors>? other, double t) => (other is NgColors && t > 0.5) ? other : this;
}

extension NgTheme on BuildContext {
  NgColors get ng => Theme.of(this).extension<NgColors>()!;
}

// 16.1 shape + spacing rules.
class NgRadius {
  static const card = 24.0;
  static const inner = 14.0;
  static const pill = 100.0;
}

ThemeData buildTheme(Brightness brightness) {
  final c = brightness == Brightness.light ? NgColors.light : NgColors.dark;
  final base = ThemeData(brightness: brightness, useMaterial3: true);
  // Typography (Inter): display 28/700/-0.4, title 20/700/-0.2, subtitle 16/600, body 15, caption 13.
  final text = GoogleFonts.interTextTheme(base.textTheme).copyWith(
    displaySmall: GoogleFonts.inter(fontSize: 28, fontWeight: FontWeight.w700, letterSpacing: -0.4, color: c.ink),
    titleLarge: GoogleFonts.inter(fontSize: 20, fontWeight: FontWeight.w700, letterSpacing: -0.2, color: c.ink),
    titleMedium: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w600, color: c.ink),
    bodyMedium: GoogleFonts.inter(fontSize: 15, color: c.ink),
    bodySmall: GoogleFonts.inter(fontSize: 13, color: c.textSecondary),
  );
  final pill = RoundedRectangleBorder(borderRadius: BorderRadius.circular(NgRadius.pill));
  return base.copyWith(
    scaffoldBackgroundColor: c.background,
    colorScheme: ColorScheme.fromSeed(seedColor: c.primary, brightness: brightness, primary: c.primary, surface: c.card, error: c.error),
    textTheme: text,
    extensions: [c],
    appBarTheme: AppBarTheme(backgroundColor: c.background, foregroundColor: c.ink, elevation: 0, scrolledUnderElevation: 0, centerTitle: false,
        titleTextStyle: text.titleLarge),
    cardTheme: CardThemeData(color: c.card, elevation: 0, margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(NgRadius.card))),
    filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(backgroundColor: c.primary, foregroundColor: Colors.white,
        minimumSize: const Size(64, 52), shape: pill, textStyle: GoogleFonts.inter(fontSize: 15, fontWeight: FontWeight.w600))),
    outlinedButtonTheme: OutlinedButtonThemeData(style: OutlinedButton.styleFrom(foregroundColor: c.primary, minimumSize: const Size(64, 52),
        shape: pill, side: BorderSide(color: c.primary))),
    textButtonTheme: TextButtonThemeData(style: TextButton.styleFrom(foregroundColor: c.primary, shape: pill)),
    inputDecorationTheme: InputDecorationTheme(
      filled: true, fillColor: c.card,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(NgRadius.inner), borderSide: BorderSide(color: c.divider)),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(NgRadius.inner), borderSide: BorderSide(color: c.divider)),
      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(NgRadius.inner), borderSide: BorderSide(color: c.primary, width: 2)),
    ),
    chipTheme: base.chipTheme.copyWith(shape: pill, side: BorderSide.none, backgroundColor: c.card, selectedColor: c.primaryTint),
    dividerTheme: DividerThemeData(color: c.divider, thickness: 1),
    // Motion rule (16.1): bouncing scroll physics on both platforms.
    scrollbarTheme: const ScrollbarThemeData(),
  );
}

// 16.1 motion: bouncing lists on Android too, not just iOS.
class NgScrollBehavior extends MaterialScrollBehavior {
  const NgScrollBehavior();
  @override
  ScrollPhysics getScrollPhysics(BuildContext context) => const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics());
}
