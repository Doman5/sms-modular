import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden-page',
  imports: [RouterLink],
  template: `<main class="auth-layout"><div class="auth-card"><h1>Brak dostępu</h1><p>Nie masz uprawnień do tego widoku.</p><a routerLink="/">Wróć do panelu</a></div></main>`,
})
export class ForbiddenPage {}
