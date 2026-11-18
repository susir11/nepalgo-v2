import 'package:material_ui/material_ui.dart';
import 'package:url_launcher/url_launcher.dart';

import 'api.dart';
import 'theme.dart';

/// White card on the tinted background: depth from surface contrast, no shadow (16.1).
class NgCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final Color? color;
  final VoidCallback? onTap;
  const NgCard({super.key, required this.child, this.padding = const EdgeInsets.all(18), this.color, this.onTap});

  @override
  Widget build(BuildContext context) {
    final card = Container(
      padding: padding,
      decoration: BoxDecoration(color: color ?? context.ng.card, borderRadius: BorderRadius.circular(NgRadius.card)),
      child: child,
    );
    return onTap == null ? card : InkWell(borderRadius: BorderRadius.circular(NgRadius.card), onTap: onTap, child: card);
  }
}

class Pill extends StatelessWidget {
  final String text;
  final Color bg, fg;
  const Pill(this.text, {super.key, required this.bg, required this.fg});
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(NgRadius.pill)),
        child: Text(text, style: TextStyle(color: fg, fontSize: 12, fontWeight: FontWeight.w600)),
      );
}

/// Customer-facing status names (spec 8), never raw enum values.
class StatusPill extends StatelessWidget {
  final String status;
  const StatusPill(this.status, {super.key});
  static String label(String s) => switch (s) {
        'PENDING_APPROVAL' => 'Research in progress',
        'AWAITING_PAYMENT' => 'Ready to pay',
        'VERIFYING_PAYMENT' => 'Payment under verification',
        'CONFIRMED' => 'Confirmed',
        'REJECTED' => 'Not available',
        'EXPIRED' => 'Expired',
        'WITHDRAWN' => 'Withdrawn',
        'CANCELLED_TIERED' || 'CANCELLED_FORCE_MAJEURE' => 'Cancelled',
        _ => s,
      };
  @override
  Widget build(BuildContext context) {
    final c = context.ng;
    final (bg, fg) = switch (status) {
      'CONFIRMED' => (c.success.withValues(alpha: .14), c.success),
      'AWAITING_PAYMENT' => (c.primaryTint, c.primary),
      'PENDING_APPROVAL' || 'VERIFYING_PAYMENT' => (c.warning.withValues(alpha: .14), c.warning),
      _ => (c.divider, c.textSecondary),
    };
    return Pill(label(status), bg: bg, fg: fg);
  }
}

class Loading extends StatelessWidget {
  const Loading({super.key});
  @override
  Widget build(BuildContext context) => const Center(child: Padding(padding: EdgeInsets.all(32), child: CircularProgressIndicator()));
}

/// Friendly error with a retry button (spec 13: error states are designed, not afterthoughts).
class ErrorView extends StatelessWidget {
  final Object error;
  final VoidCallback? onRetry;
  const ErrorView(this.error, {super.key, this.onRetry});
  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(Icons.cloud_off_rounded, size: 40, color: context.ng.textSecondary),
            const SizedBox(height: 12),
            Text(ApiException.from(error).message, textAlign: TextAlign.center),
            if (onRetry != null) ...[const SizedBox(height: 12), OutlinedButton(onPressed: onRetry, child: const Text('Try again'))],
          ]),
        ),
      );
}

class SectionTitle extends StatelessWidget {
  final String text;
  const SectionTitle(this.text, {super.key});
  @override
  Widget build(BuildContext context) =>
      Padding(padding: const EdgeInsets.fromLTRB(4, 20, 4, 10), child: Text(text, style: Theme.of(context).textTheme.titleMedium));
}

void showError(BuildContext context, Object e) => ScaffoldMessenger.of(context)
  ..hideCurrentSnackBar()
  ..showSnackBar(SnackBar(content: Text(ApiException.from(e).message), behavior: SnackBarBehavior.floating));

void toast(BuildContext context, String msg) => ScaffoldMessenger.of(context)
  ..hideCurrentSnackBar()
  ..showSnackBar(SnackBar(content: Text(msg), behavior: SnackBarBehavior.floating));

/// WhatsApp deep link with a pre-filled message (spec 6b / 12).
Future<void> openWhatsApp(BuildContext context, String number, String message) async {
  final digits = number.replaceAll(RegExp(r'[^0-9]'), '');
  final uri = Uri.parse('https://wa.me/$digits?text=${Uri.encodeComponent(message)}');
  if (!await launchUrl(uri, mode: LaunchMode.externalApplication) && context.mounted) {
    toast(context, 'Couldn\'t open WhatsApp. Our number is +$digits.');
  }
}
