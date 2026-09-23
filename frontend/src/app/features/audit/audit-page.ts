import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, AuditEntry, AuditQuery } from '../../core/api.service';
import { AuthService, TenantSummary } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

const moduleLabels: Record<string, string> = {
  TENANCY: 'Ustawienia', IDENTITY: 'Użytkownicy', AUTH: 'Logowanie', AUDIT: 'Audyt',
  ENTITLEMENTS: 'Pakiet i moduły', SMS: 'SMS', TIME_TRACKING: 'Czas pracy', ABSENCE_EVENTS: 'Braki obecności',
  EMPLOYEE_DIRECTORY: 'Pracownicy', PROJECTS: 'Projekty', PLANNING: 'Planowanie',
};

const actionLabels: Record<string, string> = {
  LOGIN: 'Zalogowano', LOGIN_DENIED: 'Odrzucono logowanie', PLATFORM_BOOTSTRAPPED: 'Utworzono operatora platformy',
  PASSWORD_CHANGED: 'Zmieniono hasło', PLATFORM_PASSWORD_CHANGED: 'Zmieniono hasło operatora',
  TENANT_PROVISIONED: 'Utworzono firmę', TENANT_UPDATED: 'Zmieniono ustawienia firmy',
  TENANT_SUSPENDED: 'Zawieszono firmę', TENANT_ACTIVATED: 'Aktywowano firmę', TENANT_CLOSED: 'Zamknięto firmę',
  USER_CREATED: 'Dodano użytkownika', USER_UPDATED: 'Zmieniono użytkownika', USER_STATUS_CHANGED: 'Zmieniono status użytkownika',
  USER_PASSWORD_RESET: 'Zresetowano hasło', ROLE_CREATED: 'Dodano rolę', ROLE_UPDATED: 'Zmieniono rolę', ROLE_DELETED: 'Usunięto rolę',
  ADDON_ENABLED: 'Włączono moduł', ADDON_DISABLED: 'Wyłączono moduł', LIMIT_UPDATED: 'Zmieniono limit',
};

const metadataLabels: Record<string, string> = {
  changedFields: 'Zmienione pola', addedPermissions: 'Dodane uprawnienia',
  removedPermissions: 'Usunięte uprawnienia', fromStatus: 'Poprzedni status',
  toStatus: 'Nowy status', capability: 'Moduł', metric: 'Metryka',
  fromMode: 'Poprzedni tryb limitu', toMode: 'Nowy tryb limitu',
  fromLimit: 'Poprzedni limit', toLimit: 'Nowy limit',
};

const operationOptions = Object.keys(actionLabels).filter(action => action !== 'LOGIN_DENIED');

