import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, AuditEntry, AuditQuery } from '../../core/api.service';
import { AuthService, TenantSummary } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-audit-page',
  imports: [FormsModule, DatePipe],
  template: `
    <main class="page audit-page">
      <div class="page-heading">
        <div><h1>Dziennik audytu</h1><p>Zmiany konfiguracji i logowania, od najnowszych.</p></div>
        <button type="button" class="mobile-filters" (click)="filtersOpen.set(!filtersOpen())"
          [attr.aria-expanded]="filtersOpen()">{{ filtersOpen() ? 'Ukryj filtry' : 'Pokaż filtry' }}</button>
      </div>
      <form class="panel filters" [class.open]="filtersOpen()" (ngSubmit)="applyFilters()">
        @if (auth.isPlatform()) {
          <label>Zakres
            <select name="scope" [(ngModel)]="scope" (change)="scopeChanged()">
              <option value="global">Platforma</option>
              @for (tenant of tenants(); track tenant.id) {
                <option [value]="tenant.id">{{ tenant.name }}</option>
              }
              <option value="manual">Inny tenant — podaj ID</option>
            </select>
          </label>
          @if (scope === 'manual') { <label>ID tenanta
            <input name="tenantId" [(ngModel)]="manualTenantId" placeholder="UUID tenanta" required />
          </label> }
        }
        <label>Od <input type="datetime-local" name="from" [(ngModel)]="from" /></label>
        <label>Do <input type="datetime-local" name="to" [(ngModel)]="to" /></label>
        <label>Moduł <select name="module" [(ngModel)]="module">
          <option value="">Wszystkie</option><option value="TENANCY">Tenancy</option><option value="IDENTITY">Identity</option>
        </select></label>
        <label>Akcja <input name="action" [(ngModel)]="action" placeholder="np. USER_CREATED" /></label>
        <label>Wynik <select name="result" [(ngModel)]="result">
          <option value="">Wszystkie</option><option value="SUCCESS">Sukces</option><option value="DENIED">Odmowa</option>
        </select></label>
        <label>Identyfikator aktora <input name="actorId" [(ngModel)]="actorId" placeholder="UUID" /></label>
        <label>Identyfikator obiektu <input name="targetId" [(ngModel)]="targetId" placeholder="UUID" /></label>
        <div class="filter-actions"><button class="primary" type="submit" [disabled]="loading()">Filtruj</button>
          <button type="button" (click)="clearFilters()">Wyczyść</button></div>
      </form>
      @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
      @if (loading()) { <p class="panel" role="status">Ładowanie zdarzeń…</p> }
      @else if (entries().length === 0) { <p class="panel">Brak zdarzeń dla wybranego zakresu i filtrów.</p> }
      @else {
        <div class="panel table-wrap">
          <table><thead><tr><th>Data i godzina</th><th>Akcja</th><th>Wynik</th><th>Obiekt</th><th>Aktor</th><th></th></tr></thead><tbody>
          @for (entry of entries(); track entry.id) {
            <tr><td>{{ entry.occurredAt | date:'short' }}</td><td><strong>{{ entry.action }}</strong><small>{{ entry.module }}</small></td>
              <td>{{ entry.result === 'SUCCESS' ? 'Sukces' : 'Odmowa' }}</td>
              <td>{{ entry.targetType }}<small class="identifier">{{ entry.targetId }}</small></td>
              <td>{{ entry.actorType }}<small class="identifier">{{ entry.actorId || 'system' }}</small></td>
              <td><button type="button" (click)="select(entry)" [attr.aria-label]="'Szczegóły zdarzenia ' + entry.action">Szczegóły</button></td>
            </tr>
          }</tbody></table>
        </div>
      }
      <div class="pagination"><button type="button" [disabled]="page() === 0 || loading()" (click)="setPage(page() - 1)">Poprzednia</button>
        <span>Strona {{ page() + 1 }} z {{ totalPages() || 1 }}</span>
        <button type="button" [disabled]="page() + 1 >= totalPages() || loading()" (click)="setPage(page() + 1)">Następna</button></div>
      @if (selected(); as detail) {
        <section class="panel detail" aria-label="Szczegóły zdarzenia">
          <div class="page-heading"><h2>Szczegóły zdarzenia</h2><button type="button" (click)="selected.set(null)">Zamknij</button></div>
          <dl><dt>Akcja</dt><dd>{{ detail.action }}</dd><dt>Wynik</dt><dd>{{ detail.result }}</dd>
            <dt>Czas</dt><dd>{{ detail.occurredAt | date:'medium' }}</dd>
            <dt>Aktor</dt><dd class="identifier">{{ detail.actorType }} · {{ detail.actorId || 'system' }}</dd>
            <dt>Obiekt</dt><dd class="identifier">{{ detail.targetType }} · {{ detail.targetId }}</dd>
            <dt>Correlation ID</dt><dd class="identifier">{{ detail.correlationId }}</dd>
            <dt>Zakres</dt><dd class="identifier">{{ detail.tenantId || 'globalny' }}</dd></dl>
          @if (metadataPairs(detail).length) { <h3>Zakres zmian</h3><dl>
            @for (pair of metadataPairs(detail); track pair[0]) { <dt>{{ pair[0] }}</dt><dd>{{ pair[1] }}</dd> }
          </dl> }
        </section>
      }
    </main>
  `,
  styles: `
    .mobile-filters { display: none; }
    .filters { display: grid; grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr)); gap: 1rem; }
    .filters label { display: grid; gap: .35rem; font-weight: 600; min-width: 0; }
    .filter-actions { display: flex; align-items: end; gap: .5rem; }
    .identifier { overflow-wrap: anywhere; font-size: .8rem; }
    .detail { border-color: var(--app-primary); }
    .detail h2 { margin: 0; }
    .detail dl { display: grid; grid-template-columns: 10rem 1fr; gap: .8rem 1rem; }
    .detail dt { color: var(--app-text-muted); }
    .detail dd { margin: 0; }
    @media (max-width: 760px) {
      .mobile-filters { display: inline-block; }
      .filters { display: none; grid-template-columns: 1fr; }
      .filters.open { display: grid; }
      .detail dl { grid-template-columns: 1fr; gap: .25rem; }
      .detail dd { margin-bottom: .7rem; }
      .audit-page td:nth-child(4), .audit-page td:nth-child(5),
      .audit-page th:nth-child(4), .audit-page th:nth-child(5) { display: none; }
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
  scope = 'global';
  manualTenantId = '';
  from = '';
  to = '';
  module = '';
  action = '';
  result = '';
  actorId = '';
  targetId = '';

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
    if (this.from) query.from = new Date(this.from).toISOString();
    if (this.to) query.to = new Date(this.to).toISOString();
    if (this.module) query.module = this.module.trim().toUpperCase();
    if (this.action) query.action = this.action.trim().toUpperCase();
    if (this.result) query.result = this.result as 'SUCCESS' | 'DENIED';
    if (this.actorId) query.actorId = this.actorId.trim();
    if (this.targetId) query.targetId = this.targetId.trim();
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

  applyFilters(): void { this.page.set(0); this.load(); }
  scopeChanged(): void { if (this.scope !== 'manual') this.applyFilters(); }
  clearFilters(): void {
    this.from = ''; this.to = ''; this.module = ''; this.action = ''; this.result = '';
    this.actorId = ''; this.targetId = '';
    this.applyFilters();
  }
  setPage(page: number): void { this.page.set(page); this.load(); }
  select(entry: AuditEntry): void { this.selected.set(entry); }
  metadataPairs(entry: AuditEntry): [string, string][] {
    return Object.entries(entry.metadata).map(([key, value]) => [key, Array.isArray(value) ? value.join(', ') : value]);
  }
}
