import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const token = auth.session()?.accessToken;
  const secured = token && request.url.startsWith('/api/') && !request.url.endsWith('/auth/login');
  const outgoing = secured
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;
  return next(outgoing).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && secured) auth.logout();
      return throwError(() => error);
    }),
  );
};
