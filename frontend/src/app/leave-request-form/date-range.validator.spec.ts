import { FormControl, FormGroup } from '@angular/forms';
import { dateRangeValidator, daysBetweenInclusive } from './date-range.validator';

describe('dateRangeValidator', () => {
  function group(start: string, end: string) {
    return new FormGroup(
      { start: new FormControl(start), end: new FormControl(end) },
      { validators: dateRangeValidator('start', 'end') }
    );
  }

  it('fails when end is before start', () => {
    expect(group('2026-05-07', '2026-05-03').hasError('dateRange')).toBeTrue();
  });

  it('passes for the same day and for a normal range', () => {
    expect(group('2026-05-03', '2026-05-03').valid).toBeTrue();
    expect(group('2026-05-03', '2026-05-07').valid).toBeTrue();
  });

  it('does not complain while a date is still empty', () => {
    expect(group('2026-05-03', '').hasError('dateRange')).toBeFalse();
  });
});

describe('daysBetweenInclusive', () => {
  it('counts both ends', () => {
    expect(daysBetweenInclusive('2026-05-03', '2026-05-03')).toBe(1);
    expect(daysBetweenInclusive('2026-05-03', '2026-05-07')).toBe(5);
  });

  it('works across a DST change', () => {
    expect(daysBetweenInclusive('2026-03-25', '2026-03-31')).toBe(7);
  });
});
