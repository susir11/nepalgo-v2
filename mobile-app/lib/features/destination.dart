import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:latlong2/latlong.dart';
import 'package:material_ui/material_ui.dart';

import '../core/format.dart';
import '../core/providers.dart';
import '../core/push.dart' show pushProvider;
import '../core/theme.dart';
import '../core/widgets.dart';
import 'explore.dart' show weatherProvider;
import 'profile.dart' show walletProvider;
import 'photo_carousel.dart';
import 'reviews.dart';

final destinationProvider = FutureProvider.family<Map<String, dynamic>, int>(
    (ref, id) => ref.read(apiProvider).get<Map<String, dynamic>>('/api/destinations/$id'));

class DestinationScreen extends ConsumerWidget {
  final int id;
  const DestinationScreen({super.key, required this.id});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final d = ref.watch(destinationProvider(id));
    return Scaffold(
      body: d.when(
        loading: () => const Loading(),
        error: (e, _) => SafeArea(child: ErrorView(e, onRetry: () => ref.invalidate(destinationProvider(id)))),
        data: (dest) => _Body(dest: dest),
      ),
    );
  }
}

class _Body extends ConsumerWidget {
  final Map<String, dynamic> dest;
  const _Body({required this.dest});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Medium size (800 px) is plenty for a 300 px-tall hero; fall back to imageUrls for older APIs.
    final images = (dest['images'] as List?)?.map((e) => (e as Map)['mediumUrl'] as String).toList()
        ?? List<String>.from(dest['imageUrls'] ?? const []);
    final itinerary = dest['defaultItinerary'] as Map<String, dynamic>?;
    final days = List<Map<String, dynamic>>.from((itinerary?['days'] as List? ?? const []).map((e) => Map<String, dynamic>.from(e)));
    final weather = ref.watch(weatherProvider(dest['id'] as int)).value;
    final tt = Theme.of(context).textTheme;

    return CustomScrollView(slivers: [
      SliverAppBar(
        pinned: true,
        expandedHeight: 300,
        backgroundColor: context.ng.background,
        leading: Padding(
          padding: const EdgeInsets.all(8),
          child: CircleAvatar(backgroundColor: context.ng.card, child: BackButton(color: context.ng.ink, onPressed: () => context.pop())),
        ),
        flexibleSpace: FlexibleSpaceBar(
          background: images.isEmpty ? const PhotoPlaceholder() : PhotoCarousel(urls: images),
        ),
      ),
      SliverPadding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 40),
        sliver: SliverList.list(children: [
          Text(dest['name'], style: tt.displaySmall),
          const SizedBox(height: 4),
          Text('${dest['region']} · ${dest['type']}${dest['bestSeason'] != null ? ' · best ${dest['bestSeason']}' : ''}', style: tt.bodySmall),
          const SizedBox(height: 12),
          Text(dest['description'] ?? '', style: const TextStyle(height: 1.45)),
          if (weather != null) ...[const SectionTitle('Weather & packing'), _WeatherCard(w: weather)],
          if ((dest['permitNotes'] ?? '').toString().isNotEmpty) ...[
            const SectionTitle('Permits & route notes'),
            NgCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(dest['permitNotes']),
                const SizedBox(height: 8),
                // Terms §2: NepalGo does not arrange permits; this is guidance only.
                Text('General guidance only — always verify with the relevant Nepali authority.', style: tt.bodySmall),
              ]),
            ),
          ],
          const SectionTitle('Your itinerary'),
          if (days.isEmpty) const NgCard(child: Text('The itinerary for this trip is being finalised. Ask us on WhatsApp.'))
          else for (final day in days) Padding(padding: const EdgeInsets.only(bottom: 12), child: _DayCard(day: day)),
          DestinationReviewsSection(destinationId: dest['id'] as int),
          if (itinerary != null) ...[
            const SectionTitle('Plan your trip'),
            _TripRequestForm(dest: dest, itineraryId: itinerary['id'] as int, days: days.length),
          ],
        ]),
      ),
    ]);
  }
}

class _WeatherCard extends StatelessWidget {
  final Map<String, dynamic> w;
  const _WeatherCard({required this.w});

  // Simple, honest packing guidance derived from the live weather.
  String _packing(num t, String condition) {
    final tips = <String>[
      if (t < 5) 'down jacket, thermal layers and warm gloves',
      if (t >= 5 && t < 15) 'a fleece and a windproof shell',
      if (t >= 15) 'breathable layers and sun protection',
      if (condition.toLowerCase().contains('rain')) 'a rain jacket and pack cover',
      if (condition.toLowerCase().contains('snow')) 'gaiters and microspikes',
    ];
    return 'Pack ${tips.join(', ')}.';
  }

