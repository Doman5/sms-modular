export type CapabilityKey =
  | 'EMPLOYEE_DIRECTORY'
  | 'SMS_INBOUND'
  | 'AI_INTERPRETATION'
  | 'TIME_TRACKING'
  | 'ABSENCE_EVENTS'
  | 'LEAVE_MANAGEMENT'
  | 'PAYROLL'
  | 'PROJECTS'
  | 'PLANNING'
  | 'TOOL_ASSIGNMENT'
  | (string & {});

export type EntitlementStatus = 'unknown' | 'resolved';

export interface EntitlementSnapshot {
  readonly status: EntitlementStatus;
  readonly capabilities: readonly CapabilityKey[];
}

export interface EntitlementStateProvider {
  getSnapshot(): EntitlementSnapshot;
}
