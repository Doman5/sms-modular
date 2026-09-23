import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService, Subscription, SubscriptionModule } from '../../core/api.service';
import { AuthService, TenantSummary } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

const labels: Record<string, string> = {
  EMPLOYEE_DIRECTORY: 'Pracownicy', SMS_INBOUND: 'SMS odebrane',
  AI_INTERPRETATION: 'Interpretacja AI', TIME_TRACKING: 'Czas pracy',
  ABSENCE_EVENTS: 'Nieobecności', LEAVE_MANAGEMENT: 'Urlopy',
  PAYROLL: 'Rozliczenia', PROJECTS: 'Projekty', PLANNING: 'Planowanie',
  TOOL_ASSIGNMENT: 'Narzędzia',
};

const icons: Record<string, string> = {
  EMPLOYEE_DIRECTORY: 'pi-users', SMS_INBOUND: 'pi-comments', AI_INTERPRETATION: 'pi-sparkles',
  TIME_TRACKING: 'pi-clock', ABSENCE_EVENTS: 'pi-calendar', LEAVE_MANAGEMENT: 'pi-calendar-plus',
  PAYROLL: 'pi-file', PROJECTS: 'pi-folder', PLANNING: 'pi-calendar-clock', TOOL_ASSIGNMENT: 'pi-wrench',
};

