import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SESSION_STATE_PROVIDER } from '../../core/auth/session-state.provider';
import { SessionStateProvider } from '../../core/auth/auth.models';
import { problemDetailsInterceptor } from '../../core/http/problem-details.interceptor';
import { TenantSettingsPageComponent } from './tenant-settings-page.component';
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

describe('TenantSettingsPageComponent', () => {
  let fixture: ComponentFixture<TenantSettingsPageComponent>;
  let controller: HttpTestingController;

  function create(permissions: readonly string[] = ['TENANT_SETTINGS_EDIT']): void {
    const session: SessionStateProvider = {
      getSnapshot: () => ({ status: 'authenticated', userId: 'user-1', permissions }),
    };
    TestBed.configureTestingModule({
      imports: [TenantSettingsPageComponent],
      providers: [
        provideHttpClient(withInterceptors([problemDetailsInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: SESSION_STATE_PROVIDER, useValue: session },
      ],
    });
    fixture = TestBed.createComponent(TenantSettingsPageComponent);
    controller = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => controller?.verify());

  it('shows an explicit loading state before the tenant response arrives', () => {
    create();
    expect(fixture.nativeElement.textContent).toContain('Ładowanie danych firmy');
    controller.expectOne('/api/v1/tenant').flush(TENANT);
  });

  it('loads, submits settings and reports success', () => {
    create();
    controller.expectOne('/api/v1/tenant').flush(TENANT);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.settingsForm.setValue({
      name: 'Acme Polska',
      timezone: 'Europe/Warsaw',
      locale: 'pl-PL',
    });
    component.save();

    const patch = controller.expectOne('/api/v1/tenant');
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({
      name: 'Acme Polska',
      timezone: 'Europe/Warsaw',
      locale: 'pl-PL',
    });
    patch.flush({ ...TENANT, name: 'Acme Polska' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Zmiany zostały zapisane.');
  });

  it('keeps a user without edit permission in read-only mode', () => {
    create([]);
    controller.expectOne('/api/v1/tenant').flush(TENANT);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Masz dostęp tylko do odczytu');
    expect(fixture.nativeElement.querySelector('button[type="submit"]')).toBeNull();
    expect(
      (fixture.nativeElement.querySelector('#tenant-name') as HTMLInputElement).readOnly,
    ).toBeTrue();
  });

  it('maps a retryable load failure to retry and retries the request', () => {
    create();
    const initial = controller.expectOne('/api/v1/tenant');
    initial.flush(
      { status: 503, code: 'INTERNAL_ERROR', message: 'Serwer chwilowo niedostępny.' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Serwer chwilowo niedostępny.');
    const retry = fixture.nativeElement.querySelector(
      'app-retryable-error-state button',
    ) as HTMLButtonElement;
    retry.click();
    const retried = controller.expectOne('/api/v1/tenant');
    retried.flush(TENANT);
  });

  it('prevents a second settings request while the first save is pending', () => {
    create();
    controller.expectOne('/api/v1/tenant').flush(TENANT);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.settingsForm.setValue({ name: 'Acme 2', timezone: 'Europe/Warsaw', locale: 'pl-PL' });
    component.save();
    component.save();

    const requests = controller.match('/api/v1/tenant');
    expect(requests.length).toBe(1);
    requests[0].flush({ ...TENANT, name: 'Acme 2' });
  });

  it('shows field validation without sending an invalid request', () => {
    create();
    controller.expectOne('/api/v1/tenant').flush(TENANT);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.settingsForm.setValue({ name: '', timezone: 'not-a-zone', locale: '???' });
    component.save();
    fixture.detectChanges();

    expect(component.settingsForm.invalid).toBeTrue();
    expect(controller.match('/api/v1/tenant').length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Popraw zaznaczone pola');
  });
});
