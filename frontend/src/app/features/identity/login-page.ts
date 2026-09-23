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
        <h1>Zaloguj się</h1>
        <p>Wprowadź swoje dane, aby kontynuować.</p>
        <div class="switch-row" role="group" aria-label="Typ konta">
          <button type="button" [class.selected]="!platform()" (click)="platform.set(false)">Panel firmy</button>
          <button type="button" [class.selected]="platform()" (click)="platform.set(true)">Panel platformy</button>
        </div>
        <label>Adres e-mail <input type="email" name="email" [(ngModel)]="email" autocomplete="username" required /></label>
        <label>Hasło <span class="password-field"><input [type]="showPassword() ? 'text' : 'password'" name="password" [(ngModel)]="password" autocomplete="current-password" required />
          <button type="button" (click)="showPassword.set(!showPassword())" [attr.aria-label]="showPassword() ? 'Ukryj hasło' : 'Pokaż hasło'"><i class="pi" [class.pi-eye]="!showPassword()" [class.pi-eye-slash]="showPassword()" aria-hidden="true"></i></button></span></label>
        @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
        <button class="primary" type="submit" [disabled]="busy()">{{ busy() ? 'Logowanie…' : 'Zaloguj się' }}</button>
      </form>
    </main>
  `,
  styles: `
    .password-field { position: relative; display: block; }
    .password-field input { padding-right: 3rem; }
    .password-field button { position: absolute; right: .2rem; top: .15rem; border: 0; background: transparent; color: #52627d; }
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly platform = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly showPassword = signal(false);
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
