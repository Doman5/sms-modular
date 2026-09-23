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

  employees(page = 0, search = '', status = '', position = '') {
    let params = new HttpParams().set('page', page);
    if (search.trim()) params = params.set('search', search.trim());
    if (status) params = params.set('status', status);
    if (position) params = params.set('position', position);
    return this.http.get<Page<Employee>>('/api/v1/employees', { params });
  }
  employeePositions() { return this.http.get<string[]>('/api/v1/employees/positions'); }
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
