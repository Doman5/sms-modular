import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ProblemDetailsApiModel } from '../api/api.models';
import {
  ApiProblemError,
  isProblemDetailsPayload,
  normalizeFieldErrors,
} from './problem-details.error';
import { CORRELATION_ID_HEADER } from './correlation-id-provider';

function defaultMessage(status: number): string {
  if (status === 0) {
    return 'Nie można połączyć się z serwerem.';
  }

  if (status === 401) {
    return 'Sesja wymaga ponownego uwierzytelnienia.';
  }

  if (status === 403) {
    return 'Nie masz uprawnień do wykonania tej operacji.';
  }

  return 'Wystąpił błąd podczas obsługi żądania.';
}

function normalizeProblem(error: HttpErrorResponse): ApiProblemError {
  const payload = isProblemDetailsPayload(error.error)
    ? (error.error as ProblemDetailsApiModel)
    : undefined;
  const status = payload?.status ?? error.status;
  const message = payload?.message ?? payload?.detail ?? payload?.title ?? defaultMessage(status);

  return new ApiProblemError(
    {
      type: payload?.type,
      title: payload?.title,
      status,
      detail: payload?.detail,
      instance: payload?.instance,
      code: payload?.code ?? (status > 0 ? `HTTP_${status}` : 'NETWORK_ERROR'),
      message,
      correlationId:
        payload?.correlationId ?? error.headers.get(CORRELATION_ID_HEADER) ?? undefined,
      errors: normalizeFieldErrors(payload?.errors, payload?.fieldErrors),
    },
    error,
  );
}

export const problemDetailsInterceptor: HttpInterceptorFn = (request, next) =>
  next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        return throwError(() => normalizeProblem(error));
      }

      return throwError(() => error);
    }),
  );
