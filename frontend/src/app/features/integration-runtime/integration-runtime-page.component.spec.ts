import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SESSION_STATE_PROVIDER } from '../../core/auth/session-state.provider';
import { SessionStateProvider } from '../../core/auth/auth.models';
import { problemDetailsInterceptor } from '../../core/http/problem-details.interceptor';
import { IntegrationRuntimePageComponent } from './integration-runtime-page.component';
import { DeadLetterResponseDto } from './integration-runtime.models';

const TENANT_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const ENTRY: DeadLetterResponseDto = {
  id: '11111111-1111-4111-8111-111111111111',
  tenantId: TENANT_ID,
  topic: 'TENANT.CREATED',
  aggregateType: 'TENANT',
  aggregateId: '22222222-2222-4222-8222-222222222222',
  payloadVersion: 1,
  status: 'DEAD_LETTER',
  attempt: 8,
  availableAt: '2026-01-01T10:00:00Z',
  correlationId: 'ir-ui-1',
  idempotencyKey: 'idempotency-1',
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:01:00Z',
  errorCode: 'PROVIDER_UNAVAILABLE',
};

describe('IntegrationRuntimePageComponent', () => {
  let fixture: ComponentFixture<IntegrationRuntimePageComponent>;
  let controller: HttpTestingController;

  function create(
    permissions: readonly string[] = ['PLATFORM_INTEGRATION_READ', 'PLATFORM_INTEGRATION_RETRY'],
  ): void {
    const session: SessionStateProvider = {
      getSnapshot: () => ({ status: 'authenticated', userId: 'platform-1', permissions }),
    };
    TestBed.configureTestingModule({
      imports: [IntegrationRuntimePageComponent],
      providers: [
        provideHttpClient(withInterceptors([problemDetailsInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: SESSION_STATE_PROVIDER, useValue: session },
      ],
    });
    fixture = TestBed.createComponent(IntegrationRuntimePageComponent);
    controller = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  function submit(): void {
    fixture.componentInstance.form.controls.tenantId.setValue(TENANT_ID);
    fixture.componentInstance.submit();
    fixture.detectChanges();
  }

  function flushPage(items: readonly DeadLetterResponseDto[] = [ENTRY]): void {
    controller
      .expectOne(
        (request) =>
          request.url === '/api/platform/v1/integration-runtime/dead-letters' &&
          request.params.get('tenantId') === TENANT_ID &&
          request.params.get('page') === '0' &&
          request.params.get('size') === '25',
      )
      .flush({
        items,
        page: 0,
        size: 25,
        totalElements: items.length,
        totalPages: items.length ? 1 : 0,
      });
  }

  afterEach(() => controller?.verify());

  it('shows loading and empty states for a tenant with no dead letters', () => {
    create();
    submit();
    expect(fixture.nativeElement.textContent).toContain('Pobieranie dead letters');
    flushPage([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Brak dead letters');
  });

  it('maps a missing permission to an explicit forbidden state', () => {
    create([]);
    expect(fixture.nativeElement.textContent).toContain('PLATFORM_INTEGRATION_READ');
    expect(controller.match('/api/platform/v1/integration-runtime/dead-letters').length).toBe(0);
  });

  it('maps a server failure to an explicit error state', () => {
    create();
    submit();
    controller
      .expectOne((request) => request.url === '/api/platform/v1/integration-runtime/dead-letters')
      .flush(
        { status: 503, code: 'INTERNAL_ERROR', message: 'Runtime niedostępny.' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Spróbuj ponownie');
  });

  it('protects retry from double submit and never renders the raw payload', () => {
    create();
    submit();
    flushPage();
    fixture.detectChanges();

    const topic = fixture.nativeElement.querySelector('.link-button') as HTMLButtonElement;
    topic.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Surowy payload nie jest dostępny');
    expect(fixture.nativeElement.textContent).not.toContain('secret-payload');

    const retry = fixture.nativeElement.querySelector('.action-button') as HTMLButtonElement;
    retry.click();
    retry.click();
    const requests = controller.match(
      (request) =>
        request.url === `/api/platform/v1/integration-runtime/dead-letters/${ENTRY.id}/retry`,
    );
    expect(requests.length).toBe(1);
    requests[0].flush({ ...ENTRY, status: 'PENDING', attempt: 0 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ponowienie zapisane');
    expect(
      fixture.nativeElement.querySelector('.action-button')?.hasAttribute('disabled'),
    ).toBeTrue();
  });

  it('offers a retryable reload action after a transient error', () => {
    create();
    submit();
    controller
      .expectOne((request) => request.url === '/api/platform/v1/integration-runtime/dead-letters')
      .flush(
        { status: 503, code: 'NETWORK_ERROR', message: 'Sieć niedostępna.' },
        { status: 503, statusText: 'Unavailable' },
      );
    fixture.detectChanges();
    (
      fixture.nativeElement.querySelector('app-retryable-error-state button') as HTMLButtonElement
    ).click();
    flushPage([ENTRY]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('TENANT.CREATED');
  });
});
