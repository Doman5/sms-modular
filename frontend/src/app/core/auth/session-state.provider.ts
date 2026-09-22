import { InjectionToken } from '@angular/core';
import { SessionSnapshot, SessionStateProvider } from './auth.models';

export const SESSION_STATE_PROVIDER = new InjectionToken<SessionStateProvider>(
  'SESSION_STATE_PROVIDER',
);

export class EmptySessionStateProvider implements SessionStateProvider {
  getSnapshot(): SessionSnapshot {
    return {
      status: 'unknown',
      permissions: [],
    };
  }
}
