import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:material_ui/material_ui.dart';

import '../core/providers.dart';
import '../core/theme.dart';
import '../core/widgets.dart';

// Destination reviews (spec 3 / 6). Only travellers with a finished, confirmed
// trip can write one, and staff publish it before anyone else sees it.

/// Public: published reviews + rating summary for one destination. No login needed.
final destinationReviewsProvider = FutureProvider.family<Map<String, dynamic>, int>((ref, destinationId) =>
    ref.read(apiProvider).get<Map<String, dynamic>>('/api/reviews/destinations/$destinationId', query: {'size': 50}));

/// The signed-in traveller's own review state for one booking.
final tripReviewProvider = FutureProvider.family<Map<String, dynamic>, int>((ref, bookingId) =>
    ref.read(apiProvider).get<Map<String, dynamic>>('/api/bookings/$bookingId/review'));

/// "2026-10" -> "Oct 2026"
String tripMonthLabel(String ym) {
  final d = DateTime.tryParse('$ym-01');
  return d == null ? ym : DateFormat('MMM yyyy').format(d);
}

class Stars extends StatelessWidget {
  final num rating;
  final double size;
  const Stars(this.rating, {super.key, this.size = 16});
  @override
  Widget build(BuildContext context) => Semantics(
        label: '${rating.toStringAsFixed(rating % 1 == 0 ? 0 : 1)} out of 5 stars',
        child: ExcludeSemantics(
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            for (var i = 1; i <= 5; i++)
              Icon(
                rating >= i ? Icons.star_rounded : (rating >= i - .5 ? Icons.star_half_rounded : Icons.star_outline_rounded),
                size: size,
                color: context.ng.secondary,
              ),
          ]),
        ),
      );
}

/// Section on Destination Detail: average, histogram, newest 3 reviews, "See all".
class DestinationReviewsSection extends ConsumerWidget {
  final int destinationId;
  const DestinationReviewsSection({super.key, required this.destinationId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(destinationReviewsProvider(destinationId));
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      const SectionTitle('Traveller reviews'),
      async.when(
        loading: () => const NgCard(child: SizedBox(height: 40, child: Loading())),
        // Reviews are a nice-to-have on this screen: a failure must not block booking.
        error: (_, _) => NgCard(
          child: Row(children: [
            const Expanded(child: Text("Reviews couldn't be loaded right now.")),
            TextButton(onPressed: () => ref.invalidate(destinationReviewsProvider(destinationId)), child: const Text('Retry')),
          ]),
        ),
        data: (data) {
          final summary = Map<String, dynamic>.from(data['summary'] as Map);
          final items = List<Map<String, dynamic>>.from((data['items'] as List).map((e) => Map<String, dynamic>.from(e)));
          final count = (summary['reviewCount'] as num).toInt();
          if (count == 0) {
            return const NgCard(child: Text('No reviews yet. Travellers can review this trip after they return.'));
          }
          return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            _SummaryCard(summary: summary),
            const SizedBox(height: 12),
            for (final r in items.take(3)) Padding(padding: const EdgeInsets.only(bottom: 12), child: ReviewTile(r: r)),
            if (items.length > 3)
              OutlinedButton(
                onPressed: () => showModalBottomSheet(
                  context: context,
                  isScrollControlled: true,
                  showDragHandle: true,
                  builder: (_) => DraggableScrollableSheet(
                    expand: false,
                    initialChildSize: .85,
                    builder: (_, controller) => ListView(
                      controller: controller,
                      padding: const EdgeInsets.fromLTRB(20, 0, 20, 32),
                      children: [
                        Text('All reviews ($count)', style: Theme.of(context).textTheme.titleMedium),
                        const SizedBox(height: 12),
                        for (final r in items) Padding(padding: const EdgeInsets.only(bottom: 12), child: ReviewTile(r: r)),
                      ],
                    ),
                  ),
                ),
                child: Text('See all $count reviews'),
              ),
          ]);
        },
      ),
    ]);
  }
}