@Component({
  selector: 'app-audit-page',
  imports: [FormsModule, DatePipe],
  template: `
    <main class="page audit-page" [class.details-open]="selected() !== null">
      <div class="page-heading">
        <div><h1><span class="desktop-title">Dziennik audytowy</span><span class="mobile-title">Audyt</span></h1><p>Niezmienna historia zdarzeń w systemie. Dane tylko do odczytu.</p></div>
        <button type="button" class="mobile-filters" (click)="filtersOpen.set(!filtersOpen())"
          [attr.aria-label]="filtersOpen() ? 'Zamknij filtry' : 'Otwórz filtry'" [attr.aria-expanded]="filtersOpen()"><i class="pi pi-filter" aria-hidden="true"></i></button>
      </div>
      <div class="audit-info"><i class="pi pi-info-circle" aria-hidden="true"></i><span><strong>Dziennik audytowy jest niezmienny — wpisy nie mogą być edytowane ani usuwane.</strong><br />Rejestrujemy operacje wykonywane w systemie w celach bezpieczeństwa.</span></div>
      <form class="panel filters" [class.open]="filtersOpen()" (ngSubmit)="applyFilters()">
        <div class="filter-title"><span class="sheet-handle" aria-hidden="true"></span><strong>Filtr audytu</strong><button type="button" (click)="filtersOpen.set(false)" aria-label="Zamknij filtry"><i class="pi pi-times" aria-hidden="true"></i></button></div>
        @if (auth.isPlatform()) {
          <div class="scope-filter"><label>Zakres danych
            <select name="scope" [(ngModel)]="scope" (ngModelChange)="scopeChanged()">
              <option value="global">Platforma</option>
              @for (tenant of tenants(); track tenant.id) {
                <option [value]="tenant.id">{{ tenant.name }}</option>
              }
              <option value="manual">Inny tenant — podaj ID</option>
            </select>
          </label>
          @if (scope === 'manual') { <label>ID tenanta
            <input name="tenantId" [(ngModel)]="manualTenantId" placeholder="UUID tenanta" (blur)="filterChanged()" />
          </label> }</div>
        }
        <div class="filter-grid">
          <fieldset class="date-range"><legend>Zakres dat</legend><label><span class="sr-only">Od</span><input type="date" name="from" [(ngModel)]="from" (change)="filterChanged()" /></label>
            <span class="date-separator" aria-hidden="true">—</span><label><span class="sr-only">Do</span><input type="date" name="to" [(ngModel)]="to" (change)="filterChanged()" /></label></fieldset>
          <label>Użytkownik <select name="actorId" [(ngModel)]="actorId" (ngModelChange)="filterChanged()">
            <option value="">Wszyscy</option>
            @for (actor of actorOptions(); track actor.id) { <option [value]="actor.id">{{ actor.label }}</option> }
          </select></label>
          <label>Moduł <select name="module" [(ngModel)]="module" (ngModelChange)="filterChanged()">
          <option value="">Wszystkie</option><option value="TENANCY">Ustawienia firmy</option><option value="IDENTITY">Użytkownicy i role</option>
          <option value="AUTH">Logowanie</option><option value="AUDIT">Audyt</option><option value="ENTITLEMENTS">Pakiet i moduły</option>
          <option value="SMS">SMS</option><option value="TIME_TRACKING">Czas pracy</option><option value="ABSENCE_EVENTS">Braki obecności</option>
          <option value="EMPLOYEE_DIRECTORY">Pracownicy</option><option value="PROJECTS">Projekty</option><option value="PLANNING">Planowanie</option>
        </select></label>
          <label>Operacja <select name="action" [(ngModel)]="action" (ngModelChange)="filterChanged()">
            <option value="">Wszystkie</option>
            @for (operation of operationOptions; track operation) { <option [value]="operation">{{ actionLabel(operation) }}</option> }
          </select></label>
          <label>Wynik <select name="result" [(ngModel)]="result" (ngModelChange)="filterChanged()">
          <option value="">Wszystkie</option><option value="SUCCESS">Sukces</option><option value="DENIED">Odrzucono</option>
        </select></label>
          <label class="target-search">Szukaj w audycie<span class="search-field"><i class="pi pi-search" aria-hidden="true"></i><input name="search" [(ngModel)]="search" placeholder="np. pracownik, projekt" maxlength="100" (blur)="filterChanged()" /></span></label>
        </div>
        <div class="filter-actions"><button type="button" (click)="clearFilters()">Wyczyść</button>
          <button class="primary" type="submit" [disabled]="loading()">Zastosuj</button></div>
      </form>
      @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
      <div class="audit-layout">
        <section class="audit-results" aria-label="Wyniki audytu">
          @if (loading()) { <p class="panel" role="status">Ładowanie zdarzeń…</p> }
          @else if (entries().length === 0) { <p class="panel empty-state">Brak zdarzeń dla wybranego zakresu i filtrów.</p> }
          @else {
            <div class="panel table-wrap"><table><thead><tr><th>Data i godzina</th><th>Użytkownik</th><th>Moduł</th><th>Operacja</th><th>Obiekt</th><th>Wynik</th><th></th></tr></thead><tbody>
            @for (entry of entries(); track entry.id) {
              <tr [class.selected]="selected()?.id === entry.id"><td>{{ entry.occurredAt | date:'dd.MM.yyyy HH:mm' }}</td>
                <td>{{ actorLabel(entry) }}<small class="identifier">{{ shortId(entry.actorId) }}</small></td>
                <td>{{ moduleLabel(entry.module) }}</td><td>{{ actionLabel(entry.action, entry.result) }}</td>
                <td>{{ targetLabel(entry) }}<small class="identifier">{{ shortId(entry.targetId) }}</small></td>
                <td><span class="result" [class.denied]="entry.result !== 'SUCCESS'"><i class="pi" [class.pi-check-circle]="entry.result === 'SUCCESS'" [class.pi-times-circle]="entry.result !== 'SUCCESS'" aria-hidden="true"></i>{{ resultLabel(entry.result) }}</span></td>
                <td><button class="open-detail" type="button" (click)="select(entry)" [attr.aria-label]="'Szczegóły zdarzenia ' + actionLabel(entry.action, entry.result)"><i class="pi pi-chevron-right" aria-hidden="true"></i></button></td>
              </tr>
            }</tbody></table></div>
            <div class="mobile-audit">@for (entry of entries(); track entry.id) {
              <button class="panel audit-card" type="button" (click)="select(entry)"><span class="result-icon" [class.denied]="entry.result !== 'SUCCESS'"><i class="pi" [class.pi-check]="entry.result === 'SUCCESS'" [class.pi-times]="entry.result !== 'SUCCESS'" aria-hidden="true"></i></span>
                <span class="audit-card-main"><strong>{{ actionLabel(entry.action, entry.result) }}</strong><small>{{ actorLabel(entry) }}</small><small>{{ moduleLabel(entry.module) }} · {{ targetLabel(entry) }}</small></span>
                <time>{{ entry.occurredAt | date:'HH:mm' }}</time><i class="pi pi-chevron-right" aria-hidden="true"></i></button>
            }</div>
          }
          <div class="pagination"><span>Wyświetlanie {{ entries().length ? page() * 20 + 1 : 0 }}–{{ page() * 20 + entries().length }} zdarzeń</span>
            <button type="button" [disabled]="page() === 0 || loading()" (click)="setPage(page() - 1)" aria-label="Poprzednia strona"><i class="pi pi-chevron-left" aria-hidden="true"></i></button>
            <span>Strona {{ page() + 1 }} z {{ totalPages() || 1 }}</span>
            <button type="button" [disabled]="page() + 1 >= totalPages() || loading()" (click)="setPage(page() + 1)" aria-label="Następna strona"><i class="pi pi-chevron-right" aria-hidden="true"></i></button>
          </div>
        </section>
      </div>
      @if (selected(); as detail) {
        <div class="detail-backdrop" (click)="selected.set(null)"></div><aside class="panel detail" aria-label="Szczegóły zdarzenia">
          <div class="detail-heading"><h2>Szczegóły zdarzenia</h2><button type="button" (click)="selected.set(null)" aria-label="Zamknij szczegóły"><i class="pi pi-times" aria-hidden="true"></i></button></div>
          <div class="detail-status"><span class="result" [class.denied]="detail.result !== 'SUCCESS'"><i class="pi" [class.pi-check-circle]="detail.result === 'SUCCESS'" [class.pi-times-circle]="detail.result !== 'SUCCESS'" aria-hidden="true"></i>{{ resultLabel(detail.result) }}</span>
            <time>{{ detail.occurredAt | date:'dd.MM.yyyy HH:mm:ss' }}</time></div>
          <div class="detail-field"><span>Operacja</span><strong>{{ actionLabel(detail.action, detail.result) }}</strong><small>{{ detail.action }}</small></div>
          <div class="detail-field"><span>Moduł</span><strong>{{ moduleLabel(detail.module) }}</strong></div>
          <div class="detail-field"><span>Użytkownik</span><strong>{{ actorLabel(detail) }}</strong><small class="identifier">{{ detail.actorId || 'System' }}</small></div>
          <div class="detail-field"><span>Obiekt</span><strong>{{ targetLabel(detail) }}</strong><small class="identifier">{{ detail.targetId }}</small></div>
          <div class="detail-field"><span>Id korelacji</span><code class="identifier">{{ detail.correlationId }}</code></div>
          @if (detail.tenantId) { <div class="detail-field"><span>Tenant</span><code class="identifier">{{ detail.tenantId }}</code></div> }
          @if (metadataPairs(detail).length) { <div class="detail-field metadata"><span>Dodatkowe informacje</span>@for (pair of metadataPairs(detail); track pair[0]) {
            <div class="metadata-pair"><strong>{{ metadataLabel(pair[0]) }}</strong><span>{{ pair[1] }}</span></div>
          }</div> }
          <p class="detail-note"><i class="pi pi-info-circle" aria-hidden="true"></i> Szczegóły nie zawierają treści wiadomości ani pełnych danych osobowych.</p>
          <button class="detail-close" type="button" (click)="selected.set(null)">Zamknij</button>
        </aside>
      }
    </main>
  `,
  styles: `
    .audit-page { max-width: 100rem; padding: 1rem 2rem; }.mobile-filters, .filter-title, .mobile-audit, .mobile-title { display: none; }
    .audit-page.details-open { max-width: none; margin: 0; padding-right: calc(2rem + 17.5rem); grid-template-columns: minmax(0,1fr); align-items: start; }
    .audit-page.details-open > :not(.detail):not(.detail-backdrop) { grid-column: 1; }
    .audit-info { display: flex; gap: .7rem; padding: .9rem; border: 1px solid #cbe2ff; border-radius: 10px; background: #eaf4ff; color: #1e518f; }
    .audit-info i { flex: 0 0 auto; font-size: 1.3rem; }.audit-info strong { font-size: .95rem; }
    .filters { display: block; }
    .scope-filter { display: flex; align-items: end; gap: 1rem; margin-bottom: .9rem; }.scope-filter label { width: min(100%,20rem); }
    .filter-grid { display: grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap: .8rem 1rem; align-items: end; }
    .filters label, .date-range { display: grid; gap: .35rem; font-weight: 600; min-width: 0; margin: 0; }
    .date-range { grid-template-columns: minmax(0,1fr) auto minmax(0,1fr); align-items: end; border: 0; border-radius: 0; padding: 0; }
    .date-range legend { grid-column: 1 / -1; padding: 0; margin-bottom: .1rem; font-weight: 600; }
    .date-range label { min-width: 0; }.date-separator { align-self: center; padding: 0 .1rem .35rem; color: var(--app-text-muted); }
    .target-search .search-field { position: relative; }.search-field i { position: absolute; z-index: 1; top: .85rem; left: .8rem; color: var(--app-text-muted); }
    .search-field input { padding-left: 2.2rem; }
    .filter-actions { display: none; justify-content: flex-end; gap: .5rem; }
    .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
    .audit-layout { display: block; }
    .audit-results { min-width: 0; }.table-wrap { padding: 0; overflow-x: auto; }
    .table-wrap table { min-width: 52rem; }.table-wrap th, .table-wrap td { padding: .7rem .55rem; vertical-align: middle; }
    .table-wrap td { font-size: .88rem; }.table-wrap th { white-space: nowrap; }
    .table-wrap tr.selected { background: #e2f6f5; }.identifier { overflow-wrap: anywhere; font-size: .76rem; }
    .result { display: inline-flex; align-items: center; gap: .35rem; border-radius: 7px; padding: .35rem .55rem; background: #ddf7ed; color: #097454; white-space: nowrap; font-size: .83rem; }
    .result.denied { background: #ffe5e7; color: #ac263b; }
    .open-detail { border: 0; padding: .4rem; background: transparent; color: #50637d; }
    .pagination { justify-content: flex-start; padding: .4rem .1rem; color: var(--app-text-muted); font-size: .85rem; }
    .pagination span:first-child { margin-right: auto; }.pagination button { min-width: 2.6rem; min-height: 2.5rem; padding: .4rem; }
    .empty-state { min-height: 10rem; display: grid; place-items: center; color: var(--app-text-muted); }
    .detail-backdrop { display: none; }
    .detail { position: fixed; z-index: 20; top: 4.5rem; right: 0; bottom: 0; width: 17.5rem; display: flex; flex-direction: column; gap: 0; overflow: auto; padding: 0; border-radius: 0; border-left: 1px solid var(--app-border); box-shadow: none; }
    .detail-heading { display: flex; align-items: center; justify-content: space-between; padding: 1rem 1rem .75rem; }
    .detail-heading h2 { margin: 0; font-size: 1.2rem; }.detail-heading button { border: 0; padding: .4rem; background: transparent; }
    .detail-status { display: flex; align-items: center; justify-content: space-between; gap: .5rem; padding: 0 1rem 1rem; border-bottom: 1px solid var(--app-border); }
    .detail-status time { color: var(--app-text-muted); font-size: .8rem; white-space: nowrap; }
    .detail-field { display: grid; grid-template-columns: 1fr auto; gap: .35rem .8rem; padding: .8rem 1rem; border-bottom: 1px solid var(--app-border); }
    .detail-field > span { grid-column: 1 / -1; color: var(--app-text-muted); font-size: .82rem; }
    .detail-field > strong, .detail-field > code { min-width: 0; overflow-wrap: anywhere; font-size: .9rem; }
    .detail-field > small { grid-column: 1 / -1; color: var(--app-text-muted); }.detail-field code { font-family: inherit; }
    .metadata-pair { display: flex; justify-content: space-between; gap: .8rem; grid-column: 1 / -1; font-size: .84rem; }
    .metadata-pair strong { color: #31445f; }.metadata-pair span { text-align: right; overflow-wrap: anywhere; }
    .detail-note { display: flex; gap: .5rem; margin: .8rem; padding: .75rem; border-radius: 9px; background: #eaf4ff; color: #1e518f; font-size: .82rem; }
    .detail-note i { flex: 0 0 auto; }.detail-close { margin: auto .8rem .8rem; border-color: var(--app-primary); color: var(--app-primary); }
    @media (max-width: 1200px) and (min-width: 761px) {
      .filter-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }
    }
    @media (max-width: 760px) {
      .audit-page, .audit-page.details-open { display: grid; grid-template-columns: minmax(0,1fr); gap: 1rem; padding: 1rem 1rem 6rem; }
      .audit-page.details-open > :not(.detail):not(.detail-backdrop) { grid-column: 1; }
      .audit-page .page-heading { align-items: center; }.audit-page .page-heading h1 { font-size: 1.55rem; }
      .desktop-title { display: none; }.mobile-title { display: inline; }
      .audit-info, .table-wrap { display: none; }.mobile-filters { display: inline-grid; place-items: center; min-width: 2.8rem; min-height: 2.8rem; padding: .5rem; }
      .mobile-filters i { font-size: 1.1rem; }
      .filters { display: none; position: fixed; z-index: 51; bottom: 0; left: 0; right: 0; max-height: 85vh; overflow: auto; border-radius: 18px 18px 0 0; box-shadow: var(--app-shadow); padding: .7rem 1rem max(1rem, env(safe-area-inset-bottom)); }
      .filters.open { display: grid; }.filter-title { display: grid; grid-template-columns: 1fr auto; grid-template-rows: auto auto; align-items: center; grid-column: 1 / -1; }
      .filter-grid { display: grid; grid-template-columns: minmax(0,1fr) minmax(0,1fr); gap: .85rem .6rem; }.filter-grid > * { min-width: 0; }
      .filter-title strong { text-align: center; grid-column: 1; grid-row: 2; }.filter-title button { grid-column: 2; grid-row: 2; border: 0; padding: .4rem; background: transparent; }
      .sheet-handle { width: 2.5rem; height: .25rem; border-radius: 99px; background: #9aa9bc; grid-column: 1 / -1; grid-row: 1; justify-self: center; margin: .1rem 0 .7rem; }
      .scope-filter { display: grid; grid-column: 1 / -1; gap: .75rem; margin-bottom: .85rem; }.scope-filter label { width: 100%; }
      .date-range { grid-column: 1 / -1; }.date-range input { min-width: 0; font-size: .78rem; padding: .55rem .45rem; }
      .filters label { min-width: 0; }.filters label input, .filters select { font-size: .88rem; padding-inline: .55rem; }
      .audit-page .target-search { display: none; }.filter-actions { display: flex; grid-column: 1 / -1; padding-top: .25rem; }
      .filter-actions button { flex: 1; min-height: 2.8rem; }
      .audit-layout { display: block; }.mobile-audit { display: grid; gap: .55rem; }
      .audit-card { display: flex; align-items: center; gap: .65rem; width: 100%; min-height: 4.5rem; text-align: left; padding: .7rem; }
      .audit-card-main { flex: 1; display: grid; gap: .15rem; min-width: 0; overflow-wrap: anywhere; }.audit-card-main small, .audit-card time { color: var(--app-text-muted); font-size: .75rem; }
      .audit-card time { align-self: flex-start; white-space: nowrap; }.result-icon { width: 2rem; height: 2rem; flex: 0 0 2rem; border-radius: 50%; background: #0e9b72; color: white; display: grid; place-items: center; }
      .result-icon.denied { background: #c73347; }.audit-page .pagination { justify-content: space-between; }.audit-page .pagination span:first-child { display: none; }
      .detail-backdrop { display: block; position: fixed; inset: 0; z-index: 49; background: #10263b55; }
      .detail { position: fixed; z-index: 50; top: auto; left: 0; right: 0; bottom: 0; width: 100%; height: auto; max-height: 85vh; border-radius: 18px 18px 0 0; padding: 0 0 max(.5rem, env(safe-area-inset-bottom)); box-shadow: var(--app-shadow); }
      .detail-heading { padding-top: 1.2rem; }.detail-close { min-height: 2.8rem; }
    }
  `,
})
export class AuditPage implements OnInit {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly entries = signal<AuditEntry[]>([]);
  readonly tenants = signal<TenantSummary[]>([]);
  readonly selected = signal<AuditEntry | null>(null);
  readonly loading = signal(true);
  readonly filtersOpen = signal(false);
  readonly error = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly operationOptions = operationOptions;
  scope = 'global';
  manualTenantId = '';
  from = '';
  to = '';
  module = '';
  action = '';
  result = '';
  actorId = '';
  search = '';

