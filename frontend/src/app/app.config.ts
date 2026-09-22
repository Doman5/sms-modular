import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import Aura from '@primeuix/themes/aura';
import { providePrimeNG } from 'primeng/config';
import { routes } from './app.routes';
import { AUTH_TOKEN_PROVIDER, NoopAuthTokenProvider } from './core/http/auth-token-provider';
import {
  CORRELATION_ID_PROVIDER,
  DefaultCorrelationIdProvider,
} from './core/http/correlation-id-provider';
import { authTokenInterceptor } from './core/http/auth-token.interceptor';
import { problemDetailsInterceptor } from './core/http/problem-details.interceptor';
import { SESSION_STATE_PROVIDER } from './core/auth/session-state.provider';
import { EmptySessionStateProvider } from './core/auth/session-state.provider';
import {
  ENTITLEMENT_STATE_PROVIDER,
  EmptyEntitlementStateProvider,
} from './core/entitlements/entitlement-state.provider';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAnimationsAsync(),
    provideHttpClient(withInterceptors([authTokenInterceptor, problemDetailsInterceptor])),
    { provide: AUTH_TOKEN_PROVIDER, useClass: NoopAuthTokenProvider },
    { provide: CORRELATION_ID_PROVIDER, useClass: DefaultCorrelationIdProvider },
    { provide: SESSION_STATE_PROVIDER, useClass: EmptySessionStateProvider },
    { provide: ENTITLEMENT_STATE_PROVIDER, useClass: EmptyEntitlementStateProvider },
    providePrimeNG({
      ripple: true,
      theme: {
        preset: Aura,
        options: {
          darkModeSelector: '.app-dark',
        },
      },
    }),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
  ],
};