  @override
  Widget build(BuildContext context) {
    final t = w['temperatureCelsius'] as num;
    return NgCard(
      child: Row(children: [
        Container(
          width: 56, height: 56,
          decoration: BoxDecoration(color: context.ng.secondary.withValues(alpha: .18), borderRadius: BorderRadius.circular(NgRadius.inner)),
          child: Icon(Icons.thermostat_rounded, color: context.ng.secondary),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('${t.round()}°C · ${w['description']}', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(_packing(t, w['condition'] ?? ''), style: Theme.of(context).textTheme.bodySmall),
          ]),
        ),
      ]),
    );
  }
}

/// One read-only itinerary day (spec 6). If the day has a curated hotel with
/// coordinates, show a static OpenStreetMap card with a branded marker (6a) —
/// no "swap hotel" control in v1.
class _DayCard extends StatelessWidget {
  final Map<String, dynamic> day;
  const _DayCard({required this.day});
  @override
  Widget build(BuildContext context) {
    final lat = (day['hotelLat'] as num?)?.toDouble(), lng = (day['hotelLng'] as num?)?.toDouble();
    return NgCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Pill('Day ${day['dayNumber']}', bg: context.ng.primaryTint, fg: context.ng.primary),
          if (day['hotelName'] != null) ...[
            const SizedBox(width: 8),
            Expanded(child: Text(day['hotelName'], overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600))),
          ],
        ]),
        const SizedBox(height: 10),
        Text(day['narrative'] ?? '', style: const TextStyle(height: 1.45)),
        if (lat != null && lng != null) ...[
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(NgRadius.inner),
            child: SizedBox(
              height: 160,
              child: FlutterMap(
                options: MapOptions(initialCenter: LatLng(lat, lng), initialZoom: 13,
                    interactionOptions: const InteractionOptions(flags: InteractiveFlag.none)),
                children: [
                  TileLayer(urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png', userAgentPackageName: 'com.nepalgo.app'),
                  MarkerLayer(markers: [
                    Marker(point: LatLng(lat, lng), width: 40, height: 40,
                        child: Icon(Icons.location_on_rounded, color: context.ng.primary, size: 40)),
                  ]),
                  const SimpleAttributionWidget(source: Text('OpenStreetMap contributors')),
                ],
              ),
            ),
          ),
        ],
      ]),
    );
  }
}

/// Spec 6b, in order: party size, arrival day, guide/porter toggles, travel
/// credits, special requests, Continue; then the WhatsApp escape hatches.
class _TripRequestForm extends ConsumerStatefulWidget {
  final Map<String, dynamic> dest;
  final int itineraryId, days;
  const _TripRequestForm({required this.dest, required this.itineraryId, required this.days});
  @override
  ConsumerState<_TripRequestForm> createState() => _TripRequestState();
}

class _TripRequestState extends ConsumerState<_TripRequestForm> {
  int _party = 2;
  DateTime? _arrival;
  bool _guide = true, _porter = false, _busy = false;
  final _special = TextEditingController();
  final Set<String> _credits = {};

