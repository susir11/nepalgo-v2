import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../core/format.dart';
import '../core/providers.dart';
import '../core/theme.dart';
import '../core/widgets.dart';

typedef _Query = ({String q, String? type});

final destinationsProvider = FutureProvider.family<List<Map<String, dynamic>>, _Query>((ref, query) async {
  final r = await ref.read(apiProvider).get<Map<String, dynamic>>('/api/destinations',
      query: {'size': 50, if (query.q.isNotEmpty) 'q': query.q, if (query.type != null) 'type': query.type});
  return List<Map<String, dynamic>>.from((r['content'] as List).map((e) => Map<String, dynamic>.from(e)));
});

// Weather is cached server-side (45 min) behind a circuit breaker, so one call per card is cheap.
final weatherProvider = FutureProvider.family<Map<String, dynamic>?, int>((ref, id) async {
  try {
    return await ref.read(apiProvider).get<Map<String, dynamic>>('/api/destinations/$id/weather');
  } catch (_) {
    return null; // weather is nice-to-have: never break the feed over it
  }
});

/// Spec 16.9: pill search bar, ONE chip row (filters live behind a button,
/// not a second row), white cards on the tinted background with zero shadow,
/// mustard weather badge, price bottom-trailing, Bespoke card at the end.
class ExploreScreen extends ConsumerStatefulWidget {
  const ExploreScreen({super.key});
  @override
  ConsumerState<ExploreScreen> createState() => _ExploreState();
}

class _ExploreState extends ConsumerState<ExploreScreen> {
  String _q = '';
  String? _type;
  final _search = TextEditingController();

  @override
  Widget build(BuildContext context) {
    final all = ref.watch(destinationsProvider((q: '', type: null)));
    final list = ref.watch(destinationsProvider((q: _q, type: _type)));
    final types = <String>{for (final d in all.value ?? const <Map<String, dynamic>>[]) d['type'] as String};
    final company = ref.watch(companyProfileProvider).value;
    final name = (ref.watch(authProvider).profile?['displayName'] as String?)?.split(' ').first;

    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: RefreshIndicator(
          onRefresh: () async => ref.invalidate(destinationsProvider),
          child: CustomScrollView(slivers: [
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
              sliver: SliverList.list(children: [
                Text(name == null ? 'Where to next?' : 'Namaste, $name', style: Theme.of(context).textTheme.displaySmall),
                const SizedBox(height: 14),
                TextField(
                  controller: _search,
                  textInputAction: TextInputAction.search,
                  onSubmitted: (v) => setState(() => _q = v.trim()),
                  decoration: InputDecoration(
                    hintText: 'Search treks, regions, experiences',
                    prefixIcon: const Icon(Icons.search),
                    suffixIcon: _q.isEmpty ? null : IconButton(icon: const Icon(Icons.close), onPressed: () => setState(() { _q = ''; _search.clear(); })),
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(NgRadius.pill), borderSide: BorderSide.none),
                    enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(NgRadius.pill), borderSide: BorderSide.none),
                  ),
                ),
                const SizedBox(height: 12),
                SizedBox(
                  height: 40,
                  child: ListView(scrollDirection: Axis.horizontal, children: [
                    _chip('All', _type == null, () => setState(() => _type = null)),
                    for (final t in types) _chip(t, _type == t, () => setState(() => _type = t)),
                  ]),
                ),
                const SizedBox(height: 8),
              ]),
            ),
            list.when(
              loading: () => const SliverToBoxAdapter(child: Loading()),
              error: (e, _) => SliverToBoxAdapter(child: ErrorView(e, onRetry: () => ref.invalidate(destinationsProvider))),
              data: (items) => SliverPadding(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 110),
                sliver: SliverList.separated(
                  itemCount: items.length + 1,
                  separatorBuilder: (_, _) => const SizedBox(height: 16),
                  itemBuilder: (_, i) => i < items.length
                      ? _DestinationCard(d: items[i])
                      : _BespokeCard(onTap: () => openWhatsApp(context, company?['supportWhatsApp'] ?? '',
                          'Hi NepalGo team, this is ${ref.read(authProvider).profile?['displayName'] ?? 'a traveller'}. I\'d like a fully custom itinerary — here\'s roughly what I\'m thinking: [tell us your dates, group size, and the kind of trip you\'re after]')),
                ),
              ),
            ),
          ]),
        ),
      ),
    );
  }

  Widget _chip(String label, bool selected, VoidCallback onTap) => Padding(
        padding: const EdgeInsets.only(right: 8),
        child: ChoiceChip(label: Text(label), selected: selected, onSelected: (_) => onTap(), showCheckmark: false),
      );
}

class _DestinationCard extends ConsumerWidget {
  final Map<String, dynamic> d;
  const _DestinationCard({required this.d});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final w = ref.watch(weatherProvider(d['id'] as int)).value;
    return NgCard(
      padding: EdgeInsets.zero,
      onTap: () => context.push('/destination/${d['id']}'),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        ClipRRect(
          borderRadius: const BorderRadius.vertical(top: Radius.circular(NgRadius.card)),
          child: AspectRatio(
            aspectRatio: 16 / 10,
            child: Stack(fit: StackFit.expand, children: [
              d['thumbnailUrl'] != null
                  ? Image.network(d['thumbnailUrl'], fit: BoxFit.cover, errorBuilder: (_, _, _) => const _ImagePlaceholder())
                  : const _ImagePlaceholder(),
              if (w != null)
                Positioned(
                  left: 12, top: 12,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                    decoration: BoxDecoration(color: context.ng.card, borderRadius: BorderRadius.circular(NgRadius.pill)),
                    child: Row(mainAxisSize: MainAxisSize.min, children: [
                      Icon(Icons.wb_sunny_rounded, size: 14, color: context.ng.secondary),
                      const SizedBox(width: 4),
                      Text('${(w['temperatureCelsius'] as num).round()}°C · ${d['region']}', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                    ]),
                  ),
                ),
            ]),
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(18, 14, 18, 16),
          child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(d['name'], style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 2),
                Text('${d['region']} · ${d['type']}', style: Theme.of(context).textTheme.bodySmall),
              ]),
            ),
            Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
              Text('from', style: Theme.of(context).textTheme.bodySmall),
              Text(usd(d['basePriceUsd'] as num?), style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: context.ng.primary)),
            ]),
          ]),
        ),
      ]),
    );
  }
}

class _BespokeCard extends StatelessWidget {
  final VoidCallback onTap;
  const _BespokeCard({required this.onTap});
  @override
  Widget build(BuildContext context) => NgCard(
        color: context.ng.primaryTint,
        onTap: onTap,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(Icons.landscape_rounded, color: context.ng.primary, size: 30),
          const SizedBox(height: 10),
          Text('Bespoke Himalayan Expedition', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 6),
          const Text('Nothing here quite right? Tell our Kathmandu team what you dream of and we\'ll build it from scratch.'),
          const SizedBox(height: 12),
          Text('Request a custom itinerary →', style: TextStyle(color: context.ng.primary, fontWeight: FontWeight.w600)),
        ]),
      );
}

class _ImagePlaceholder extends StatelessWidget {
  const _ImagePlaceholder();
  @override
  Widget build(BuildContext context) => const DecoratedBox(
        decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight,
            colors: [Color(0xFF9DB9C8), Color(0xFF4C6B57)])),
        child: Center(child: Icon(Icons.terrain_rounded, color: Colors.white70, size: 48)),
      );
}
