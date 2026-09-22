import { routes } from './app.routes';

describe('application routing', () => {
  it('keeps the platform tenant space outside the tenant shell', () => {
    const platform = routes.find((route) => route.path === 'platform');
    const tenantShell = routes.find((route) => route.path === '');

    expect(platform).toBeTruthy();
    expect(platform?.children?.some((route) => route.path === 'tenants')).toBeTrue();
    expect(tenantShell?.children?.some((route) => route.path === 'settings')).toBeTrue();
  });

  it('protects the platform tenant route with the read permission', () => {
    const platform = routes.find((route) => route.path === 'platform');
    const tenants = platform?.children?.find((route) => route.path === 'tenants');

    expect(tenants?.data?.['permission']).toBe('PLATFORM_TENANT_READ');
    expect(tenants?.canMatch?.length).toBe(1);
  });
});