  Future<void> _submit() async {
    if (_arrival == null) return toast(context, 'Choose your arrival day');
    final end = _arrival!.add(Duration(days: widget.days - 1));
    final ok = await showModalBottomSheet<bool>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => Padding(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Text('Send trip request?', style: Theme.of(ctx).textTheme.titleLarge),
          const SizedBox(height: 12),
          Text('${widget.dest['name']} · $_party ${_party == 1 ? 'person' : 'people'}\n'
              '${niceDate(isoDate(_arrival!))} → ${niceDate(isoDate(end))} (${widget.days} days)'
              '${_guide ? '\nGuide requested' : ''}${_porter ? '\nPorter requested' : ''}'
              '${_credits.isNotEmpty ? '\nTravel credit: ${_credits.join(', ')}' : ''}'),
          const SizedBox(height: 12),
          Text('This is not a confirmed booking and you won\'t pay now. Our team checks availability and sends you an itemised bill.',
              style: Theme.of(ctx).textTheme.bodySmall),
          const SizedBox(height: 16),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Send request')),
        ]),
      ),
    );
    if (ok != true) return;
    setState(() => _busy = true);
    try {
      final api = ref.read(apiProvider);
      final b = await api.post<Map<String, dynamic>>('/api/bookings', {
        'destinationId': widget.dest['id'], 'itineraryVersionId': widget.itineraryId, 'partySize': _party,
        'arrivalDate': isoDate(_arrival!), 'guideRequested': _guide, 'porterRequested': _porter,
        if (_special.text.trim().isNotEmpty) 'specialRequestText': _special.text.trim(),
      });
      if (_credits.isNotEmpty) {
        // Flags intent only (spec 6b): nothing is deducted until the bill is paid.
        await api.post('/api/store-credits/apply', {'bookingId': b['id'], 'codes': _credits.toList()});
        ref.invalidate(walletProvider);
      }
      // First moment a notification clearly helps ("we will tell you when your bill is ready"):
      // ask for permission now, never at first launch (store checklist). Not awaited.
      ref.read(pushProvider).askAndRegister();
      if (mounted) context.pushReplacement('/trip/${b['id']}');
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final wallet = ref.watch(walletProvider).value ?? const [];
    final spendable = wallet.where((c) => c['status'] == 'ACTIVE').toList();
    final company = ref.watch(companyProfileProvider).value;
    final who = ref.watch(authProvider).profile?['displayName'] ?? 'a traveller';
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      NgCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(children: [
            const Expanded(child: Text('Number of persons', style: TextStyle(fontWeight: FontWeight.w600))),
            IconButton.filledTonal(onPressed: _party > 1 ? () => setState(() => _party--) : null, icon: const Icon(Icons.remove), tooltip: 'Fewer'),
            SizedBox(width: 36, child: Text('$_party', textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleMedium)),
            IconButton.filledTonal(onPressed: _party < 30 ? () => setState(() => _party++) : null, icon: const Icon(Icons.add), tooltip: 'More'),
          ]),
          const Divider(height: 24),
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Arrival day in Nepal', style: TextStyle(fontWeight: FontWeight.w600)),
            subtitle: Text(_arrival == null ? 'Choose a date' : '${niceDate(isoDate(_arrival!))} · ${widget.days}-day trip'),
            trailing: const Icon(Icons.calendar_month_rounded),
            onTap: () async {
              final now = DateTime.now();
              final d = await showDatePicker(context: context, firstDate: now.add(const Duration(days: 1)), lastDate: now.add(const Duration(days: 730)),
                  initialDate: _arrival ?? now.add(const Duration(days: 30)));
              if (d != null) setState(() => _arrival = d);
            },
          ),
          const Divider(height: 12),
          SwitchListTile(contentPadding: EdgeInsets.zero, value: _guide, onChanged: (v) => setState(() => _guide = v),
              title: const Text('Add a guide'), subtitle: const Text('Often required by law for trekking in Nepal')),
          SwitchListTile(contentPadding: EdgeInsets.zero, value: _porter, onChanged: (v) => setState(() => _porter = v), title: const Text('Add a porter')),
          if (spendable.isNotEmpty) ...[
            const Divider(height: 24),
            const Text('Use a travel credit', style: TextStyle(fontWeight: FontWeight.w600)),
            for (final c in spendable)
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                value: _credits.contains(c['code']),
                onChanged: (v) => setState(() => v == true ? _credits.add(c['code']) : _credits.remove(c['code'])),
                title: Text('${usd(c['amountUsd'] as num)} · ${c['code']}'),
                subtitle: Text('expires ${niceDate(c['expiresOn'])}'),
              ),
          ],
          const SizedBox(height: 8),
          TextField(controller: _special, maxLines: 3, maxLength: 1000,
              decoration: const InputDecoration(labelText: 'Special requests', hintText: 'Anything else we should know? e.g. vegetarian food only')),
          const SizedBox(height: 8),
          FilledButton(onPressed: _busy ? null : _submit, child: Text(_busy ? 'Sending…' : 'Continue')),
        ]),
      ),
      const SizedBox(height: 16),
      // Lower-emphasis escape hatches (spec 6b, item 7).
      OutlinedButton.icon(
        icon: const Icon(Icons.chat_rounded),
        label: const Text('Chat with us on WhatsApp'),
        onPressed: () => openWhatsApp(context, company?['supportWhatsApp'] ?? '',
            'Hi NepalGo team, this is $who. I\'m looking at ${widget.dest['name']} and had a quick question before booking.'),
      ),
      const SizedBox(height: 10),
      OutlinedButton.icon(
        icon: const Icon(Icons.edit_note_rounded),
        label: const Text('Request custom itinerary'),
        onPressed: () => openWhatsApp(context, company?['supportWhatsApp'] ?? '',
            'Hi NepalGo team, this is $who. I\'d like a fully custom itinerary — here\'s roughly what I\'m thinking: [tell us your dates, group size, and the kind of trip you\'re after]'),
      ),
    ]);
  }
}
