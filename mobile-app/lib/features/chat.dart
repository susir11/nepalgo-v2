import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../core/format.dart';
import '../core/providers.dart';
import '../core/theme.dart';
import '../core/widgets.dart';

// Native support chat + callback request (spec 12). Dormant: only shown when
// support-service reports in_app_support_chat_enabled = true (spec 20).
final supportEnabledProvider = FutureProvider<bool>((ref) async {
  try {
    final r = await ref.read(apiProvider).get<Map<String, dynamic>>('/api/support-status');
    return r['inAppChatEnabled'] == true;
  } catch (_) {
    return false;   // unknown => keep the dormant feature hidden
  }
});

final threadsProvider = FutureProvider<List<Map<String, dynamic>>>((ref) async {
  final r = await ref.read(apiProvider).get<List<dynamic>>('/api/support/threads');
  return r.map((e) => Map<String, dynamic>.from(e)).toList();
});

final threadProvider = FutureProvider.family<Map<String, dynamic>, int>(
    (ref, id) => ref.read(apiProvider).get<Map<String, dynamic>>('/api/support/threads/$id'));

class ChatListScreen extends ConsumerWidget {
  const ChatListScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final threads = ref.watch(threadsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Chat with NepalGo')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _newThread(context, ref),
        icon: const Icon(Icons.add_comment_rounded),
        label: const Text('New message'),
      ),
      body: threads.when(
        loading: () => const Loading(),
        error: (e, _) => ErrorView(e, onRetry: () => ref.invalidate(threadsProvider)),
        data: (list) => ListView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 100), children: [
          NgCard(
            onTap: () => _callback(context, ref),
            child: const Row(children: [Icon(Icons.phone_callback_rounded), SizedBox(width: 12), Expanded(child: Text('Prefer a phone call? Request a callback'))]),
          ),
          const SectionTitle('Conversations'),
          if (list.isEmpty) const NgCard(child: Text('No conversations yet.')),
          for (final t in list)
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: NgCard(
                onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => ThreadScreen(id: t['id'] as int))),
                child: Row(children: [
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(t['subject'], style: Theme.of(context).textTheme.titleMedium),
                    Text(niceDate(t['lastMessageAt']), style: Theme.of(context).textTheme.bodySmall),
                  ])),
                  Pill(t['status'] == 'OPEN' ? 'Open' : 'Closed', bg: context.ng.primaryTint, fg: context.ng.primary),
                ]),
              ),
            ),
        ]),
      ),
    );
  }

  Future<void> _newThread(BuildContext context, WidgetRef ref) async {
    final subject = TextEditingController(), message = TextEditingController();
    final ok = await showModalBottomSheet<bool>(
      context: context, isScrollControlled: true, showDragHandle: true,
      builder: (ctx) => Padding(
        padding: EdgeInsets.fromLTRB(24, 0, 24, 24 + MediaQuery.of(ctx).viewInsets.bottom),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          TextField(controller: subject, decoration: const InputDecoration(labelText: 'Subject')),
          const SizedBox(height: 10),
          TextField(controller: message, maxLines: 4, decoration: const InputDecoration(labelText: 'Message')),
          const SizedBox(height: 14),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Send')),
        ]),
      ),
    );
    if (ok != true || subject.text.trim().isEmpty || message.text.trim().isEmpty) return;
    try {
      await ref.read(apiProvider).post('/api/support/threads', {'subject': subject.text.trim(), 'message': message.text.trim()});
      ref.invalidate(threadsProvider);
    } catch (e) {
      if (context.mounted) showError(context, e);
    }
  }

  Future<void> _callback(BuildContext context, WidgetRef ref) async {
    final phone = TextEditingController(), when = TextEditingController();
    final ok = await showModalBottomSheet<bool>(
      context: context, isScrollControlled: true, showDragHandle: true,
      builder: (ctx) => Padding(
        padding: EdgeInsets.fromLTRB(24, 0, 24, 24 + MediaQuery.of(ctx).viewInsets.bottom),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          TextField(controller: phone, keyboardType: TextInputType.phone, decoration: const InputDecoration(labelText: 'Phone number (with country code)')),
          const SizedBox(height: 10),
          TextField(controller: when, decoration: const InputDecoration(labelText: 'Best time to call', hintText: 'e.g. weekday evenings, Nepal time')),
          const SizedBox(height: 14),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Request callback')),
        ]),
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(apiProvider).post('/api/support/callbacks', {'phoneNumber': phone.text.trim(), 'preferredTime': when.text.trim()});
      if (context.mounted) toast(context, 'Thanks — our team will call you.');
    } catch (e) {
      if (context.mounted) showError(context, e);
    }
  }
}

class ThreadScreen extends ConsumerStatefulWidget {
  final int id;
  const ThreadScreen({super.key, required this.id});
  @override
  ConsumerState<ThreadScreen> createState() => _ThreadState();
}

class _ThreadState extends ConsumerState<ThreadScreen> {
  final _text = TextEditingController();
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final t = ref.watch(threadProvider(widget.id));
    return Scaffold(
      appBar: AppBar(title: Text(t.value?['thread']?['subject'] ?? 'Conversation')),
      body: t.when(
        loading: () => const Loading(),
        error: (e, _) => ErrorView(e, onRetry: () => ref.invalidate(threadProvider(widget.id))),
        data: (d) {
          final msgs = List<Map<String, dynamic>>.from((d['messages'] as List).map((e) => Map<String, dynamic>.from(e)));
          return Column(children: [
            Expanded(
              child: ListView(padding: const EdgeInsets.all(16), children: [
                for (final m in msgs)
                  Align(
                    alignment: m['senderType'] == 'CUSTOMER' ? Alignment.centerRight : Alignment.centerLeft,
                    child: Container(
                      margin: const EdgeInsets.only(bottom: 8),
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                      constraints: const BoxConstraints(maxWidth: 320),
                      decoration: BoxDecoration(
                        color: m['senderType'] == 'CUSTOMER' ? context.ng.primary : context.ng.card,
                        borderRadius: BorderRadius.circular(NgRadius.inner),
                      ),
                      child: Text(m['body'], style: TextStyle(color: m['senderType'] == 'CUSTOMER' ? Colors.white : context.ng.ink)),
                    ),
                  ),
              ]),
            ),
            SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
                child: Row(children: [
                  Expanded(child: TextField(controller: _text, minLines: 1, maxLines: 4, decoration: const InputDecoration(hintText: 'Message'))),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    tooltip: 'Send',
                    onPressed: _busy
                        ? null
                        : () async {
                            if (_text.text.trim().isEmpty) return;
                            setState(() => _busy = true);
                            try {
                              await ref.read(apiProvider).post('/api/support/threads/${widget.id}/messages', {'body': _text.text.trim()});
                              _text.clear();
                              ref.invalidate(threadProvider(widget.id));
                            } catch (e) {
                              if (context.mounted) showError(context, e);
                            } finally {
                              if (mounted) setState(() => _busy = false);
                            }
                          },
                    icon: const Icon(Icons.send_rounded),
                  ),
                ]),
              ),
            ),
          ]);
        },
      ),
    );
  }
}