class _SummaryCard extends StatelessWidget {
  final Map<String, dynamic> summary;
  const _SummaryCard({required this.summary});
  @override
  Widget build(BuildContext context) {
    final avg = (summary['averageRating'] as num).toDouble();
    final count = (summary['reviewCount'] as num).toInt();
    final buckets = Map<String, dynamic>.from(summary['ratingCounts'] as Map);
    return NgCard(
      child: Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
        Column(children: [
          Text(avg.toStringAsFixed(1), style: Theme.of(context).textTheme.displaySmall),
          Stars(avg),
          const SizedBox(height: 4),
          Text('$count review${count == 1 ? '' : 's'}', style: Theme.of(context).textTheme.bodySmall),
        ]),
        const SizedBox(width: 20),
        Expanded(
          child: Column(children: [
            for (var s = 5; s >= 1; s--)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Row(children: [
                  SizedBox(width: 14, child: Text('$s', style: Theme.of(context).textTheme.bodySmall)),
                  const SizedBox(width: 6),
                  Expanded(
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(NgRadius.pill),
                      child: LinearProgressIndicator(
                        value: count == 0 ? 0 : ((buckets['$s'] ?? 0) as num) / count,
                        minHeight: 6,
                        color: context.ng.secondary,
                        backgroundColor: context.ng.divider,
                      ),
                    ),
                  ),
                ]),
              ),
          ]),
        ),
      ]),
    );
  }
}

class ReviewTile extends StatelessWidget {
  final Map<String, dynamic> r;
  const ReviewTile({super.key, required this.r});
  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    return NgCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Stars(r['rating'] as num),
          const Spacer(),
          Text('Travelled ${tripMonthLabel(r['tripMonth'] as String)}', style: tt.bodySmall),
        ]),
        if ((r['title'] ?? '').toString().isNotEmpty) ...[
          const SizedBox(height: 8),
          Text(r['title'], style: tt.titleSmall),
        ],
        const SizedBox(height: 6),
        Text(r['body'] ?? '', style: const TextStyle(height: 1.45)),
        const SizedBox(height: 8),
        Text('— ${r['authorName']} · Verified traveller', style: tt.bodySmall),
      ]),
    );
  }
}

/// Card on a CONFIRMED trip: explains when reviewing opens, or shows the
/// traveller's review with its moderation state and an edit button.
class TripReviewCard extends ConsumerWidget {
  final Map<String, dynamic> booking;
  const TripReviewCard({super.key, required this.booking});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final id = booking['id'] as int;
    final async = ref.watch(tripReviewProvider(id));
    final tt = Theme.of(context).textTheme;
    return async.when(
      loading: () => const SizedBox.shrink(),
      error: (_, _) => const SizedBox.shrink(),   // optional feature: never block the trip screen
      data: (data) {
        final review = data['review'] == null ? null : Map<String, dynamic>.from(data['review'] as Map);
        if (data['eligible'] != true && review == null) {
          if (data['ineligibleReason'] != 'TRIP_NOT_FINISHED') return const SizedBox.shrink();
          final from = DateTime.tryParse(data['eligibleFrom'] as String? ?? '');
          return NgCard(
            child: Row(children: [
              Icon(Icons.rate_review_outlined, color: context.ng.textSecondary),
              const SizedBox(width: 12),
              Expanded(child: Text(
                  'After your trip${from == null ? '' : ' (from ${DateFormat('d MMM yyyy').format(from)})'} you can leave a review for other travellers.',
                  style: tt.bodySmall)),
            ]),
          );
        }
        if (review == null) {
          return NgCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('How was your trip?', style: tt.titleMedium),
              const SizedBox(height: 6),
              const Text('Your review helps the next traveller plan. Our team checks every review before it goes live.'),
              const SizedBox(height: 12),
              FilledButton(onPressed: () => _openSheet(context, ref, id, null), child: const Text('Write a review')),
            ]),
          );
        }
        final status = review['status'] as String;
        final (label, bg, fg) = switch (status) {
          'PUBLISHED' => ('Published', context.ng.primaryTint, context.ng.primary),
          'REJECTED' => ('Needs changes', context.ng.error.withValues(alpha: .12), context.ng.error),
          'HIDDEN' => ('Not shown', context.ng.divider, context.ng.textSecondary),
          _ => ('Being checked', context.ng.secondary.withValues(alpha: .2), context.ng.ink),
        };
        return NgCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Text('Your review', style: tt.titleMedium),
              const Spacer(),
              Pill(label, bg: bg, fg: fg),
            ]),
            const SizedBox(height: 8),
            Stars(review['rating'] as num),
            if ((review['title'] ?? '').toString().isNotEmpty) ...[const SizedBox(height: 6), Text(review['title'], style: tt.titleSmall)],
            const SizedBox(height: 4),
            Text(review['body'] ?? ''),
            if (status == 'REJECTED' && review['moderationNote'] != null) ...[
              const SizedBox(height: 10),
              Text('Our team: ${review['moderationNote']}', style: TextStyle(color: context.ng.error)),
            ],
            if (status == 'PENDING') ...[
              const SizedBox(height: 10),
              Text('We usually check reviews within a day.', style: tt.bodySmall),
            ],
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton(onPressed: () => _openSheet(context, ref, id, review), child: const Text('Edit review')),
            ),
          ]),
        );
      },
    );
  }

  Future<void> _openSheet(BuildContext context, WidgetRef ref, int bookingId, Map<String, dynamic>? existing) async {
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _ReviewSheet(bookingId: bookingId, existing: existing),
    );
    if (saved == true) {
      ref.invalidate(tripReviewProvider(bookingId));
      if (context.mounted) toast(context, 'Thanks! Your review will appear once our team has checked it.');
    }
  }
}

