import { HttpErrorResponse } from '@angular/common/http';

// Backend errors come back as ProblemDetail ({ status, title, detail }).
export function apiErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'Server is not reachable. Please try again.';
    }
    if (typeof err.error?.detail === 'string') {
      return err.error.detail;
    }
  }
  return fallback;
}
