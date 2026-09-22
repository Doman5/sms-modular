import { TestBed } from '@angular/core/testing';
import { Route, UrlTree, provideRouter } from '@angular/router';
import { CapabilityKey, EntitlementSnapshot, EntitlementStateProvider } from './entitlement.models';
import { capabilityGuard } from './entitlement.guards';
import { ENTITLEMENT_STATE_PROVIDER } from './entitlement-state.provider';

describe('capabilityGuard', () => {
  const route = (capability: string): Route => ({ data: { capability } });

  function configure(snapshot: EntitlementSnapshot): void {
    const provider: EntitlementStateProvider = { getSnapshot: () => snapshot };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ENTITLEMENT_STATE_PROVIDER, useValue: provider }],
    });
  }

  it('keeps a route reachable until entitlement context is resolved', () => {
    configure({ status: 'unknown', capabilities: [] });

    expect(TestBed.runInInjectionContext(() => capabilityGuard(route('PROJECTS'), []))).toBeTrue();
  });

  it('allows an enabled capability and rejects a missing capability', () => {
    configure({ status: 'resolved', capabilities: ['PROJECTS' as CapabilityKey] });

    expect(TestBed.runInInjectionContext(() => capabilityGuard(route('PROJECTS'), []))).toBeTrue();
    const result = TestBed.runInInjectionContext(() => capabilityGuard(route('PAYROLL'), []));
    expect(result).toEqual(jasmine.any(UrlTree));
    expect((result as UrlTree).toString()).toBe('/forbidden');
  });
});
