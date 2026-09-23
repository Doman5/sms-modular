import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, Subscription } from '../../core/api.service';
import { AuthService, TenantSummary } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

type TenantAction = 'suspend' | 'activate' | 'close';

@Component({
  selector: 'app-platform-tenants-page',
  imports: [FormsModule, DatePipe, RouterLink],
  template: `
    <main class="page platform-tenants">
      <div class="page-heading"><div><h1>Tenanci</h1><p>Zarządzaj tenantami platformy. Przeglądaj statusy, plany i użycie.</p></div>
        @if (auth.has('PLATFORM_TENANT_MANAGE')) { <button class="primary add-button" type="button" (click)="creating.set(true)"><i class="pi pi-user-plus" aria-hidden="true"></i> Dodaj tenanta</button> }
      </div>
      @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
      @if (temporaryPassword()) { <div class="notice" role="status"><strong>Hasło pierwszego administratora — skopiuj teraz:</strong>
        <code>{{ temporaryPassword() }}</code><button type="button" (click)="temporaryPassword.set('')">Zamknij</button></div> }
      @if (creating()) { <form class="panel form-grid create-form" (ngSubmit)="create()"><div class="page-heading"><h2>Nowa firma</h2>
        <button type="button" (click)="creating.set(false)" aria-label="Zamknij formularz">✕</button></div>
        <label>Identyfikator <input name="slug" [(ngModel)]="slug" required pattern="[a-z0-9-]+" /></label>
        <label>Nazwa <input name="name" [(ngModel)]="name" required /></label>
        <label>Strefa czasowa <input name="zone" [(ngModel)]="timeZone" required /></label>
        <label>Język <input name="locale" [(ngModel)]="locale" required /></label>
        <label>E-mail administratora <input type="email" name="email" [(ngModel)]="adminEmail" required /></label>
        <label>Imię i nazwisko administratora <input name="adminName" [(ngModel)]="adminDisplayName" required /></label>
        <div class="form-actions"><button class="primary" type="submit" [disabled]="saving()">Utwórz firmę</button>
          <button type="button" (click)="creating.set(false)">Anuluj</button></div>
      </form> }
      <form class="search-row" (ngSubmit)="applyFilters()"><label class="search-box"><span class="sr-only">Szukaj tenanta</span>
        <i class="pi pi-search" aria-hidden="true"></i><input name="search" [(ngModel)]="search" placeholder="Szukaj tenanta (nazwa, slug…)" /></label>
        <label><span class="sr-only">Status tenanta</span><select name="status" [(ngModel)]="statusFilter" (change)="applyFilters()">
          <option value="">Wszystkie statusy</option><option value="ACTIVE">Aktywne</option>
          <option value="SUSPENDED">Zawieszone</option><option value="CLOSED">Zamknięte</option></select></label>
        <button type="submit">Szukaj</button>
      </form>
      <div class="content-grid" [class.with-detail]="selected()">
        <section class="panel results" aria-label="Lista tenantów">
          @if (loading()) { <p role="status">Ładowanie tenantów…</p> }
          @else if (tenants().length === 0) { <p>Brak tenantów dla wybranych filtrów.</p> }
          @else { <div class="table-wrap"><table><thead><tr><th>Firma / organizacja</th><th>Slug</th><th>Status</th><th>Plan</th><th>Utworzono</th><th></th></tr></thead>
            <tbody>@for (tenant of tenants(); track tenant.id) {
              <tr [class.selected]="selected()?.id === tenant.id"><td><button class="tenant-name" type="button" (click)="select(tenant)">
                <span class="avatar">{{ tenant.name.slice(0,1).toUpperCase() }}</span><strong>{{ tenant.name }}</strong></button></td>
                <td>{{ tenant.slug }}</td><td><span class="status-badge" [class.active]="tenant.status === 'ACTIVE'"
                  [class.suspended]="tenant.status === 'SUSPENDED'" [class.closed]="tenant.status === 'CLOSED'">{{ statusLabel(tenant.status) }}</span></td>
                <td>Pakiet bazowy</td><td>{{ tenant.createdAt | date:'shortDate' }}</td>
                <td><button type="button" class="row-open" [attr.aria-label]="'Szczegóły ' + tenant.name" (click)="select(tenant)">›</button></td></tr>
            }</tbody></table></div> }
          <div class="mobile-cards">@for (tenant of tenants(); track tenant.id) { <button class="tenant-card" type="button" (click)="select(tenant)">
            <span class="avatar">{{ tenant.name.slice(0,1).toUpperCase() }}</span><span class="tenant-card-main"><strong>{{ tenant.name }}</strong>
              <small>{{ tenant.slug }} · Pakiet bazowy</small><span class="status-badge" [class.active]="tenant.status === 'ACTIVE'"
                [class.suspended]="tenant.status === 'SUSPENDED'" [class.closed]="tenant.status === 'CLOSED'">{{ statusLabel(tenant.status) }}</span></span>
            <span class="arrow" aria-hidden="true">›</span></button> }</div>
          <div class="pagination"><button type="button" [disabled]="page() === 0 || loading()" (click)="setPage(page() - 1)">Poprzednia</button>
            <span>Strona {{ page() + 1 }} z {{ totalPages() || 1 }}</span>
            <button type="button" [disabled]="page() + 1 >= totalPages() || loading()" (click)="setPage(page() + 1)">Następna</button></div>
        </section>
        @if (selected(); as tenant) { <aside class="panel detail-panel" aria-label="Szczegóły tenanta">
          <div class="detail-head"><span class="avatar large">{{ tenant.name.slice(0,1).toUpperCase() }}</span>
            <div><h2>{{ tenant.name }}</h2><span class="status-badge" [class.active]="tenant.status === 'ACTIVE'"
              [class.suspended]="tenant.status === 'SUSPENDED'" [class.closed]="tenant.status === 'CLOSED'">{{ statusLabel(tenant.status) }}</span></div>
            <button type="button" class="close-detail" aria-label="Zamknij szczegóły" (click)="selected.set(null)">✕</button></div>
          <div class="detail-tabs" role="tablist" aria-label="Zakres szczegółów">
            <button type="button" role="tab" [attr.aria-selected]="tab() === 'summary'" (click)="tab.set('summary')">Podsumowanie</button>
            @if (auth.has('PLATFORM_SUBSCRIPTION_READ')) { <button type="button" role="tab" [attr.aria-selected]="tab() === 'subscription'" (click)="tab.set('subscription')">Subskrypcja</button> }
          </div>
          @if (tab() === 'summary') { <div class="detail-body"><h3>Podstawowe informacje</h3><dl><dt>Nazwa firmy</dt><dd>{{ tenant.name }}</dd>
            <dt>Slug</dt><dd>{{ tenant.slug }}</dd><dt>Utworzono</dt><dd>{{ tenant.createdAt | date:'medium' }}</dd></dl>
            <h3>Subskrypcja</h3><dl><dt>Pakiet bazowy</dt><dd>BASE v1</dd>
              <dt>Aktywni użytkownicy</dt><dd>@if (selectedSubscription(); as sub) { {{ sub.usage.used }} /
              {{ sub.usage.limit === null ? 'bez limitu' : sub.usage.limit }} } @else { {{ auth.has('PLATFORM_SUBSCRIPTION_READ') ? 'Ładowanie…' : 'Brak uprawnienia' }} }</dd></dl>
            @if (auth.has('PLATFORM_SUBSCRIPTION_READ')) { <a class="detail-link" [routerLink]="['/platform/tenants', tenant.id, 'subscription']">Przejdź do subskrypcji ›</a> }
            @if (auth.has('PLATFORM_TENANT_MANAGE')) { <h3>Akcje na tenancie</h3><div class="tenant-actions">
              @if (tenant.status === 'ACTIVE') { <button type="button" class="warning" (click)="confirmAction('suspend')">Zawieś tenanta</button> }
              @if (tenant.status === 'SUSPENDED') { <button type="button" (click)="confirmAction('activate')">Aktywuj tenanta</button> }
              @if (tenant.status !== 'CLOSED') { <button type="button" class="danger" (click)="confirmAction('close')">Zamknij tenanta</button> }
            </div> }</div> }
          @else { <div class="detail-body"><h3>Pakiet i zużycie</h3>
            @if (selectionLoading()) { <p role="status">Ładowanie subskrypcji…</p> }
            @else if (selectedSubscription(); as sub) { <p>Pakiet bazowy · wersja {{ sub.planVersion }}</p>
              <p><strong>{{ sub.usage.used }}</strong> aktywnych użytkowników · limit:
                {{ sub.usage.limit === null ? 'bez limitu' : sub.usage.limit }}</p>
              <p>{{ activeAddons(sub) }} aktywnych dodatków</p> }
            @if (auth.has('PLATFORM_SUBSCRIPTION_READ')) { <a class="detail-link" [routerLink]="['/platform/tenants', tenant.id, 'subscription']">Zarządzaj pakietem ›</a> }
          </div> }
        </aside> }
      </div>
      @if (pendingAction(); as action) { <div class="dialog-backdrop" role="presentation"><section class="confirm-sheet" role="dialog" aria-modal="true"
          [attr.aria-label]="actionLabel(action) + ' tenanta'"><div class="page-heading"><h2>{{ actionLabel(action) }} tenanta</h2>
            <button type="button" aria-label="Anuluj" (click)="pendingAction.set(null)">✕</button></div>
          <p>Czy na pewno chcesz {{ actionLabel(action).toLowerCase() }} tenanta „{{ selected()?.name }}”?</p>
          @if (action === 'suspend') { <p>Wszyscy użytkownicy stracą dostęp. Tenanta będzie można ponownie aktywować.</p> }
          @if (action === 'close') { <p>Ta operacja jest trwała i nie można jej cofnąć.</p> }
          <div class="form-actions"><button type="button" (click)="pendingAction.set(null)">Anuluj</button>
            <button class="primary" type="button" [disabled]="saving()" (click)="applyAction(action)">{{ actionLabel(action) }} tenanta</button></div>
        </section></div> }
    </main>
  `,
  styles: `
    .platform-tenants { max-width: 98rem; }.add-button { background: #078a91; border-color: #078a91; display: flex; gap: .6rem; align-items: center; }
    .search-row { display: grid; grid-template-columns: minmax(15rem, 22rem) minmax(10rem, 14rem) auto; gap: .7rem; align-items: end; }
    .search-box { position: relative; }.search-box i { position: absolute; top: .9rem; left: .9rem; color: var(--app-text-muted); }
    .search-box input { padding-left: 2.4rem; }.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }
    .content-grid { display: grid; grid-template-columns: minmax(0,1fr); gap: .7rem; align-items: start; }
    .content-grid.with-detail { grid-template-columns: minmax(0,1.7fr) minmax(18rem, .8fr); }.results { min-width: 0; padding: 0; }
    .results table { white-space: nowrap; }.results th, .results td { padding: 1rem .7rem; }.results tr.selected { background: #ecf6ff; }
    .tenant-name { border: 0; padding: 0; display: flex; align-items: center; gap: .6rem; text-align: left; }
    .avatar { border-radius: 50%; width: 2.1rem; height: 2.1rem; flex: 0 0 2.1rem; display: grid; place-items: center; background: #dce9f9; color: #17355f; font-weight: 800; }
    .avatar.large { width: 3.2rem; height: 3.2rem; flex-basis: 3.2rem; font-size: 1.4rem; }.row-open { border: 0; color: #087f96; font-size: 1.4rem; padding: 0 .4rem; }
    .status-badge { display: inline-flex; border-radius: 99px; padding: .25rem .65rem; background: #edf1f6; font-size: .8rem; white-space: nowrap; }
    .status-badge.active { background: #dcf8ec; color: #08734e; }.status-badge.suspended { background: #fff0d7; color: #9c6100; }
    .status-badge.closed { background: #ffe6e9; color: #b72e45; }.results .pagination { padding: .8rem; }
    .detail-panel { padding: 0; }.detail-head { display: flex; align-items: center; gap: .7rem; padding: 1rem; }.detail-head h2 { margin: 0 .2rem .3rem 0; }
    .close-detail { margin-left: auto; border: 0; }.detail-tabs { display: flex; border-bottom: 1px solid var(--app-border); }
    .detail-tabs button { flex: 1; border: 0; border-radius: 0; font-size: .9rem; padding: .7rem .4rem; }
    .detail-tabs [aria-selected=true] { border-bottom: 3px solid #078a91; color: #075d68; font-weight: 700; }
    .detail-body { padding: 1rem; }.detail-body h3 { margin: 1rem 0 .6rem; font-size: 1rem; }.detail-body dl { display: grid; grid-template-columns: 1fr 1fr; gap: .5rem; }
    .detail-body dt { color: var(--app-text-muted); }.detail-body dd { margin: 0; overflow-wrap: anywhere; }.detail-link { display: block; margin: .6rem 0; }
    .tenant-actions { display: grid; gap: .5rem; }.tenant-actions button { text-align: left; }.warning { color: #9c6100; border-color: #e0b865; }
    .danger { color: #b72e45; border-color: #e6a0aa; }.mobile-cards { display: none; }.create-form { max-width: 40rem; }
    .dialog-backdrop { position: fixed; inset: 0; z-index: 30; background: rgba(12,29,53,.46); display: grid; place-items: center; padding: 1rem; }
    .confirm-sheet { background: white; border-radius: 20px; padding: 1.5rem; width: min(100%,31rem); box-shadow: var(--app-shadow); }
    .confirm-sheet h2 { margin: 0; }.confirm-sheet .form-actions { justify-content: end; }
    @media (max-width: 950px) { .content-grid.with-detail { grid-template-columns: 1fr; } }
    @media (max-width: 760px) { .search-row { grid-template-columns: 1fr 1fr; }.search-box { grid-column: 1 / -1; }.search-row button { grid-column: 1 / -1; }
      .results table { display: none; }.mobile-cards { display: grid; gap: .5rem; padding: .7rem; }
      .tenant-card { display: flex; align-items: center; gap: .7rem; width: 100%; text-align: left; padding: .8rem; }
      .tenant-card-main { display: grid; gap: .25rem; flex: 1; }.tenant-card-main small { color: var(--app-text-muted); }
      .tenant-card-main .status-badge { width: fit-content; }.arrow { color: #087f96; font-size: 1.6rem; }
      .detail-panel { position: fixed; z-index: 20; inset: auto 0 0; max-height: 84vh; overflow-y: auto; border-radius: 22px 22px 0 0; box-shadow: var(--app-shadow); }
      .dialog-backdrop { align-items: end; padding: 0; }.confirm-sheet { width: 100%; border-radius: 22px 22px 0 0; padding-bottom: max(1.5rem, env(safe-area-inset-bottom)); }
    }
  `,
})
export class PlatformTenantsPage implements OnInit {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly tenants = signal<TenantSummary[]>([]);
  readonly selected = signal<TenantSummary | null>(null);
  readonly selectedSubscription = signal<Subscription | null>(null);
  readonly selectionLoading = signal(false);
  readonly tab = signal<'summary' | 'subscription'>('summary');
  readonly pendingAction = signal<TenantAction | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly creating = signal(false);
  readonly error = signal('');
  readonly temporaryPassword = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  search = '';
  statusFilter = '';
  slug = '';
  name = '';
  timeZone = 'Europe/Warsaw';
  locale = 'pl-PL';
  adminEmail = '';
  adminDisplayName = '';

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading.set(true);
    this.api.tenants(this.page(), 20, this.search, this.statusFilter).subscribe({
      next: result => {
        this.tenants.set(result.content); this.totalPages.set(result.totalPages);
        this.loading.set(false); this.error.set('');
        const current = this.selected();
        if (current) this.selected.set(result.content.find(item => item.id === current.id) ?? null);
      },
      error: error => { this.error.set(problemMessage(error)); this.loading.set(false); },
    });
  }
  applyFilters(): void { this.page.set(0); this.load(); }
  setPage(page: number): void { this.page.set(page); this.load(); }
  select(tenant: TenantSummary): void {
    this.selected.set(tenant); this.selectedSubscription.set(null); this.tab.set('summary');
    if (!this.auth.has('PLATFORM_SUBSCRIPTION_READ')) { this.selectionLoading.set(false); return; }
    this.selectionLoading.set(true);
    this.api.platformSubscription(tenant.id).subscribe({
      next: subscription => { if (this.selected()?.id === tenant.id) this.selectedSubscription.set(subscription); this.selectionLoading.set(false); },
      error: error => { this.error.set(problemMessage(error)); this.selectionLoading.set(false); },
    });
  }
  statusLabel(status: TenantSummary['status']): string {
    return status === 'ACTIVE' ? 'Aktywny' : status === 'SUSPENDED' ? 'Zawieszony' : 'Zamknięty';
  }
  activeAddons(subscription: Subscription): number {
    const now = Date.now();
    return subscription.modules.filter(module => module.type === 'ADD_ON' && module.availability === 'AVAILABLE'
      && module.status === 'ENABLED' && (!module.startsAt || new Date(module.startsAt).getTime() <= now)
      && (!module.endsAt || new Date(module.endsAt).getTime() > now)).length;
  }
  actionLabel(action: TenantAction): string { return action === 'suspend' ? 'Zawieś' : action === 'activate' ? 'Aktywuj' : 'Zamknij'; }
  confirmAction(action: TenantAction): void { this.pendingAction.set(action); }
  applyAction(action: TenantAction): void {
    const tenant = this.selected();
    if (!tenant || this.saving()) return;
    this.saving.set(true);
    this.api.setTenantStatus(tenant.id, action).subscribe({
      next: updated => { this.selected.set(updated); this.pendingAction.set(null); this.saving.set(false); this.load(); },
      error: error => { this.error.set(problemMessage(error)); this.pendingAction.set(null); this.saving.set(false); },
    });
  }
  create(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.api.provisionTenant({ slug: this.slug, name: this.name, timeZone: this.timeZone, locale: this.locale,
      adminEmail: this.adminEmail, adminDisplayName: this.adminDisplayName }).subscribe({
      next: result => { this.temporaryPassword.set(result.temporaryPassword); this.creating.set(false); this.saving.set(false); this.load(); },
      error: error => { this.error.set(problemMessage(error)); this.saving.set(false); },
    });
  }
}
