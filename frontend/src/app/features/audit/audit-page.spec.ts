import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth.service';
import { AuditPage } from './audit-page';

describe('AuditPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditPage],
      providers: [provideHttpClient(), provideHttpClientTesting(),
        { provide: AuthService, useValue: { isPlatform: () => false } }],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows empty state and opens details with one action', () => {
    const fixture = TestBed.createComponent(AuditPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ładowanie zdarzeń');
    http.expectOne(request => request.url === '/api/v1/audit-logs').flush({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Brak zdarzeń');
    fixture.componentInstance.load();
    http.expectOne(request => request.url === '/api/v1/audit-logs').flush({
      content: [{ id: 'event-1', tenantId: 'tenant-1', actorType: 'SYSTEM', actorId: null,
        module: 'TENANCY', action: 'TENANT_PROVISIONED', targetType: 'TENANT', targetId: 'tenant-1',
        result: 'SUCCESS', occurredAt: '2026-09-22T12:00:00Z', correlationId: 'test-id', metadata: {} }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('td button') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.detail').textContent).toContain('test-id');
  });

  it('offers retry after a failed read', () => {
    const fixture = TestBed.createComponent(AuditPage);
    fixture.detectChanges();
    http.expectOne(request => request.url === '/api/v1/audit-logs')
      .flush({ title: 'Błąd', detail: 'Niedostępne' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    (fixture.nativeElement.querySelector('[role="alert"] button') as HTMLButtonElement).click();
    http.expectOne(request => request.url === '/api/v1/audit-logs').flush({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });
});
