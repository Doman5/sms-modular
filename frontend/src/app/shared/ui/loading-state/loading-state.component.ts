import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-loading-state',
  standalone: true,
  template: `
    <section class="state state-loading" role="status" aria-live="polite">
      <span class="spinner" aria-hidden="true"></span>
      <span>{{ label }}</span>
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
    .state {
      min-height: 9rem;
      display: grid;
      place-content: center;
      justify-items: center;
      gap: 0.75rem;
      color: var(--app-text-muted);
      text-align: center;
    }
    .spinner {
      width: 1.5rem;
      height: 1.5rem;
      border: 0.18rem solid var(--app-border);
      border-top-color: var(--app-primary);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .spinner {
        animation: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoadingStateComponent {
  @Input() label = 'Ładowanie…';
}
