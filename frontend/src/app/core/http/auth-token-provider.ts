import { InjectionToken } from '@angular/core';

export interface AuthTokenProvider {
  getAccessToken(): string | null;
}

export const AUTH_TOKEN_PROVIDER = new InjectionToken<AuthTokenProvider>('AUTH_TOKEN_PROVIDER');

export class NoopAuthTokenProvider implements AuthTokenProvider {
  getAccessToken(): string | null {
    return null;
  }
}
