export type SessionStatus = 'unknown' | 'authenticated' | 'unauthenticated';

export type PermissionKey = string & {};

export interface SessionSnapshot {
  readonly status: SessionStatus;
  readonly userId?: string;
  readonly permissions: readonly PermissionKey[];
}

export interface SessionStateProvider {
  getSnapshot(): SessionSnapshot;
}
