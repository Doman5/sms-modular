import { Routes } from '@angular/router';
import { permissionGuard, sessionGuard } from '../../core/auth/auth.guards';

export const AUDIT_ROUTES: Routes = [
  {
    path: '',
    canMatch: [sessionGuard, permissionGuard],
    data: { permission: 'AUDIT_READ' },
    loadComponent: () =>
      import('./audit-page.component').then((module) => module.AuditPageComponent),
  },
];
