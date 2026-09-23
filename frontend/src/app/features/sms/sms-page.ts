import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, SmsMessage, SmsResolveInput, WorkforceEmployeeOption } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-sms-page',
  imports: [DatePipe, FormsModule],
  template: `
    <main class="page sms-page">
      <div class="page-heading"><div><h1>Weryfikacja SMS</h1><p>Wiadomości przychodzące i decyzje wymagające uwagi.</p></div>
        <button type="button" (click)="load()"><i class="pi pi-refresh" aria-hidden="true"></i> Odśwież</button></div>
      <section class="panel queue-panel">
        <div class="filters"><label>Od <input name="from" type="date" [(ngModel)]="fromDate" (change)="applyFilters()" /></label>
          <label>Do <input name="to" type="date" [(ngModel)]="toDate" (change)="applyFilters()" /></label>
          <label>Status <select name="status" [(ngModel)]="statusFilter" (change)="applyFilters()"><option value="">Wszystkie</option><option value="REVIEW_REQUIRED">Do weryfikacji</option><option value="PENDING">Oczekujące</option><option value="COMPLETED">Zakończone</option><option value="ERROR">Błąd</option><option value="DISMISSED">Odrzucone</option><option value="EXPIRED">Wygasłe</option></select></label>
          <label>Pracownik <select name="employee" [(ngModel)]="employeeId" (change)="applyFilters()"><option value="">Wszyscy</option>@for (employee of employees(); track employee.id) { <option [value]="employee.id">{{ employee.firstName }} {{ employee.lastName }}</option> }</select></label>
          <label class="review-filter"><input name="reviewOnly" type="checkbox" [(ngModel)]="reviewOnly" (change)="applyFilters()" /> Tylko do weryfikacji</label></div>
        @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
        @if (loading()) { <p class="empty" role="status">Ładowanie wiadomości…</p> }
        @else if (messages().length === 0) { <p class="empty">Brak wiadomości dla wybranych filtrów.</p> }
        @else { <div class="desktop-list"><table><thead><tr><th>Odebrano</th><th>Nadawca</th><th>Pracownik</th><th>Status</th><th>Powód</th><th></th></tr></thead><tbody>
          @for (message of messages(); track message.id) { <tr><td>{{ message.receivedAt | date:'dd.MM.yyyy HH:mm' }}</td><td>{{ message.sender || 'Zanonimizowany' }}</td><td>{{ employeeName(message.employeeId) }}</td><td><span class="status" [class.review]="message.status === 'REVIEW_REQUIRED'">{{ statusLabel(message.status) }}</span></td><td>{{ reasonLabel(message.reviewReason) }}</td><td><button type="button" (click)="open(message)">Szczegóły</button></td></tr> }
        </tbody></table></div>
        <div class="mobile-list">@for (message of messages(); track message.id) { <button type="button" class="sms-card" (click)="open(message)"><strong>{{ message.sender || 'Zanonimizowany' }}</strong><small>{{ message.receivedAt | date:'dd.MM.yyyy HH:mm' }} · {{ statusLabel(message.status) }}</small><span>{{ reasonLabel(message.reviewReason) }}</span></button> }</div> }
        <div class="pagination"><span>Strona {{ page() + 1 }} z {{ totalPages() || 1 }}</span><button type="button" [disabled]="page() === 0 || loading()" (click)="setPage(page() - 1)">Poprzednia</button><button type="button" [disabled]="page() + 1 >= totalPages() || loading()" (click)="setPage(page() + 1)">Następna</button></div>
      </section>
      @if (selected(); as sms) { <div class="drawer-backdrop" (click)="close()"></div><aside class="sms-drawer" aria-label="Szczegóły SMS"><div class="drawer-head"><h2>Wiadomość SMS</h2><button type="button" aria-label="Zamknij" (click)="close()"><i class="pi pi-times"></i></button></div>
        @if (detailLoading()) { <p role="status">Ładowanie szczegółów…</p> }
        @if (detailError()) { <p class="alert" role="alert">{{ detailError() }}</p> }
        @if (!detailLoading()) { <div class="drawer-body"><dl><dt>Odebrano</dt><dd>{{ sms.receivedAt | date:'dd.MM.yyyy HH:mm' }}</dd><dt>Nadawca</dt><dd>{{ sms.sender || 'Zanonimizowany' }}</dd><dt>Status</dt><dd>{{ statusLabel(sms.status) }}</dd><dt>Powód</dt><dd>{{ reasonLabel(sms.reviewReason) }}</dd></dl>
          <div class="message-content">{{ sms.content || 'Treść niedostępna po zakończeniu retencji.' }}</div>
          @if (auth.has('SMS_REVIEW') && (sms.status === 'REVIEW_REQUIRED' || sms.status === 'ERROR')) {
            <form id="resolve-sms" (ngSubmit)="resolve()"><label>Decyzja <select name="category" [(ngModel)]="category"><option value="WORK_TIME">Czas pracy</option><option value="ABSENCE">Nieobecność</option><option value="DISMISS">Odrzuć wiadomość</option></select></label>
              @if (category !== 'DISMISS') { <label>Szukaj pracownika <input name="employeeSearch" [(ngModel)]="employeeSearch" (input)="searchEmployees()" placeholder="Imię lub nazwisko" /></label><label>Pracownik <select name="employeeId" [(ngModel)]="formEmployeeId" required><option value="">Wybierz pracownika</option>@for (employee of employees(); track employee.id) { <option [value]="employee.id">{{ employee.firstName }} {{ employee.lastName }}</option> }</select></label> }
              @if (category === 'WORK_TIME') { <label>Data pracy <input name="workDate" type="date" [(ngModel)]="workDate" required /></label><div class="time-fields"><label>Od <input name="startTime" type="time" [(ngModel)]="startTime" required /></label><label>Do <input name="endTime" type="time" [(ngModel)]="endTime" required /></label></div> }
              @if (category === 'ABSENCE') { <label>Data nieobecności <input name="absenceDate" type="date" [(ngModel)]="absenceDate" required /></label> }
            </form>
            <div class="drawer-actions"><button type="button" [disabled]="saving()" (click)="reparse()">Przetwórz ponownie</button><button class="primary" type="submit" form="resolve-sms" [disabled]="saving()">{{ saving() ? 'Zapisywanie…' : 'Zatwierdź decyzję' }}</button></div>
          }
        </div> }
      </aside> }
    </main>
  `,
  styles: `
    .sms-page { max-width: 100rem; gap: 1rem; }.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }.queue-panel { padding: 0; overflow: hidden; }
    .filters { display: flex; gap: .7rem; flex-wrap: wrap; align-items: end; padding: 1rem; }.filters label { display: grid; gap: .25rem; min-width: 9rem; font-size: .82rem; }.filters .review-filter { display: flex; align-items: center; min-width: auto; padding-bottom: .65rem; }
    .desktop-list { overflow-x: auto; }.desktop-list th, .desktop-list td { padding: .8rem; }.status { display: inline-block; padding: .25rem .5rem; border-radius: 999px; background: #edf1f6; white-space: nowrap; }.status.review { background: #fff1d4; color: #865400; }.empty { padding: 2rem; color: var(--app-text-muted); }.mobile-list { display: none; }.pagination { padding: .8rem; justify-content: flex-end; }
    .drawer-backdrop { position: fixed; inset: 4.5rem 0 0; z-index: 35; background: #142b4333; }.sms-drawer { position: fixed; z-index: 36; top: 4.5rem; right: 0; bottom: 0; width: min(32rem,100vw); background: #fff; overflow: auto; display: flex; flex-direction: column; box-shadow: var(--app-shadow); }.drawer-head, .drawer-actions { display: flex; align-items: center; justify-content: space-between; gap: .5rem; padding: 1rem; border-bottom: 1px solid var(--app-border); }.drawer-head h2 { margin: 0; }.drawer-head button { border: 0; }.drawer-body { display: grid; gap: 1rem; padding: 1rem; }.drawer-body dl { display: grid; grid-template-columns: 7rem 1fr; gap: .4rem; margin: 0; }.drawer-body dt { color: var(--app-text-muted); }.drawer-body dd { margin: 0; }.message-content { padding: 1rem; background: #f4f8fb; border-radius: .7rem; white-space: pre-wrap; overflow-wrap: anywhere; }.drawer-body form { display: grid; gap: .7rem; }.drawer-body label { display: grid; gap: .3rem; }.time-fields { display: flex; gap: .5rem; }.time-fields label { flex: 1; }.drawer-actions { padding: 0; border: 0; }.drawer-actions button { flex: 1; }
    @media (max-width: 760px) { .sms-page { padding: 1rem 1rem 6rem; }.filters { display: grid; }.filters label { min-width: 0; }.desktop-list { display: none; }.mobile-list { display: grid; }.sms-card { display: grid; text-align: left; gap: .3rem; border: 0; border-top: 1px solid var(--app-border); border-radius: 0; padding: .85rem 1rem; }.sms-card small { color: var(--app-text-muted); }.sms-drawer { inset: 0; width: 100%; z-index: 60; }.drawer-actions { position: sticky; bottom: 0; background: #fff; padding-bottom: max(.7rem, env(safe-area-inset-bottom)); } }
  `,
})
export class SmsPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly messages = signal<SmsMessage[]>([]);
  readonly employees = signal<WorkforceEmployeeOption[]>([]);
  readonly selected = signal<SmsMessage | null>(null);
  readonly loading = signal(true);
  readonly detailLoading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly detailError = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  fromDate = this.localDate(-30);
  toDate = this.localDate(0);
  statusFilter = '';
  employeeId = '';
  reviewOnly = true;
  employeeSearch = '';
  category: SmsResolveInput['category'] = 'WORK_TIME';
  formEmployeeId = '';
  workDate = '';
  absenceDate = '';
  startTime = '08:00';
  endTime = '16:00';
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void { this.load(); this.loadEmployees(); }
  ngOnDestroy(): void { if (this.searchTimer) clearTimeout(this.searchTimer); }
  loadEmployees(): void { this.api.workforceEmployees(this.employeeSearch).subscribe({ next: result => { this.employees.set(result.content); if (this.formEmployeeId && !result.content.some(person => person.id === this.formEmployeeId)) this.api.workforceEmployee(this.formEmployeeId).subscribe({ next: person => this.employees.update(items => [person, ...items]), error: () => {} }); }, error: () => this.employees.set([]) }); }
  searchEmployees(): void { if (this.searchTimer) clearTimeout(this.searchTimer); this.searchTimer = setTimeout(() => this.loadEmployees(), 250); }
  private localDate(delta: number): string { const date = new Date(); date.setDate(date.getDate() + delta); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
  private range(): [string, string] { const to = new Date(`${this.toDate}T00:00:00`); to.setDate(to.getDate() + 1); return [new Date(`${this.fromDate}T00:00:00`).toISOString(), to.toISOString()]; }
  load(): void { const [from, to] = this.range(); this.loading.set(true); this.api.smsMessages(from, to, this.statusFilter, this.employeeId, this.reviewOnly, this.page()).subscribe({ next: result => { this.messages.set(result.content); this.totalPages.set(result.totalPages); this.loading.set(false); this.error.set(''); }, error: error => { this.error.set(problemMessage(error)); this.loading.set(false); } }); }
  applyFilters(): void { this.page.set(0); this.load(); }
  setPage(page: number): void { this.page.set(page); this.load(); }
  open(message: SmsMessage): void { this.selected.set(message); this.detailLoading.set(true); this.detailError.set(''); this.api.smsMessage(message.id).subscribe({ next: detail => { this.selected.set(detail); this.detailLoading.set(false); this.formEmployeeId = detail.employeeId ?? ''; this.workDate = this.localDate(0); this.absenceDate = this.workDate; if (this.formEmployeeId && !this.employees().some(person => person.id === this.formEmployeeId)) this.api.workforceEmployee(this.formEmployeeId).subscribe({ next: person => this.employees.update(items => [person, ...items]), error: () => {} }); }, error: error => { this.detailError.set(problemMessage(error)); this.detailLoading.set(false); } }); }
  close(): void { this.selected.set(null); }
  employeeName(id: string | null): string { const person = this.employees().find(value => value.id === id); return person ? `${person.firstName} ${person.lastName}` : '—'; }
  statusLabel(status: SmsMessage['status']): string { return ({ PENDING: 'Oczekuje', COMPLETED: 'Zakończony', REVIEW_REQUIRED: 'Do weryfikacji', DISMISSED: 'Odrzucony', ERROR: 'Błąd', EXPIRED: 'Wygasły' })[status]; }
  reasonLabel(reason: string | null): string { return ({ EMPLOYEE_UNKNOWN: 'Nieznany pracownik', EMPLOYEE_INACTIVE: 'Nieaktywny pracownik', AMBIGUOUS_CONTENT: 'Niejednoznaczna treść', CATEGORY_NOT_SUPPORTED: 'Typ nieobecności wymaga dodatku', CONFLICT: 'Konflikt z istniejącym dniem' } as Record<string, string>)[reason ?? ''] ?? '—'; }
  resolve(): void { const sms = this.selected(); if (!sms || this.saving()) return; const input: SmsResolveInput = { version: sms.version, category: this.category, employeeId: this.category === 'DISMISS' ? null : this.formEmployeeId, workDate: this.category === 'WORK_TIME' ? this.workDate : null, startTime: this.category === 'WORK_TIME' ? this.startTime : null, endTime: this.category === 'WORK_TIME' ? this.endTime : null, absenceDate: this.category === 'ABSENCE' ? this.absenceDate : null }; this.saving.set(true); this.api.resolveSms(sms.id, input).subscribe({ next: () => this.afterDecision(sms.id), error: error => { this.saving.set(false); this.detailError.set(problemMessage(error)); } }); }
  reparse(): void { const sms = this.selected(); if (!sms || this.saving()) return; this.saving.set(true); this.api.reparseSms(sms.id, sms.version).subscribe({ next: () => this.afterDecision(sms.id), error: error => { this.saving.set(false); this.detailError.set(problemMessage(error)); } }); }
  private afterDecision(id: string): void { this.saving.set(false); const next = this.messages().find(message => message.id !== id && message.status === 'REVIEW_REQUIRED'); this.close(); this.load(); if (next) this.open(next); }
}
