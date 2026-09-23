import { Component, inject, signal } from '@angular/core';
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
            <a routerLink="/platform/tenants" routerLinkActive="active"><i class="pi pi-building" aria-hidden="true"></i>Tenanci</a>
            @if (auth.has('PLATFORM_AUDIT_READ')) { <a routerLink="/platform/audit" routerLinkActive="active"><i class="pi pi-file" aria-hidden="true"></i>Dziennik audytu</a> }
          } @else {
            <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"><i class="pi pi-home" aria-hidden="true"></i>Pulpit</a>
            @if (auth.has('EMPLOYEE_READ') && auth.hasCapability('EMPLOYEE_DIRECTORY')) { <a routerLink="/employees" routerLinkActive="active"><i class="pi pi-users" aria-hidden="true"></i>Pracownicy</a> }
            @if (auth.has('TIME_READ') && auth.hasCapability('TIME_TRACKING')) { <a routerLink="/time" routerLinkActive="active"><i class="pi pi-clock" aria-hidden="true"></i>Czas pracy</a> }
            @if (auth.has('ABSENCE_READ') && auth.hasCapability('ABSENCE_EVENTS')) { <a routerLink="/absence-days" routerLinkActive="active"><i class="pi pi-calendar-times" aria-hidden="true"></i>Braki obecności</a> }
            @if (auth.has('USER_READ')) { <a routerLink="/users" routerLinkActive="active"><i class="pi pi-users" aria-hidden="true"></i>Użytkownicy</a> }
            @if (auth.has('ROLE_READ')) { <a routerLink="/roles" routerLinkActive="active"><i class="pi pi-shield" aria-hidden="true"></i>Role i uprawnienia</a> }
            @if (auth.has('TENANT_READ')) { <a routerLink="/settings" routerLinkActive="active"><i class="pi pi-cog" aria-hidden="true"></i>Ustawienia firmy</a> }
            @if (auth.has('AUDIT_READ')) { <a routerLink="/audit" routerLinkActive="active"><i class="pi pi-file" aria-hidden="true"></i>Dziennik audytu</a> }
            @if (auth.has('SUBSCRIPTION_READ')) { <a routerLink="/subscription" routerLinkActive="active"><i class="pi pi-box" aria-hidden="true"></i>Pakiet i moduły</a> }
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
      <nav class="mobile-nav" aria-label="Menu mobilne">
        <a routerLink="/" (click)="moreOpen.set(false)"><i class="pi pi-home" aria-hidden="true"></i>Pulpit</a>
        @if (auth.isPlatform()) {
          <a routerLink="/platform/tenants" (click)="moreOpen.set(false)"><i class="pi pi-building" aria-hidden="true"></i>Tenanci</a>
          @if (auth.has('PLATFORM_AUDIT_READ')) { <a routerLink="/platform/audit" (click)="moreOpen.set(false)"><i class="pi pi-file" aria-hidden="true"></i>Audyt</a> }
        } @else {
          @if (auth.has('EMPLOYEE_READ') && auth.hasCapability('EMPLOYEE_DIRECTORY')) { <a routerLink="/employees" (click)="moreOpen.set(false)"><i class="pi pi-users" aria-hidden="true"></i>Pracownicy</a> }
          @if (auth.has('USER_READ')) { <a routerLink="/users" (click)="moreOpen.set(false)"><i class="pi pi-users" aria-hidden="true"></i>Użytkownicy</a> }
          @if (auth.has('SUBSCRIPTION_READ')) { <a routerLink="/subscription" (click)="moreOpen.set(false)"><i class="pi pi-box" aria-hidden="true"></i>Pakiet</a> }
        }
        <button type="button" (click)="moreOpen.set(!moreOpen())" [attr.aria-expanded]="moreOpen()"><i class="pi pi-ellipsis-h" aria-hidden="true"></i>Więcej</button>
      </nav>
      @if (moreOpen()) { <nav class="mobile-more" aria-label="Więcej opcji">
        @if (!auth.isPlatform()) {
          @if (auth.has('TIME_READ') && auth.hasCapability('TIME_TRACKING')) { <a routerLink="/time" (click)="moreOpen.set(false)">Czas pracy</a> }
          @if (auth.has('ABSENCE_READ') && auth.hasCapability('ABSENCE_EVENTS')) { <a routerLink="/absence-days" (click)="moreOpen.set(false)">Braki obecności</a> }
          @if (auth.has('ROLE_READ')) { <a routerLink="/roles" (click)="moreOpen.set(false)">Role i uprawnienia</a> }
          @if (auth.has('TENANT_READ')) { <a routerLink="/settings" (click)="moreOpen.set(false)">Ustawienia firmy</a> }
          @if (auth.has('AUDIT_READ')) { <a routerLink="/audit" (click)="moreOpen.set(false)">Dziennik audytu</a> }
        }
        <button type="button" (click)="auth.logout()">Wyloguj</button>
      </nav> }
    </div>
  `,
  styles: `
    .mobile-nav, .mobile-more { display: none; }
    @media (max-width: 760px) {
      .mobile-nav { position: fixed; z-index: 30; bottom: 0; left: 0; right: 0; display: flex; justify-content: space-around; gap: .2rem; padding: .5rem .4rem max(.5rem, env(safe-area-inset-bottom)); background: #fff; border-top: 1px solid var(--app-border); box-shadow: 0 -6px 22px #142b4318; }
      .mobile-nav a, .mobile-nav button { border: 0; background: transparent; color: #34445d; text-decoration: none; display: grid; place-items: center; gap: .2rem; padding: .3rem; font-size: .7rem; min-width: 3.5rem; }
      .mobile-nav i { font-size: 1.25rem; }
      .mobile-more { display: grid; position: fixed; z-index: 29; bottom: 4.8rem; right: .5rem; left: .5rem; padding: .8rem; background: #fff; border: 1px solid var(--app-border); border-radius: 14px; box-shadow: var(--app-shadow); }
      .mobile-more a, .mobile-more button { text-decoration: none; text-align: left; padding: .75rem; border: 0; background: transparent; color: var(--app-text); }
    }
  `,
})
export class ShellPage {
  readonly auth = inject(AuthService);
  readonly moreOpen = signal(false);
}
