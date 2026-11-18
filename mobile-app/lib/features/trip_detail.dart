import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:material_ui/material_ui.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:url_launcher/url_launcher.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/providers.dart';
import '../core/theme.dart';
import '../core/widgets.dart';
import 'profile.dart' show walletProvider;
import 'trips.dart';

final tripProvider = FutureProvider.family<Map<String, dynamic>, int>(
    (ref, id) => ref.read(apiProvider).get<Map<String, dynamic>>('/api/bookings/$id'));

// The customer's bill: USD ONLY (spec 21). 404 while it's still being priced.
final billProvider = FutureProvider.family<Map<String, dynamic>?, int>((ref, id) async {
  try {
    return await ref.read(apiProvider).get<Map<String, dynamic>>('/api/bills/booking/$id');
  } on ApiException catch (e) {
    if (e.status == 404) return null;
    rethrow;
  }
});

final policyReviewProvider = FutureProvider<Map<String, dynamic>>(
    (ref) => ref.read(apiProvider).get<Map<String, dynamic>>('/api/legal/policy-review'));

class TripDetailScreen extends ConsumerWidget {
  final int id;
  const TripDetailScreen({super.key, required this.id});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final trip = ref.watch(tripProvider(id));
    void reload() {
      ref.invalidate(tripProvider(id));
      ref.invalidate(billProvider(id));
      ref.invalidate(tripsProvider);
    }

    return Scaffold(
      appBar: AppBar(title: Text(trip.value?['destinationNameSnapshot'] ?? 'Trip')),
      body: trip.when(
        loading: () => const Loading(),
        error: (e, _) => ErrorView(e, onRetry: reload),
        data: (b) => RefreshIndicator(
          onRefresh: () async => reload(),
          child: ListView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 40), children: [
            _Header(b: b),
            if (b['rejectionReason'] != null && b['status'] != 'CONFIRMED') ...[
              const SizedBox(height: 12),
              NgCard(color: context.ng.warning.withValues(alpha: .12), child: Text('Note from our team: ${b['rejectionReason']}')),
            ],
            const SizedBox(height: 16),
            switch (b['status']) {
              'PENDING_APPROVAL' => _Researching(b: b, onChanged: reload),
              'AWAITING_PAYMENT' => _AwaitingPayment(b: b, onChanged: reload),
              'VERIFYING_PAYMENT' => const _Verifying(),
              'CONFIRMED' => _Confirmed(b: b, onChanged: reload),
              _ => _Closed(b: b),
            },
          ]),
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  final Map<String, dynamic> b;
  const _Header({required this.b});
  @override
  Widget build(BuildContext context) => NgCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          StatusPill(b['status']),
          const SizedBox(height: 10),
          Text(b['destinationNameSnapshot'], style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 4),
          Text('Arriving ${niceDate(b['arrivalDate'])} · ${b['durationDays']} days · ${b['partySize']} ${b['partySize'] == 1 ? 'person' : 'people'}'),
          const SizedBox(height: 4),
          Text('Request ${b['voucherCode']}', style: Theme.of(context).textTheme.bodySmall),
        ]),
      );
}

// ---------------------------------------------------------------- Step 1: Research in progress
class _Researching extends ConsumerWidget {
  final Map<String, dynamic> b;
  final VoidCallback onChanged;
  const _Researching({required this.b, required this.onChanged});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final company = ref.watch(companyProfileProvider).value;
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      NgCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(Icons.travel_explore_rounded, color: context.ng.primary, size: 32),
          const SizedBox(height: 10),
          Text('Research in progress', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          const Text('Our Kathmandu team is checking availability with hotels, drivers and guides for your dates, '
              'then pricing your itemised bill by hand. You can safely close the app — we\'ll notify you the moment it\'s ready.'),
        ]),
      ),
      const SizedBox(height: 12),
      OutlinedButton.icon(
        icon: const Icon(Icons.chat_rounded),
        label: const Text('Meanwhile, chat with our team'),
        onPressed: () => openWhatsApp(context, company?['supportWhatsApp'] ?? '',
            'Hi NepalGo team, about my trip request ${b['voucherCode']} (${b['destinationNameSnapshot']}).'),
      ),
      const SizedBox(height: 8),
      _WithdrawButton(b: b, onChanged: onChanged),
    ]);
  }
}

