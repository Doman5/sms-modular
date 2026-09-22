import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-password-page',
  imports: [FormsModule],
  template: `
    <main class="auth-layout">
      <form class="auth-card" (ngSubmit)="submit()">
        <span class="brand">SMS Modular</span>
        <h1>Zmień hasło</h1>
        <p>Ustaw własne hasło, aby uzyskać dostęp do panelu.</p>
        <label>Obecne hasło <input type="password" name="current" [(ngModel)]="current" autocomplete="current-password" required /></label>
        <label>Nowe hasło <input type="password" name="next" [(ngModel)]="next" autocomplete="new-password" minlength="12" required /></label>
        <label>Powtórz nowe hasło <input type="password" name="confirm" [(ngModel)]="confirm" autocomplete="new-password" required /></label>
        @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
        <button class="primary" type="submit" [disabled]="busy()">{{ busy() ? 'Zapisywanie…' : 'Zmień hasło' }}</button>
        <button type="button" class="link-button" (click)="auth.logout()">Wyloguj</button>
      </form>
    </main>
  `,
})
export class PasswordPage {
  readonly auth = inject(AuthService);
  readonly busy = signal(false);
  readonly error = signal('');
  current = '';
  next = '';
  confirm = '';

  submit(): void {
    if (this.next !== this.confirm) {
      this.error.set('Nowe hasła nie są takie same.');
      return;
    }
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.auth.changePassword(this.current, this.next).subscribe({
      next: () => this.auth.logout(),
      error: (error) => {
        this.busy.set(false);
        this.error.set(problemMessage(error));
      },
    });
  }
}
