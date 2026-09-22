import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-login-page',
  imports: [FormsModule],
  template: `
    <main class="auth-layout">
      <form class="auth-card" (ngSubmit)="submit()">
        <span class="brand">SMS Modular</span>
        <h1>Logowanie</h1>
        <p>Wybierz panel i zaloguj się na swoje konto.</p>
        <div class="switch-row" role="group" aria-label="Typ konta">
          <button type="button" [class.selected]="!platform()" (click)="platform.set(false)">Panel firmy</button>
          <button type="button" [class.selected]="platform()" (click)="platform.set(true)">Panel platformy</button>
        </div>
        <label>Adres e-mail <input type="email" name="email" [(ngModel)]="email" autocomplete="username" required /></label>
        <label>Hasło <input type="password" name="password" [(ngModel)]="password" autocomplete="current-password" required /></label>
        @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
        <button class="primary" type="submit" [disabled]="busy()">{{ busy() ? 'Logowanie…' : 'Zaloguj' }}</button>
      </form>
    </main>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly platform = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  email = '';
  password = '';

  submit(): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.auth.login(this.email, this.password, this.platform()).subscribe({
      next: (response) => {
        this.password = '';
        void this.router.navigateByUrl(response.mustChangePassword ? '/change-password' : '/');
      },
      error: (error) => {
        this.busy.set(false);
        this.error.set(problemMessage(error));
      },
    });
  }
}
