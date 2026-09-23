import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService, Employee } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-employee-detail-page',
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <main class="page employee-detail-page">
      <nav class="breadcrumb" aria-label="Ścieżka"><a routerLink="/employees">Pracownicy</a><i class="pi pi-angle-right" aria-hidden="true"></i>@if (employee(); as person) { <span>{{ person.firstName }} {{ person.lastName }}</span> }</nav>
      @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
      @if (loading()) { <p class="panel" role="status">Ładowanie danych pracownika…</p> }
      @else if (employee(); as person) {
        <div class="detail-header"><div class="identity"><span class="avatar">{{ initials(person) }}</span><div><h1>{{ person.firstName }} {{ person.lastName }}</h1><div class="badges"><span class="status" [class.inactive]="person.status === 'INACTIVE'">{{ person.status === 'ACTIVE' ? 'Aktywny' : 'Nieaktywny' }}</span><span class="position">{{ person.position }}</span></div></div></div>
          <div class="header-actions">@if (auth.has('EMPLOYEE_EDIT')) { <button type="button" (click)="startEdit()"><i class="pi pi-pencil" aria-hidden="true"></i> Edytuj dane</button> }
            @if (auth.has('EMPLOYEE_STATUS_CHANGE')) { <button type="button" [disabled]="saving()" (click)="changeStatus(person)">{{ person.status === 'ACTIVE' ? 'Dezaktywuj' : 'Aktywuj' }}</button> }
          </div>
        </div>
        <nav class="tabs" aria-label="Sekcje pracownika"><span class="active">Podsumowanie</span>
          @if (auth.has('TIME_READ') && auth.hasCapability('TIME_TRACKING')) { <a routerLink="/time" [queryParams]="{ employeeId: person.id }">Czas pracy</a> }
          @if (auth.has('ABSENCE_READ') && auth.hasCapability('ABSENCE_EVENTS')) { <a routerLink="/absence-days" [queryParams]="{ employeeId: person.id }">Braki obecności</a> }
        </nav>
        <section class="panel details-card" aria-label="Dane pracownika">
          <div><span>Telefon</span><strong>{{ person.phone }}</strong></div>
          <div><span>Adres e-mail</span><strong>{{ person.email || 'Nie podano' }}</strong></div>
          <div><span>Stanowisko</span><strong>{{ person.position }}</strong></div>
          <div><span>Data zatrudnienia</span><strong>{{ person.employmentDate | date:'dd.MM.yyyy' }}</strong></div>
          <div><span>Status</span><strong>{{ person.status === 'ACTIVE' ? 'Aktywny' : 'Nieaktywny' }}</strong></div>
          @if (person.note) { <div class="note"><span>Notatka</span><strong>{{ person.note }}</strong></div> }
        </section>
        @if (editing()) { <div class="drawer-backdrop" (click)="editing.set(false)"></div><aside class="edit-drawer" aria-label="Edytuj pracownika">
          <div class="drawer-heading"><h2>Edytuj pracownika</h2><button type="button" (click)="editing.set(false)" aria-label="Zamknij"><i class="pi pi-times" aria-hidden="true"></i></button></div>
          @if (formError()) { <p class="alert" role="alert">{{ formError() }}</p> }
          <form id="edit-employee" class="edit-form" (ngSubmit)="save()">
            <label>Imię <span aria-hidden="true">*</span><input name="firstName" [(ngModel)]="firstName" required maxlength="100" /></label>
            <label>Nazwisko <span aria-hidden="true">*</span><input name="lastName" [(ngModel)]="lastName" required maxlength="100" /></label>
            <label>Adres e-mail <input name="email" type="email" [(ngModel)]="email" maxlength="150" /></label>
            <label>Telefon <span aria-hidden="true">*</span><input name="phone" type="tel" [(ngModel)]="phone" required maxlength="30" /></label>
            <label>Stanowisko <span aria-hidden="true">*</span><input name="position" [(ngModel)]="position" required maxlength="120" /></label>
            <label>Data zatrudnienia <span aria-hidden="true">*</span><input name="employmentDate" type="date" [(ngModel)]="employmentDate" required /></label>
            <label>Notatka <textarea name="note" [(ngModel)]="note" maxlength="2000" rows="4"></textarea></label>
          </form>
          <div class="drawer-actions"><button type="button" (click)="editing.set(false)">Anuluj</button><button class="primary" type="submit" form="edit-employee" [disabled]="saving()">{{ saving() ? 'Zapisywanie…' : 'Zapisz' }}</button></div>
        </aside> }
      }
    </main>
  `,
  styles: `
    .employee-detail-page { max-width: 100rem; gap: 1rem; }.breadcrumb { display: flex; gap: .5rem; align-items: center; color: var(--app-text-muted); font-size: .85rem; }.breadcrumb a { color: inherit; }
    .detail-header { display: flex; align-items: center; justify-content: space-between; gap: 1rem; flex-wrap: wrap; }.identity { display: flex; align-items: center; gap: .9rem; }.identity h1 { margin: 0; }.avatar { width: 3.3rem; height: 3.3rem; border-radius: 50%; background: #dcf8f3; display: grid; place-items: center; color: #086e70; font-weight: 800; }
    .badges { display: flex; gap: .5rem; margin-top: .4rem; }.status, .position { display: inline-block; padding: .3rem .6rem; border-radius: 99px; background: #ddf8ec; color: #0a7955; font-size: .8rem; }.status.inactive { background: #ffe7e9; color: #a62b42; }.position { background: #e9eef8; color: #31445f; }
    .header-actions { display: flex; gap: .5rem; }.tabs { display: flex; flex-wrap: wrap; border-bottom: 1px solid var(--app-border); }.tabs span, .tabs a { display: inline-block; padding: .6rem 1rem; }.tabs a { color: var(--app-text); text-decoration: none; }.tabs .active { color: var(--app-primary); border-bottom: 3px solid var(--app-primary); font-weight: 700; }
    .details-card { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 1rem; }.details-card > div { display: grid; gap: .35rem; min-width: 0; }.details-card span { color: var(--app-text-muted); font-size: .84rem; }.details-card strong { overflow-wrap: anywhere; }.details-card .note { grid-column: 1 / -1; white-space: pre-wrap; }
    .drawer-backdrop { position: fixed; inset: 4.5rem 0 0; z-index: 35; background: #142b431c; }.edit-drawer { position: fixed; z-index: 36; top: 4.5rem; bottom: 0; right: 0; width: 20rem; background: #fff; border-left: 1px solid var(--app-border); display: flex; flex-direction: column; overflow: auto; }
    .drawer-heading { display: flex; align-items: center; justify-content: space-between; padding: 1rem; border-bottom: 1px solid var(--app-border); }.drawer-heading h2 { margin: 0; }.drawer-heading button { border: 0; padding: .4rem; }
    .edit-form { display: grid; gap: .8rem; padding: 1rem; }.edit-form label { display: block; font-size: .85rem; font-weight: 600; }.edit-form label > span { color: #bd2f45; }.edit-form input, .edit-form textarea { display: block; margin-top: .3rem; }.edit-form textarea { width: 100%; font: inherit; border: 1px solid var(--app-border); border-radius: 8px; padding: .7rem; resize: vertical; }
    .drawer-actions { display: flex; gap: .6rem; padding: 1rem; border-top: 1px solid var(--app-border); margin-top: auto; }.drawer-actions button { flex: 1; }
    @media (max-width: 760px) { .employee-detail-page { padding: 1rem 1rem 6rem; }.identity h1 { font-size: 1.35rem; }.avatar { width: 2.8rem; height: 2.8rem; }.header-actions { width: 100%; }.header-actions button { flex: 1; }.details-card { grid-template-columns: 1fr; }.details-card .note { grid-column: 1; }
      .drawer-backdrop { display: none; }.edit-drawer { top: 0; left: 0; width: 100%; z-index: 60; border-left: 0; }.drawer-actions { position: sticky; bottom: 0; background: #fff; padding-bottom: max(1rem, env(safe-area-inset-bottom)); }
    }
  `,
})
export class EmployeeDetailPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);
  readonly employee = signal<Employee | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly formError = signal('');
  readonly saving = signal(false);
  readonly editing = signal(false);
  firstName = '';
  lastName = '';
  email = '';
  phone = '';
  position = '';
  employmentDate = '';
  note = '';
  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading.set(true);
    this.api.employee(this.id).subscribe({
      next: person => { this.employee.set(person); this.loading.set(false); this.error.set(''); },
      error: error => { this.error.set(problemMessage(error)); this.loading.set(false); },
    });
  }
  initials(person: Employee): string { return `${person.firstName.charAt(0)}${person.lastName.charAt(0)}`.toUpperCase(); }
  startEdit(): void {
    const person = this.employee();
    if (!person) return;
    this.firstName = person.firstName; this.lastName = person.lastName; this.phone = person.phone;
    this.email = person.email ?? ''; this.position = person.position; this.note = person.note ?? '';
    this.employmentDate = person.employmentDate; this.formError.set(''); this.editing.set(true);
  }
  save(): void {
    const person = this.employee();
    if (!person || this.saving()) return;
    this.saving.set(true);
    this.api.updateEmployee(person.id, { firstName: this.firstName.trim(), lastName: this.lastName.trim(),
      phone: this.phone.trim(), email: this.email.trim() || null, position: this.position.trim(),
      employmentDate: this.employmentDate, note: this.note.trim() || null, version: person.version }).subscribe({
      next: updated => { this.employee.set(updated); this.editing.set(false); this.saving.set(false); this.error.set(''); },
      error: error => { this.formError.set(problemMessage(error)); this.saving.set(false); },
    });
  }
  changeStatus(person: Employee): void {
    if (this.saving()) return;
    const action = person.status === 'ACTIVE' ? 'deactivate' : 'activate';
    if (action === 'deactivate' && !window.confirm('Dezaktywować pracownika? Historia pozostanie zachowana.')) return;
    this.saving.set(true);
    this.api.setEmployeeStatus(person.id, action, person.version).subscribe({
      next: updated => { this.employee.set(updated); this.error.set(''); this.saving.set(false); },
      error: error => { this.error.set(problemMessage(error)); this.saving.set(false); },
    });
  }
}
