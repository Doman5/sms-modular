import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { TenantSummary, UserSummary, UsageSnapshot } from './auth.service';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Role {
  id: string;
  code: string;
  name: string;
  permissions: string[];
}

export interface CreatedUser {
  user: UserSummary;
  temporaryPassword: string;
}

export interface ProvisionedTenant {
  tenant: TenantSummary;
  firstAdmin: UserSummary;
  temporaryPassword: string;
}

export interface AuditEntry {
  id: string;
  tenantId: string | null;
  actorType: 'TENANT_USER' | 'PLATFORM_USER' | 'SYSTEM';
  actorId: string | null;
  module: string;
  action: string;
  targetType: string;
  targetId: string;
  result: 'SUCCESS' | 'DENIED';
  occurredAt: string;
  correlationId: string;
  metadata: Record<string, string | string[]>;
}

export interface AuditQuery {
  tenantId?: string;
  global?: boolean;
  from?: string;
  to?: string;
  actorId?: string;
  module?: string;
  action?: string;
  result?: 'SUCCESS' | 'DENIED';
  targetId?: string;
  search?: string;
  page?: number;
}

export interface Employee {
  id: string;
  firstName: string;
  lastName: string;
  phone: string;
  normalizedPhone: string;
  email: string | null;
  position: string;
  note: string | null;
  status: 'ACTIVE' | 'INACTIVE';
  employmentDate: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface EmployeeInput {
  firstName: string;
  lastName: string;
  phone: string;
  email: string | null;
  position: string;
  note: string | null;
  employmentDate: string;
}

export interface WorkforceEmployeeOption { id: string; firstName: string; lastName: string; }

export interface WorkInterval {
  id: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
}

export interface WorkDay {
  id: string;
  employeeId: string;
  workDate: string;
  status: 'ACTIVE' | 'CANCELLED';
  source: 'MANUAL' | 'SMS';
  totalMinutes: number;
  intervals: WorkInterval[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface WorkSummary { month: string; dayCount: number; totalMinutes: number; }

export interface AbsenceDay {
  id: string;
  employeeId: string;
  absenceDate: string;
  source: 'MANUAL' | 'SMS';
  note: string | null;
  status: 'ACTIVE' | 'CANCELLED';
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface AbsenceCalendarDay { date: string; count: number; }

export interface SmsMessage {
  id: string;
  employeeId: string | null;
  sender: string | null;
  content: string | null;
  recipient: string | null;
  receivedAt: string;
  status: 'PENDING' | 'COMPLETED' | 'REVIEW_REQUIRED' | 'DISMISSED' | 'ERROR' | 'EXPIRED';
  reviewReason: string | null;
  resolution: string | null;
  version: number;
}

export interface SmsResolveInput {
  version: number;
  category: 'WORK_TIME' | 'ABSENCE' | 'DISMISS';
  employeeId: string | null;
  workDate: string | null;
  startTime: string | null;
  endTime: string | null;
  absenceDate: string | null;
}

export interface SmsRoute {
  id: string;
  tenantId: string;
  deviceId: string;
  recipient: string | null;
  simNumber: number | null;
  active: boolean;
}

export interface SubscriptionModule {
  key: string;
  type: 'BASE' | 'ADD_ON';
  availability: 'AVAILABLE' | 'PLANNED';
  status: 'INCLUDED' | 'ENABLED' | 'DISABLED';
  startsAt: string | null;
  endsAt: string | null;
  dependsOn: string | null;
}

export interface Subscription {
  tenantId: string;
  planCode: string;
  planVersion: number;
  capabilities: string[];
  modules: SubscriptionModule[];
  usage: UsageSnapshot;
  limitOverride: { mode: 'FINITE' | 'UNLIMITED' | 'INHERIT'; value: number | null; expiresAt: string | null };
  employeeUsage: UsageSnapshot;
  employeeLimitOverride: { mode: 'FINITE' | 'UNLIMITED' | 'INHERIT'; value: number | null; expiresAt: string | null };
  validUntil: string | null;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  employees(page = 0, search = '', status = '', position = '', size = 20) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search.trim()) params = params.set('search', search.trim());
    if (status) params = params.set('status', status);
    if (position) params = params.set('position', position);
    return this.http.get<Page<Employee>>('/api/v1/employees', { params });
  }
  employeePositions() { return this.http.get<string[]>('/api/v1/employees/positions'); }
  workforceEmployees(search = '', size = 100) {
    let params = new HttpParams().set('size', size);
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<Page<WorkforceEmployeeOption>>('/api/v1/employees/options', { params });
  }
  workforceEmployee(id: string) { return this.http.get<WorkforceEmployeeOption>(`/api/v1/employees/options/${id}`); }
  employee(id: string) { return this.http.get<Employee>(`/api/v1/employees/${id}`); }
  createEmployee(input: EmployeeInput & { status: Employee['status'] }) {
    return this.http.post<Employee>('/api/v1/employees', input);
  }
  updateEmployee(id: string, input: EmployeeInput & { version: number }) {
    return this.http.put<Employee>(`/api/v1/employees/${id}`, input);
  }
  setEmployeeStatus(id: string, action: 'activate' | 'deactivate', version: number) {
    return this.http.post<Employee>(`/api/v1/employees/${id}/${action}`, { version });
  }

  workDays(from: string, to: string, employeeId = '', page = 0) {
    let params = new HttpParams().set('from', from).set('to', to).set('page', page);
    if (employeeId) params = params.set('employeeId', employeeId);
    return this.http.get<Page<WorkDay>>('/api/v1/work-days', { params });
  }
  workSummary(month: string, employeeId = '') {
    let params = new HttpParams().set('month', month);
    if (employeeId) params = params.set('employeeId', employeeId);
    return this.http.get<WorkSummary>('/api/v1/work-days/summary', { params });
  }
  createWorkDay(employeeId: string, workDate: string, intervals: { startTime: string; endTime: string }[]) {
    return this.http.post<WorkDay>(`/api/v1/employees/${employeeId}/work-days`, { workDate, intervals });
  }
  updateWorkDay(day: WorkDay, intervals: { startTime: string; endTime: string }[]) {
    return this.http.put<WorkDay>(`/api/v1/employees/${day.employeeId}/work-days/${day.id}`,
      { version: day.version, intervals });
  }
  cancelWorkDay(day: WorkDay) {
    return this.http.post<WorkDay>(`/api/v1/employees/${day.employeeId}/work-days/${day.id}/cancel`,
      { version: day.version });
  }
  absenceDays(from: string, to: string, employeeId = '', page = 0) {
    let params = new HttpParams().set('from', from).set('to', to).set('page', page);
    if (employeeId) params = params.set('employeeId', employeeId);
    return this.http.get<Page<AbsenceDay>>('/api/v1/absence-days', { params });
  }
  absenceCalendar(month: string, employeeId = '') {
    let params = new HttpParams().set('month', month);
    if (employeeId) params = params.set('employeeId', employeeId);
    return this.http.get<AbsenceCalendarDay[]>('/api/v1/absence-days/calendar', { params });
  }
  createAbsenceDays(employeeId: string, dateFrom: string, dateTo: string, note: string | null) {
    return this.http.post<AbsenceDay[]>('/api/v1/absence-days', { employeeId, dateFrom, dateTo, note });
  }
  updateAbsenceDay(day: AbsenceDay, note: string | null) {
    return this.http.put<AbsenceDay>(`/api/v1/absence-days/${day.id}`, { version: day.version, note });
  }
  cancelAbsenceDay(day: AbsenceDay) {
    return this.http.post<AbsenceDay>(`/api/v1/absence-days/${day.id}/cancel`, { version: day.version });
  }

  smsMessages(from: string, to: string, status = '', employeeId = '', reviewOnly = false, page = 0) {
    let params = new HttpParams().set('from', from).set('to', to).set('reviewOnly', reviewOnly).set('page', page);
    if (status) params = params.set('status', status);
    if (employeeId) params = params.set('employeeId', employeeId);
    return this.http.get<Page<SmsMessage>>('/api/v1/sms', { params });
  }
  smsMessage(id: string) { return this.http.get<SmsMessage>(`/api/v1/sms/${id}`); }
  resolveSms(id: string, input: SmsResolveInput) {
    return this.http.post<SmsMessage>(`/api/v1/sms/${id}/resolve`, input);
  }
  reparseSms(id: string, version: number) {
    return this.http.post<SmsMessage>(`/api/v1/sms/${id}/reparse`, { version });
  }
  smsRoutes() { return this.http.get<SmsRoute[]>('/api/platform/v1/sms/routes'); }
  createSmsRoute(tenantId: string, recipient: string | null, simNumber: number | null) {
    return this.http.post<SmsRoute>('/api/platform/v1/sms/routes', { tenantId, recipient, simNumber });
  }
  deactivateSmsRoute(id: string) {
    return this.http.post<SmsRoute>(`/api/platform/v1/sms/routes/${id}/deactivate`, {});
  }

  users(page = 0) { return this.http.get<Page<UserSummary>>(`/api/v1/users?page=${page}`); }
  roles() { return this.http.get<Role[]>('/api/v1/roles'); }
  createUser(email: string, displayName: string, roleId: string) {
    return this.http.post<CreatedUser>('/api/v1/users', { email, displayName, roleId });
  }
  updateUser(id: string, displayName: string, roleId: string) {
    return this.http.put<UserSummary>(`/api/v1/users/${id}`, { displayName, roleId });
  }
  setUserStatus(id: string, status: UserSummary['status']) {
    return this.http.put<UserSummary>(`/api/v1/users/${id}/status`, { status });
  }
  resetPassword(id: string) {
    return this.http.post<{ temporaryPassword: string }>(`/api/v1/users/${id}/reset-password`, {});
  }
  createRole(code: string, name: string, permissions: string[]) {
    return this.http.post<Role>('/api/v1/roles', { code, name, permissions });
  }
  updateRole(id: string, name: string, permissions: string[]) {
    return this.http.put<Role>(`/api/v1/roles/${id}`, { name, permissions });
  }
  deleteRole(id: string) { return this.http.delete<void>(`/api/v1/roles/${id}`); }
  tenantSettings() { return this.http.get<TenantSummary>('/api/v1/tenant/settings'); }
  updateTenantSettings(name: string, timeZone: string, locale: string) {
    return this.http.put<TenantSummary>('/api/v1/tenant/settings', { name, timeZone, locale });
  }
  tenants(page = 0, size = 20, search = '', status = '') {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search.trim()) params = params.set('search', search.trim());
    if (status) params = params.set('status', status);
    return this.http.get<Page<TenantSummary>>('/api/platform/v1/tenants', { params });
  }
  provisionTenant(request: {
    slug: string; name: string; timeZone: string; locale: string;
    adminEmail: string; adminDisplayName: string;
  }) { return this.http.post<ProvisionedTenant>('/api/platform/v1/tenants', request); }
  setTenantStatus(id: string, action: 'suspend' | 'activate' | 'close') {
    return this.http.post<TenantSummary>(`/api/platform/v1/tenants/${id}/${action}`, {});
  }
  auditLogs(query: AuditQuery, platform: boolean) {
    let params = new HttpParams().set('page', query.page ?? 0);
    if (platform) {
      if (query.global) params = params.set('scope', 'global');
      else if (query.tenantId) params = params.set('tenantId', query.tenantId);
    }
    for (const key of ['from', 'to', 'actorId', 'module', 'action', 'result', 'targetId', 'search'] as const) {
      const value = query[key];
      if (value) params = params.set(key, value);
    }
    const path = platform ? '/api/platform/v1/audit-logs' : '/api/v1/audit-logs';
    return this.http.get<Page<AuditEntry>>(path, { params });
  }
  subscription() { return this.http.get<Subscription>('/api/v1/subscription'); }
  platformSubscription(tenantId: string) {
    return this.http.get<Subscription>(`/api/platform/v1/tenants/${tenantId}/subscription`);
  }
  updateAddon(tenantId: string, key: string, enabled: boolean, startsAt: string | null, endsAt: string | null) {
    return this.http.put<Subscription>(`/api/platform/v1/tenants/${tenantId}/subscription/addons/${key}`,
      { enabled, startsAt, endsAt });
  }
  updateActiveUserLimit(tenantId: string, mode: 'FINITE' | 'UNLIMITED' | 'INHERIT',
    value: number | null, expiresAt: string | null) {
    return this.http.put<Subscription>(`/api/platform/v1/tenants/${tenantId}/subscription/limits/ACTIVE_USERS`,
      { mode, value, expiresAt });
  }
  updateActiveEmployeeLimit(tenantId: string, mode: 'FINITE' | 'UNLIMITED' | 'INHERIT',
    value: number | null, expiresAt: string | null) {
    return this.http.put<Subscription>(`/api/platform/v1/tenants/${tenantId}/subscription/limits/ACTIVE_EMPLOYEES`,
      { mode, value, expiresAt });
  }
}
