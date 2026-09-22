import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { TenantSummary, UserSummary } from './auth.service';

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
  page?: number;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

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
  tenants(page = 0, size = 20) { return this.http.get<Page<TenantSummary>>(`/api/platform/v1/tenants?page=${page}&size=${size}`); }
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
    for (const key of ['from', 'to', 'actorId', 'module', 'action', 'result', 'targetId'] as const) {
      const value = query[key];
      if (value) params = params.set(key, value);
    }
    const path = platform ? '/api/platform/v1/audit-logs' : '/api/v1/audit-logs';
    return this.http.get<Page<AuditEntry>>(path, { params });
  }
}