  ngOnInit(): void {
    if (this.auth.isPlatform()) {
      this.api.tenants(0, 100).subscribe({
        next: (response) => this.tenants.set(response.content),
        error: (error) => this.error.set(problemMessage(error)),
      });
    }
    this.load();
  }

  load(): void {
    const query: AuditQuery = { page: this.page() };
    if (this.auth.isPlatform()) {
      if (this.scope === 'global') query.global = true;
      else query.tenantId = this.scope === 'manual' ? this.manualTenantId.trim() : this.scope;
    }
    if (this.from) query.from = this.localDateBoundary(this.from).toISOString();
    if (this.to) {
      const end = this.localDateBoundary(this.to);
      end.setDate(end.getDate() + 1);
      query.to = end.toISOString();
    }
    if (this.module) query.module = this.module.trim().toUpperCase();
    if (this.action) query.action = this.action.trim().toUpperCase();
    if (this.result) query.result = this.result as 'SUCCESS' | 'DENIED';
    if (this.actorId) query.actorId = this.actorId.trim();
    if (this.search.trim()) query.search = this.search.trim();
    this.loading.set(true);
    this.api.auditLogs(query, this.auth.isPlatform()).subscribe({
      next: (response) => {
        this.entries.set(response.content);
        this.totalPages.set(response.totalPages);
        this.selected.set(null);
        this.error.set('');
        this.loading.set(false);
      },
      error: (error) => { this.error.set(problemMessage(error)); this.loading.set(false); },
    });
  }

