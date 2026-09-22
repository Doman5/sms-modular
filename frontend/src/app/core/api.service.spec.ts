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
});
