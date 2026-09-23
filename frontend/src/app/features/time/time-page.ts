import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService, WorkforceEmployeeOption, WorkDay, WorkSummary } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-time-page',
  imports: [FormsModule, DatePipe],
  template: `
    <main class="page time-page">
      <div class="page-heading"><div><h1>Czas pracy</h1><p>Rejestracja i kontrola czasu pracy pracowników.</p></div>
        @if (auth.has('TIME_EDIT')) { <button class="primary" type="button" (click)="openCreate()"><i class="pi pi-plus" aria-hidden="true"></i> Dodaj czas pracy</button> }
      </div>
      <section class="summary-grid" aria-label="Podsumowanie miesiąca">
        <div class="panel summary-card"><span>Przepracowany czas</span><strong>{{ hours(summary()?.totalMinutes || 0) }}</strong></div>
        <div class="panel summary-card"><span>Dni z wpisem</span><strong>{{ summary()?.dayCount || 0 }}</strong></div>
      </section>
      <section class="panel list-panel">
        <div class="filters"><div class="month-control"><button type="button" (click)="moveMonth(-1)" aria-label="Poprzedni miesiąc"><i class="pi pi-chevron-left"></i></button><strong>{{ monthLabel() }}</strong><button type="button" (click)="moveMonth(1)" aria-label="Następny miesiąc"><i class="pi pi-chevron-right"></i></button></div>
          <label class="employee-filter">Pracownik <select name="employeeFilter" [(ngModel)]="employeeId" (change)="applyFilters()"><option value="">Wszyscy pracownicy</option>@for (person of employeeOptions(); track person.id) { <option [value]="person.id">{{ person.firstName }} {{ person.lastName }}</option> }</select></label>
          <label class="employee-search">Szukaj pracownika <input name="employeeSearch" [(ngModel)]="employeeSearch" (input)="searchEmployees()" placeholder="Imię lub nazwisko" /></label>
        </div>
        @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
        @if (loading()) { <p role="status">Ładowanie wpisów…</p> }
        @else if (days().length === 0) { <p class="empty">Brak wpisów czasu pracy w wybranym miesiącu.</p> }
        @else {
          <div class="desktop-list"><table><thead><tr><th>Pracownik</th><th>Data</th><th>Przedziały</th><th>Czas</th><th>Źródło</th><th>Akcje</th></tr></thead><tbody>
            @for (day of days(); track day.id) { <tr><td>{{ employeeName(day.employeeId) }}</td><td>{{ day.workDate | date:'dd.MM.yyyy' }}</td><td>{{ intervalsLabel(day) }}</td><td>{{ hours(day.totalMinutes) }}</td><td>{{ day.source === 'SMS' ? 'SMS' : 'Ręcznie' }}</td><td class="actions">@if (auth.has('TIME_EDIT')) { <button type="button" (click)="openEdit(day)">Edytuj</button><button type="button" (click)="cancel(day)">Anuluj</button> }</td></tr> }
          </tbody></table></div>
          <div class="mobile-list">@for (day of days(); track day.id) { <article class="entry"><div><strong>{{ employeeName(day.employeeId) }}</strong><small>{{ day.workDate | date:'dd.MM.yyyy' }} · {{ intervalsLabel(day) }}</small></div><strong>{{ hours(day.totalMinutes) }}</strong>@if (auth.has('TIME_EDIT')) { <div class="mobile-actions"><button type="button" (click)="openEdit(day)">Edytuj</button><button type="button" (click)="cancel(day)">Anuluj</button></div> }</article> }</div>
        }
        <div class="pagination"><span>Strona {{ page() + 1 }} z {{ totalPages() || 1 }}</span><button type="button" [disabled]="page() === 0 || loading()" (click)="setPage(page() - 1)">Poprzednia</button><button type="button" [disabled]="page() + 1 >= totalPages() || loading()" (click)="setPage(page() + 1)">Następna</button></div>
      </section>
      @if (drawerOpen()) { <div class="drawer-backdrop" (click)="closeDrawer()"></div><aside class="entry-drawer" aria-label="Formularz czasu pracy"><div class="drawer-heading"><h2>{{ editing() ? 'Edytuj czas pracy' : 'Dodaj czas pracy' }}</h2><button type="button" (click)="closeDrawer()" aria-label="Zamknij"><i class="pi pi-times"></i></button></div>
        @if (formError()) { <p class="alert" role="alert">{{ formError() }}</p> }
        <form id="work-form" (ngSubmit)="save()">
          <label>Pracownik <select name="formEmployee" [(ngModel)]="formEmployeeId" [disabled]="!!editing()" required><option value="">Wybierz pracownika</option>@for (person of employeeOptions(); track person.id) { <option [value]="person.id">{{ person.firstName }} {{ person.lastName }}</option> }</select></label>
          <label>Data <input name="workDate" type="date" [(ngModel)]="formDate" [disabled]="!!editing()" required /></label>
          @for (interval of formIntervals; track $index; let index = $index) { <div class="interval-row"><label>Od <input type="time" [name]="'start' + index" [(ngModel)]="interval.startTime" required /></label><label>Do <input type="time" [name]="'end' + index" [(ngModel)]="interval.endTime" required /></label>@if (formIntervals.length > 1) { <button type="button" (click)="removeInterval(index)" aria-label="Usuń przedział"><i class="pi pi-trash"></i></button> }</div> }
          <button type="button" class="add-interval" (click)="addInterval()"><i class="pi pi-plus"></i> Dodaj przedział</button>
        </form>
        <div class="drawer-actions"><button type="button" (click)="closeDrawer()">Anuluj</button><button class="primary" type="submit" form="work-form" [disabled]="saving()">{{ saving() ? 'Zapisywanie…' : 'Zapisz' }}</button></div>
      </aside> }
    </main>
  `,
  styles: `
    .time-page { max-width: 100rem; gap: 1rem; }.page-heading { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }.page-heading button { white-space: nowrap; }
    .summary-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: .7rem; }.summary-card { display: grid; gap: .4rem; }.summary-card span { color: var(--app-text-muted); }.summary-card strong { font-size: 1.8rem; }
    .list-panel { padding: 0; overflow: hidden; }.filters { display: flex; align-items: end; gap: .7rem; padding: 1rem; flex-wrap: wrap; }.filters label { display: grid; gap: .25rem; font-size: .8rem; min-width: 11rem; }.month-control { display: flex; align-items: center; justify-content: space-between; gap: .5rem; min-width: 13rem; }.month-control button { border: 0; }.desktop-list { overflow-x: auto; }.desktop-list th, .desktop-list td { padding: .8rem; }.actions { white-space: nowrap; }.actions button { margin-right: .3rem; }.empty { padding: 2rem; color: var(--app-text-muted); }.mobile-list { display: none; }.pagination { padding: .8rem; justify-content: flex-end; }
    .drawer-backdrop { position: fixed; inset: 4.5rem 0 0; z-index: 35; background: #142b4333; }.entry-drawer { position: fixed; z-index: 36; top: 4.5rem; bottom: 0; right: 0; width: min(26rem,100vw); background: #fff; display: flex; flex-direction: column; overflow: auto; box-shadow: var(--app-shadow); }.drawer-heading, .drawer-actions { display: flex; justify-content: space-between; align-items: center; gap: .5rem; padding: 1rem; border-bottom: 1px solid var(--app-border); }.drawer-heading h2 { margin: 0; }.drawer-heading button { border: 0; }.entry-drawer form { display: grid; gap: 1rem; padding: 1rem; }.entry-drawer label { display: grid; gap: .3rem; }.interval-row { display: flex; align-items: end; gap: .4rem; }.interval-row label { flex: 1; min-width: 0; }.interval-row button { height: 2.7rem; }.add-interval { justify-self: start; }.drawer-actions { margin-top: auto; border-top: 1px solid var(--app-border); border-bottom: 0; }.drawer-actions button { flex: 1; }
    @media (max-width: 760px) { .time-page { padding: 1rem 1rem 6rem; }.page-heading { align-items: start; }.page-heading h1 { font-size: 1.5rem; }.page-heading button { font-size: .8rem; }.summary-grid { grid-template-columns: 1fr 1fr; }.summary-card strong { font-size: 1.25rem; }.filters { display: grid; }.filters label { min-width: 0; }.desktop-list { display: none; }.mobile-list { display: grid; }.entry { padding: .8rem 1rem; border-top: 1px solid var(--app-border); display: grid; grid-template-columns: 1fr auto; gap: .4rem; }.entry div:first-child { display: grid; }.entry small { color: var(--app-text-muted); }.mobile-actions { grid-column: 1/-1; display: flex; gap: .4rem; }.entry-drawer { top: 0; left: 0; width: 100%; z-index: 60; }.drawer-actions { position: sticky; bottom: 0; background: #fff; padding-bottom: max(1rem, env(safe-area-inset-bottom)); } }
  `,
})
export class TimePage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);
  readonly days = signal<WorkDay[]>([]);
  readonly employeeOptions = signal<WorkforceEmployeeOption[]>([]);
  readonly employeeNames = signal<Record<string, string>>({});
  readonly summary = signal<WorkSummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly formError = signal('');
  readonly saving = signal(false);
  readonly drawerOpen = signal(false);
  readonly editing = signal<WorkDay | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  month = `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}`;
  employeeId = '';
  employeeSearch = '';
  formEmployeeId = '';
  formDate = '';
  formIntervals: { startTime: string; endTime: string }[] = [{ startTime: '08:00', endTime: '16:00' }];
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly pendingEmployeeIds = new Set<string>();

  ngOnInit(): void {
    this.employeeId = this.route.snapshot.queryParamMap.get('employeeId') ?? '';
    this.loadEmployees();
    this.load();
  }
  ngOnDestroy(): void { if (this.searchTimer) clearTimeout(this.searchTimer); }
  private dates(): [string, string] {
    const [year, month] = this.month.split('-').map(Number);
    return [`${this.month}-01`, `${this.month}-${String(new Date(year, month, 0).getDate()).padStart(2, '0')}`];
  }
  monthLabel(): string { return new Intl.DateTimeFormat('pl-PL', { month: 'long', year: 'numeric' }).format(new Date(`${this.month}-01T12:00:00`)); }
  moveMonth(delta: number): void {
    const [year, month] = this.month.split('-').map(Number);
    const date = new Date(year, month - 1 + delta, 1);
    this.month = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
    this.applyFilters();
  }
  loadEmployees(): void {
    this.api.workforceEmployees(this.employeeSearch).subscribe({
      next: response => {
        this.employeeOptions.set(response.content);
        response.content.forEach(person => this.remember(person));
        if (this.employeeId && !response.content.some(person => person.id === this.employeeId)) {
          this.ensureEmployee(this.employeeId);
        }
      }, error: () => this.employeeOptions.set([]),
    });
  }
  searchEmployees(): void { if (this.searchTimer) clearTimeout(this.searchTimer); this.searchTimer = setTimeout(() => this.loadEmployees(), 250); }
  private remember(person: WorkforceEmployeeOption): void { this.employeeNames.update(names => ({ ...names, [person.id]: `${person.firstName} ${person.lastName}` })); }
  private ensureEmployee(id: string): void {
    if (this.employeeNames()[id] || this.pendingEmployeeIds.has(id)) return;
    this.pendingEmployeeIds.add(id);
    this.api.workforceEmployee(id).subscribe({
      next: person => { this.remember(person); this.pendingEmployeeIds.delete(id); if (this.employeeId === id) this.employeeOptions.update(items => [person, ...items]); },
      error: () => this.pendingEmployeeIds.delete(id),
    });
  }
  employeeName(id: string): string { return this.employeeNames()[id] ?? id; }
  hours(minutes: number): string { return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`; }
  intervalsLabel(day: WorkDay): string { return day.intervals.map(item => `${item.startTime.slice(0, 5)}–${item.endTime.slice(0, 5)}${item.endTime.startsWith('00:00') ? ' (+1 dzień)' : ''}`).join(', '); }
  load(): void {
    const [from, to] = this.dates();
    this.loading.set(true);
    this.api.workDays(from, to, this.employeeId, this.page()).subscribe({
      next: response => { this.days.set(response.content); response.content.forEach(day => this.ensureEmployee(day.employeeId)); this.totalPages.set(response.totalPages); this.loading.set(false); this.error.set(''); },
      error: error => { this.error.set(problemMessage(error)); this.loading.set(false); },
    });
    this.api.workSummary(this.month, this.employeeId).subscribe({ next: value => this.summary.set(value), error: () => this.summary.set(null) });
  }
  applyFilters(): void { this.page.set(0); this.load(); }
  setPage(page: number): void { this.page.set(page); this.load(); }
  openCreate(): void { this.editing.set(null); this.formEmployeeId = this.employeeId; this.formDate = `${this.month}-01`; this.formIntervals = [{ startTime: '08:00', endTime: '16:00' }]; this.formError.set(''); this.drawerOpen.set(true); }
  openEdit(day: WorkDay): void { this.editing.set(day); this.formEmployeeId = day.employeeId; this.formDate = day.workDate; this.formIntervals = day.intervals.map(item => ({ startTime: item.startTime.slice(0, 5), endTime: item.endTime.slice(0, 5) })); this.formError.set(''); this.drawerOpen.set(true); }
  closeDrawer(): void { this.drawerOpen.set(false); }
  addInterval(): void { if (this.formIntervals.length < 32) this.formIntervals.push({ startTime: '08:00', endTime: '16:00' }); }
  removeInterval(index: number): void { this.formIntervals.splice(index, 1); }
  save(): void {
    if (this.saving()) return;
    const operation = this.editing()
      ? this.api.updateWorkDay(this.editing()!, this.formIntervals)
      : this.api.createWorkDay(this.formEmployeeId, this.formDate, this.formIntervals);
    this.saving.set(true);
    operation.subscribe({ next: () => { this.saving.set(false); this.closeDrawer(); this.load(); }, error: error => { this.saving.set(false); this.formError.set(problemMessage(error)); } });
  }
  cancel(day: WorkDay): void {
    if (!window.confirm('Anulować wpis czasu pracy? Historia pozostanie zachowana.')) return;
    this.api.cancelWorkDay(day).subscribe({ next: () => this.load(), error: error => this.error.set(problemMessage(error)) });
  }
}