class _ReviewSheet extends ConsumerStatefulWidget {
  final int bookingId;
  final Map<String, dynamic>? existing;
  const _ReviewSheet({required this.bookingId, this.existing});
  @override
  ConsumerState<_ReviewSheet> createState() => _ReviewSheetState();
}

class _ReviewSheetState extends ConsumerState<_ReviewSheet> {
  late int _rating = (widget.existing?['rating'] as int?) ?? 0;
  late final _title = TextEditingController(text: widget.existing?['title'] as String? ?? '');
  late final _body = TextEditingController(text: widget.existing?['body'] as String? ?? '');
  bool _busy = false;

  @override
  void dispose() {
    _title.dispose();
    _body.dispose();
    super.dispose();
  }

  bool get _valid => _rating >= 1 && _body.text.trim().length >= 10;

  Future<void> _save() async {
    setState(() => _busy = true);
    try {
      await ref.read(apiProvider).put<Map<String, dynamic>>('/api/bookings/${widget.bookingId}/review',
          {'rating': _rating, 'title': _title.text.trim(), 'body': _body.text.trim()});
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 0, 20, 20 + MediaQuery.viewInsetsOf(context).bottom),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text(widget.existing == null ? 'Review your trip' : 'Edit your review', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 12),
        Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          for (var i = 1; i <= 5; i++)
            IconButton(
              tooltip: '$i star${i == 1 ? '' : 's'}',
              iconSize: 36,
              onPressed: () => setState(() => _rating = i),
              icon: Icon(_rating >= i ? Icons.star_rounded : Icons.star_outline_rounded, color: context.ng.secondary),
            ),
        ]),
        const SizedBox(height: 8),
        TextField(controller: _title, maxLength: 80, decoration: const InputDecoration(labelText: 'Title (optional)')),
        const SizedBox(height: 4),
        TextField(
          controller: _body,
          minLines: 4,
          maxLines: 8,
          maxLength: 2000,
          onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(
            labelText: 'Your experience',
            hintText: 'What stood out? Guide, lodges, pace, views… (at least 10 characters)',
            alignLabelWithHint: true,
          ),
        ),
        const SizedBox(height: 4),
        Text('Please keep it about the trip. No phone numbers, e-mail addresses or personal details.',
            style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: 4),
        // Transparency: tell people exactly what becomes public before they submit.
        Text('Once our team approves it, your review is public with your first name and last initial '
            '(from the lead booker name) and your travel month. Deleting your account deletes your reviews.',
            style: Theme.of(context).textTheme.bodySmall),
        if (widget.existing != null) ...[
          const SizedBox(height: 4),
          Text('Edited reviews are checked again before they appear.', style: Theme.of(context).textTheme.bodySmall),
        ],
        const SizedBox(height: 16),
        FilledButton(
          onPressed: _valid && !_busy ? _save : null,
          child: _busy ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2)) : const Text('Submit review'),
        ),
      ]),
    );
  }
}
