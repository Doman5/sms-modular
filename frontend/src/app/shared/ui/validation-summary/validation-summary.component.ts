import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { ProblemFieldError } from '../../../core/http/problem-details.error';

@Component({
  selector: 'app-validation-summary',
  standalone: true,
  template: `
    @if (errors.length > 0) {
      <section class="validation" role="alert" aria-labelledby="validation-title">
        <h2 id="validation-title">Sprawdź formularz</h2>
        <ul>
          @for (error of errors; track error.field) {
            <li>
              <strong>{{ error.field }}</strong>
              <ul>
                @for (message of error.messages; track message) {
                  <li>{{ message }}</li>
                }
              </ul>
            </li>
          }
        </ul>
      </section>
    }
  `,
  styles: `
    :host {
      display: block;
    }
    .validation {
      padding: 1rem 1.15rem;
      border: 1px solid var(--app-danger);
      border-radius: 0.75rem;
      background: var(--app-danger-soft);
      color: var(--app-text);
    }
    h2 {
      margin: 0 0 0.5rem;
      color: var(--app-danger);
      font-size: 1rem;
    }
    ul {
      margin: 0;
      padding-left: 1.2rem;
    }
    li + li {
      margin-top: 0.25rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ValidationSummaryComponent {
  @Input() errors: readonly ProblemFieldError[] = [];
}
