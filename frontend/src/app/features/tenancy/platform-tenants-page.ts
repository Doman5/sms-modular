import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { TenantSummary } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-platform-tenants-page',
  imports: [FormsModule],
  template: `
    <main class="page"><div class="page-heading"><div><h1>Tenanci</h1><p>Firmy obsługiwane przez platformę.</p></div><button class="primary" type="button" (click)="creating.set(true)">Dodaj firmę</button></div>
      @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
      @if (temporaryPassword()) { <div class="notice" role="status"><strong>Hasło pierwszego administratora — skopiuj teraz:</strong> <code>{{ temporaryPassword() }}</code><button type="button" (click)="temporaryPassword.set('')">Zamknij</button></div> }
      @if (creating()) { <form class="panel form-grid" (ngSubmit)="create()"><h2>Nowa firma</h2>
        <label>Identyfikator <input name="slug" [(ngModel)]="slug" required pattern="[a-z0-9-]+" /></label>
        <label>Nazwa <input name="name" [(ngModel)]="name" required /></label>
        <label>Strefa czasowa <input name="zone" [(ngModel)]="timeZone" required /></label>
        <label>Język <input name="locale" [(ngModel)]="locale" required /></label>
        <label>E-mail administratora <input type="email" name="email" [(ngModel)]="adminEmail" required /></label>
        <label>Imię i nazwisko administratora <input name="adminName" [(ngModel)]="adminDisplayName" required /></label>
        <div class="form-actions"><button class="primary" type="submit" [disabled]="saving()">Utwórz firmę</button><button type="button" (click)="creating.set(false)">Anuluj</button></div>
      </form> }
      @if (loading()) { <p class="panel" role="status">Ładowanie firm…</p> }
      @else if (tenants().length === 0) { <p class="panel">Brak firm.</p> }
      @else { <div class="panel table-wrap"><table><thead><tr><th>Firma</th><th>Strefa</th><th>Status</th><th>Akcje</th></tr></thead><tbody>
        @for (tenant of tenants(); track tenant.id) { <tr><td><strong>{{ tenant.name }}</strong><small>{{ tenant.slug }}</small></td><td>{{ tenant.timeZone }}</td><td>{{ tenant.status }}</td><td class="actions">
          @if (tenant.status === 'ACTIVE') { <button type="button" (click)="status(tenant, 'suspend')">Zawieś</button> }
          @if (tenant.status === 'SUSPENDED') { <button type="button" (click)="status(tenant, 'activate')">Aktywuj</button> }
          @if (tenant.status !== 'CLOSED') { <button type="button" (click)="status(tenant, 'close')">Zamknij</button> }
        </td></tr> }</tbody></table></div> }
      <div class="pagination"><button type="button" [disabled]="page() === 0 || loading()" (click)="setPage(page() - 1)">Poprzednia</button><span>Strona {{ page() + 1 }} z {{ totalPages() || 1 }}</span><button type="button" [disabled]="page() + 1 >= totalPages() || loading()" (click)="setPage(page() + 1)">Następna</button></div>
    </main>
  `,
})
export class PlatformTenantsPage implements OnInit {
  private readonly api = inject(ApiService);
  readonly tenants = signal<TenantSummary[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly creating = signal(false);
  readonly error = signal('');
  readonly temporaryPassword = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  slug = '';
  name = '';
  timeZone = 'Europe/Warsaw';
  locale = 'pl-PL';
  adminEmail = '';
  adminDisplayName = '';

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading.set(true);
    this.api.tenants(this.page()).subscribe({
      next: (result) => { this.tenants.set(result.content); this.totalPages.set(result.totalPages); this.loading.set(false); this.error.set(''); },
      error: (error) => { this.error.set(problemMessage(error)); this.loading.set(false); },
    });
  }
  setPage(page: number): void { this.page.set(page); this.load(); }
  create(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.api.provisionTenant({
      slug: this.slug, name: this.name, timeZone: this.timeZone, locale: this.locale,
      adminEmail: this.adminEmail, adminDisplayName: this.adminDisplayName,
    }).subscribe({
      next: (result) => { this.temporaryPassword.set(result.temporaryPassword); this.creating.set(false); this.saving.set(false); this.load(); },
      error: (error) => { this.error.set(problemMessage(error)); this.saving.set(false); },
    });
  }
  status(tenant: TenantSummary, action: 'suspend' | 'activate' | 'close'): void {
    if (action === 'close' && !window.confirm(`Zamknąć firmę ${tenant.name}? Tej operacji nie można cofnąć.`)) return;
    this.api.setTenantStatus(tenant.id, action).subscribe({ next: () => this.load(), error: (e) => this.error.set(problemMessage(e)) });
  }
}
