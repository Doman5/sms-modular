import { Route, UrlTree, provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { SESSION_STATE_PROVIDER } from './session-state.provider';
import { permissionGuard, sessionGuard } from './auth.guards';
import { SessionSnapshot, SessionStateProvider } from './auth.models';

describe('session and permission guards', () => {
  const route = (data: Route['data'] = {}): Route => ({ data });

  function configure(snapshot: SessionSnapshot): void {
    const provider: SessionStateProvider = { getSnapshot: () => snapshot };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SESSION_STATE_PROVIDER, useValue: provider }],
    });
  }

  it('leaves an unknown greenfield session reachable', () => {
    configure({ status: 'unknown', permissions: [] });

    expect(TestBed.runInInjectionContext(() => sessionGuard(route(), []))).toBeTrue();
  });

  it('rejects a known unauthenticated session', () => {
    configure({ status: 'unauthenticated', permissions: [] });

    const result = TestBed.runInInjectionContext(() => sessionGuard(route(), []));
    expect(result).toEqual(jasmine.any(UrlTree));
    expect((result as UrlTree).toString()).toBe('/forbidden');
  });

  it('checks route permission only after session context is resolved', () => {
    configure({ status: 'authenticated', userId: 'user-1', permissions: ['EMPLOYEE_READ'] });

    expect(
      TestBed.runInInjectionContext(() =>
        permissionGuard(route({ permission: 'EMPLOYEE_READ' }), []),
      ),
    ).toBeTrue();
    const result = TestBed.runInInjectionContext(() =>
      permissionGuard(route({ permission: 'EMPLOYEE_WRITE' }), []),
    );
    expect(result).toEqual(jasmine.any(UrlTree));
    expect((result as UrlTree).toString()).toBe('/forbidden');
  });

  it('does not block a route while permission context is unknown', () => {
    configure({ status: 'unknown', permissions: [] });

    expect(
      TestBed.runInInjectionContext(() =>
        permissionGuard(route({ permission: 'EMPLOYEE_READ' }), []),
      ),
    ).toBeTrue();
  });
});
