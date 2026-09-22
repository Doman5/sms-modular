import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-operation-confirmation',
  standalone: true,
  template: `
    <section
      class="confirmation"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="confirmation-title"
    >
      <h2 id="confirmation-title">{{ title }}</h2>
      <p>{{ message }}</p>
      <div class="actions">
        <button type="button" class="secondary" [disabled]="busy" (click)="cancelled.emit()">
          {{ cancelLabel }}
        </button>
        <button type="button" class="primary" [disabled]="busy" (click)="confirmed.emit()">
          {{ busy ? 'Zapisywanie…' : confirmLabel }}
        </button>
      </div>
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
    .confirmation {
      padding: 1.25rem;
      border: 1px solid var(--app-border);
      border-radius: 0.85rem;
      background: var(--app-surface);
      box-shadow: var(--app-shadow);
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
      margin-top: 0.45rem;
      color: var(--app-text-muted);
      line-height: 1.5;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.6rem;
      margin-top: 1rem;
    }
    button {
      min-width: 7rem;
      min-height: 2.75rem;
      padding: 0.55rem 0.9rem;
      border-radius: 0.65rem;
      font: inherit;
      font-weight: 700;
      cursor: pointer;
    }
    button:disabled {
      cursor: wait;
      opacity: 0.6;
    }
    .secondary {
      border: 1px solid var(--app-border);
      background: var(--app-surface);
      color: var(--app-text);
    }
    .primary {
      border: 0;
      background: var(--app-primary);
      color: #fff;
    }
    button:focus-visible {
      outline: 3px solid var(--app-focus);
      outline-offset: 2px;
    }
    @media (max-width: 480px) {
      .actions {
        flex-direction: column-reverse;
      }
      button {
        width: 100%;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OperationConfirmationComponent {
  @Input() title = 'Potwierdź operację';
  @Input() message = 'Czy chcesz kontynuować?';
  @Input() confirmLabel = 'Potwierdź';
  @Input() cancelLabel = 'Anuluj';
  @Input() busy = false;
  @Output() readonly confirmed = new EventEmitter<void>();
  @Output() readonly cancelled = new EventEmitter<void>();
}
