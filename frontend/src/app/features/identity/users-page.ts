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
    <main class="page">
      <div class="page-heading"><div><h1>Użytkownicy</h1><p>Konta przypisane do tej firmy.</p></div>
        @if (auth.has('USER_MANAGE')) { <button class="primary" type="button" (click)="startCreate()">Dodaj użytkownika</button> }
      </div>
      @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Spróbuj ponownie</button></p> }
      @if (temporaryPassword()) { <div class="notice" role="status"><strong>Hasło tymczasowe — skopiuj teraz:</strong> <code>{{ temporaryPassword() }}</code><button type="button" (click)="temporaryPassword.set('')">Zamknij</button></div> }
      @if (editing()) {
        <form class="panel form-grid" (ngSubmit)="save()">
          <h2>{{ editing() === 'new' ? 'Nowy użytkownik' : 'Edytuj użytkownika' }}</h2>
          @if (editing() === 'new') { <label>E-mail <input type="email" name="email" [(ngModel)]="email" required /></label> }
          <label>Imię i nazwisko <input name="name" [(ngModel)]="displayName" required maxlength="160" /></label>
          <label>Rola <select name="role" [(ngModel)]="roleId" required><option value="">Wybierz rolę</option>@for (role of roles(); track role.id) { <option [value]="role.id">{{ role.name }}</option> }</select></label>
          <div class="form-actions"><button class="primary" type="submit" [disabled]="saving()">Zapisz</button><button type="button" (click)="editing.set('')">Anuluj</button></div>
        </form>
      }
      @if (loading()) { <p class="panel" role="status">Ładowanie użytkowników…</p> }
      @else if (users().length === 0) { <p class="panel">Brak użytkowników.</p> }
      @else {
        <div class="panel table-wrap"><table><thead><tr><th>Użytkownik</th><th>Rola</th><th>Status</th><th>Akcje</th></tr></thead>
          <tbody>@for (user of users(); track user.id) { <tr><td><strong>{{ user.displayName }}</strong><small>{{ user.email }}</small></td><td>{{ roleName(user.roleId) }}</td><td>{{ user.status === 'ACTIVE' ? 'Aktywny' : 'Wyłączony' }}</td><td class="actions">
            @if (auth.has('USER_MANAGE')) {
              <button type="button" (click)="startEdit(user)">Edytuj</button>
              <button type="button" (click)="toggleStatus(user)">{{ user.status === 'ACTIVE' ? 'Wyłącz' : 'Aktywuj' }}</button>
              <button type="button" (click)="resetPassword(user)">Reset hasła</button>
            }
          </td></tr> }</tbody></table></div>
      }
      <div class="pagination"><button type="button" [disabled]="page() === 0 || loading()" (click)="setPage(page() - 1)">Poprzednia</button><span>Strona {{ page() + 1 }} z {{ totalPages() || 1 }}</span><button type="button" [disabled]="page() + 1 >= totalPages() || loading()" (click)="setPage(page() + 1)">Następna</button></div>
    </main>
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
  readonly page = signal(0);
  readonly totalPages = signal(0);
  email = '';
  displayName = '';
  roleId = '';

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
  startCreate(): void { this.editing.set('new'); this.email = ''; this.displayName = ''; this.roleId = ''; }
  startEdit(user: UserSummary): void { this.editing.set(user.id); this.displayName = user.displayName; this.roleId = user.roleId; }

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
        this.editing.set(''); this.saving.set(false); this.load();
      },
      error: (error) => { this.error.set(problemMessage(error)); this.saving.set(false); },
    });
  }

  toggleStatus(user: UserSummary): void {
    const next = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    this.api.setUserStatus(user.id, next).subscribe({ next: () => this.load(), error: (e) => this.error.set(problemMessage(e)) });
  }

  resetPassword(user: UserSummary): void {
    if (!window.confirm(`Zresetować hasło użytkownika ${user.displayName}?`)) return;
    this.api.resetPassword(user.id).subscribe({
      next: (result) => this.temporaryPassword.set(result.temporaryPassword),
      error: (error) => this.error.set(problemMessage(error)),
    });
  }
}
