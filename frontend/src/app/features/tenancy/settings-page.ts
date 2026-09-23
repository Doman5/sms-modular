import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService, TenantSummary } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-settings-page',
  imports: [FormsModule, RouterLink],
  template: `
    <main class="page settings-page"><div class="settings-links panel" aria-label="Sekcje ustawień">
      <h2>Ustawienia</h2><a class="active" routerLink="/settings"><i class="pi pi-building" aria-hidden="true"></i>Firma</a>
      @if (auth.has('USER_READ')) { <a routerLink="/users"><i class="pi pi-users" aria-hidden="true"></i>Użytkownicy</a> }
      @if (auth.has('SUBSCRIPTION_READ')) { <a routerLink="/subscription"><i class="pi pi-box" aria-hidden="true"></i>Pakiet i moduły</a> }
      @if (auth.has('AUDIT_READ')) { <a routerLink="/audit"><i class="pi pi-shield" aria-hidden="true"></i>Audyt</a> }
    </div><div class="settings-content"><div class="page-heading"><div><h1>Ustawienia firmy</h1><p>Zarządzaj podstawowymi danymi i preferencjami firmy.</p></div></div>
      @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
      @if (saved()) { <p class="notice" role="status">Ustawienia zapisane.</p> }
      @if (loading()) { <p class="panel" role="status">Ładowanie ustawień…</p> }
      @else if (tenant()) { <form class="settings-form" (ngSubmit)="save()">
        <section class="panel form-grid"><div><h2>Nazwa firmy</h2><p>Ta nazwa będzie widoczna w całym systemie.</p></div>
          <label>Nazwa firmy <input name="name" [(ngModel)]="name" required maxlength="160" [disabled]="!auth.has('TENANT_EDIT')" /></label>
          <small>{{ name.length }}/160</small><div class="slug">Identyfikator: {{ tenant()?.slug }}</div>
        </section>
        <section class="panel form-grid"><div><h2>Strefa czasowa i język</h2><p>Te ustawienia wpływają na wyświetlanie dat i godzin.</p></div>
          <div class="field-row"><label>Strefa czasowa <input name="timeZone" [(ngModel)]="timeZone" required [disabled]="!auth.has('TENANT_EDIT')" /></label>
            <label>Język <input name="locale" [(ngModel)]="locale" required [disabled]="!auth.has('TENANT_EDIT')" /></label></div>
        </section>
        @if (auth.has('TENANT_EDIT')) { <div class="form-actions"><button class="primary" type="submit" [disabled]="saving()"><i class="pi pi-save" aria-hidden="true"></i> {{ saving() ? 'Zapisywanie…' : 'Zapisz zmiany' }}</button></div> }
      </form> }
      </div>
    </main>
  `,
  styles: `
    .settings-page { max-width: 100rem; grid-template-columns: 14rem minmax(0,1fr); align-items: start; }.settings-links { display: grid; gap: .3rem; position: sticky; top: 1rem; }
    .settings-links h2 { margin: 0 0 .5rem; }.settings-links a { color: #213552; text-decoration: none; padding: .75rem; display: flex; gap: .8rem; align-items: center; border-radius: 8px; }
    .settings-links a.active, .settings-links a:hover { color: #056e73; background: #e0f7f7; }.settings-content, .settings-form { display: grid; gap: 1rem; }
    .settings-form .panel p { color: var(--app-text-muted); margin: -.3rem 0 .2rem; }.settings-form .panel small { justify-self: end; color: var(--app-text-muted); }
    .settings-form .form-grid { max-width: none; }.slug { color: var(--app-text-muted); font-size: .9rem; }.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
    .settings-form .form-actions { justify-content: end; }
    @media (max-width: 760px) { .settings-page { grid-template-columns: 1fr; }.settings-links { position: static; }.settings-links h2 { display: none; }
      .settings-links a { border-bottom: 1px solid var(--app-border); }.field-row { grid-template-columns: 1fr; } }
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
