import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  template: `
    <section class="state state-empty" role="status" aria-live="polite">
      <div class="state-icon" aria-hidden="true">{{ icon }}</div>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
      @if (actionLabel) {
        <button type="button" class="state-action" (click)="action.emit()">
          {{ actionLabel }}
        </button>
      }
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
      border-radius: 1rem;
      background: var(--app-surface-muted);
      color: var(--app-primary);
      font-size: 1.25rem;
      font-weight: 700;
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
      border: 0;
      border-radius: 0.65rem;
      background: var(--app-primary);
      color: #fff;
      font: inherit;
      font-weight: 700;
      cursor: pointer;
    }
    .state-action:focus-visible {
      outline: 3px solid var(--app-focus);
      outline-offset: 2px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  @Input() title = 'Brak danych';
  @Input() description = 'Nie ma jeszcze elementów do wyświetlenia.';
  @Input() actionLabel = '';
  @Input() icon = '○';
  @Output() readonly action = new EventEmitter<void>();
}