  applyFilters(): void {
    if (this.auth.isPlatform() && this.scope === 'manual' && !this.manualTenantId.trim()) {
      this.error.set('Podaj identyfikator tenanta.');
      return;
    }
    this.page.set(0);
    this.filtersOpen.set(false);
    this.load();
  }
  scopeChanged(): void { if (this.scope !== 'manual') this.filterChanged(); }
  filterChanged(): void {
    if (typeof window !== 'undefined' && window.matchMedia('(min-width: 761px)').matches) this.applyFilters();
  }
  clearFilters(): void {
    this.from = ''; this.to = ''; this.module = ''; this.action = ''; this.result = ''; this.search = '';
    this.actorId = ''; this.manualTenantId = '';
    if (this.auth.isPlatform()) this.scope = 'global';
    this.filtersOpen.set(false);
    this.applyFilters();
  }
  setPage(page: number): void { this.page.set(page); this.load(); }
  select(entry: AuditEntry): void { this.selected.set(entry); }
  actorOptions(): { id: string; label: string }[] {
    const options = new Map<string, string>();
    for (const entry of this.entries()) {
      if (entry.actorId) options.set(entry.actorId, `${this.actorLabel(entry)} · ${this.shortId(entry.actorId)}`);
    }
    if (this.actorId && !options.has(this.actorId)) {
      options.set(this.actorId, `Użytkownik · ${this.shortId(this.actorId)}`);
    }
    return Array.from(options, ([id, label]) => ({ id, label }));
  }
  actorLabel(entry: AuditEntry): string {
    if (entry.actorType === 'SYSTEM') return 'System';
    return entry.actorType === 'PLATFORM_USER' ? 'Operator platformy' : 'Użytkownik';
  }
  shortId(id: string | null): string { return id ? id.slice(0, 8) : ''; }
  moduleLabel(module: string): string { return moduleLabels[module] ?? this.humanize(module); }
  actionLabel(action: string, result?: AuditEntry['result']): string {
    if (action === 'LOGIN' && result === 'DENIED') return 'Odrzucono logowanie';
    if (action === 'LOGIN') return 'Zalogowano';
    return actionLabels[action] ?? this.humanize(action);
  }
  targetLabel(entry: AuditEntry): string {
    const labels: Record<string, string> = { TENANT: 'Firma', USER: 'Użytkownik', ROLE: 'Rola',
      TENANT_ADDON: 'Moduł', TENANT_LIMIT: 'Limit', SMS_MESSAGE: 'Wiadomość SMS', PROJECT: 'Projekt' };
    return labels[entry.targetType] ?? this.humanize(entry.targetType);
  }
  resultLabel(result: AuditEntry['result']): string { return result === 'SUCCESS' ? 'Sukces' : 'Odrzucono'; }
  metadataLabel(key: string): string { return metadataLabels[key] ?? this.humanize(key); }
  metadataPairs(entry: AuditEntry): [string, string][] {
    return Object.entries(entry.metadata).map(([key, value]) => [key, Array.isArray(value) ? value.join(', ') : value]);
  }
  private localDateBoundary(value: string): Date {
    const [year, month, day] = value.split('-').map(Number);
    return new Date(year, month - 1, day);
  }
  private humanize(value: string): string {
    return value.toLocaleLowerCase('pl').split('_').map(part => part ? part[0].toLocaleUpperCase('pl') + part.slice(1) : '').join(' ');
  }
}
