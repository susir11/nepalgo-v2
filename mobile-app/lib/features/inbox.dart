import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../core/format.dart';
import '../core/providers.dart';
import '../core/theme.dart';
import '../core/widgets.dart';

// In-app inbox: every notification notification-service created for this
// customer (spec 19). Works even when push isn't set up on the device.
final inboxProvider = FutureProvider<Map<String, dynamic>>(
    (ref) => ref.read(apiProvider).get<Map<String, dynamic>>('/api/notifications'));

class InboxBell extends ConsumerWidget {
  const InboxBell({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final unread = (ref.watch(inboxProvider).value?['unread'] as num?)?.toInt() ?? 0;
    return IconButton(
      tooltip: unread > 0 ? '$unread unread notifications' : 'Notifications',
      onPressed: () => context.push('/inbox'),
      icon: Badge(isLabelVisible: unread > 0, label: Text('$unread'), backgroundColor: context.ng.secondary,
          textColor: context.ng.nav, child: const Icon(Icons.notifications_none_rounded)),
    );
  }
}

class InboxScreen extends ConsumerWidget {
  const InboxScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final inbox = ref.watch(inboxProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Notifications')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(inboxProvider),
        child: inbox.when(
          loading: () => const Loading(),
          error: (e, _) => ErrorView(e, onRetry: () => ref.invalidate(inboxProvider)),
          data: (d) {
            final items = List<Map<String, dynamic>>.from((d['content'] as List).map((e) => Map<String, dynamic>.from(e)));
            if (items.isEmpty) return ListView(children: const [SizedBox(height: 80), Center(child: Text('Nothing yet — updates about your trips appear here.'))]);
            return ListView.separated(
              padding: const EdgeInsets.all(20),
              itemCount: items.length,
              separatorBuilder: (_, _) => const SizedBox(height: 10),
              itemBuilder: (_, i) {
                final n = items[i];
                return NgCard(
                  onTap: () async {
                    if (n['read'] != true) {
                      await ref.read(apiProvider).post('/api/notifications/${n['id']}/read');
                      ref.invalidate(inboxProvider);
                    }
                    if (n['bookingId'] != null && context.mounted) context.push('/trip/${n['bookingId']}');
                  },
                  child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Container(
                      width: 10, height: 10, margin: const EdgeInsets.only(top: 6, right: 10),
                      decoration: BoxDecoration(shape: BoxShape.circle, color: n['read'] == true ? Colors.transparent : context.ng.secondary),
                    ),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(n['title'], style: Theme.of(context).textTheme.titleMedium),
                        const SizedBox(height: 4),
                        Text(n['body']),
                        const SizedBox(height: 6),
                        Text(niceDate(n['createdAt']), style: Theme.of(context).textTheme.bodySmall),
                      ]),
                    ),
                  ]),
                );
              },
            );
          },
        ),
      ),
    );
  }
}
