import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-retryable-error-state',
  standalone: true,
  template: `
    <section class="state state-error" role="alert" aria-live="assertive">
      <div class="state-icon" aria-hidden="true">!</div>
      <h2>{{ title }}</h2>
      <p>{{ message }}</p>
      <button type="button" class="state-action" [disabled]="busy" (click)="retry.emit()">
        {{ busy ? 'Ponawianie…' : retryLabel }}
      </button>
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
      background: var(--app-danger-soft);
      color: var(--app-danger);
      font-size: 1.2rem;
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
    .state-action {
      min-height: 2.75rem;
      margin-top: 0.5rem;
      padding: 0.55rem 1rem;
      border: 1px solid var(--app-danger);
      border-radius: 0.65rem;
      background: transparent;
      color: var(--app-danger);
      font: inherit;
      font-weight: 700;
      cursor: pointer;
    }
    .state-action:disabled {
      cursor: wait;
      opacity: 0.65;
    }
    .state-action:focus-visible {
      outline: 3px solid var(--app-focus);
      outline-offset: 2px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RetryableErrorStateComponent {
  @Input() title = 'Nie udało się wczytać danych';
  @Input() message = 'Spróbuj ponownie za chwilę.';
  @Input() retryLabel = 'Spróbuj ponownie';
  @Input() busy = false;
  @Output() readonly retry = new EventEmitter<void>();
}
