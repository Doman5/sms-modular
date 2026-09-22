import { Routes } from '@angular/router';
import { permissionGuard, sessionGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: 'forbidden',
    loadComponent: () =>
      import('./features/access/forbidden-page.component').then(
        (module) => module.ForbiddenPageComponent,
      ),
  },
  {
    path: 'platform',
    loadComponent: () =>
      import('./features/tenancy/platform-shell.component').then(
        (module) => module.PlatformShellComponent,
      ),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tenants' },
      {
        path: 'tenants',
        canMatch: [permissionGuard],
        data: { permission: 'PLATFORM_TENANT_READ' },
        loadComponent: () =>
          import('./features/tenancy/platform-tenants-page.component').then(
            (module) => module.PlatformTenantsPageComponent,
          ),
      },
      {
        path: 'integration-runtime',
        canMatch: [permissionGuard],
        data: { permission: 'PLATFORM_INTEGRATION_READ' },
        loadComponent: () =>
          import('./features/integration-runtime/integration-runtime-page.component').then(
            (module) => module.IntegrationRuntimePageComponent,
          ),
      },
    ],
  },
  {
    path: '',
    loadComponent: () =>
      import('./features/shell/shell.component').then((module) => module.ShellComponent),
    children: [
      {
        path: '',
        canMatch: [sessionGuard],
        loadComponent: () =>
          import('./features/home/home-page.component').then((module) => module.HomePageComponent),
      },
      {
        path: 'settings',
        canMatch: [sessionGuard],
        loadComponent: () =>
          import('./features/tenancy/tenant-settings-page.component').then(
            (module) => module.TenantSettingsPageComponent,
          ),
      },
      {
        path: 'audit',
        loadChildren: () =>
          import('./features/audit/audit.routes').then((module) => module.AUDIT_ROUTES),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
