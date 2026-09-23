import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import { AbsenceCalendarDay, AbsenceDay, ApiService, WorkforceEmployeeOption } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-absence-page',
  imports: [DatePipe, FormsModule],
  template: `
    <main class="page absence-page">
      <div class="page-heading"><div><h1>Braki obecności</h1><p>Oznaczaj dni, w których pracownik nie był obecny.</p></div>
        @if (auth.has('ABSENCE_EDIT')) { <button class="primary" type="button" (click)="openCreate()"><i class="pi pi-plus" aria-hidden="true"></i> Oznacz nieobecność</button> }
      </div>
      <section class="panel calendar-panel">
        <div class="filters"><div class="month-control"><button type="button" (click)="moveMonth(-1)" aria-label="Poprzedni miesiąc"><i class="pi pi-chevron-left"></i></button><strong>{{ monthLabel() }}</strong><button type="button" (click)="moveMonth(1)" aria-label="Następny miesiąc"><i class="pi pi-chevron-right"></i></button></div>
          <label>Pracownik <select name="employeeId" [(ngModel)]="employeeId" (change)="applyFilters()"><option value="">Wszyscy pracownicy</option>@for (person of employeeOptions(); track person.id) { <option [value]="person.id">{{ person.firstName }} {{ person.lastName }}</option> }</select></label>
          <label>Szukaj pracownika <input name="employeeSearch" [(ngModel)]="employeeSearch" (input)="searchEmployees()" placeholder="Imię lub nazwisko" /></label>
          @if (selectedDate()) { <button type="button" (click)="selectedDate.set(''); applyFilters()">Wyczyść dzień</button> }
        </div>
        @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
        <div class="calendar-layout">
          <div class="calendar" aria-label="Kalendarz nieobecności"><div class="weekday">Pn</div><div class="weekday">Wt</div><div class="weekday">Śr</div><div class="weekday">Cz</div><div class="weekday">Pt</div><div class="weekday">So</div><div class="weekday">Nd</div>
            @for (date of calendarDates(); track date) { <button class="calendar-day" type="button" [class.outside]="!date.startsWith(month)" [class.selected]="selectedDate() === date" (click)="selectDate(date)"><span>{{ date.slice(-2) }}</span>@if (count(date) > 0) { <strong>{{ count(date) }} nieob.</strong> }</button> }
          </div>
          <div class="day-list"><h2>{{ selectedDate() ? 'Wybrany dzień' : 'Nieobecności w miesiącu' }}</h2>
            @if (loading()) { <p role="status">Ładowanie nieobecności…</p> }
            @else if (days().length === 0) { <p class="empty">Brak oznaczonych nieobecności.</p> }
            @else { @for (day of days(); track day.id) { <article class="absence-row"><span class="avatar">{{ initials(day.employeeId) }}</span><div><strong>{{ employeeName(day.employeeId) }}</strong><small>{{ day.absenceDate | date:'dd.MM.yyyy' }} · Ręcznie</small>@if (day.note) { <small>{{ day.note }}</small> }</div>
              @if (auth.has('ABSENCE_EDIT')) { <div class="row-actions"><button type="button" (click)="openEdit(day)">Edytuj</button><button type="button" (click)="cancel(day)">Anuluj</button></div> }
            </article> } }
            <div class="pagination"><span>Strona {{ page() + 1 }} z {{ totalPages() || 1 }}</span><button type="button" [disabled]="page() === 0 || loading()" (click)="setPage(page() - 1)">Poprzednia</button><button type="button" [disabled]="page() + 1 >= totalPages() || loading()" (click)="setPage(page() + 1)">Następna</button></div>
          </div>
        </div>
      </section>
      @if (drawerOpen()) { <div class="drawer-backdrop" (click)="closeDrawer()"></div><aside class="absence-drawer" aria-label="Formularz nieobecności"><div class="drawer-heading"><h2>{{ editing() ? 'Edytuj notatkę' : 'Oznacz nieobecność' }}</h2><button type="button" (click)="closeDrawer()" aria-label="Zamknij"><i class="pi pi-times"></i></button></div>
        @if (formError()) { <p class="alert" role="alert">{{ formError() }}</p> }
        <form id="absence-form" (ngSubmit)="save()"><label>Pracownik <select name="formEmployee" [(ngModel)]="formEmployeeId" [disabled]="!!editing()" required><option value="">Wybierz pracownika</option>@for (person of employeeOptions(); track person.id) { <option [value]="person.id">{{ person.firstName }} {{ person.lastName }}</option> }</select></label>
          <div class="date-row"><label>Data od <input name="dateFrom" type="date" [(ngModel)]="dateFrom" [disabled]="!!editing()" required /></label><label>Data do <input name="dateTo" type="date" [(ngModel)]="dateTo" [disabled]="!!editing()" required /></label></div>
          <label>Notatka <textarea name="note" [(ngModel)]="note" maxlength="2000" rows="4" placeholder="Opcjonalna notatka"></textarea></label>
          @if (!editing()) { <p class="hint">Wszystkie dni kalendarzowe w zakresie zostaną oznaczone osobno. Typ i rozliczenie będą dostępne w dodatku „Szczegółowe nieobecności”.</p> }
        </form>
        <div class="drawer-actions"><button type="button" (click)="closeDrawer()">Anuluj</button><button class="primary" type="submit" form="absence-form" [disabled]="saving()">{{ saving() ? 'Zapisywanie…' : 'Zapisz' }}</button></div>
      </aside> }
    </main>
  `,
  styles: `
    .absence-page { max-width: 100rem; gap: 1rem; }.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }.page-heading button { white-space: nowrap; }.calendar-panel { padding: 0; overflow: hidden; }.filters { display: flex; align-items: end; flex-wrap: wrap; gap: .7rem; padding: 1rem; border-bottom: 1px solid var(--app-border); }.filters label { display: grid; gap: .25rem; min-width: 11rem; font-size: .8rem; }.month-control { display: flex; align-items: center; gap: .5rem; justify-content: space-between; min-width: 13rem; }.month-control button { border: 0; }.calendar-layout { display: grid; grid-template-columns: minmax(0,1.4fr) minmax(17rem,1fr); }.calendar { display: grid; grid-template-columns: repeat(7,minmax(0,1fr)); align-content: start; border-right: 1px solid var(--app-border); }.weekday { text-align: center; background: #f5f9fb; padding: .6rem .2rem; font-weight: 700; font-size: .8rem; }.calendar-day { min-height: 5rem; border: 0; border-top: 1px solid var(--app-border); border-right: 1px solid var(--app-border); border-radius: 0; background: #fff; display: grid; align-content: start; justify-items: start; gap: .4rem; font: inherit; text-align: left; }.calendar-day strong { color: #087d7e; background: #dff7f3; border-radius: 5px; padding: .2rem; font-size: .72rem; }.calendar-day.outside { color: var(--app-text-muted); background: #fafbfc; }.calendar-day.selected { background: #e5f7f5; }.day-list { padding: 1rem; }.day-list h2 { font-size: 1rem; margin: 0 0 .7rem; }.absence-row { display: flex; align-items: center; gap: .6rem; padding: .65rem 0; border-bottom: 1px solid var(--app-border); }.absence-row > div:nth-child(2) { display: grid; flex: 1; min-width: 0; }.absence-row small { color: var(--app-text-muted); overflow-wrap: anywhere; }.avatar { flex: 0 0 2.2rem; height: 2.2rem; border-radius: 50%; background: #e5effb; display: grid; place-items: center; color: #325579; font-weight: 700; font-size: .75rem; }.row-actions { display: grid; gap: .2rem; }.row-actions button { font-size: .75rem; }.pagination { justify-content: flex-end; margin-top: 1rem; }.empty { color: var(--app-text-muted); }
    .drawer-backdrop { position: fixed; inset: 4.5rem 0 0; z-index: 35; background: #142b4333; }.absence-drawer { position: fixed; z-index: 36; top: 4.5rem; right: 0; bottom: 0; width: min(27rem,100vw); background: #fff; box-shadow: var(--app-shadow); display: flex; flex-direction: column; overflow: auto; }.drawer-heading, .drawer-actions { display: flex; align-items: center; justify-content: space-between; gap: .5rem; padding: 1rem; border-bottom: 1px solid var(--app-border); }.drawer-heading h2 { margin: 0; }.drawer-heading button { border: 0; }.absence-drawer form { display: grid; gap: 1rem; padding: 1rem; }.absence-drawer label { display: grid; gap: .3rem; }.date-row { display: flex; gap: .5rem; }.date-row label { flex: 1; min-width: 0; }.absence-drawer textarea { font: inherit; padding: .6rem; border: 1px solid var(--app-border); border-radius: 8px; }.hint { font-size: .8rem; color: var(--app-text-muted); }.drawer-actions { margin-top: auto; border-top: 1px solid var(--app-border); border-bottom: 0; }.drawer-actions button { flex: 1; }
    @media (max-width: 900px) { .calendar-layout { grid-template-columns: 1fr; }.calendar { border-right: 0; }.calendar-day { min-height: 4rem; }.day-list { border-top: 1px solid var(--app-border); } }
    @media (max-width: 760px) { .absence-page { padding: 1rem 1rem 6rem; }.page-heading h1 { font-size: 1.5rem; }.page-heading button { font-size: .75rem; }.filters { display: grid; }.filters label { min-width: 0; }.calendar-day { min-height: 3.1rem; padding: .3rem; }.calendar-day strong { width: .45rem; height: .45rem; overflow: hidden; color: transparent; border-radius: 50%; padding: 0; }.absence-drawer { top: 0; left: 0; width: 100%; z-index: 60; }.drawer-actions { position: sticky; bottom: 0; background: #fff; padding-bottom: max(1rem,env(safe-area-inset-bottom)); } }
  `,
})
export class AbsencePage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);
  readonly days = signal<AbsenceDay[]>([]);
  readonly calendar = signal<AbsenceCalendarDay[]>([]);
  readonly employeeOptions = signal<WorkforceEmployeeOption[]>([]);
  readonly employeeNames = signal<Record<string, string>>({});
  readonly loading = signal(true);
  readonly error = signal('');
  readonly formError = signal('');
  readonly saving = signal(false);
  readonly drawerOpen = signal(false);
  readonly editing = signal<AbsenceDay | null>(null);
  readonly selectedDate = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  month = `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}`;
  employeeId = '';
  employeeSearch = '';
  formEmployeeId = '';
  dateFrom = '';
  dateTo = '';
  note = '';
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly pendingEmployeeIds = new Set<string>();

  ngOnInit(): void { this.employeeId = this.route.snapshot.queryParamMap.get('employeeId') ?? ''; this.loadEmployees(); this.load(); }
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
    this.selectedDate.set(''); this.applyFilters();
  }
  calendarDates(): string[] {
    const [year, month] = this.month.split('-').map(Number);
    const first = new Date(year, month - 1, 1);
    const offset = (first.getDay() + 6) % 7;
    return Array.from({ length: 42 }, (_, index) => {
      const date = new Date(year, month - 1, index - offset + 1);
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    });
  }
  count(date: string): number { return this.calendar().find(day => day.date === date)?.count ?? 0; }
  selectDate(date: string): void { this.selectedDate.set(date); this.page.set(0); this.load(); }
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
  initials(id: string): string { const words = this.employeeName(id).split(' '); return this.employeeNames()[id] ? `${words[0][0]}${words[1]?.[0] ?? ''}`.toUpperCase() : '•'; }
  load(): void {
    const [start, end] = this.dates(); const from = this.selectedDate() || start; const to = this.selectedDate() || end;
    this.loading.set(true);
    this.api.absenceDays(from, to, this.employeeId, this.page()).subscribe({
      next: response => { this.days.set(response.content); response.content.forEach(day => this.ensureEmployee(day.employeeId)); this.totalPages.set(response.totalPages); this.loading.set(false); this.error.set(''); },
      error: error => { this.error.set(problemMessage(error)); this.loading.set(false); },
    });
    this.api.absenceCalendar(this.month, this.employeeId).subscribe({ next: value => this.calendar.set(value), error: () => this.calendar.set([]) });
  }
  applyFilters(): void { this.page.set(0); this.load(); }
  setPage(page: number): void { this.page.set(page); this.load(); }
  openCreate(): void { this.editing.set(null); this.formEmployeeId = this.employeeId; this.dateFrom = this.selectedDate() || `${this.month}-01`; this.dateTo = this.dateFrom; this.note = ''; this.formError.set(''); this.drawerOpen.set(true); }
  openEdit(day: AbsenceDay): void { this.editing.set(day); this.formEmployeeId = day.employeeId; this.dateFrom = day.absenceDate; this.dateTo = day.absenceDate; this.note = day.note ?? ''; this.formError.set(''); this.drawerOpen.set(true); }
  closeDrawer(): void { this.drawerOpen.set(false); }
  save(): void {
    if (this.saving()) return;
    const operation: Observable<unknown> = this.editing()
      ? this.api.updateAbsenceDay(this.editing()!, this.note.trim() || null)
      : this.api.createAbsenceDays(this.formEmployeeId, this.dateFrom, this.dateTo, this.note.trim() || null);
    this.saving.set(true);
    operation.subscribe({ next: () => { this.saving.set(false); this.closeDrawer(); this.load(); }, error: error => { this.saving.set(false); this.formError.set(problemMessage(error)); } });
  }
  cancel(day: AbsenceDay): void {
    if (!window.confirm('Anulować oznaczenie nieobecności? Historia pozostanie zachowana.')) return;
    this.api.cancelAbsenceDay(day).subscribe({ next: () => this.load(), error: error => this.error.set(problemMessage(error)) });
  }
}
