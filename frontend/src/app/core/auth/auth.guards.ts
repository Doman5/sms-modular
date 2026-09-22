import { CanMatchFn, Route, Router } from '@angular/router';
import { inject } from '@angular/core';
import { SESSION_STATE_PROVIDER } from './session-state.provider';

function requiredPermission(route: Route): string | undefined {
  const value = route.data?.['permission'];
  return typeof value === 'string' ? value : undefined;
}

function forbiddenUrl() {
  return inject(Router).parseUrl('/forbidden');
}

export const sessionGuard: CanMatchFn = () => {
  const snapshot = inject(SESSION_STATE_PROVIDER).getSnapshot();
  return snapshot.status === 'unauthenticated' ? forbiddenUrl() : true;
};

export const permissionGuard: CanMatchFn = (route) => {
  const snapshot = inject(SESSION_STATE_PROVIDER).getSnapshot();
  const permission = requiredPermission(route);

  if (snapshot.status === 'unknown' || !permission) {
    return true;
  }

  return snapshot.status === 'authenticated' && snapshot.permissions.includes(permission)
    ? true
    : forbiddenUrl();
};