class _WithdrawButton extends ConsumerWidget {
  final Map<String, dynamic> b;
  final VoidCallback onChanged;
  const _WithdrawButton({required this.b, required this.onChanged});
  @override
  Widget build(BuildContext context, WidgetRef ref) => TextButton(
        onPressed: () async {
          final ok = await _confirm(context, 'Withdraw this request?',
              'You haven\'t paid anything, so there\'s no fee. Your dates are released and any travel credit you applied goes back to your wallet.',
              'Withdraw request');
          if (!ok) return;
          try {
            await ref.read(apiProvider).post('/api/bookings/${b['id']}/withdraw');
            ref.invalidate(walletProvider);
            onChanged();
          } catch (e) {
            if (context.mounted) showError(context, e);
          }
        },
        child: Text('Withdraw request', style: TextStyle(color: context.ng.error)),
      );
}

// ---------------------------------------------------------------- Step 2: review -> bill -> identity -> policy -> pay -> receipt
class _AwaitingPayment extends ConsumerStatefulWidget {
  final Map<String, dynamic> b;
  final VoidCallback onChanged;
  const _AwaitingPayment({required this.b, required this.onChanged});
  @override
  ConsumerState<_AwaitingPayment> createState() => _AwaitingPaymentState();
}

class _AwaitingPaymentState extends ConsumerState<_AwaitingPayment> {
  bool _agreedItinerary = false;
  bool _identitySaved = false;
  bool _consented = false;
  bool _paymentOpened = false;

  @override
  Widget build(BuildContext context) {
    final b = widget.b;
    if (b['invoiceNumber'] == null) {
      return const NgCard(child: Text('Your itemised bill is being prepared. We\'ll notify you as soon as it\'s ready.'));
    }
    final bill = ref.watch(billProvider(b['id'] as int));
    return bill.when(
      loading: () => const Loading(),
      error: (e, _) => ErrorView(e, onRetry: () => ref.invalidate(billProvider(b['id'] as int))),
      data: (bl) {
        if (bl == null) return const NgCard(child: Text('Your bill is almost ready. Pull down to refresh in a moment.'));
        // Sub-screen 1: Final Itinerary Review (a navigation gate, not a legal consent).
        if (!_agreedItinerary) return _ItineraryReview(b: b, bill: bl, onAgree: () => setState(() => _agreedItinerary = true));
        return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          _BillCard(bill: bl),
          const SectionTitle('Lead booker'),
          _IdentityForm(bookingId: b['id'] as int, onSaved: () => setState(() => _identitySaved = true), saved: _identitySaved),
          const SectionTitle('Policy review'),
          _PolicyReview(bookingId: b['id'] as int, enabled: _identitySaved, done: _consented, onConsented: () => setState(() => _consented = true)),
          const SizedBox(height: 16),
          FilledButton.icon(
            icon: const Icon(Icons.lock_rounded),
            label: Text('Proceed to secure payment via Payoneer · ${usd(bl['payableUsd'] as num)}'),
            onPressed: !_consented
                ? null
                : () async {
                    await launchUrl(Uri.parse(bl['payoneerLink']), mode: LaunchMode.inAppBrowserView);
                    setState(() => _paymentOpened = true);
                  },
          ),
          if (_paymentOpened || _consented) ...[
            const SectionTitle('After paying: upload proof'),
            _ReceiptUpload(bookingId: b['id'] as int, onSubmitted: widget.onChanged),
          ],
          const SizedBox(height: 8),
          _WithdrawButton(b: b, onChanged: widget.onChanged),
        ]);
      },
    );
  }
}

class _ItineraryReview extends StatelessWidget {
  final Map<String, dynamic> b, bill;
  final VoidCallback onAgree;
  const _ItineraryReview({required this.b, required this.bill, required this.onAgree});

