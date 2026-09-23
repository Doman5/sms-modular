import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates users without accepting a tenant ID from the client', () => {
    api.createUser('a@example.test', 'Anna', 'role-id').subscribe();
    const request = http.expectOne('/api/v1/users');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'a@example.test', displayName: 'Anna', roleId: 'role-id' });
    request.flush({ user: {}, temporaryPassword: 'one-time-value' });
  });

  it('sends platform provisioning to a separate API', () => {
    const body = { slug: 'acme', name: 'Acme', timeZone: 'Europe/Warsaw', locale: 'pl-PL', adminEmail: 'a@example.test', adminDisplayName: 'Anna' };
    api.provisionTenant(body).subscribe();
    const request = http.expectOne('/api/platform/v1/tenants');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({ tenant: {}, firstAdmin: {}, temporaryPassword: 'one-time-value' });
  });

  it('filters employees within the tenant API', () => {
    api.employees(2, ' Jan ', 'ACTIVE', 'Kierowca').subscribe();
    const request = http.expectOne(value => value.url === '/api/v1/employees');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('search')).toBe('Jan');
    expect(request.request.params.get('status')).toBe('ACTIVE');
    expect(request.request.params.get('position')).toBe('Kierowca');
    expect(request.request.params.has('tenantId')).toBeFalse();
    request.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('creates employees and changes status with a version', () => {
    const input = { firstName: 'Jan', lastName: 'Nowak', phone: '501234567', email: null,
      position: 'Kierowca', note: null, employmentDate: '2024-01-01', status: 'ACTIVE' as const };
    api.createEmployee(input).subscribe();
    const created = http.expectOne('/api/v1/employees');
    expect(created.request.method).toBe('POST');
    expect(created.request.body).toEqual(input);
    created.flush({ id: 'employee-1' });
    api.setEmployeeStatus('employee-1', 'deactivate', 3).subscribe();
    const statusRequest = http.expectOne('/api/v1/employees/employee-1/deactivate');
    expect(statusRequest.request.method).toBe('POST');
    expect(statusRequest.request.body).toEqual({ version: 3 });
    statusRequest.flush({ id: 'employee-1', status: 'INACTIVE', version: 4 });
  });

  it('sends tenant-scoped work and absence commands with versions', () => {
    api.workDays('2025-04-01', '2025-04-30', 'employee-1').subscribe();
    const list = http.expectOne(value => value.url === '/api/v1/work-days');
    expect(list.request.params.get('employeeId')).toBe('employee-1');
    expect(list.request.params.has('tenantId')).toBeFalse();
    list.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    api.createWorkDay('employee-1', '2025-04-17', [{ startTime: '08:00', endTime: '12:00' }]).subscribe();
    const work = http.expectOne('/api/v1/employees/employee-1/work-days');
    expect(work.request.body).toEqual({ workDate: '2025-04-17', intervals: [{ startTime: '08:00', endTime: '12:00' }] });
    work.flush({});
    api.createAbsenceDays('employee-1', '2025-04-18', '2025-04-19', null).subscribe();
    const absence = http.expectOne('/api/v1/absence-days');
    expect(absence.request.body).toEqual({ employeeId: 'employee-1', dateFrom: '2025-04-18', dateTo: '2025-04-19', note: null });
    absence.flush([]);
    api.cancelAbsenceDay({ id: 'absence-1', employeeId: 'employee-1', absenceDate: '2025-04-18',
      source: 'MANUAL', note: null, status: 'ACTIVE', createdAt: '', updatedAt: '', version: 4 }).subscribe();
    const cancel = http.expectOne('/api/v1/absence-days/absence-1/cancel');
    expect(cancel.request.body).toEqual({ version: 4 });
    cancel.flush({});
  });

  it('does not accept tenant scope for tenant audit reads', () => {
    api.auditLogs({ tenantId: 'foreign-tenant', module: 'IDENTITY', page: 2 }, false).subscribe();
    const request = http.expectOne(value => value.url === '/api/v1/audit-logs');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.has('tenantId')).toBeFalse();
    expect(request.request.params.get('module')).toBe('IDENTITY');
    expect(request.request.params.get('page')).toBe('2');
    request.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('sends an explicit platform audit scope', () => {
    api.auditLogs({ global: true, result: 'DENIED' }, true).subscribe();
    const request = http.expectOne(value => value.url === '/api/platform/v1/audit-logs');
    expect(request.request.params.get('scope')).toBe('global');
    expect(request.request.params.has('tenantId')).toBeFalse();
    expect(request.request.params.get('result')).toBe('DENIED');
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('reads tenant subscription without a tenant ID supplied by the client', () => {
    api.subscription().subscribe();
    const request = http.expectOne('/api/v1/subscription');
    expect(request.request.method).toBe('GET');
    request.flush({ tenantId: 'tenant', planCode: 'BASE', modules: [], capabilities: [], usage: {} });
  });

  it('sends platform limit changes to the selected tenant', () => {
    api.updateActiveUserLimit('tenant-1', 'FINITE', 3, null).subscribe();
    const request = http.expectOne('/api/platform/v1/tenants/tenant-1/subscription/limits/ACTIVE_USERS');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ mode: 'FINITE', value: 3, expiresAt: null });
    request.flush({ tenantId: 'tenant-1', planCode: 'BASE', modules: [], capabilities: [], usage: {} });
  });
});
