import { InjectionToken } from '@angular/core';
import { EntitlementSnapshot, EntitlementStateProvider } from './entitlement.models';

export const ENTITLEMENT_STATE_PROVIDER = new InjectionToken<EntitlementStateProvider>(
  'ENTITLEMENT_STATE_PROVIDER',
);

export class EmptyEntitlementStateProvider implements EntitlementStateProvider {
  getSnapshot(): EntitlementSnapshot {
    return {
      status: 'unknown',
      capabilities: [],
    };
  }
}
