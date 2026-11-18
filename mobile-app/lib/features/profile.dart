import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../core/format.dart';
import '../core/providers.dart';
import '../core/theme.dart';
import '../core/widgets.dart';
import 'trip_detail.dart' show BookingConfirmationCard;
import 'trips.dart';

final walletProvider = FutureProvider<List<Map<String, dynamic>>>((ref) async {
  final r = await ref.read(apiProvider).get<List<dynamic>>('/api/store-credits');
  return r.map((e) => Map<String, dynamic>.from(e)).toList();
});

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = ref.watch(authProvider).profile ?? const {};
    final wallet = ref.watch(walletProvider);
    final trips = ref.watch(tripsProvider).value ?? const [];
    final confirmed = trips.where((b) => b['status'] == 'CONFIRMED').toList();
    final mode = ref.watch(themeModeProvider);
    final tt = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Profile')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(walletProvider);
          ref.invalidate(tripsProvider);
          await ref.read(authProvider.notifier).refreshProfile();
        },
        child: ListView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 110), children: [
          // Spec 5a: the NepalGo ID, prominent and clearly labelled — what they share on WhatsApp.
          NgCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(p['displayName'] ?? 'Traveller', style: tt.titleLarge),
              Text(p['email'] ?? '', style: tt.bodySmall),
              const SizedBox(height: 16),
              Text('Your NepalGo ID', style: tt.bodySmall),
              Row(children: [
                Text(groupNepalgoId(p['nepalgoId']), style: tt.displaySmall?.copyWith(letterSpacing: 2)),
                IconButton(
                  tooltip: 'Copy NepalGo ID',
                  icon: const Icon(Icons.copy_rounded),
                  onPressed: () {
                    Clipboard.setData(ClipboardData(text: p['nepalgoId'] ?? ''));
                    toast(context, 'NepalGo ID copied');
                  },
                ),
              ]),
              Text('Share this with our team on WhatsApp so we can find your account.', style: tt.bodySmall),
            ]),
          ),
          const SectionTitle('Wallet'),
          if (confirmed.isEmpty && (wallet.value ?? const []).isEmpty)
            const NgCard(child: Text('Your booking confirmations and NepalGo Travel Credits will appear here.')),
          for (final b in confirmed) Padding(padding: const EdgeInsets.only(bottom: 12),
              child: GestureDetector(onTap: () => context.push('/trip/${b['id']}'), child: BookingConfirmationCard(b: b))),
          ...?wallet.value?.map((c) => Padding(padding: const EdgeInsets.only(bottom: 12), child: _CreditCard(c: c))),
          const SectionTitle('Appearance'),
          NgCard(
            padding: const EdgeInsets.all(8),
            child: SegmentedButton<ThemeMode>(
              segments: const [
                ButtonSegment(value: ThemeMode.light, label: Text('Light'), icon: Icon(Icons.light_mode_rounded)),
                ButtonSegment(value: ThemeMode.dark, label: Text('Dark'), icon: Icon(Icons.dark_mode_rounded)),
                ButtonSegment(value: ThemeMode.system, label: Text('System'), icon: Icon(Icons.settings_suggest_rounded)),
              ],
              selected: {mode},
              onSelectionChanged: (s) => ref.read(themeModeProvider.notifier).set(s.first),
            ),
          ),
          const SectionTitle('Account'),
          NgCard(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Column(children: [
              ListTile(leading: const Icon(Icons.logout_rounded), title: const Text('Sign out'),
                  onTap: () => ref.read(authProvider.notifier).signOut()),
              ListTile(
                leading: Icon(Icons.delete_forever_rounded, color: context.ng.error),
                title: Text('Delete account', style: TextStyle(color: context.ng.error)),
                subtitle: const Text('Removes your profile; tax records of paid trips are kept as the law requires.'),
                onTap: () => _deleteAccount(context, ref, p),
              ),
            ]),
          ),
        ]),
      ),
    );
  }

  // Account deletion needs a cross-service erasure workflow (spec 15.2); until
  // that backend ships, route the request to support so it's handled manually.
  void _deleteAccount(BuildContext context, WidgetRef ref, Map p) {
    final c = ref.read(companyProfileProvider).value;
    openWhatsApp(context, c?['supportWhatsApp'] ?? '',
        'Hi NepalGo team, I\'d like to delete my account. NepalGo ID ${p['nepalgoId']}, email ${p['email']}.');
  }
}

/// Wallet card #2 (spec 6c): a real, spendable asset — code, USD value, expiry.
class _CreditCard extends StatelessWidget {
  final Map<String, dynamic> c;
  const _CreditCard({required this.c});
  @override
  Widget build(BuildContext context) {
    final usable = c['status'] == 'ACTIVE';
    return Opacity(
      opacity: usable || c['status'] == 'RESERVED' ? 1 : .55,
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(NgRadius.card),
          gradient: const LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: [Color(0xFF8A6A10), Color(0xFFE2B338)]),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            const Expanded(child: Text('NepalGo Travel Credit', style: TextStyle(color: Colors.white, fontSize: 12))),
            Pill(StatusPill.label(c['status']) == c['status'] ? (c['status'] as String).toLowerCase() : StatusPill.label(c['status']),
                bg: Colors.white24, fg: Colors.white),
          ]),
          const SizedBox(height: 10),
          Text(usd(c['amountUsd'] as num), style: const TextStyle(color: Colors.white, fontSize: 30, fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text('${c['code']} · valid until ${niceDate(c['expiresOn'])}', style: const TextStyle(color: Colors.white)),
          if (c['appliedToBookingId'] != null) const Text('Applied to a trip request', style: TextStyle(color: Colors.white70, fontSize: 12)),
        ]),
      ),
    );
  }
}
