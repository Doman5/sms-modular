import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { PageRequest } from '../../../core/api/api.models';

export interface PaginationState extends PageRequest {
  readonly totalElements: number;
  readonly totalPages: number;
}

@Component({
  selector: 'app-pagination',
  standalone: true,
  template: `
    <nav class="pagination" aria-label="Paginacja">
      <span class="summary">{{ summary }}</span>
      <div class="controls">
        <button
          type="button"
          aria-label="Poprzednia strona"
          [disabled]="state.page <= 0 || disabled"
          (click)="selectPage(state.page - 1)"
        >
          <span aria-hidden="true">‹</span>
        </button>
        <span aria-current="page">{{ state.page + 1 }} / {{ pageCount }}</span>
        <button
          type="button"
          aria-label="Następna strona"
          [disabled]="state.page >= pageCount - 1 || disabled"
          (click)="selectPage(state.page + 1)"
        >
          <span aria-hidden="true">›</span>
        </button>
      </div>
    </nav>
  `,
  styles: `
    :host {
      display: block;
    }
    .pagination {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding-top: 1rem;
      color: var(--app-text-muted);
      font-size: 0.9rem;
    }
    .controls {
      display: flex;
      align-items: center;
      gap: 0.65rem;
    }
    button {
      display: grid;
      width: 2.75rem;
      height: 2.75rem;
      place-items: center;
      border: 1px solid var(--app-border);
      border-radius: 0.65rem;
      background: var(--app-surface);
      color: var(--app-text);
      font: inherit;
      font-size: 1.25rem;
      cursor: pointer;
    }
    button:disabled {
      cursor: not-allowed;
      opacity: 0.45;
    }
    button:focus-visible {
      outline: 3px solid var(--app-focus);
      outline-offset: 2px;
    }
    @media (max-width: 480px) {
      .pagination {
        align-items: flex-start;
        flex-direction: column;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaginationComponent {
  @Input({ required: true }) state!: PaginationState;
  @Input() disabled = false;
  @Output() readonly pageChange = new EventEmitter<PageRequest>();

  get pageCount(): number {
    return Math.max(1, this.state?.totalPages ?? 1);
  }

  get summary(): string {
    const total = this.state?.totalElements ?? 0;
    return total === 0 ? 'Brak wyników' : `${total} ${total === 1 ? 'wynik' : 'wyników'}`;
  }

  selectPage(page: number): void {
    if (
      !this.state ||
      this.disabled ||
      page < 0 ||
      page >= this.pageCount ||
      page === this.state.page
    ) {
      return;
    }

    this.pageChange.emit({ page, size: this.state.size });
  }
}
