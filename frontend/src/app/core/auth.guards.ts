import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';

export const sessionGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.session()) return router.createUrlTree(['/login']);
  return auth.loadContext().pipe(
    map(() => auth.mustChangePassword() ? router.createUrlTree(['/change-password']) : true),
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};

export const passwordGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.session() ? true : router.createUrlTree(['/login']);
};

export const permissionGuard = (permission: string): CanActivateFn => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.has(permission) ? true : router.createUrlTree(['/forbidden']);
};

export const platformGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isPlatform() ? true : router.createUrlTree(['/forbidden']);
};

export const tenantGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isPlatform() ? router.createUrlTree(['/forbidden']) : true;
};
