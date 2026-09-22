import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-forbidden-state',
  standalone: true,
  template: `
    <section class="state state-forbidden" role="alert">
      <div class="state-icon" aria-hidden="true">×</div>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
    .state {
      min-height: 12rem;
      display: grid;
      place-content: center;
      justify-items: center;
      gap: 0.55rem;
      padding: 2rem;
      color: var(--app-text-muted);
      text-align: center;
    }
    .state-icon {
      display: grid;
      width: 3rem;
      height: 3rem;
      place-items: center;
      border-radius: 50%;
      background: var(--app-warning-soft);
      color: var(--app-warning);
      font-size: 1.3rem;
      font-weight: 800;
    }
    h2,
    p {
      margin: 0;
    }
    h2 {
      color: var(--app-text);
      font-size: 1.05rem;
    }
    p {
      max-width: 34rem;
      line-height: 1.55;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForbiddenStateComponent {
  @Input() title = 'Brak dostępu';
  @Input() description = 'Twoje uprawnienia nie pozwalają na wyświetlenie tej części aplikacji.';
}
