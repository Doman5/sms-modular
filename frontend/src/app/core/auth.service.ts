import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

export interface UserSummary {
  id: string;
  email: string;
  displayName: string;
  roleId: string;
  status: 'ACTIVE' | 'DISABLED';
  mustChangePassword: boolean;
}

export interface TenantSummary {
  id: string;
  slug: string;
  name: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
  timeZone: string;
  locale: string;
}

export interface TenantContext {
  user: UserSummary;
  tenant: TenantSummary;
  permissions: string[];
  capabilities: string[];
  usage: string[];
}

export interface PlatformContext {
  id: string;
  email: string;
  permissions: string[];
  mustChangePassword: boolean;
}

interface StoredSession {
  accessToken: string;
  platform: boolean;
  mustChangePassword: boolean;
}

interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  mustChangePassword: boolean;
}

const storageKey = 'sms-modular-session';

function restoredSession(): StoredSession | null {
  if (typeof sessionStorage === 'undefined') return null;
  try {
    const value = sessionStorage.getItem(storageKey);
    if (!value) return null;
    const session = JSON.parse(value) as StoredSession;
    return session.accessToken && typeof session.platform === 'boolean' ? session : null;
  } catch {
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  readonly session = signal<StoredSession | null>(restoredSession());
  readonly tenantContext = signal<TenantContext | null>(null);
  readonly platformContext = signal<PlatformContext | null>(null);
  readonly isPlatform = computed(() => this.session()?.platform === true);
  readonly mustChangePassword = computed(() => this.session()?.mustChangePassword === true);

  login(email: string, password: string, platform: boolean): Observable<LoginResponse> {
    const url = platform ? '/api/platform/v1/auth/login' : '/api/v1/auth/login';
    return this.http.post<LoginResponse>(url, { email, password }).pipe(
      tap((response) => {
        const session = {
          accessToken: response.accessToken,
          platform,
          mustChangePassword: response.mustChangePassword,
        };
        sessionStorage.setItem(storageKey, JSON.stringify(session));
        this.session.set(session);
        this.tenantContext.set(null);
        this.platformContext.set(null);
      }),
    );
  }

  loadContext(): Observable<TenantContext | PlatformContext> {
    if (this.isPlatform()) {
      return this.http.get<PlatformContext>('/api/platform/v1/me/context').pipe(
        tap((context) => {
          this.platformContext.set(context);
          this.syncPasswordFlag(context.mustChangePassword);
        }),
      );
    }
    return this.http.get<TenantContext>('/api/v1/me/context').pipe(
      tap((context) => {
        this.tenantContext.set(context);
        this.syncPasswordFlag(context.user.mustChangePassword);
      }),
    );
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    const url = this.isPlatform()
      ? '/api/platform/v1/auth/change-password'
      : '/api/v1/auth/change-password';
    return this.http.post<void>(url, { currentPassword, newPassword });
  }

  has(permission: string): boolean {
    const context = this.isPlatform() ? this.platformContext() : this.tenantContext();
    return context?.permissions.includes(permission) ?? false;
  }

  logout(): void {
    sessionStorage.removeItem(storageKey);
    this.session.set(null);
    this.tenantContext.set(null);
    this.platformContext.set(null);
    void this.router.navigateByUrl('/login');
  }

  private syncPasswordFlag(value: boolean): void {
    const session = this.session();
    if (!session || session.mustChangePassword === value) return;
    const updated = { ...session, mustChangePassword: value };
    sessionStorage.setItem(storageKey, JSON.stringify(updated));
    this.session.set(updated);
  }
}
