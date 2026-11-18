import 'package:flutter_test/flutter_test.dart';
import 'package:nepalgo/core/format.dart';
import 'package:nepalgo/core/widgets.dart';

void main() {
  group('money (customers only ever see USD)', () {
    test('formats dollars with grouping and cents', () {
      expect(usd(1234.5), r'$1,234.50');
      expect(usd(0), r'$0.00');
      expect(usd(null), '—');
    });
  });

  group('NepalGo ID (spec 5a)', () {
    test('shown as two blocks of four', () => expect(groupNepalgoId('48291057'), '4829 1057'));
    test('anything else shown as-is', () {
      expect(groupNepalgoId('123'), '123');
      expect(groupNepalgoId(null), '—');
    });
  });

  group('dates', () {
    test('ISO date for the API', () => expect(isoDate(DateTime(2027, 3, 9)), '2027-03-09'));
    test('human date', () => expect(niceDate('2027-03-09'), '9 Mar 2027'));
    test('null-safe', () => expect(niceDate(null), '—'));
  });

  group('booking status copy (spec 8 customer wording, never raw enums)', () {
    test('every lifecycle status has friendly text', () {
      const statuses = ['PENDING_APPROVAL', 'AWAITING_PAYMENT', 'VERIFYING_PAYMENT', 'CONFIRMED', 'REJECTED', 'EXPIRED',
        'WITHDRAWN', 'CANCELLED_TIERED', 'CANCELLED_FORCE_MAJEURE'];
      for (final s in statuses) {
        final label = StatusPill.label(s);
        expect(label, isNot(contains('_')), reason: s);
      }
      expect(StatusPill.label('PENDING_APPROVAL'), 'Research in progress');
      expect(StatusPill.label('VERIFYING_PAYMENT'), 'Payment under verification');
    });
  });
}
