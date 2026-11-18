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
