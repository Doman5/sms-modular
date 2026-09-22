import { SessionStateProvider } from '../../core/auth/auth.models';

export function hasTenantPermission(provider: SessionStateProvider, permission: string): boolean {
  const snapshot = provider.getSnapshot();
  return snapshot.status === 'unknown'
    ? true
    : snapshot.status === 'authenticated' && snapshot.permissions.includes(permission);
}
