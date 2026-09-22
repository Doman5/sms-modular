import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmptyStateComponent } from './empty-state/empty-state.component';
import { ForbiddenStateComponent } from './forbidden-state/forbidden-state.component';
import { LoadingStateComponent } from './loading-state/loading-state.component';
import { OperationConfirmationComponent } from './operation-confirmation/operation-confirmation.component';
import { PaginationComponent, PaginationState } from './pagination/pagination.component';
import { RetryableErrorStateComponent } from './retryable-error-state/retryable-error-state.component';
import { ValidationSummaryComponent } from './validation-summary/validation-summary.component';

@Component({
  standalone: true,
  imports: [
    EmptyStateComponent,
    ForbiddenStateComponent,
    LoadingStateComponent,
    OperationConfirmationComponent,
    PaginationComponent,
    RetryableErrorStateComponent,
    ValidationSummaryComponent,
  ],
  template: `
    <app-empty-state (action)="emptyAction = true" actionLabel="Dodaj" />
    <app-loading-state label="Trwa ładowanie" />
    <app-forbidden-state />
    <app-retryable-error-state (retry)="retryCount = retryCount + 1" />
    <app-operation-confirmation (confirmed)="confirmed = true" (cancelled)="cancelled = true" />
    <app-pagination [state]="pagination" (pageChange)="pageRequest = $event" />
    <app-validation-summary [errors]="validationErrors" />
  `,
})
class StateHostComponent {
  emptyAction = false;
  retryCount = 0;
  confirmed = false;
  cancelled = false;
  pageRequest: { page: number; size: number } | undefined;
  pagination: PaginationState = { page: 0, size: 10, totalElements: 21, totalPages: 3 };
  validationErrors = [{ field: 'name', messages: ['Pole jest wymagane.'] }];
}

describe('foundation state components', () => {
  let fixture: ComponentFixture<StateHostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [StateHostComponent] }).compileComponents();
    fixture = TestBed.createComponent(StateHostComponent);
    fixture.detectChanges();
  });

  it('emits retry, confirmation and empty-state interactions', () => {
    const buttons = fixture.nativeElement.querySelectorAll(
      'button',
    ) as NodeListOf<HTMLButtonElement>;
    const retryButton = Array.from(buttons).find((button) =>
      button.textContent?.includes('Spróbuj'),
    );
    const confirmButton = Array.from(buttons).find((button) =>
      button.textContent?.includes('Potwierdź'),
    );
    const emptyButton = Array.from(buttons).find((button) => button.textContent?.includes('Dodaj'));

    retryButton?.click();
    confirmButton?.click();
    emptyButton?.click();
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.retryCount).toBe(1);
    expect(component.confirmed).toBeTrue();
    expect(component.emptyAction).toBeTrue();
  });

  it('emits a controlled next-page request and exposes accessible labels', () => {
    const nextButton = fixture.nativeElement.querySelector(
      'app-pagination button[aria-label="Następna strona"]',
    ) as HTMLButtonElement;
    nextButton.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.pageRequest).toEqual({ page: 1, size: 10 });
    expect(nextButton.disabled).toBeFalse();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[role="alertdialog"]')).toBeTruthy();
  });

  it('disables pagination controls at the boundaries', () => {
    fixture.componentInstance.pagination = { page: 2, size: 10, totalElements: 21, totalPages: 3 };
    fixture.detectChanges();

    const previous = fixture.nativeElement.querySelector(
      'app-pagination button[aria-label="Poprzednia strona"]',
    ) as HTMLButtonElement;
    const next = fixture.nativeElement.querySelector(
      'app-pagination button[aria-label="Następna strona"]',
    ) as HTMLButtonElement;
    expect(previous.disabled).toBeFalse();
    expect(next.disabled).toBeTrue();
  });

  it('renders explicit loading and forbidden semantics', () => {
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Brak dostępu');
  });
});
