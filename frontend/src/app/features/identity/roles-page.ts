import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, Role } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

const permissions = ['TENANT_READ', 'TENANT_EDIT', 'USER_READ', 'USER_MANAGE', 'ROLE_READ', 'ROLE_MANAGE', 'AUDIT_READ'];

@Component({
  selector: 'app-roles-page',
  imports: [FormsModule],
  template: `
    <main class="page"><div class="page-heading"><div><h1>Role i uprawnienia</h1><p>Wybierz zakres dostępu dla każdej roli.</p></div>
      @if (auth.has('ROLE_MANAGE')) { <button class="primary" type="button" (click)="startCreate()">Dodaj rolę</button> }
    </div>
    @if (error()) { <p class="alert" role="alert">{{ error() }} <button type="button" (click)="load()">Ponów</button></p> }
    @if (editing()) { <form class="panel form-grid" (ngSubmit)="save()"><h2>{{ editing() === 'new' ? 'Nowa rola' : 'Edytuj rolę' }}</h2>
      @if (editing() === 'new') { <label>Kod roli <input name="code" [(ngModel)]="code" required pattern="[A-Za-z][A-Za-z0-9_]+" /></label> }
      <label>Nazwa <input name="name" [(ngModel)]="name" required /></label>
      <fieldset><legend>Uprawnienia</legend><div class="checkbox-grid">@for (permission of allPermissions; track permission) {
        <label><input type="checkbox" [checked]="selected().includes(permission)" (change)="toggle(permission)" /> {{ permission }}</label>
      }</div></fieldset>
      <div class="form-actions"><button class="primary" type="submit" [disabled]="saving()">Zapisz</button><button type="button" (click)="editing.set('')">Anuluj</button></div>
    </form> }
    @if (loading()) { <p class="panel" role="status">Ładowanie ról…</p> }
    @else if (roles().length === 0) { <p class="panel">Brak ról.</p> }
    @else { <div class="cards">@for (role of roles(); track role.id) { <article class="panel"><div class="card-head"><div><h2>{{ role.name }}</h2><small>{{ role.code }}</small></div>
      @if (auth.has('ROLE_MANAGE')) { <div class="actions"><button type="button" (click)="startEdit(role)">Edytuj</button><button type="button" (click)="remove(role)">Usuń</button></div> }</div>
      <p>{{ role.permissions.join(', ') || 'Brak uprawnień' }}</p></article> }</div> }
    </main>
  `,
})
export class RolesPage implements OnInit {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly allPermissions = permissions;
  readonly roles = signal<Role[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly editing = signal('');
  readonly selected = signal<string[]>([]);
  code = '';
  name = '';

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading.set(true);
    this.api.roles().subscribe({
      next: (roles) => { this.roles.set(roles); this.loading.set(false); this.error.set(''); },
      error: (error) => { this.error.set(problemMessage(error)); this.loading.set(false); },
    });
  }
  startCreate(): void { this.editing.set('new'); this.code = ''; this.name = ''; this.selected.set([]); }
  startEdit(role: Role): void { this.editing.set(role.id); this.code = role.code; this.name = role.name; this.selected.set([...role.permissions]); }
  toggle(permission: string): void {
    this.selected.update((value) => value.includes(permission) ? value.filter((item) => item !== permission) : [...value, permission]);
  }
  save(): void {
    if (!this.name.trim() || this.saving()) return;
    this.saving.set(true);
    const request = this.editing() === 'new'
      ? this.api.createRole(this.code, this.name, this.selected())
      : this.api.updateRole(this.editing(), this.name, this.selected());
    request.subscribe({
      next: () => { this.saving.set(false); this.editing.set(''); this.load(); },
      error: (error) => { this.error.set(problemMessage(error)); this.saving.set(false); },
    });
  }
  remove(role: Role): void {
    if (!window.confirm(`Usunąć rolę ${role.name}?`)) return;
    this.api.deleteRole(role.id).subscribe({ next: () => this.load(), error: (e) => this.error.set(problemMessage(e)) });
  }
}
