import 'package:intl/intl.dart';

// The customer only ever sees USD (spec 21 currency display rule). NPR never
// appears in the app; it lives on the tax invoice PDF only.
final _usd = NumberFormat.currency(locale: 'en_US', symbol: r'$');
String usd(num? v) => v == null ? '—' : _usd.format(v);

String niceDate(String? iso) {
  if (iso == null) return '—';
  final d = DateTime.tryParse(iso);
  return d == null ? iso : DateFormat('d MMM yyyy').format(d.toLocal());
}

String isoDate(DateTime d) => DateFormat('yyyy-MM-dd').format(d);

// "48291057" -> "4829 1057" (spec 5a: two blocks of four for readability)
String groupNepalgoId(String? id) => (id != null && id.length == 8) ? '${id.substring(0, 4)} ${id.substring(4)}' : (id ?? '—');

/// "Pay by" copy for an AWAITING_PAYMENT trip (deadline = bill issued + payment window).
/// Returns (headline, urgent). `now` is injected so the wording is unit-testable.
(String, bool) paymentDueLabel(DateTime due, DateTime now) {
  final left = due.difference(now);
  final when = DateFormat('EEE d MMM, HH:mm').format(due.toLocal());
  if (left.isNegative) return ('The payment time for this trip has passed. Message us if you have already paid.', true);
  if (left.inHours < 1) return ('Less than an hour left: pay and upload your receipt by $when.', true);
  if (left.inHours < 24) return ('${left.inHours} h left: pay and upload your receipt by $when.', left.inHours < 6);
  return ('Please pay and upload your receipt by $when.', false);
}
