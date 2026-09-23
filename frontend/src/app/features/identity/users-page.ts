import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, CreatedUser, Role } from '../../core/api.service';
import { AuthService, UserSummary } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-users-page',
  imports: [FormsModule],
  template: `
    <main class="page users-page">
      <div class="page-heading"><div><h1>Użytkownicy</h1><p>Konta przypisane do tej firmy.</p></div>
        @if (auth.has('USER_MANAGE')) { <button class="primary" type="button" (click)="startCreate()"><i class="pi pi-user-plus" aria-hidden="true"></i> Dodaj użytkownika</button> }
      </div>
      @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Spróbuj ponownie</button></p> }
      @if (temporaryPassword()) { <div class="notice" role="status"><strong>Hasło tymczasowe — skopiuj teraz:</strong> <code>{{ temporaryPassword() }}</code><button type="button" (click)="temporaryPassword.set('')">Zamknij</button></div> }
      <div class="panel user-filters">
        <label><span class="sr-only">Szukaj na tej stronie</span><input name="search" [(ngModel)]="search" placeholder="Szukaj na tej stronie…" /></label>
        <label><span class="sr-only">Rola</span><select name="roleFilter" [(ngModel)]="roleFilter"><option value="">Wszystkie role</option>@for (role of roles(); track role.id) { <option [value]="role.id">{{ role.name }}</option> }</select></label>
        <label><span class="sr-only">Status</span><select name="statusFilter" [(ngModel)]="statusFilter"><option value="">Wszystkie statusy</option><option value="ACTIVE">Aktywni</option><option value="DISABLED">Wyłączeni</option></select></label>
        <button type="button" (click)="clearFilters()">Wyczyść</button>
      </div>
      @if (loading()) { <p class="panel" role="status">Ładowanie użytkowników…</p> }
      @else if (users().length === 0) { <p class="panel">Brak użytkowników.</p> }
      @else {
        @if (filteredUsers().length === 0) { <p class="panel">Brak użytkowników spełniających filtry na tej stronie.</p> }
        <div class="panel table-wrap desktop-users"><table><thead><tr><th>Imię i nazwisko</th><th>E-mail</th><th>Rola</th><th>Status</th><th>Akcje</th></tr></thead>
          <tbody>@for (user of filteredUsers(); track user.id) { <tr [class.selected]="selected()?.id === user.id" (click)="select(user)">
            <td><strong><span class="avatar">{{ user.displayName.charAt(0) }}</span>{{ user.displayName }}</strong></td><td>{{ user.email }}</td>
            <td><span class="role-badge">{{ roleName(user.roleId) }}</span></td><td><span class="status" [class.off]="user.status !== 'ACTIVE'">{{ user.status === 'ACTIVE' ? 'Aktywny' : 'Wyłączony' }}</span></td>
            <td><button type="button" (click)="select(user); $event.stopPropagation()" [attr.aria-label]="'Szczegóły ' + user.displayName"><i class="pi pi-ellipsis-h" aria-hidden="true"></i></button></td>
          </tr> }</tbody></table></div>
        <div class="mobile-users">@for (user of filteredUsers(); track user.id) {
          <button class="panel user-card" type="button" (click)="select(user)"><span class="avatar">{{ user.displayName.charAt(0) }}</span>
            <span><strong>{{ user.displayName }}</strong><small>{{ user.email }}</small><span class="status" [class.off]="user.status !== 'ACTIVE'">{{ user.status === 'ACTIVE' ? 'Aktywny' : 'Wyłączony' }}</span></span>
            <span class="role-badge">{{ roleName(user.roleId) }}</span><i class="pi pi-chevron-right" aria-hidden="true"></i></button>
        }</div>
      }
      <div class="pagination"><button type="button" [disabled]="page() === 0 || loading()" (click)="setPage(page() - 1)">Poprzednia</button><span>Strona {{ page() + 1 }} z {{ totalPages() || 1 }}</span><button type="button" [disabled]="page() + 1 >= totalPages() || loading()" (click)="setPage(page() + 1)">Następna</button></div>
      @if (selected() || editing()) { <div class="drawer-backdrop" (click)="closeDrawer()"></div><aside class="user-drawer" aria-label="Szczegóły użytkownika">
        <div class="drawer-head"><h2>{{ editing() === 'new' ? 'Dodaj użytkownika' : editing() ? 'Edytuj użytkownika' : 'Szczegóły użytkownika' }}</h2><button type="button" (click)="closeDrawer()" aria-label="Zamknij"><i class="pi pi-times" aria-hidden="true"></i></button></div>
        @if (editing()) { <form class="form-grid" (ngSubmit)="save()">
          @if (editing() === 'new') { <label>E-mail <input type="email" name="email" [(ngModel)]="email" required /></label> }
          <label>Imię i nazwisko <input name="name" [(ngModel)]="displayName" required maxlength="160" /></label>
          <label>Rola <select name="role" [(ngModel)]="roleId" required><option value="">Wybierz rolę</option>@for (role of roles(); track role.id) { <option [value]="role.id">{{ role.name }}</option> }</select></label>
          <div class="form-actions"><button class="primary" type="submit" [disabled]="saving()">Zapisz</button><button type="button" (click)="closeDrawer()">Anuluj</button></div>
        </form> } @else if (selected(); as user) {
          <div class="profile"><span class="avatar large">{{ user.displayName.charAt(0) }}</span><strong>{{ user.displayName }}</strong><span>{{ user.email }}</span>
            <span class="status" [class.off]="user.status !== 'ACTIVE'">{{ user.status === 'ACTIVE' ? 'Aktywny' : 'Wyłączony' }}</span></div>
          <dl><dt>Rola</dt><dd>{{ roleName(user.roleId) }}</dd><dt>Status</dt><dd>{{ user.status === 'ACTIVE' ? 'Aktywny' : 'Wyłączony' }}</dd></dl>
          @if (auth.has('USER_MANAGE')) { <div class="drawer-actions"><button class="primary" type="button" (click)="startEdit(user)">Edytuj dane i rolę</button>
            <button type="button" (click)="resetPassword(user)">Reset hasła</button>
            <button type="button" [class.danger]="user.status === 'ACTIVE'" (click)="toggleStatus(user)">{{ user.status === 'ACTIVE' ? 'Wyłącz konto' : 'Aktywuj konto' }}</button></div> }
        }
      </aside> }
    </main>
  `,
  styles: `
    .users-page { max-width: 100rem; }.user-filters { display: grid; grid-template-columns: minmax(15rem,2fr) repeat(2,minmax(9rem,1fr)) auto; gap: .7rem; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }
    .desktop-users tr { cursor: pointer; }.desktop-users tr:hover, .desktop-users tr.selected { background: #eafafa; }
    .desktop-users td strong { display: flex; align-items: center; gap: .6rem; }.desktop-users td { vertical-align: middle; }
    .avatar { width: 2rem; height: 2rem; flex: 0 0 2rem; border-radius: 50%; background: #438f95; color: #fff; display: inline-grid; place-items: center; font-weight: 700; }
    .avatar.large { width: 4rem; height: 4rem; font-size: 1.7rem; }
    .role-badge, .status { display: inline-block; border-radius: 99px; padding: .25rem .55rem; font-size: .8rem; background: #e3f6f5; color: #067173; white-space: nowrap; }
    .status { background: #def8ed; color: #086b4d; }.status.off { background: #f2f3f5; color: #607087; }
    .mobile-users { display: none; }.drawer-backdrop { position: fixed; inset: 0; z-index: 40; background: #10263b55; }
    .user-drawer { position: fixed; z-index: 41; top: 0; right: 0; bottom: 0; width: min(100%,25rem); overflow: auto; background: #fff; padding: 1.4rem; box-shadow: var(--app-shadow); display: flex; flex-direction: column; gap: 1.2rem; }
    .drawer-head { display: flex; align-items: center; justify-content: space-between; }.drawer-head h2 { margin: 0; }
    .profile { display: grid; place-items: center; gap: .35rem; text-align: center; }.profile span:not(.avatar):not(.status) { color: var(--app-text-muted); }
    .user-drawer dl { display: grid; grid-template-columns: 1fr auto; gap: .75rem; border-top: 1px solid var(--app-border); padding-top: 1rem; }
    .user-drawer dd { margin: 0; font-weight: 700; }.drawer-actions { display: grid; gap: .6rem; margin-top: auto; }.danger { color: #a52235; border-color: #e6aab3; }
    @media (max-width: 1150px) and (min-width: 761px) { .user-filters { grid-template-columns: repeat(2,minmax(0,1fr)); } }
    @media (max-width: 760px) { .user-filters { grid-template-columns: 1fr 1fr; }.user-filters label:first-child { grid-column: 1 / -1; }.desktop-users { display: none; }
      .mobile-users { display: grid; gap: .5rem; }.user-card { display: flex; align-items: center; gap: .7rem; width: 100%; text-align: left; padding: .8rem; }
      .user-card > span:nth-child(2) { flex: 1; display: grid; gap: .2rem; min-width: 0; }.user-card small { overflow-wrap: anywhere; color: var(--app-text-muted); }
      .user-card .status { width: fit-content; }.user-card .role-badge { align-self: flex-start; }.user-card i { font-size: .75rem; }
      .user-drawer { top: auto; left: 0; width: 100%; max-height: 85vh; border-radius: 18px 18px 0 0; padding-bottom: max(1.5rem, env(safe-area-inset-bottom)); }
    }
  `,
})
export class UsersPage implements OnInit {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly users = signal<UserSummary[]>([]);
  readonly roles = signal<Role[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly temporaryPassword = signal('');
  readonly editing = signal('');
  readonly selected = signal<UserSummary | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  email = '';
  displayName = '';
  roleId = '';
  search = '';
  roleFilter = '';
  statusFilter = '';

  ngOnInit(): void {
    this.load();
    if (this.auth.has('ROLE_READ') || this.auth.has('USER_MANAGE')) {
      this.api.roles().subscribe({ next: (roles) => this.roles.set(roles), error: (e) => this.error.set(problemMessage(e)) });
    }
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.users(this.page()).subscribe({
      next: (result) => { this.users.set(result.content); this.totalPages.set(result.totalPages); this.loading.set(false); },
      error: (error) => { this.error.set(problemMessage(error)); this.loading.set(false); },
    });
  }

  setPage(page: number): void { this.page.set(page); this.load(); }
  roleName(id: string): string { return this.roles().find((role) => role.id === id)?.name ?? 'Bez podglądu roli'; }
  startCreate(): void { this.selected.set(null); this.editing.set('new'); this.email = ''; this.displayName = ''; this.roleId = ''; }
  startEdit(user: UserSummary): void { this.selected.set(user); this.editing.set(user.id); this.displayName = user.displayName; this.roleId = user.roleId; }
  select(user: UserSummary): void { this.editing.set(''); this.selected.set(user); }
  closeDrawer(): void { this.editing.set(''); this.selected.set(null); }
  clearFilters(): void { this.search = ''; this.roleFilter = ''; this.statusFilter = ''; }
  filteredUsers(): UserSummary[] {
    const search = this.search.toLocaleLowerCase('pl').trim();
    return this.users().filter(user => (!search || `${user.displayName} ${user.email}`.toLocaleLowerCase('pl').includes(search))
      && (!this.roleFilter || user.roleId === this.roleFilter)
      && (!this.statusFilter || user.status === this.statusFilter));
  }

  save(): void {
    if (!this.roleId || !this.displayName.trim() || this.saving()) return;
    this.saving.set(true);
    this.error.set('');
    const request: Observable<CreatedUser | UserSummary> = this.editing() === 'new'
      ? this.api.createUser(this.email, this.displayName, this.roleId)
      : this.api.updateUser(this.editing(), this.displayName, this.roleId);
    request.subscribe({
      next: (result) => {
        if ('temporaryPassword' in result) this.temporaryPassword.set(result.temporaryPassword);
        this.closeDrawer(); this.saving.set(false); this.load();
      },
      error: (error) => { this.error.set(problemMessage(error)); this.saving.set(false); },
    });
  }

  toggleStatus(user: UserSummary): void {
    const next = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    this.api.setUserStatus(user.id, next).subscribe({ next: () => { this.closeDrawer(); this.load(); }, error: (e) => this.error.set(problemMessage(e)) });
  }

  resetPassword(user: UserSummary): void {
    if (!window.confirm(`Zresetować hasło użytkownika ${user.displayName}?`)) return;
    this.api.resetPassword(user.id).subscribe({
      next: (result) => this.temporaryPassword.set(result.temporaryPassword),
      error: (error) => this.error.set(problemMessage(error)),
    });
  }
}
