import { Routes } from '@angular/router';
import { capabilityGuard, passwordGuard, permissionGuard, platformGuard, sessionGuard, tenantGuard } from './core/auth.guards';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/identity/login-page').then(m => m.LoginPage) },
  { path: 'change-password', loadComponent: () => import('./features/identity/password-page').then(m => m.PasswordPage), canActivate: [passwordGuard] },
  { path: 'forbidden', loadComponent: () => import('./features/identity/forbidden-page').then(m => m.ForbiddenPage) },
  { path: '', loadComponent: () => import('./features/shell/shell-page').then(m => m.ShellPage), canActivate: [sessionGuard], children: [
    { path: '', pathMatch: 'full', redirectTo: 'home' },
    { path: 'home', loadComponent: () => import('./features/home/home-page').then(m => m.HomePage) },
    { path: 'employees', loadComponent: () => import('./features/employee/employees-page').then(m => m.EmployeesPage), canActivate: [tenantGuard, permissionGuard('EMPLOYEE_READ'), capabilityGuard('EMPLOYEE_DIRECTORY')] },
    { path: 'employees/new', loadComponent: () => import('./features/employee/employees-page').then(m => m.EmployeesPage), canActivate: [tenantGuard, permissionGuard('EMPLOYEE_READ'), permissionGuard('EMPLOYEE_CREATE'), capabilityGuard('EMPLOYEE_DIRECTORY')] },
    { path: 'employees/:id', loadComponent: () => import('./features/employee/employee-detail-page').then(m => m.EmployeeDetailPage), canActivate: [tenantGuard, permissionGuard('EMPLOYEE_READ'), capabilityGuard('EMPLOYEE_DIRECTORY')] },
    { path: 'users', loadComponent: () => import('./features/identity/users-page').then(m => m.UsersPage), canActivate: [tenantGuard, permissionGuard('USER_READ')] },
    { path: 'roles', loadComponent: () => import('./features/identity/roles-page').then(m => m.RolesPage), canActivate: [tenantGuard, permissionGuard('ROLE_READ')] },
    { path: 'settings', loadComponent: () => import('./features/tenancy/settings-page').then(m => m.SettingsPage), canActivate: [tenantGuard, permissionGuard('TENANT_READ')] },
    { path: 'audit', loadComponent: () => import('./features/audit/audit-page').then(m => m.AuditPage), canActivate: [tenantGuard, permissionGuard('AUDIT_READ')] },
    { path: 'subscription', loadComponent: () => import('./features/entitlements/subscription-page').then(m => m.SubscriptionPage), canActivate: [tenantGuard, permissionGuard('SUBSCRIPTION_READ')] },
    { path: 'platform/tenants', loadComponent: () => import('./features/tenancy/platform-tenants-page').then(m => m.PlatformTenantsPage), canActivate: [platformGuard, permissionGuard('PLATFORM_TENANT_READ')] },
    { path: 'platform/tenants/:tenantId/subscription', loadComponent: () => import('./features/entitlements/subscription-page').then(m => m.SubscriptionPage), canActivate: [platformGuard, permissionGuard('PLATFORM_SUBSCRIPTION_READ')] },
    { path: 'platform/audit', loadComponent: () => import('./features/audit/audit-page').then(m => m.AuditPage), canActivate: [platformGuard, permissionGuard('PLATFORM_AUDIT_READ')] },
  ] },
  { path: '**', redirectTo: '' },
];