  @override
  Widget build(BuildContext context) {
    List days = const [];
    try {
      days = (jsonDecode(b['itinerarySnapshot'] ?? '{}')['days'] as List?) ?? const [];
    } catch (_) {}
    final lines = List<Map<String, dynamic>>.from((bill['lineItems'] as List).map((e) => Map<String, dynamic>.from(e)));
    final hasGuide = lines.any((l) => l['category'] == 'GUIDE' && !(l['description'] as String).toLowerCase().contains('porter'));
    final hasPorter = lines.any((l) => (l['description'] as String).toLowerCase().contains('porter'));
    Widget req(String label, bool asked, bool given) => !asked
        ? const SizedBox.shrink()
        : ListTile(
            contentPadding: EdgeInsets.zero,
            leading: Icon(given ? Icons.check_circle_rounded : Icons.info_rounded, color: given ? context.ng.success : context.ng.warning),
            title: Text(label),
            subtitle: Text(given ? 'Included in your bill' : 'Not included — ask us on WhatsApp if you need it'),
          );
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Text('Your final itinerary', style: Theme.of(context).textTheme.titleLarge),
      const SizedBox(height: 4),
      Text('This is exactly the plan you requested. Review it, then continue to your bill.', style: Theme.of(context).textTheme.bodySmall),
      const SizedBox(height: 12),
      NgCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          req('Guide', b['guideRequested'] == true, hasGuide),
          req('Porter', b['porterRequested'] == true, hasPorter),
          if ((b['specialRequestText'] ?? '').toString().isNotEmpty)
            ListTile(contentPadding: EdgeInsets.zero, leading: const Icon(Icons.notes_rounded), title: const Text('Your special request'),
                subtitle: Text(b['specialRequestText'])),
        ]),
      ),
      const SizedBox(height: 12),
      for (final d in days)
        Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: NgCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Pill('Day ${d['dayNumber']}', bg: context.ng.primaryTint, fg: context.ng.primary),
              const SizedBox(height: 8),
              Text(d['narrative'] ?? ''),
              if (d['hotelName'] != null) ...[const SizedBox(height: 6), Text('Stay: ${d['hotelName']}', style: Theme.of(context).textTheme.bodySmall)],
            ]),
          ),
        ),
      const SizedBox(height: 8),
      FilledButton(onPressed: onAgree, child: const Text('Agree & view my bill')),
    ]);
  }
}

class _BillCard extends StatelessWidget {
  final Map<String, dynamic> bill;
  const _BillCard({required this.bill});
  @override
  Widget build(BuildContext context) {
    final lines = List<Map<String, dynamic>>.from((bill['lineItems'] as List).map((e) => Map<String, dynamic>.from(e)));
    final tt = Theme.of(context).textTheme;
    Widget row(String l, String v, {bool bold = false, Color? color}) => Padding(
          padding: const EdgeInsets.symmetric(vertical: 3),
          child: Row(children: [
            Expanded(child: Text(l, style: bold ? const TextStyle(fontWeight: FontWeight.w700) : null)),
            Text(v, style: TextStyle(fontWeight: bold ? FontWeight.w700 : FontWeight.w500, color: color)),
          ]),
        );
    return NgCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Row(children: [
          Expanded(child: Text('Your bill', style: tt.titleLarge)),
          Text(bill['invoiceNumber'] ?? '', style: tt.bodySmall),
        ]),
        const SizedBox(height: 12),
        for (final l in lines)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('${l['itineraryDay'] != null ? 'Day ${l['itineraryDay']} — ' : ''}${l['description']}'),
                  Text('${l['quantity']} × ${usd(l['unitPriceUsd'] as num)}', style: tt.bodySmall),
                ]),
              ),
              Text(usd(l['lineTotalUsd'] as num)),
            ]),
          ),
        const Divider(height: 24),
        row('Subtotal', usd(bill['subtotalUsd'] as num)),
        row('Service charge (${bill['serviceChargePercent']}%)', usd(bill['serviceChargeUsd'] as num)),
        if (bill['vatApplied'] == true) row('VAT (${bill['vatPercent']}%)', usd(bill['vatUsd'] as num)),
        row('Total', usd(bill['grandTotalUsd'] as num), bold: true),
        if ((bill['appliedCreditUsd'] as num) > 0) row('Travel credit', '− ${usd(bill['appliedCreditUsd'] as num)}', color: context.ng.success),
        const Divider(height: 20),
        row('You pay', usd(bill['payableUsd'] as num), bold: true, color: context.ng.primary),
        const SizedBox(height: 6),
        Text('Exchange rate locked when your bill was issued; it won\'t change.', style: tt.bodySmall),
      ]),
    );
  }
}

class _IdentityForm extends ConsumerStatefulWidget {
  final int bookingId;
  final bool saved;
  final VoidCallback onSaved;
  const _IdentityForm({required this.bookingId, required this.saved, required this.onSaved});
  @override
  ConsumerState<_IdentityForm> createState() => _IdentityFormState();
}

