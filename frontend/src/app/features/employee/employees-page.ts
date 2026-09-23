import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService, Employee } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-employees-page',
  imports: [FormsModule, DatePipe, RouterLink],
  template: `
    <main class="page employees-page" [class.drawer-visible]="creating()">
      <div class="page-heading"><div><h1>Pracownicy</h1><p>Zarządzaj zespołem. Dodawaj, edytuj i kontroluj dane pracowników.</p></div></div>
      <div class="employee-toolbar">
        @if (auth.has('EMPLOYEE_CREATE')) { <a class="primary add-employee" routerLink="/employees/new"><i class="pi pi-user-plus" aria-hidden="true"></i> Dodaj pracownika</a> }
        <label class="search"><span class="sr-only">Szukaj pracownika</span><i class="pi pi-search" aria-hidden="true"></i><input name="search" [(ngModel)]="search" (input)="scheduleSearch()" placeholder="Szukaj pracownika" /></label>
        <button class="mobile-filter-button" type="button" (click)="filtersOpen.set(!filtersOpen())" aria-label="Filtruj pracowników"><i class="pi pi-filter" aria-hidden="true"></i></button>
        <div class="select-filters" [class.open]="filtersOpen()">
          <label>Status <select name="status" [(ngModel)]="status" (change)="applyFilters()"><option value="">Wszystkie</option><option value="ACTIVE">Aktywni</option><option value="INACTIVE">Nieaktywni</option></select></label>
          <label>Stanowisko <select name="position" [(ngModel)]="position" (change)="applyFilters()"><option value="">Wszystkie</option>@for (item of positions(); track item) { <option [value]="item">{{ item }}</option> }</select></label>
          <button class="close-filters" type="button" (click)="filtersOpen.set(false)">Zamknij filtry</button>
        </div>
      </div>
      @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
      @if (loading()) { <p class="panel" role="status">Ładowanie pracowników…</p> }
      @else if (employees().length === 0) { <div class="panel empty-state">{{ search || status || position ? 'Brak pracowników spełniających filtry.' : 'Nie ma jeszcze pracowników.' }}</div> }
      @else {
        <div class="panel table-wrap desktop-employees"><table><thead><tr><th>Pracownik</th><th>Telefon</th><th>Stanowisko</th><th>Status</th><th>Data zatrudnienia</th><th>Akcje</th></tr></thead>
          <tbody>@for (employee of employees(); track employee.id) { <tr><td><a [routerLink]="['/employees', employee.id]" class="employee-name"><span class="avatar">{{ initials(employee) }}</span><span><strong>{{ employee.firstName }} {{ employee.lastName }}</strong><small>{{ employee.email || 'Brak e-maila' }}</small></span></a></td>
            <td>{{ employee.phone }}</td><td>{{ employee.position }}</td><td><span class="status" [class.inactive]="employee.status === 'INACTIVE'">{{ statusLabel(employee.status) }}</span></td>
            <td>{{ employee.employmentDate | date:'dd.MM.yyyy' }}</td><td><a [routerLink]="['/employees', employee.id]" [attr.aria-label]="'Otwórz pracownika ' + employee.firstName + ' ' + employee.lastName"><i class="pi pi-ellipsis-h" aria-hidden="true"></i></a></td>
          </tr> }</tbody></table></div>
        <div class="mobile-employees">@for (employee of employees(); track employee.id) {
          <a class="panel employee-card" [routerLink]="['/employees', employee.id]"><span class="avatar">{{ initials(employee) }}</span><span class="card-main"><strong>{{ employee.firstName }} {{ employee.lastName }}</strong><small>{{ employee.position }}</small></span><span class="status" [class.inactive]="employee.status === 'INACTIVE'">{{ statusLabel(employee.status) }}</span><i class="pi pi-chevron-right" aria-hidden="true"></i></a>
        }</div>
      }
      <div class="pagination"><span>Wyświetlanie {{ employees().length ? page() * 20 + 1 : 0 }}–{{ page() * 20 + employees().length }} z {{ totalElements() }}</span>
        <button type="button" [disabled]="page() === 0 || loading()" (click)="setPage(page() - 1)" aria-label="Poprzednia strona"><i class="pi pi-chevron-left" aria-hidden="true"></i></button>
        <span>Strona {{ page() + 1 }} z {{ totalPages() || 1 }}</span>
        <button type="button" [disabled]="page() + 1 >= totalPages() || loading()" (click)="setPage(page() + 1)" aria-label="Następna strona"><i class="pi pi-chevron-right" aria-hidden="true"></i></button>
      </div>
      @if (creating()) { <div class="drawer-backdrop" (click)="closeCreate()"></div><aside class="employee-drawer" aria-label="Nowy pracownik">
        <div class="drawer-heading"><h2>Nowy pracownik</h2><button type="button" (click)="closeCreate()" aria-label="Zamknij"><i class="pi pi-times" aria-hidden="true"></i></button></div>
        @if (formError()) { <p class="alert" role="alert">{{ formError() }}</p> }
        <form id="create-employee" class="employee-form" (ngSubmit)="save()">
          <h3>Dane podstawowe</h3>
          <label>Imię <span aria-hidden="true">*</span><input name="firstName" [(ngModel)]="firstName" required maxlength="100" autocomplete="given-name" placeholder="Wpisz imię" /></label>
          <label>Nazwisko <span aria-hidden="true">*</span><input name="lastName" [(ngModel)]="lastName" required maxlength="100" autocomplete="family-name" placeholder="Wpisz nazwisko" /></label>
          <label>Adres e-mail <input name="email" type="email" [(ngModel)]="email" maxlength="150" autocomplete="email" placeholder="np. jan.kowalski@firma.pl" /></label>
          <label>Telefon <span aria-hidden="true">*</span><input name="phone" type="tel" [(ngModel)]="phone" required maxlength="30" autocomplete="tel" placeholder="np. 600 123 456" /></label>
          <label>Stanowisko <span aria-hidden="true">*</span><input name="positionInput" [(ngModel)]="newPosition" required maxlength="120" list="employee-positions" placeholder="Wpisz stanowisko" /></label>
          <datalist id="employee-positions">@for (item of positions(); track item) { <option [value]="item"></option> }</datalist>
          <label>Status <select name="newStatus" [(ngModel)]="newStatus"><option value="ACTIVE">Aktywny</option><option value="INACTIVE">Nieaktywny</option></select></label>
          <label>Data zatrudnienia <span aria-hidden="true">*</span><input name="employmentDate" type="date" [(ngModel)]="employmentDate" required /></label>
          <label>Notatka <textarea name="note" [(ngModel)]="note" maxlength="2000" rows="3" placeholder="Wpisz dodatkowe informacje…"></textarea></label>
        </form>
        <div class="drawer-actions"><button type="button" (click)="closeCreate()">Anuluj</button><button class="primary" type="submit" form="create-employee" [disabled]="saving()"><i class="pi pi-save" aria-hidden="true"></i> {{ saving() ? 'Zapisywanie…' : 'Zapisz' }}</button></div>
      </aside> }
      @if (auth.has('EMPLOYEE_CREATE')) { <a class="mobile-add primary" routerLink="/employees/new"><i class="pi pi-user-plus" aria-hidden="true"></i> Dodaj pracownika</a> }
    </main>
  `,
  styles: `
    .employees-page { max-width: 100rem; gap: 1rem; }.employees-page.drawer-visible { padding-right: calc(2rem + 19rem); max-width: none; margin: 0; }
    .employee-toolbar { display: flex; align-items: end; gap: .65rem; }.add-employee, .mobile-add { display: inline-flex; align-items: center; justify-content: center; gap: .4rem; padding: .76rem 1rem; border-radius: 8px; text-decoration: none; white-space: nowrap; }
    .search { position: relative; flex: 1; max-width: 22rem; }.search i { position: absolute; left: .8rem; top: .9rem; color: var(--app-text-muted); }.search input { padding-left: 2.2rem; }
    .select-filters { display: flex; gap: .65rem; }.select-filters label { display: grid; gap: .25rem; min-width: 9rem; font-size: .8rem; color: var(--app-text-muted); }
    .select-filters select { font-size: .85rem; }.mobile-filter-button, .close-filters, .mobile-employees, .mobile-add { display: none; }
    .table-wrap { padding: 0; overflow-x: auto; }.table-wrap th, .table-wrap td { padding: .65rem .55rem; vertical-align: middle; }.table-wrap th { white-space: nowrap; }
    .employee-name { display: flex; align-items: center; gap: .6rem; color: inherit; text-decoration: none; }.employee-name strong { white-space: nowrap; }.employee-name small { font-size: .75rem; }
    .avatar { flex: 0 0 2.3rem; width: 2.3rem; height: 2.3rem; border-radius: 50%; background: #dcf8f3; display: grid; place-items: center; color: #086e70; font-weight: 700; font-size: .8rem; }
    .status { display: inline-block; padding: .25rem .55rem; border-radius: 99px; background: #dff8eb; color: #0b7151; white-space: nowrap; font-size: .78rem; }.status.inactive { background: #ffe8ea; color: #a52d43; }
    .empty-state { min-height: 12rem; display: grid; place-items: center; color: var(--app-text-muted); }.pagination { justify-content: flex-start; font-size: .84rem; }.pagination span:first-child { margin-right: auto; }
    .drawer-backdrop { position: fixed; inset: 4.5rem 0 0; z-index: 35; background: #142b431c; }.employee-drawer { position: fixed; z-index: 36; top: 4.5rem; right: 0; bottom: 0; width: 19rem; background: #fff; border-left: 1px solid var(--app-border); display: flex; flex-direction: column; overflow: auto; }
    .drawer-heading { display: flex; justify-content: space-between; align-items: center; padding: 1rem; border-bottom: 1px solid var(--app-border); }.drawer-heading h2 { margin: 0; font-size: 1.1rem; }.drawer-heading button { border: 0; padding: .4rem; }
    .employee-form { display: grid; gap: .8rem; padding: 1rem; }.employee-form h3 { margin: 0; font-size: .95rem; }.employee-form label { display: block; font-size: .84rem; font-weight: 600; }.employee-form label > span { color: #bd2f45; }.employee-form input, .employee-form select, .employee-form textarea { display: block; margin-top: .3rem; }.employee-form textarea { width: 100%; font: inherit; border: 1px solid var(--app-border); border-radius: 8px; padding: .7rem; resize: vertical; }
    .drawer-actions { display: flex; gap: .6rem; padding: 1rem; margin-top: auto; border-top: 1px solid var(--app-border); }.drawer-actions button { flex: 1; }
    .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
    @media (max-width: 950px) and (min-width: 761px) { .employee-toolbar { flex-wrap: wrap; }.employees-page.drawer-visible { padding-right: calc(1rem + 19rem); } }
    @media (max-width: 760px) { .employees-page, .employees-page.drawer-visible { padding: 1rem 1rem 7rem; max-width: none; }.employees-page .page-heading h1 { font-size: 1.5rem; }.add-employee, .desktop-employees { display: none; }
      .employee-toolbar { align-items: center; }.search { max-width: none; }.mobile-filter-button { display: inline-grid; place-items: center; flex: 0 0 2.8rem; height: 2.8rem; padding: .5rem; }
      .select-filters { display: none; position: fixed; z-index: 42; bottom: 0; left: 0; right: 0; padding: 1rem 1rem max(1rem, env(safe-area-inset-bottom)); background: #fff; border-radius: 18px 18px 0 0; box-shadow: var(--app-shadow); }.select-filters.open { display: grid; }.select-filters label { width: 100%; }.close-filters { display: block; }
      .mobile-employees { display: grid; gap: .5rem; }.employee-card { display: flex; align-items: center; gap: .6rem; min-height: 4.3rem; padding: .65rem; color: inherit; text-decoration: none; }.card-main { display: grid; flex: 1; min-width: 0; }.card-main small { color: var(--app-text-muted); }.employee-card .status { font-size: .68rem; }
      .pagination { justify-content: space-between; }.pagination span:first-child { display: none; }.mobile-add { display: flex; position: fixed; z-index: 28; bottom: 4.6rem; left: 1rem; right: 1rem; }
      .drawer-backdrop { display: none; }.employee-drawer { top: 0; left: 0; width: 100%; border-left: 0; z-index: 60; }.employee-drawer .drawer-actions { position: sticky; bottom: 0; background: #fff; padding-bottom: max(1rem, env(safe-area-inset-bottom)); }
      .employees-page.drawer-visible > .mobile-add { display: none; }
    }
  `,
})
export class EmployeesPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  readonly employees = signal<Employee[]>([]);
  readonly positions = signal<string[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly formError = signal('');
  readonly saving = signal(false);
  readonly creating = signal(false);
  readonly filtersOpen = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  search = '';
  status = '';
  position = '';
  firstName = '';
  lastName = '';
  phone = '';
  email = '';
  newPosition = '';
  note = '';
  employmentDate = '';
  newStatus: Employee['status'] = 'ACTIVE';
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.creating.set(this.route.snapshot.routeConfig?.path === 'employees/new');
    this.load();
    this.api.employeePositions().subscribe({ next: value => this.positions.set(value), error: () => this.positions.set([]) });
  }
  ngOnDestroy(): void { if (this.searchTimer) clearTimeout(this.searchTimer); }
  load(): void {
    this.loading.set(true);
    this.api.employees(this.page(), this.search, this.status, this.position).subscribe({
      next: response => { this.employees.set(response.content); this.totalPages.set(response.totalPages); this.totalElements.set(response.totalElements); this.loading.set(false); this.error.set(''); },
      error: error => { this.error.set(problemMessage(error)); this.loading.set(false); },
    });
  }
  scheduleSearch(): void {
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.applyFilters(), 300);
  }
  applyFilters(): void { this.page.set(0); this.filtersOpen.set(false); this.load(); }
  setPage(value: number): void { this.page.set(value); this.load(); }
  initials(employee: Employee): string { return `${employee.firstName.charAt(0)}${employee.lastName.charAt(0)}`.toUpperCase(); }
  statusLabel(value: Employee['status']): string { return value === 'ACTIVE' ? 'Aktywny' : 'Nieaktywny'; }
  closeCreate(): void { void this.router.navigateByUrl('/employees'); }
  save(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.api.createEmployee({ firstName: this.firstName.trim(), lastName: this.lastName.trim(), phone: this.phone.trim(),
      email: this.email.trim() || null, position: this.newPosition.trim(), note: this.note.trim() || null,
      employmentDate: this.employmentDate, status: this.newStatus }).subscribe({
      next: employee => { this.saving.set(false); void this.router.navigate(['/employees', employee.id]); },
      error: error => { this.formError.set(problemMessage(error)); this.saving.set(false); },
    });
  }
}
