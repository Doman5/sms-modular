import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-shell-page',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="shell">
      <aside class="sidebar">
        <a class="brand" routerLink="/">SMS Modular</a>
        <nav aria-label="Menu główne">
          @if (auth.isPlatform()) {
            <a routerLink="/platform/tenants" routerLinkActive="active">Tenanci</a>
            @if (auth.has('PLATFORM_AUDIT_READ')) { <a routerLink="/platform/audit" routerLinkActive="active">Dziennik audytu</a> }
          } @else {
            @if (auth.has('USER_READ')) { <a routerLink="/users" routerLinkActive="active">Użytkownicy</a> }
            @if (auth.has('ROLE_READ')) { <a routerLink="/roles" routerLinkActive="active">Role i uprawnienia</a> }
            @if (auth.has('TENANT_READ')) { <a routerLink="/settings" routerLinkActive="active">Ustawienia firmy</a> }
            @if (auth.has('AUDIT_READ')) { <a routerLink="/audit" routerLinkActive="active">Dziennik audytu</a> }
          }
        </nav>
        <button type="button" (click)="auth.logout()">Wyloguj</button>
      </aside>
      <div class="workspace">
        <header class="topbar">
          <span>{{ auth.isPlatform() ? 'Panel platformy' : auth.tenantContext()?.tenant?.name }}</span>
          <span>{{ auth.isPlatform() ? auth.platformContext()?.email : auth.tenantContext()?.user?.displayName }}</span>
        </header>
        <router-outlet />
      </div>
    </div>
  `,
})
export class ShellPage {
  readonly auth = inject(AuthService);
}
