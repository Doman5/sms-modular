import { HttpErrorResponse } from '@angular/common/http';

export function problemMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return 'Brak połączenia z serwerem. Spróbuj ponownie.';
    if (typeof error.error?.detail === 'string') return error.error.detail;
    if (error.status === 403) return 'Nie masz uprawnień do tej operacji.';
  }
  return 'Operacja nie powiodła się. Spróbuj ponownie.';
}
