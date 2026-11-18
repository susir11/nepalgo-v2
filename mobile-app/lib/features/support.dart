import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../core/providers.dart';
import '../core/theme.dart';
import '../core/widgets.dart';
import 'chat.dart';

final legalDocProvider = FutureProvider.family<Map<String, dynamic>, String>(
    (ref, type) => ref.read(apiProvider).get<Map<String, dynamic>>('/api/legal/$type/current'));

/// Support (spec 12): ONE prominent WhatsApp action (in-app chat is built in
/// Phase 8 but flagged OFF), company profile, and the legal documents.
class SupportScreen extends ConsumerWidget {
  const SupportScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final c = ref.watch(companyProfileProvider).value ?? const {};
    final p = ref.watch(authProvider).profile;
    return Scaffold(
      appBar: AppBar(title: const Text('Support')),
      body: ListView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 110), children: [
        NgCard(
          color: context.ng.primary,
          onTap: () => openWhatsApp(context, c['supportWhatsApp'] ?? '',
              'Hi NepalGo team, this is ${p?['displayName'] ?? 'a traveller'} (NepalGo ID ${p?['nepalgoId'] ?? '—'}). I need some help.'),
          child: Row(children: [
            const Icon(Icons.chat_rounded, color: Colors.white, size: 32),
            const SizedBox(width: 14),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Chat with us on WhatsApp', style: Theme.of(context).textTheme.titleMedium?.copyWith(color: Colors.white)),
                const SizedBox(height: 2),
                const Text('Our Kathmandu team replies personally.', style: TextStyle(color: Colors.white70)),
              ]),
            ),
            const Icon(Icons.arrow_forward_rounded, color: Colors.white),
          ]),
        ),
        // Dormant native chat (spec 12/20): only appears when the flag is ON.
        if (ref.watch(supportEnabledProvider).value == true) ...[
          const SizedBox(height: 12),
          NgCard(
            onTap: () => context.push('/chat'),
            child: const Row(children: [Icon(Icons.forum_rounded), SizedBox(width: 12), Expanded(child: Text('Chat in the app or request a callback')), Icon(Icons.chevron_right)]),
          ),
        ],
        const SectionTitle('About NepalGo'),
        NgCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(c['about'] ?? ''),
            const SizedBox(height: 12),
            for (final (label, key) in [('Company', 'legalName'), ('Registration no.', 'registrationNumber'), ('Address', 'registeredAddress'),
              ('Email', 'supportEmail'), ('Website', 'website')])
              if (c[key] != null)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 3),
                  child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    SizedBox(width: 120, child: Text(label, style: Theme.of(context).textTheme.bodySmall)),
                    Expanded(child: SelectableText(c[key].toString())),
                  ]),
                ),
          ]),
        ),
        const SectionTitle('Legal'),
        NgCard(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Column(children: [
            ListTile(leading: const Icon(Icons.gavel_rounded), title: const Text('Terms & Conditions'), trailing: const Icon(Icons.chevron_right),
                onTap: () => _openDoc(context, 'terms')),
            ListTile(leading: const Icon(Icons.privacy_tip_rounded), title: const Text('Privacy Policy'), trailing: const Icon(Icons.chevron_right),
                onTap: () => _openDoc(context, 'privacy')),
          ]),
        ),
      ]),
    );
  }

  void _openDoc(BuildContext context, String type) =>
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => LegalDocScreen(type: type)));
}

class LegalDocScreen extends ConsumerWidget {
  final String type;
  const LegalDocScreen({super.key, required this.type});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final doc = ref.watch(legalDocProvider(type));
    return Scaffold(
      appBar: AppBar(title: Text(type == 'terms' ? 'Terms & Conditions' : 'Privacy Policy')),
      body: doc.when(
        loading: () => const Loading(),
        error: (e, _) => ErrorView(e, onRetry: () => ref.invalidate(legalDocProvider(type))),
        data: (d) => ListView(padding: const EdgeInsets.all(20), children: [
          Text('Version ${d['versionLabel']}', style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 12),
          SelectableText(d['fullText'] ?? '', style: const TextStyle(height: 1.5)),
        ]),
      ),
    );
  }
}
