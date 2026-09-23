import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService, TenantSummary } from '../../core/auth.service';
import { PlatformTenantsPage } from './platform-tenants-page';

describe('PlatformTenantsPage', () => {
  it('shows tenant details without subscription or write actions to read-only operators', async () => {
    await TestBed.configureTestingModule({ imports: [PlatformTenantsPage], providers: [
      provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: { has: (permission: string) => permission === 'PLATFORM_TENANT_READ' } },
    ] }).compileComponents();
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PlatformTenantsPage);
    fixture.detectChanges();
    const tenant = { id: 'tenant-1', slug: 'demo', name: 'Firma Demo', status: 'ACTIVE',
      timeZone: 'Europe/Warsaw', locale: 'pl-PL', createdAt: '2026-09-23T00:00:00Z' };
    http.expectOne('/api/platform/v1/tenants?page=0&size=20').flush({ content: [tenant], totalPages: 1 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Dodaj tenanta');
    fixture.componentInstance.select(tenant as TenantSummary);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.detail-panel').textContent).toContain('Firma Demo');
    expect(fixture.nativeElement.textContent).not.toContain('Zawieś tenanta');
    http.verify();
  });
});
