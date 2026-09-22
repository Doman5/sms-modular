import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TenancyApiService } from './tenancy.api';
import { TenantResponseDto } from './tenancy.models';

const TENANT: TenantResponseDto = {
  id: '11111111-1111-4111-8111-111111111111',
  slug: 'acme',
  name: 'Acme Sp. z o.o.',
  status: 'ACTIVE',
  timezone: 'Europe/Warsaw',
  locale: 'pl-PL',
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-02T10:00:00Z',
  closedAt: null,
};

describe('TenancyApiService', () => {
  let service: TenancyApiService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TenancyApiService);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('gets and maps the current tenant projection', () => {
    let received: unknown;
    service.getTenant().subscribe((tenant) => (received = tenant));

    const request = controller.expectOne('/api/v1/tenant');
    expect(request.request.method).toBe('GET');
    request.flush(TENANT);

    expect(received).toEqual(TENANT);
  });

  it('sends only tenant settings in a patch request', () => {
    service.updateTenant({ name: 'Nowa nazwa', locale: 'en-US' }).subscribe();

    const request = controller.expectOne('/api/v1/tenant');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ name: 'Nowa nazwa', locale: 'en-US' });
    request.flush({ ...TENANT, name: 'Nowa nazwa', locale: 'en-US' });
  });

  it('uses zero-based page and size for the platform list', () => {
    service.listPlatformTenants({ page: 1, size: 10 }).subscribe();

    const request = controller.expectOne(
      (candidate) =>
        candidate.url === '/api/platform/v1/tenants' &&
        candidate.params.get('page') === '1' &&
        candidate.params.get('size') === '10',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ items: [TENANT], page: 1, size: 10, totalElements: 11, totalPages: 2 });
  });

  it('maps platform creation and lifecycle endpoints', () => {
    service
      .createPlatformTenant({
        slug: 'beta',
        name: 'Beta',
        timezone: 'Europe/Warsaw',
        locale: 'pl-PL',
      })
      .subscribe();
    const create = controller.expectOne('/api/platform/v1/tenants');
    expect(create.request.method).toBe('POST');
    expect(create.request.body.slug).toBe('beta');
    create.flush(TENANT);

    service.closePlatformTenant(TENANT.id).subscribe();
    const close = controller.expectOne(`/api/platform/v1/tenants/${TENANT.id}/close`);
    expect(close.request.method).toBe('POST');
    expect(close.request.body).toEqual({});
    close.flush({ ...TENANT, status: 'CLOSED', closedAt: '2026-01-03T10:00:00Z' });
  });
});