class _IdentityFormState extends ConsumerState<_IdentityForm> {
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController(), _passport = TextEditingController(), _email = TextEditingController();
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _email.text = ref.read(authProvider).profile?['email'] ?? '';
  }

  @override
  Widget build(BuildContext context) => NgCard(
        child: Form(
          key: _form,
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Text('Enter your details exactly as on your passport. You are responsible for their accuracy (Terms §5).',
                style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 12),
            TextFormField(controller: _name, enabled: !widget.saved, decoration: const InputDecoration(labelText: 'Legal name (as on passport)'),
                textCapitalization: TextCapitalization.characters, validator: (v) => (v ?? '').trim().length < 2 ? 'Required' : null),
            const SizedBox(height: 10),
            TextFormField(controller: _passport, enabled: !widget.saved, decoration: const InputDecoration(labelText: 'Passport number'),
                textCapitalization: TextCapitalization.characters, validator: (v) => (v ?? '').trim().length < 5 ? 'Required' : null),
            const SizedBox(height: 10),
            TextFormField(controller: _email, enabled: !widget.saved, keyboardType: TextInputType.emailAddress,
                decoration: const InputDecoration(labelText: 'Email (we send your invoice here)'),
                validator: (v) => (v ?? '').contains('@') ? null : 'Enter a valid email'),
            const SizedBox(height: 12),
            if (widget.saved)
              Row(children: [Icon(Icons.check_circle_rounded, color: context.ng.success), const SizedBox(width: 8), const Text('Details saved')])
            else
              FilledButton(
                onPressed: _busy
                    ? null
                    : () async {
                        if (!_form.currentState!.validate()) return;
                        setState(() => _busy = true);
                        try {
                          await ref.read(apiProvider).post('/api/bookings/${widget.bookingId}/identity', {
                            'legalName': _name.text.trim(), 'passportNumber': _passport.text.trim(), 'email': _email.text.trim()});
                          widget.onSaved();
                        } catch (e) {
                          if (context.mounted) showError(context, e);
                        } finally {
                          if (mounted) setState(() => _busy = false);
                        }
                      },
                child: const Text('Save details'),
              ),
          ]),
        ),
      );
}

/// Spec 9: three individually gated cards. Each checkbox stays LOCKED until its
/// card has been expanded; no "agree to all". Expand/check times are recorded
/// per card and sent with the consent, which the server validates and stores
/// against the exact Terms version shown. Payment is refused server-side
/// without it — the UI just makes the honest path easy.
class _PolicyReview extends ConsumerStatefulWidget {
  final int bookingId;
  final bool enabled, done;
  final VoidCallback onConsented;
  const _PolicyReview({required this.bookingId, required this.enabled, required this.done, required this.onConsented});
  @override
  ConsumerState<_PolicyReview> createState() => _PolicyReviewState();
}

