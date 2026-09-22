import { Routes } from '@angular/router';
import { passwordGuard, permissionGuard, platformGuard, sessionGuard, tenantGuard } from './core/auth.guards';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/identity/login-page').then(m => m.LoginPage) },
  { path: 'change-password', loadComponent: () => import('./features/identity/password-page').then(m => m.PasswordPage), canActivate: [passwordGuard] },
  { path: 'forbidden', loadComponent: () => import('./features/identity/forbidden-page').then(m => m.ForbiddenPage) },
  { path: '', loadComponent: () => import('./features/shell/shell-page').then(m => m.ShellPage), canActivate: [sessionGuard], children: [
    { path: '', pathMatch: 'full', redirectTo: 'home' },
    { path: 'home', loadComponent: () => import('./features/home/home-page').then(m => m.HomePage) },
    { path: 'users', loadComponent: () => import('./features/identity/users-page').then(m => m.UsersPage), canActivate: [tenantGuard, permissionGuard('USER_READ')] },
    { path: 'roles', loadComponent: () => import('./features/identity/roles-page').then(m => m.RolesPage), canActivate: [tenantGuard, permissionGuard('ROLE_READ')] },
    { path: 'settings', loadComponent: () => import('./features/tenancy/settings-page').then(m => m.SettingsPage), canActivate: [tenantGuard, permissionGuard('TENANT_READ')] },
    { path: 'audit', loadComponent: () => import('./features/audit/audit-page').then(m => m.AuditPage), canActivate: [tenantGuard, permissionGuard('AUDIT_READ')] },
    { path: 'platform/tenants', loadComponent: () => import('./features/tenancy/platform-tenants-page').then(m => m.PlatformTenantsPage), canActivate: [platformGuard, permissionGuard('PLATFORM_TENANT_READ')] },
    { path: 'platform/audit', loadComponent: () => import('./features/audit/audit-page').then(m => m.AuditPage), canActivate: [platformGuard, permissionGuard('PLATFORM_AUDIT_READ')] },
  ] },
  { path: '**', redirectTo: '' },
];
