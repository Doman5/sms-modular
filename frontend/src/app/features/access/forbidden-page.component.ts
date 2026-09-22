import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ForbiddenStateComponent } from '../../shared/ui/forbidden-state/forbidden-state.component';

@Component({
  selector: 'app-forbidden-page',
  standalone: true,
  imports: [ForbiddenStateComponent],
  template: `
    <main class="forbidden-page">
      <app-forbidden-state />
    </main>
  `,
  styles: `
    :host {
      display: block;
      min-height: 100vh;
    }
    .forbidden-page {
      display: grid;
      min-height: 100vh;
      place-items: center;
      padding: 1.5rem;
      background: var(--app-bg);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForbiddenPageComponent {}