@Component({
  selector: 'app-subscription-page',
  imports: [FormsModule, DatePipe, RouterLink],
  template: `
    <main class="page subscription-page">
      <div class="page-heading"><div>
        <h1>Pakiet i moduły</h1>
        <p>{{ auth.isPlatform() ? 'Konfiguracja pakietu i limitów firmy.' : 'Twój plan i dostępne moduły. W razie pytań skontaktuj się z opiekunem.' }}</p>
      </div>
      @if (auth.isPlatform()) { <a routerLink="/platform/tenants">Powrót do tenantów</a> }
      </div>
      @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
      @if (loading()) { <p class="panel" role="status">Ładowanie pakietu…</p> }
      @else if (subscription(); as plan) {
        <section class="panel overview" aria-label="Obecny plan i wykorzystanie">
          <div class="plan-summary">
            <div class="plan-title"><span class="module-icon"><i class="pi pi-box" aria-hidden="true"></i></span>
              <div><small>Twój obecny plan</small><h2>Pakiet bazowy <span class="badge good">Aktywny</span></h2>
                <p>Podstawowe funkcje systemu SMS Modular · wersja {{ plan.planVersion }}</p></div></div>
            <h3>W ramach pakietu masz:</h3>
            <ul class="base-list">@for (module of baseModules(plan); track module.key) {
              <li><i class="pi pi-check-circle" aria-hidden="true"></i>{{ label(module.key) }}
                @if (module.availability === 'PLANNED') { <small>W przygotowaniu</small> }
              </li>
            }</ul>
          </div>
          <div class="usage-summary"><h3>Wykorzystanie pakietu</h3>
            <div class="usage-tile"><div class="usage-head"><span class="module-icon small"><i class="pi pi-users" aria-hidden="true"></i></span>
              <span>Aktywni użytkownicy</span></div>
              <strong>{{ plan.usage.used }} / {{ plan.usage.limit === null ? 'bez limitu' : plan.usage.limit }}</strong>
              @if (plan.usage.limit !== null) { <div class="meter" role="progressbar" [attr.aria-valuenow]="plan.usage.used"
                  [attr.aria-valuemax]="plan.usage.limit" aria-valuemin="0"><span [style.width.%]="progress(plan)"></span></div>
                <small>Pozostało: {{ plan.usage.remaining }}</small>
              } @else { <small>Brak ograniczenia liczby użytkowników.</small> }
            </div>
            @if (auth.isPlatform() && auth.has('PLATFORM_SUBSCRIPTION_MANAGE')) {
              <button type="button" class="outline-action" (click)="startLimit(plan, 'ACTIVE_USERS')">Zmień limit użytkowników</button>
              @if (editingLimit() === 'ACTIVE_USERS') { <form class="limit-form" (ngSubmit)="saveLimit()">
                <label>Tryb limitu <select name="mode" [(ngModel)]="limitMode"><option value="INHERIT">Z planu bazowego</option>
                  <option value="UNLIMITED">Bez limitu</option><option value="FINITE">Ustalony limit</option></select></label>
                @if (limitMode === 'FINITE') { <label>Liczba użytkowników <input type="number" name="value" min="1" required [(ngModel)]="limitValue" /></label> }
                @if (limitMode !== 'INHERIT') { <label>Ważny do (opcjonalnie) <input type="datetime-local" name="expires" [(ngModel)]="limitExpires" /></label> }
                <div class="form-actions"><button class="primary" type="submit" [disabled]="saving()">Zapisz limit</button>
                  <button type="button" (click)="editingLimit.set('')">Anuluj</button></div>
              </form> }
            }
            <div class="usage-tile"><div class="usage-head"><span class="module-icon small"><i class="pi pi-id-card" aria-hidden="true"></i></span>
              <span>Aktywni pracownicy</span></div>
              <strong>{{ plan.employeeUsage.used }} / {{ plan.employeeUsage.limit === null ? 'bez limitu' : plan.employeeUsage.limit }}</strong>
              @if (plan.employeeUsage.limit !== null) { <div class="meter" role="progressbar" [attr.aria-valuenow]="plan.employeeUsage.used"
                  [attr.aria-valuemax]="plan.employeeUsage.limit" aria-valuemin="0"><span [style.width.%]="employeeProgress(plan)"></span></div>
                <small>Pozostało: {{ plan.employeeUsage.remaining }}</small>
              } @else { <small>Brak ograniczenia liczby pracowników.</small> }
            </div>
            @if (auth.isPlatform() && auth.has('PLATFORM_SUBSCRIPTION_MANAGE')) {
              <button type="button" class="outline-action" (click)="startLimit(plan, 'ACTIVE_EMPLOYEES')">Zmień limit pracowników</button>
              @if (editingLimit() === 'ACTIVE_EMPLOYEES') { <form class="limit-form" (ngSubmit)="saveLimit()">
                <label>Tryb limitu <select name="employeeMode" [(ngModel)]="limitMode"><option value="INHERIT">Z planu bazowego</option>
                  <option value="UNLIMITED">Bez limitu</option><option value="FINITE">Ustalony limit</option></select></label>
                @if (limitMode === 'FINITE') { <label>Liczba pracowników <input type="number" name="employeeValue" min="1" required [(ngModel)]="limitValue" /></label> }
                @if (limitMode !== 'INHERIT') { <label>Ważny do (opcjonalnie) <input type="datetime-local" name="employeeExpires" [(ngModel)]="limitExpires" /></label> }
                <div class="form-actions"><button class="primary" type="submit" [disabled]="saving()">Zapisz limit</button>
                  <button type="button" (click)="editingLimit.set('')">Anuluj</button></div>
              </form> }
            }
          </div>
        </section>
        <section class="addons"><div class="section-heading"><h2>Dodatkowe moduły</h2><p>Rozszerz możliwości systemu o moduły dopasowane do potrzeb firmy.</p></div>
          <div class="module-grid">@for (module of addons(plan); track module.key) {
            <article class="panel addon-card">
              <div class="card-top"><span class="module-icon"><i class="pi" [class]="'pi ' + icon(module.key)" aria-hidden="true"></i></span>
                <div><h3>{{ label(module.key) }}</h3><p>{{ description(module.key) }}</p></div>
                <span class="badge" [class.good]="moduleState(module) === 'Aktywny'" [class.muted]="moduleState(module) !== 'Aktywny'">
                  {{ moduleState(module) }}
                </span></div>
              @if (module.dependsOn) { <p class="dependency"><i class="pi pi-info-circle" aria-hidden="true"></i> Wymaga modułu {{ label(module.dependsOn) }}</p> }
              @if (module.status === 'ENABLED') { <p class="module-period">Okres: od {{ module.startsAt | date:'shortDate' }}
                  @if (module.endsAt) { do {{ module.endsAt | date:'shortDate' }} }</p> }
              @if (auth.isPlatform() && auth.has('PLATFORM_SUBSCRIPTION_MANAGE') && module.availability === 'AVAILABLE') {
                <button type="button" class="outline-action" (click)="startAddon(module)">{{ module.status === 'ENABLED' ? 'Zmień lub wyłącz' : 'Włącz moduł' }}</button>
                @if (editingAddon() === module.key) { <form class="addon-form" (ngSubmit)="saveAddon(module)">
                  <label>Start (opcjonalnie) <input type="datetime-local" name="start" [(ngModel)]="addonStart" /></label>
                  <label>Koniec (opcjonalnie) <input type="datetime-local" name="end" [(ngModel)]="addonEnd" /></label>
                  <div class="form-actions"><button class="primary" type="submit" [disabled]="saving()">Włącz</button>
                    @if (module.status === 'ENABLED') { <button type="button" [disabled]="saving()" (click)="disableAddon(module)">Wyłącz</button> }
                    <button type="button" (click)="editingAddon.set('')">Anuluj</button></div>
                </form> }
              } @else if (module.availability === 'PLANNED') { <p class="coming-soon">Moduł pojawi się po wdrożeniu jego funkcji.</p> }
            </article>
          }</div>
        </section>
      }
    </main>
  `,
  styles: `
    .subscription-page { max-width: 94rem; }
    .overview { display: grid; grid-template-columns: 1.45fr 1fr; gap: 1.3rem; }
    .plan-summary { padding-right: 1.4rem; border-right: 1px solid var(--app-border); }
    .plan-title, .card-top { display: flex; gap: 1rem; align-items: flex-start; }
    .plan-title h2 { font-size: 1.65rem; margin: .3rem 0; }
    .plan-title p, .card-top p, .section-heading p { color: var(--app-text-muted); margin: .25rem 0; }
    .module-icon { width: 3.6rem; height: 3.6rem; flex: 0 0 3.6rem; border-radius: 50%; background: #e4fbf8; color: #087b79; display: grid; place-items: center; font-size: 1.5rem; }
    .module-icon.small { width: 2.5rem; height: 2.5rem; flex-basis: 2.5rem; font-size: 1.1rem; }
    .badge { display: inline-block; border-radius: 99px; padding: .3rem .75rem; font-size: .78rem; white-space: nowrap; }
    .badge.good { background: #dcf8ec; color: #086b4d; }.badge.muted { background: #edf1f6; color: #53627a; }
    .base-list { list-style: none; padding: 0; display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: .7rem; }
    .base-list li { display: flex; flex-wrap: wrap; gap: .45rem; align-items: center; }.base-list i { color: #139b77; }
    .base-list small { color: var(--app-text-muted); }.usage-summary h3 { margin-top: 0; }
    .usage-tile { border: 1px solid var(--app-border); border-radius: 12px; padding: 1rem; display: grid; gap: .6rem; margin-top: .8rem; }
    .usage-head { display: flex; align-items: center; gap: .7rem; font-weight: 700; }.usage-tile strong { font-size: 1.45rem; }
    .usage-tile small { color: var(--app-text-muted); }.meter { height: .65rem; border-radius: 99px; background: #e3eaf2; overflow: hidden; }
    .meter span { display: block; height: 100%; background: #0a9992; border-radius: inherit; }
    .outline-action { border-color: #0a9992; color: #086c6a; width: 100%; margin-top: .8rem; font-weight: 700; }
    .section-heading h2 { margin: 0; }.module-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 1rem; margin-top: 1rem; }
    .addon-card { display: flex; flex-direction: column; gap: .8rem; }.addon-card h3 { margin: .2rem 0; }.card-top .badge { margin-left: auto; }
    .dependency { background: #eaf2ff; color: #1b4c9f; border-radius: 9px; padding: .7rem; margin: 0; }
    .module-period, .coming-soon { margin: auto 0 0; color: var(--app-text-muted); font-size: .9rem; }
    .limit-form, .addon-form { display: grid; gap: .7rem; margin-top: .8rem; }.limit-form label, .addon-form label { display: grid; gap: .3rem; }
    @media (max-width: 1050px) { .module-grid { grid-template-columns: repeat(2,minmax(0,1fr)); } }
    @media (max-width: 760px) { .overview { grid-template-columns: 1fr; }.plan-summary { border-right: 0; border-bottom: 1px solid var(--app-border); padding: 0 0 1rem; }
      .module-grid, .base-list { grid-template-columns: 1fr; }.card-top { align-items: center; }.card-top .module-icon { width: 2.6rem; height: 2.6rem; flex-basis: 2.6rem; font-size: 1.15rem; }
      .card-top .badge { font-size: .68rem; padding: .25rem .5rem; }.plan-title h2 { font-size: 1.3rem; }
    }
  `,
})
export class SubscriptionPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);
  readonly subscription = signal<Subscription | null>(null);
  readonly tenant = signal<TenantSummary | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly editingLimit = signal<'ACTIVE_USERS' | 'ACTIVE_EMPLOYEES' | ''>('');
  readonly editingAddon = signal('');
  limitMode: 'FINITE' | 'UNLIMITED' | 'INHERIT' = 'INHERIT';
  limitValue: number | null = null;
  limitExpires = '';
  addonStart = '';
  addonEnd = '';
  private readonly tenantId = this.route.snapshot.paramMap.get('tenantId');

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading.set(true);
    const request = this.tenantId ? this.api.platformSubscription(this.tenantId) : this.api.subscription();
    request.subscribe({
      next: plan => { this.subscription.set(plan); this.error.set(''); this.loading.set(false); },
      error: error => { this.error.set(problemMessage(error)); this.loading.set(false); },
    });
  }
  label(key: string): string { return labels[key] ?? key; }
  icon(key: string): string { return icons[key] ?? 'pi-box'; }
  description(key: string): string { return key === 'PLANNING' ? 'Grafiki i harmonogramy.' :
    key === 'PROJECTS' ? 'Zarządzanie projektami i zadaniami.' :
    key === 'LEAVE_MANAGEMENT' ? 'Wnioski urlopowe i akceptacje.' :
    key === 'PAYROLL' ? 'Rozliczanie czasu pracy i kosztów.' : 'Dodatkowe narzędzia administracyjne.'; }
  baseModules(plan: Subscription): SubscriptionModule[] { return plan.modules.filter(item => item.type === 'BASE'); }
  addons(plan: Subscription): SubscriptionModule[] { return plan.modules.filter(item => item.type === 'ADD_ON'); }
  progress(plan: Subscription): number { return plan.usage.limit ? Math.min(100, plan.usage.used / plan.usage.limit * 100) : 0; }
  employeeProgress(plan: Subscription): number { return plan.employeeUsage.limit ? Math.min(100, plan.employeeUsage.used / plan.employeeUsage.limit * 100) : 0; }
  moduleState(module: SubscriptionModule): string {
    if (module.availability === 'PLANNED') return 'W przygotowaniu';
    if (module.status !== 'ENABLED') return 'Nieaktywny';
    if (module.startsAt && new Date(module.startsAt).getTime() > Date.now()) return 'Zaplanowany';
    if (module.endsAt && new Date(module.endsAt).getTime() <= Date.now()) return 'Wygasł';
    return 'Aktywny';
  }
  startLimit(plan: Subscription, metric: 'ACTIVE_USERS' | 'ACTIVE_EMPLOYEES'): void {
    if (this.editingLimit() === metric) { this.editingLimit.set(''); return; }
    const override = metric === 'ACTIVE_USERS' ? plan.limitOverride : plan.employeeLimitOverride;
    this.limitMode = override.mode;
    this.limitValue = override.value;
    this.limitExpires = override.expiresAt ? this.localDate(override.expiresAt) : '';
    this.editingLimit.set(metric);
  }
  startAddon(module: SubscriptionModule): void {
    this.editingAddon.set(module.key);
    this.addonStart = module.startsAt ? this.localDate(module.startsAt) : '';
    this.addonEnd = module.endsAt ? this.localDate(module.endsAt) : '';
  }
  saveAddon(module: SubscriptionModule): void {
    if (!this.tenantId || this.saving()) return;
    this.saving.set(true);
    this.api.updateAddon(this.tenantId, module.key, true,
      this.addonStart ? new Date(this.addonStart).toISOString() : null,
      this.addonEnd ? new Date(this.addonEnd).toISOString() : null).subscribe({
        next: plan => { this.subscription.set(plan); this.editingAddon.set(''); this.saving.set(false); this.error.set(''); },
        error: error => { this.error.set(problemMessage(error)); this.saving.set(false); },
      });
  }
  disableAddon(module: SubscriptionModule): void {
    if (!this.tenantId || this.saving() || !window.confirm(`Wyłączyć moduł ${this.label(module.key)}? Dane pozostaną zachowane.`)) return;
    this.saving.set(true);
    this.api.updateAddon(this.tenantId, module.key, false, null, null).subscribe({
      next: plan => { this.subscription.set(plan); this.editingAddon.set(''); this.saving.set(false); this.error.set(''); },
      error: error => { this.error.set(problemMessage(error)); this.saving.set(false); },
    });
  }
  saveLimit(): void {
    if (!this.tenantId || this.saving()) return;
    if (this.limitMode === 'FINITE' && (!Number.isInteger(this.limitValue) || this.limitValue === null || this.limitValue < 1)) {
      this.error.set('Limit musi być dodatnią liczbą całkowitą.'); return;
    }
    this.saving.set(true);
    const request = this.editingLimit() === 'ACTIVE_EMPLOYEES' ? this.api.updateActiveEmployeeLimit.bind(this.api) : this.api.updateActiveUserLimit.bind(this.api);
    request(this.tenantId, this.limitMode,
      this.limitMode === 'FINITE' ? this.limitValue : null,
      this.limitMode === 'INHERIT' || !this.limitExpires ? null : new Date(this.limitExpires).toISOString()).subscribe({
        next: plan => { this.subscription.set(plan); this.editingLimit.set(''); this.saving.set(false); this.error.set(''); },
        error: error => { this.error.set(problemMessage(error)); this.saving.set(false); },
      });
  }
  private localDate(value: string): string {
    const date = new Date(value);
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
  }
}
