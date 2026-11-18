import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../core/format.dart';
import '../core/providers.dart';
import '../core/theme.dart';
import '../core/widgets.dart';

final tripsProvider = FutureProvider<List<Map<String, dynamic>>>((ref) async {
  final r = await ref.read(apiProvider).get<Map<String, dynamic>>('/api/bookings', query: {'page': 0, 'size': 50});
  return List<Map<String, dynamic>>.from((r['content'] as List).map((e) => Map<String, dynamic>.from(e)));
});

const _active = {'PENDING_APPROVAL', 'AWAITING_PAYMENT', 'VERIFYING_PAYMENT', 'CONFIRMED'};

class TripsScreen extends ConsumerWidget {
  const TripsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final trips = ref.watch(tripsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('My Trips')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(tripsProvider),
        child: trips.when(
          loading: () => const Loading(),
          error: (e, _) => ErrorView(e, onRetry: () => ref.invalidate(tripsProvider)),
          data: (list) {
            if (list.isEmpty) {
              return ListView(children: [
                const SizedBox(height: 80),
                Icon(Icons.luggage_rounded, size: 48, color: context.ng.textSecondary),
                const SizedBox(height: 12),
                const Center(child: Text('No trips yet')),
                const SizedBox(height: 8),
                Center(child: TextButton(onPressed: () => context.go('/explore'), child: const Text('Explore destinations →'))),
              ]);
            }
            final current = list.where((b) => _active.contains(b['status'])).toList();
            final past = list.where((b) => !_active.contains(b['status'])).toList();
            return ListView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 110), children: [
              if (current.isNotEmpty) ...[const SectionTitle('Current'), for (final b in current) _TripTile(b: b)],
              if (past.isNotEmpty) ...[const SectionTitle('Past & closed'), for (final b in past) _TripTile(b: b)],
            ]);
          },
        ),
      ),
    );
  }
}

class _TripTile extends StatelessWidget {
  final Map<String, dynamic> b;
  const _TripTile({required this.b});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: NgCard(
          onTap: () => context.push('/trip/${b['id']}'),
          child: Row(children: [
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(b['destinationNameSnapshot'], style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 4),
                Text('${niceDate(b['arrivalDate'])} · ${b['durationDays']} days · ${b['partySize']} ${b['partySize'] == 1 ? 'person' : 'people'}',
                    style: Theme.of(context).textTheme.bodySmall),
                const SizedBox(height: 8),
                StatusPill(b['status']),
              ]),
            ),
            const Icon(Icons.chevron_right_rounded),
          ]),
        ),
      );
}
