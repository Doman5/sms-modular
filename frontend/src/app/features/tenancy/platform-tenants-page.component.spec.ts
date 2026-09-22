import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SESSION_STATE_PROVIDER } from '../../core/auth/session-state.provider';
import { SessionStateProvider } from '../../core/auth/auth.models';
import { problemDetailsInterceptor } from '../../core/http/problem-details.interceptor';
import { PlatformTenantsPageComponent } from './platform-tenants-page.component';
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

describe('PlatformTenantsPageComponent', () => {
  let fixture: ComponentFixture<PlatformTenantsPageComponent>;
  let controller: HttpTestingController;

  function create(permissions?: readonly string[]): void {
    const session: SessionStateProvider = {
      getSnapshot: () =>
        permissions
          ? { status: 'authenticated', userId: 'platform-1', permissions }
          : { status: 'unknown', permissions: [] },
    };
    TestBed.configureTestingModule({
      imports: [PlatformTenantsPageComponent],
      providers: [
        provideHttpClient(withInterceptors([problemDetailsInterceptor])),
        provideHttpClientTesting(),
        { provide: SESSION_STATE_PROVIDER, useValue: session },
      ],
    });
    fixture = TestBed.createComponent(PlatformTenantsPageComponent);
    controller = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  function flushList(items: readonly TenantResponseDto[] = [TENANT]): void {
    controller
      .expectOne(
        (request) =>
          request.url === '/api/platform/v1/tenants' &&
          request.params.get('page') === '0' &&
          request.params.get('size') === '10',
      )
      .flush({
        items,
        page: 0,
        size: 10,
        totalElements: items.length,
        totalPages: items.length ? 1 : 0,
      });
  }

  afterEach(() => controller?.verify());

  it('shows loading and then an empty state for an empty platform list', () => {
    create();
    expect(fixture.nativeElement.textContent).toContain('Ładowanie tenantów');
    flushList([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Brak tenantów');
  });

  it('renders a tenant list and opens a loaded detail projection', () => {
    create();
    flushList();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Acme Sp. z o.o.');
    fixture.componentInstance.selectTenant({ ...TENANT, status: 'ACTIVE' });
    const detail = controller.expectOne(`/api/platform/v1/tenants/${TENANT.id}`);
    detail.flush(TENANT);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Podstawowe ustawienia');
    expect(fixture.nativeElement.textContent).toContain('Zamknij tenanta');
  });

  it('creates a tenant and refreshes the current page', () => {
    create();
    flushList([]);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.openCreate();
    component.createForm.setValue({
      slug: 'beta',
      name: 'Beta Tech',
      timezone: 'Europe/Warsaw',
      locale: 'pl-PL',
    });
    component.createTenant();

    const createRequest = controller.expectOne('/api/platform/v1/tenants');
    expect(createRequest.request.method).toBe('POST');
    createRequest.flush({
      ...TENANT,
      id: '22222222-2222-4222-8222-222222222222',
      slug: 'beta',
      name: 'Beta Tech',
    });

    const reload = controller.expectOne(
      (request) =>
        request.url === '/api/platform/v1/tenants' &&
        request.params.get('page') === '0' &&
        request.params.get('size') === '10',
    );
    reload.flush({
      items: [{ ...TENANT, slug: 'beta', name: 'Beta Tech' }],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    });
    expect(component.createOpen()).toBeFalse();
    expect(component.selectedTenant()?.slug).toBe('beta');
  });

  it('requires confirmation before closing and refreshes lifecycle state', () => {
    create();
    flushList();
    fixture.componentInstance.selectTenant({ ...TENANT, status: 'ACTIVE' });
    controller.expectOne(`/api/platform/v1/tenants/${TENANT.id}`).flush(TENANT);
    fixture.detectChanges();

    fixture.componentInstance.startLifecycle('close');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ta operacja trwale zamknie tenanta');
    expect(controller.match(`/api/platform/v1/tenants/${TENANT.id}/close`).length).toBe(0);

    fixture.componentInstance.confirmClose();
    const close = controller.expectOne(`/api/platform/v1/tenants/${TENANT.id}/close`);
    expect(close.request.method).toBe('POST');
    close.flush({ ...TENANT, status: 'CLOSED', closedAt: '2026-01-03T10:00:00Z' });
    expect(fixture.componentInstance.selectedTenant()?.status).toBe('CLOSED');
  });

  it('does not request or show actions without the granular read permission', () => {
    create([]);
    expect(fixture.componentInstance.listStatus()).toBe('forbidden');
    expect(fixture.nativeElement.textContent).toContain('PLATFORM_TENANT_READ');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  it('shows a retryable list error and retries through the shared state', () => {
    create();
    const request = controller.expectOne(
      (candidate) => candidate.url === '/api/platform/v1/tenants',
    );
    request.flush(
      { status: 503, code: 'INTERNAL_ERROR', message: 'Platforma niedostępna.' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Platforma niedostępna.');

    fixture.componentInstance.retryList();
    const retry = controller.expectOne((candidate) => candidate.url === '/api/platform/v1/tenants');
    retry.flush({ items: [TENANT], page: 0, size: 10, totalElements: 1, totalPages: 1 });
  });
});
