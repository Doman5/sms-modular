import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiProblemError } from '../../core/http/problem-details.error';
import { TenantContextService } from './tenant-context.service';
import { TenancyApiService } from './tenancy.api';
import { Tenant } from './tenancy.models';

const TENANT: Tenant = {
  id: '11111111-1111-4111-8111-111111111111',
  slug: 'acme',
  name: 'Acme',
  status: 'ACTIVE',
  timezone: 'Europe/Warsaw',
  locale: 'pl-PL',
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-02T10:00:00Z',
  closedAt: null,
};

describe('TenantContextService', () => {
  let service: TenantContextService;
  let getTenant: jasmine.Spy;

  beforeEach(() => {
    getTenant = jasmine.createSpy('getTenant').and.returnValue(of(TENANT));
    TestBed.configureTestingModule({
      providers: [TenantContextService, { provide: TenancyApiService, useValue: { getTenant } }],
    });
    service = TestBed.inject(TenantContextService);
  });

  it('loads the tenant once and exposes no tenant switcher', () => {
    service.load();
    service.load();

    expect(getTenant).toHaveBeenCalledTimes(1);
    expect(service.status()).toBe('ready');
    expect(service.tenant()).toEqual(TENANT);
  });

  it('keeps a retryable error and reloads after retry', () => {
    const problem = new ApiProblemError({
      status: 503,
      code: 'INTERNAL_ERROR',
      message: 'Spróbuj ponownie.',
      errors: [],
    });
    getTenant.and.returnValues(
      throwError(() => problem),
      of(TENANT),
    );

    service.load();
    expect(service.status()).toBe('error');
    expect(service.error()).toBe(problem);

    service.retry();
    expect(service.status()).toBe('ready');
    expect(service.tenant()).toEqual(TENANT);
    expect(getTenant).toHaveBeenCalledTimes(2);
  });
});
