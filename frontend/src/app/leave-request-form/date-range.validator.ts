import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

// Dates come from <input type="date"> as yyyy-MM-dd, so string compare is enough.
export function dateRangeValidator(startKey: string, endKey: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const start: string | null = group.get(startKey)?.value;
    const end: string | null = group.get(endKey)?.value;
    if (!start || !end) {
      return null;
    }
    return start > end ? { dateRange: true } : null;
  };
}

export function daysBetweenInclusive(start: string, end: string): number {
  const ms = Date.parse(end) - Date.parse(start);
  return Math.round(ms / 86_400_000) + 1;
}
