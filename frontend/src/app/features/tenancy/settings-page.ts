import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService, TenantSummary } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-settings-page',
  imports: [FormsModule],
  template: `
    <main class="page"><div class="page-heading"><div><h1>Ustawienia firmy</h1><p>Podstawowe dane i preferencje tenanta.</p></div></div>
      @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
      @if (saved()) { <p class="notice" role="status">Ustawienia zapisane.</p> }
      @if (loading()) { <p class="panel" role="status">Ładowanie ustawień…</p> }
      @else if (tenant()) { <form class="panel form-grid" (ngSubmit)="save()">
        <label>Identyfikator <input [value]="tenant()?.slug" disabled /></label>
        <label>Nazwa firmy <input name="name" [(ngModel)]="name" required maxlength="160" [disabled]="!auth.has('TENANT_EDIT')" /></label>
        <label>Strefa czasowa <input name="timeZone" [(ngModel)]="timeZone" required [disabled]="!auth.has('TENANT_EDIT')" /></label>
        <label>Język <input name="locale" [(ngModel)]="locale" required [disabled]="!auth.has('TENANT_EDIT')" /></label>
        @if (auth.has('TENANT_EDIT')) { <div class="form-actions"><button class="primary" type="submit" [disabled]="saving()">{{ saving() ? 'Zapisywanie…' : 'Zapisz zmiany' }}</button></div> }
      </form> }
    </main>
  `,
})
export class SettingsPage implements OnInit {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly tenant = signal<TenantSummary | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly saved = signal(false);
  readonly error = signal('');
  name = '';
  timeZone = '';
  locale = '';

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading.set(true);
    this.api.tenantSettings().subscribe({
      next: (tenant) => {
        this.tenant.set(tenant); this.name = tenant.name; this.timeZone = tenant.timeZone;
        this.locale = tenant.locale; this.loading.set(false); this.error.set('');
      },
      error: (error) => { this.error.set(problemMessage(error)); this.loading.set(false); },
    });
  }
  save(): void {
    if (this.saving()) return;
    this.saving.set(true); this.saved.set(false);
    this.api.updateTenantSettings(this.name, this.timeZone, this.locale).subscribe({
      next: (tenant) => {
        this.tenant.set(tenant); this.saving.set(false); this.saved.set(true);
        this.auth.loadContext().subscribe({ error: (error) => this.error.set(problemMessage(error)) });
      },
      error: (error) => { this.error.set(problemMessage(error)); this.saving.set(false); },
    });
  }
}
