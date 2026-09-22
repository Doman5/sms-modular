import { CanMatchFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { CapabilityKey } from './entitlement.models';
import { ENTITLEMENT_STATE_PROVIDER } from './entitlement-state.provider';

function requiredCapability(route: Parameters<CanMatchFn>[0]): CapabilityKey | undefined {
  const value = route.data?.['capability'];
  return typeof value === 'string' ? (value as CapabilityKey) : undefined;
}

export const capabilityGuard: CanMatchFn = (route) => {
  const snapshot = inject(ENTITLEMENT_STATE_PROVIDER).getSnapshot();
  const capability = requiredCapability(route);

  if (snapshot.status === 'unknown' || !capability) {
    return true;
  }

  return snapshot.capabilities.includes(capability) ? true : inject(Router).parseUrl('/forbidden');
};
