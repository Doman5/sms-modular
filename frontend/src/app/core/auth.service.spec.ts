import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('stores a tenant token and sends it only to secured API requests', () => {
    auth.login('admin@example.test', 'temporary-password', false).subscribe();
    const login = http.expectOne('/api/v1/auth/login');
    expect(login.request.headers.has('Authorization')).toBeFalse();
    login.flush({ accessToken: 'token-value', tokenType: 'Bearer', expiresAt: '2026-01-01T00:00:00Z', mustChangePassword: false });

    auth.loadContext().subscribe();
    const context = http.expectOne('/api/v1/me/context');
    expect(context.request.headers.get('Authorization')).toBe('Bearer token-value');
    context.flush({
      user: { id: 'u', email: 'admin@example.test', displayName: 'Admin', roleId: 'r', status: 'ACTIVE', mustChangePassword: false },
      tenant: { id: 't', slug: 'tenant', name: 'Tenant', status: 'ACTIVE', timeZone: 'Europe/Warsaw', locale: 'pl-PL' },
      permissions: ['USER_READ'], capabilities: [], usage: [],
    });
    expect(auth.has('USER_READ')).toBeTrue();
    expect(auth.session()?.accessToken).toBe('token-value');
  });

  it('uses a separate platform login path', () => {
    auth.login('platform@example.test', 'password', true).subscribe();
    http.expectOne('/api/platform/v1/auth/login').flush({
      accessToken: 'platform-token', tokenType: 'Bearer', expiresAt: '2026-01-01T00:00:00Z', mustChangePassword: true,
    });
    expect(auth.isPlatform()).toBeTrue();
    expect(auth.mustChangePassword()).toBeTrue();
  });
});
