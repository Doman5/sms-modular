import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AUTH_TOKEN_PROVIDER } from './auth-token-provider';
import { CORRELATION_ID_HEADER, CORRELATION_ID_PROVIDER } from './correlation-id-provider';

export const authTokenInterceptor: HttpInterceptorFn = (request, next) => {
  const tokenProvider = inject(AUTH_TOKEN_PROVIDER);
  const correlationIdProvider = inject(CORRELATION_ID_PROVIDER);
  const currentCorrelationId = request.headers.get(CORRELATION_ID_HEADER);
  const correlationId =
    currentCorrelationId && correlationIdProvider.isValid(currentCorrelationId)
      ? currentCorrelationId
      : correlationIdProvider.generate();
  const accessToken = tokenProvider.getAccessToken()?.trim();

  const headers: Record<string, string> = {
    [CORRELATION_ID_HEADER]: correlationId,
  };

  if (accessToken && !request.headers.has('Authorization')) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }

  return next(request.clone({ setHeaders: headers }));
};
