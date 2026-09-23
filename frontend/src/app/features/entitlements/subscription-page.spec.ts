import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { SubscriptionPage } from './subscription-page';

const subscription = {
  tenantId: 'tenant-1', planCode: 'BASE', planVersion: 1, capabilities: [], validUntil: null,
  limitOverride: { mode: 'INHERIT', value: null, expiresAt: null },
  usage: { metric: 'ACTIVE_USERS', used: 1, mode: 'UNLIMITED', limit: null, remaining: null },
  employeeUsage: { metric: 'ACTIVE_EMPLOYEES', used: 0, mode: 'UNLIMITED', limit: null, remaining: null },
  employeeLimitOverride: { mode: 'INHERIT', value: null, expiresAt: null },
  modules: [{ key: 'EMPLOYEE_DIRECTORY', type: 'BASE', availability: 'PLANNED', status: 'INCLUDED', startsAt: null, endsAt: null, dependsOn: null },
    { key: 'PROJECTS', type: 'ADD_ON', availability: 'PLANNED', status: 'DISABLED', startsAt: null, endsAt: null, dependsOn: null }],
};

describe('SubscriptionPage', () => {
  let http: HttpTestingController;

  afterEach(() => http.verify());

  it('shows plan, usage and planned modules without edit actions for tenant', async () => {
    await TestBed.configureTestingModule({ imports: [SubscriptionPage], providers: [
      provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: { isPlatform: () => false, has: () => false } },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({}) } } },
    ] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SubscriptionPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ładowanie pakietu');
    http.expectOne('/api/v1/subscription').flush(subscription);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Pakiet bazowy');
    expect(fixture.nativeElement.textContent).toContain('bez limitu');
    expect(fixture.nativeElement.textContent).toContain('W przygotowaniu');
    expect(fixture.nativeElement.textContent).not.toContain('Zmień limit użytkowników');
  });

  it('lets the platform operator set a finite active-user limit', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({ imports: [SubscriptionPage], providers: [
      provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: { isPlatform: () => true, has: () => true } },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ tenantId: 'tenant-1' }) } } },
    ] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SubscriptionPage);
    fixture.detectChanges();
    http.expectOne('/api/platform/v1/tenants/tenant-1/subscription').flush(subscription);
    fixture.detectChanges();
    fixture.componentInstance.limitMode = 'FINITE';
    fixture.componentInstance.limitValue = 5;
    fixture.componentInstance.saveLimit();
    const request = http.expectOne('/api/platform/v1/tenants/tenant-1/subscription/limits/ACTIVE_USERS');
    expect(request.request.body).toEqual({ mode: 'FINITE', value: 5, expiresAt: null });
    request.flush({ ...subscription, usage: { metric: 'ACTIVE_USERS', used: 1, mode: 'FINITE', limit: 5, remaining: 4 } });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('1 / 5');
  });
});
