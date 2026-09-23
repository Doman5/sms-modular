import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, SmsRoute } from '../../core/api.service';
import { AuthService, TenantSummary } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-sms-routes-page',
  imports: [FormsModule],
  template: `
    <main class="page routes-page"><div class="page-heading"><div><h1>Trasy SMS</h1><p>Powiąż numer odbiorcy lub SIM urządzenia SMS-Gate z firmą.</p></div></div>
      @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
      @if (auth.has('PLATFORM_TENANT_MANAGE')) { <form class="panel route-form" (ngSubmit)="create()"><h2>Nowa trasa</h2><label>Szukaj firmy <input name="tenantSearch" [(ngModel)]="tenantSearch" (input)="searchTenants()" placeholder="Nazwa lub slug" /></label><label>Firma <select name="tenantId" [(ngModel)]="tenantId" required><option value="">Wybierz firmę</option>@for (tenant of tenants(); track tenant.id) { <option [value]="tenant.id">{{ tenant.name }} ({{ tenant.slug }})</option> }</select></label><label>Numer odbiorcy <input name="recipient" [(ngModel)]="recipient" placeholder="+48…" /></label><label>Numer SIM <input name="simNumber" type="number" min="0" [(ngModel)]="simNumber" /></label><p>Podaj numer odbiorcy, SIM albo oba pola. Urządzenie i klucz podpisu są konfigurowane na serwerze.</p><button class="primary" type="submit" [disabled]="saving()">{{ saving() ? 'Zapisywanie…' : 'Dodaj trasę' }}</button></form> }
      <section class="panel"><h2>Aktywne trasy</h2>@if (loading()) { <p role="status">Ładowanie tras…</p> } @else if (routes().length === 0) { <p>Nie ma aktywnych tras SMS.</p> } @else { <div class="route-list">@for (route of routes(); track route.id) { <article><div><strong>{{ route.recipient || 'Bez numeru odbiorcy' }}</strong><small>Firma: {{ tenantName(route.tenantId) }} · SIM: {{ route.simNumber === null ? '—' : route.simNumber }} · Urządzenie: {{ route.deviceId }}</small></div>@if (auth.has('PLATFORM_TENANT_MANAGE')) { <button type="button" (click)="deactivate(route)">Wyłącz</button> }</article> }</div> }</section>
    </main>
  `,
  styles: `
    .routes-page { max-width: 75rem; gap: 1rem; }.route-form { display: grid; gap: .7rem; max-width: 42rem; }.route-form h2 { margin: 0; }.route-form label { display: grid; gap: .25rem; }.route-form p { color: var(--app-text-muted); }.route-form button { justify-self: start; }.route-list { display: grid; }.route-list article { display: flex; justify-content: space-between; align-items: center; gap: 1rem; padding: .8rem 0; border-top: 1px solid var(--app-border); }.route-list article div { display: grid; gap: .2rem; min-width: 0; }.route-list small { color: var(--app-text-muted); overflow-wrap: anywhere; }@media(max-width:760px){.routes-page{padding:1rem 1rem 6rem}.route-list article{align-items:start;flex-direction:column}}
  `,
})
export class SmsRoutesPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly routes = signal<SmsRoute[]>([]);
  readonly tenants = signal<TenantSummary[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  tenantId = '';
  tenantSearch = '';
  recipient = '';
  simNumber: number | null = null;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  ngOnInit(): void { this.load(); this.loadTenants(); }
  ngOnDestroy(): void { if (this.searchTimer) clearTimeout(this.searchTimer); }
  loadTenants(): void { this.api.tenants(0, 100, this.tenantSearch).subscribe({ next: result => this.tenants.set(result.content), error: () => this.tenants.set([]) }); }
  searchTenants(): void { if (this.searchTimer) clearTimeout(this.searchTimer); this.searchTimer = setTimeout(() => this.loadTenants(), 250); }
  tenantName(id: string): string { return this.tenants().find(tenant => tenant.id === id)?.name ?? id; }
  load(): void { this.loading.set(true); this.api.smsRoutes().subscribe({ next: routes => { this.routes.set(routes); this.loading.set(false); this.error.set(''); }, error: error => { this.error.set(problemMessage(error)); this.loading.set(false); } }); }
  create(): void { if (this.saving()) return; this.saving.set(true); this.api.createSmsRoute(this.tenantId.trim(), this.recipient.trim() || null, this.simNumber).subscribe({ next: () => { this.saving.set(false); this.recipient = ''; this.simNumber = null; this.load(); }, error: error => { this.saving.set(false); this.error.set(problemMessage(error)); } }); }
  deactivate(route: SmsRoute): void { if (!window.confirm('Wyłączyć trasę SMS?')) return; this.api.deactivateSmsRoute(route.id).subscribe({ next: () => this.load(), error: error => this.error.set(problemMessage(error)) }); }
}
