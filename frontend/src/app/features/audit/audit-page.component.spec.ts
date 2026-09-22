import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SESSION_STATE_PROVIDER } from '../../core/auth/session-state.provider';
import { SessionStateProvider } from '../../core/auth/auth.models';
import { problemDetailsInterceptor } from '../../core/http/problem-details.interceptor';
import { AuditPageComponent } from './audit-page.component';
import { AuditLogResponseDto } from './audit.models';

const ENTRY: AuditLogResponseDto = {
  id: '11111111-1111-4111-8111-111111111111',
  tenantId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  actorType: 'USER',
  actorId: '22222222-2222-4222-8222-222222222222',
  module: 'TENANCY',
  action: 'SETTINGS_UPDATED',
  subjectType: 'TENANT',
  subjectId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  outcome: 'SUCCESS',
  occurredAt: '2026-01-01T10:00:00Z',
  correlationId: 'audit-ui-1',
  metadata: { operation: 'SETTINGS_UPDATED' },
};

describe('AuditPageComponent', () => {
  let fixture: ComponentFixture<AuditPageComponent>;
  let controller: HttpTestingController;

  function create(permissions: readonly string[] = ['AUDIT_READ']): void {
    const session: SessionStateProvider = {
      getSnapshot: () => ({ status: 'authenticated', userId: 'user-1', permissions }),
    };
    TestBed.configureTestingModule({
      imports: [AuditPageComponent],
      providers: [
        provideHttpClient(withInterceptors([problemDetailsInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: SESSION_STATE_PROVIDER, useValue: session },
      ],
    });
    fixture = TestBed.createComponent(AuditPageComponent);
    controller = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  function page(items: readonly AuditLogResponseDto[] = [ENTRY]) {
    return {
      items,
      page: 0,
      size: 25,
      totalElements: items.length,
      totalPages: items.length === 0 ? 0 : 1,
    };
  }

  afterEach(() => controller?.verify());

  it('shows loading, then displays rows and safe details without raw JSON', () => {
    create();
    expect(fixture.nativeElement.textContent).toContain('Ładowanie wpisów audytu');
    controller.expectOne((request) => request.url === '/api/v1/audit-logs').flush(page());
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('SETTINGS_UPDATED');
    const details = fixture.nativeElement.querySelector('.details-button') as HTMLButtonElement;
    details.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('audit-ui-1');
    expect(fixture.nativeElement.textContent).toContain('operation');
    expect(fixture.nativeElement.textContent).not.toContain('"payload"');
  });

  it('shows an empty state when the server returns no rows', () => {
    create();
    controller.expectOne((request) => request.url === '/api/v1/audit-logs').flush(page([]));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Brak wpisów audytu');
  });

  it('maps forbidden responses to an explicit forbidden state', () => {
    create();
    controller
      .expectOne((request) => request.url === '/api/v1/audit-logs')
      .flush(
        { status: 403, code: 'FORBIDDEN', message: 'Brak dostępu.' },
        { status: 403, statusText: 'Forbidden' },
      );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Brak dostępu do audytu');
  });

  it('offers retry for a retryable error', () => {
    create();
    controller
      .expectOne((request) => request.url === '/api/v1/audit-logs')
      .flush(
        { status: 503, code: 'INTERNAL_ERROR', message: 'Spróbuj ponownie.' },
        { status: 503, statusText: 'Unavailable' },
      );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Spróbuj ponownie.');
    (
      fixture.nativeElement.querySelector('app-retryable-error-state button') as HTMLButtonElement
    ).click();
    controller.expectOne((request) => request.url === '/api/v1/audit-logs').flush(page());
  });

  it('keeps the permission check in the feature and does not call the API when denied', () => {
    create([]);
    expect(fixture.nativeElement.textContent).toContain('Brak dostępu do audytu');
    expect(controller.match('/api/v1/audit-logs').length).toBe(0);
  });

  it('sends filters and server-side pagination parameters', () => {
    create();
    controller
      .expectOne((request) => request.url === '/api/v1/audit-logs')
      .flush({
        ...page([ENTRY]),
        totalElements: 26,
        totalPages: 2,
      });
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.filterForm.patchValue({ module: 'TENANCY' });
    component.submitFilters();
    const filtered = controller.expectOne((request) => request.url === '/api/v1/audit-logs');
    expect(filtered.request.params.get('module')).toBe('TENANCY');
    expect(filtered.request.params.get('page')).toBe('0');
    filtered.flush({ ...page([ENTRY]), totalElements: 26, totalPages: 2 });
    fixture.detectChanges();

    (
      fixture.nativeElement.querySelector(
        'app-pagination button[aria-label="Następna strona"]',
      ) as HTMLButtonElement
    ).click();
    const next = controller.expectOne((request) => request.url === '/api/v1/audit-logs');
    expect(next.request.params.get('page')).toBe('1');
    next.flush({ ...page([ENTRY]), page: 1, totalElements: 26, totalPages: 2 });
  });
});
