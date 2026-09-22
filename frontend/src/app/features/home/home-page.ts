import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink],
  template: `<main class="page"><div class="page-heading"><div><h1>Witaj w SMS Modular</h1><p>Wybierz obszar pracy.</p></div></div>
    <div class="cards">
      @if (auth.isPlatform()) { <a class="panel tile" routerLink="/platform/tenants"><h2>Tenanci</h2><p>Firmy i ich statusy</p></a> }
      @else {
        @if (auth.has('USER_READ')) { <a class="panel tile" routerLink="/users"><h2>Użytkownicy</h2><p>Konta i dostęp</p></a> }
        @if (auth.has('ROLE_READ')) { <a class="panel tile" routerLink="/roles"><h2>Role</h2><p>Uprawnienia zespołu</p></a> }
        @if (auth.has('TENANT_READ')) { <a class="panel tile" routerLink="/settings"><h2>Ustawienia</h2><p>Dane firmy</p></a> }
      }
    </div>
  </main>`,
})
export class HomePage {
  readonly auth = inject(AuthService);
}
