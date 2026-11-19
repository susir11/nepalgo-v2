import 'package:flutter_test/flutter_test.dart';
import 'package:nepalgo/features/reviews.dart';

void main() {
  test('trip month label: API "YYYY-MM" -> "Mon YYYY", garbage passes through', () {
    expect(tripMonthLabel('2026-10'), 'Oct 2026');
    expect(tripMonthLabel('2027-01'), 'Jan 2027');
    expect(tripMonthLabel('soon'), 'soon');
  });
}