class _PolicyReviewState extends ConsumerState<_PolicyReview> {
  final Map<String, DateTime> _expanded = {}, _checked = {};
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final pr = ref.watch(policyReviewProvider);
    if (widget.done) {
      return NgCard(child: Row(children: [
        Icon(Icons.verified_rounded, color: context.ng.success), const SizedBox(width: 8),
        const Expanded(child: Text('You agreed to the Terms. Continue to payment.')),
      ]));
    }
    return pr.when(
      loading: () => const Loading(),
      error: (e, _) => ErrorView(e, onRetry: () => ref.invalidate(policyReviewProvider)),
      data: (p) {
        final cards = List<Map<String, dynamic>>.from((p['cards'] as List).map((e) => Map<String, dynamic>.from(e)));
        final allChecked = cards.every((c) => _checked.containsKey(c['key']));
        return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Text('Please open and read each section, then tick it. Terms ${p['versionLabel']}.', style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 10),
          for (final c in cards)
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: NgCard(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                child: Column(children: [
                  ExpansionTile(
                    shape: const Border(),
                    title: Text(c['title'], style: const TextStyle(fontWeight: FontWeight.w600)),
                    leading: Icon(_expanded.containsKey(c['key']) ? Icons.lock_open_rounded : Icons.lock_rounded,
                        color: _expanded.containsKey(c['key']) ? context.ng.primary : context.ng.textSecondary),
                    onExpansionChanged: (open) {
                      if (open) setState(() => _expanded.putIfAbsent(c['key'], () => DateTime.now().toUtc()));
                    },
                    childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    children: [Text(c['text'] ?? '', style: const TextStyle(height: 1.45))],
                  ),
                  CheckboxListTile(
                    value: _checked.containsKey(c['key']),
                    // Locked until expanded (spec 9).
                    onChanged: (!widget.enabled || !_expanded.containsKey(c['key']))
                        ? null
                        : (v) => setState(() => v == true ? _checked[c['key']] = DateTime.now().toUtc() : _checked.remove(c['key'])),
                    controlAffinity: ListTileControlAffinity.leading,
                    title: Text(_expanded.containsKey(c['key']) ? 'I have read and accept this section' : 'Open the section above to unlock'),
                  ),
                ]),
              ),
            ),
          if (!widget.enabled) Text('Save your passport details first.', style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 6),
          FilledButton(
            onPressed: (!allChecked || _busy || !widget.enabled) ? null : () => _submit(p, cards),
            child: Text(_busy ? 'Saving…' : 'I agree to the Terms'),
          ),
        ]);
      },
    );
  }

  Future<void> _submit(Map<String, dynamic> p, List<Map<String, dynamic>> cards) async {
    setState(() => _busy = true);
    Map<String, String> times(String key) => {'expandedAt': _expanded[key]!.toIso8601String(), 'checkedAt': _checked[key]!.toIso8601String()};
    try {
      await ref.read(apiProvider).post('/api/legal/consents', {
        'bookingId': widget.bookingId,
        'termsDocumentId': p['termsDocumentId'],
        'cancellationMatrix': times('CANCELLATION_MATRIX'),
        'forceMajeureWeather': times('FORCE_MAJEURE_WEATHER'),
        'identityLiability': times('IDENTITY_LIABILITY'),
      });
      widget.onConsented();
    } on ApiException catch (e) {
      if (e.code == 'TERMS_VERSION_CHANGED') {
        // Admin published new Terms mid-checkout: show the new text and start over.
        ref.invalidate(policyReviewProvider);
        setState(() { _expanded.clear(); _checked.clear(); });
      }
      if (mounted) showError(context, e);
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}

/// Spec 8 Step 3: a REAL photo/screenshot (never a link) + the transaction code.
class _ReceiptUpload extends ConsumerStatefulWidget {
  final int bookingId;
  final VoidCallback onSubmitted;
  const _ReceiptUpload({required this.bookingId, required this.onSubmitted});
  @override
  ConsumerState<_ReceiptUpload> createState() => _ReceiptUploadState();
}

class _ReceiptUploadState extends ConsumerState<_ReceiptUpload> {
  XFile? _file;
  List<int>? _bytes;
  final _code = TextEditingController();
  bool _busy = false;

  @override
  Widget build(BuildContext context) => NgCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Text('Once Payoneer shows "payment successful", take a screenshot and upload it here with the transaction ID.'),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            icon: Icon(_file == null ? Icons.add_photo_alternate_rounded : Icons.check_circle_rounded),
            label: Text(_file == null ? 'Choose screenshot' : _file!.name),
            onPressed: () async {
              final f = await ImagePicker().pickImage(source: ImageSource.gallery, imageQuality: 85, maxWidth: 2000);
              if (f == null) return;
              final bytes = await f.readAsBytes();
              setState(() { _file = f; _bytes = bytes; });
            },
          ),
          const SizedBox(height: 10),
          TextField(controller: _code, decoration: const InputDecoration(labelText: 'Transaction / receipt ID')),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: (_busy || _file == null || _code.text.trim().isEmpty) ? null : _submit,
            child: Text(_busy ? 'Uploading…' : 'I\'ve paid, please check'),
          ),
        ]),
      );

  Future<void> _submit() async {
    setState(() => _busy = true);
    try {
      final form = FormData.fromMap({
        'paymentReceiptCode': _code.text.trim(),
        'file': MultipartFile.fromBytes(_bytes!, filename: _file!.name),
      });
      await ref.read(apiProvider).dio.post('/api/bookings/${widget.bookingId}/payment-receipt', data: form);
      widget.onSubmitted();
    } catch (e) {
      if (mounted) showError(context, ApiException.from(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}

// ---------------------------------------------------------------- Step 3: verifying
class _Verifying extends StatelessWidget {
  const _Verifying();
  @override
  Widget build(BuildContext context) => NgCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(Icons.hourglass_top_rounded, color: context.ng.warning, size: 32),
          const SizedBox(height: 10),
          Text('Payment under verification', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          const Text('We\'ve received your payment details and our team is confirming them now. '
              'This usually takes 5–30 minutes during business hours — we\'ll notify you the moment it\'s done.'),
        ]),
      );
}

// ---------------------------------------------------------------- Step 4: confirmed
class _Confirmed extends ConsumerWidget {
  final Map<String, dynamic> b;
  final VoidCallback onChanged;
  const _Confirmed({required this.b, required this.onChanged});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      BookingConfirmationCard(b: b),
      const SizedBox(height: 12),
      NgCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('Show this to your hotel, driver or guide', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 12),
          // Plain voucher text (not a deep link) so any generic scanner can read it (spec 8 Step 4).
          Center(child: Container(color: Colors.white, padding: const EdgeInsets.all(8), child: QrImageView(data: b['voucherCode'], size: 200))),
        ]),
      ),
      const SizedBox(height: 16),
      TextButton(
        onPressed: () async {
          final ok = await _confirm(context, 'Cancel this trip?',
              'Refunds depend on how far ahead you cancel (Terms §6):\n'
                  '• 60+ days: 90% cash refund\n• 30–59 days: 70% as Travel Credit\n• 15–29 days: 50% as Travel Credit\n• 0–14 days: non-refundable\n\n'
                  'Weather or flight delays are not a cancellation event — message us for rebooking help instead.',
              'Cancel trip');
          if (!ok) return;
          try {
            final r = await ref.read(apiProvider).post<Map<String, dynamic>>('/api/bookings/${b['id']}/cancel');
            ref.invalidate(walletProvider);
            if (context.mounted) {
              toast(context, (r['cashRefundUsd'] as num) > 0
                  ? 'Cancelled. ${usd(r['cashRefundUsd'] as num)} will be refunded to you.'
                  : (r['travelCreditUsd'] as num) > 0
                      ? 'Cancelled. ${usd(r['travelCreditUsd'] as num)} Travel Credit added to your wallet.'
                      : 'Cancelled. This trip was within 14 days, so no refund applies.');
            }
            onChanged();
          } catch (e) {
            if (context.mounted) showError(context, e);
          }
        },
        child: Text('Cancel trip', style: TextStyle(color: context.ng.error)),
      ),
    ]);
  }
}

/// Wallet card #1 (spec 6c): rendered from the booking itself, no extra table.
class BookingConfirmationCard extends StatelessWidget {
  final Map<String, dynamic> b;
  const BookingConfirmationCard({super.key, required this.b});
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(NgRadius.card),
          gradient: const LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: [Color(0xFF1D2B22), Color(0xFF387B53)]),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('NepalGo · Booking confirmation', style: TextStyle(color: Colors.white70, fontSize: 12)),
          const SizedBox(height: 12),
          Text(b['voucherCode'], style: const TextStyle(color: Colors.white, fontSize: 26, fontWeight: FontWeight.w700, letterSpacing: 1)),
          const SizedBox(height: 6),
          Text('${b['destinationNameSnapshot']} · ${niceDate(b['arrivalDate'])}', style: const TextStyle(color: Colors.white)),
        ]),
      );
}

class _Closed extends StatelessWidget {
  final Map<String, dynamic> b;
  const _Closed({required this.b});
  @override
  Widget build(BuildContext context) => NgCard(
        child: Text(switch (b['status']) {
          'REJECTED' => 'Sorry — we couldn\'t arrange this trip for your dates.${b['rejectionReason'] != null ? ' ${b['rejectionReason']}' : ''}',
          'EXPIRED' => 'The payment window for this trip has passed, so the dates were released. You can send a new request anytime.',
          'WITHDRAWN' => 'You withdrew this request. Nothing was charged.',
          _ => 'This trip was cancelled. Any refund or travel credit has been issued as per our cancellation policy.',
        }),
      );
}

Future<bool> _confirm(BuildContext context, String title, String body, String action) async =>
    await showModalBottomSheet<bool>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => Padding(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Text(title, style: Theme.of(ctx).textTheme.titleLarge),
          const SizedBox(height: 10),
          Text(body),
          const SizedBox(height: 18),
          FilledButton(style: FilledButton.styleFrom(backgroundColor: ctx.ng.error), onPressed: () => Navigator.pop(ctx, true), child: Text(action)),
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Keep it')),
        ]),
      ),
    ) ==
    true;
