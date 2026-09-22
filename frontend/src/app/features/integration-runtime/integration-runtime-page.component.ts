import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { PageRequest } from '../../core/api/api.models';
import { SESSION_STATE_PROVIDER } from '../../core/auth/session-state.provider';
import { ApiProblemError } from '../../core/http/problem-details.error';
import { EmptyStateComponent } from '../../shared/ui/empty-state/empty-state.component';
import { ForbiddenStateComponent } from '../../shared/ui/forbidden-state/forbidden-state.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state/loading-state.component';
import {
  PaginationComponent,
  PaginationState,
} from '../../shared/ui/pagination/pagination.component';
import { RetryableErrorStateComponent } from '../../shared/ui/retryable-error-state/retryable-error-state.component';
import { hasTenantPermission } from '../tenancy/tenant-permissions';
import { IntegrationRuntimeApiService } from './integration-runtime.api';
import { DeadLetter, DeadLetterPage } from './integration-runtime.models';

type PageStatus = 'idle' | 'loading' | 'ready' | 'empty' | 'forbidden' | 'error';

@Component({
  selector: 'app-integration-runtime-page',
  standalone: true,
  imports: [
    DatePipe,
    EmptyStateComponent,
    ForbiddenStateComponent,
    LoadingStateComponent,
    PaginationComponent,
    ReactiveFormsModule,
    RetryableErrorStateComponent,
  ],
  templateUrl: './integration-runtime-page.component.html',
  styleUrl: './integration-runtime-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IntegrationRuntimePageComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(IntegrationRuntimeApiService);
  private readonly sessionProvider = inject(SESSION_STATE_PROVIDER);
  private readonly pageSize = 25;

  readonly form = this.formBuilder.group({
    tenantId: [
      '',
      [
        Validators.required,
        Validators.pattern(
          /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
        ),
      ],
    ],
  });
  readonly status = signal<PageStatus>('idle');
  readonly page = signal<DeadLetterPage | null>(null);
  readonly selected = signal<DeadLetter | null>(null);
  readonly problem = signal<ApiProblemError | null>(null);
  readonly retryingId = signal<string | null>(null);
  readonly message = signal('');
  readonly busy = computed(() => this.status() === 'loading');
  readonly canRead = hasTenantPermission(this.sessionProvider, 'PLATFORM_INTEGRATION_READ');
  readonly canRetry = hasTenantPermission(this.sessionProvider, 'PLATFORM_INTEGRATION_RETRY');

  submit(): void {
    if (!this.canRead || this.busy()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.load({ page: 0, size: this.pageSize });
  }

  load(request: PageRequest): void {
    if (!this.canRead) {
      this.status.set('forbidden');
      return;
    }
    const tenantId = this.form.controls.tenantId.value.trim();
    if (!tenantId || this.form.invalid) {
      this.status.set('idle');
      return;
    }
    this.status.set('loading');
    this.problem.set(null);
    this.message.set('');
    this.api.list(tenantId, request).subscribe({
      next: (result) => {
        this.page.set(result);
        this.selected.set(null);
        this.status.set(result.items.length === 0 ? 'empty' : 'ready');
      },
      error: (error: unknown) => {
        const problem = error instanceof ApiProblemError ? error : null;
        this.problem.set(problem);
        this.status.set(this.isForbidden(problem) ? 'forbidden' : 'error');
      },
    });
  }

  retryLoad(): void {
    const current = this.page();
    this.load({ page: current?.page ?? 0, size: current?.size ?? this.pageSize });
  }

  changePage(request: PageRequest): void {
    this.load(request);
  }

  select(entry: DeadLetter): void {
    this.selected.set(this.selected()?.id === entry.id ? null : entry);
  }

  retry(entry: DeadLetter): void {
    if (!this.canRetry || this.retryingId() || entry.status !== 'DEAD_LETTER') return;
    const tenantId = this.form.controls.tenantId.value.trim();
    this.retryingId.set(entry.id);
    this.message.set('');
    this.api.retry(tenantId, entry.id).subscribe({
      next: (updated) => {
        this.retryingId.set(null);
        this.page.update((current) =>
          current
            ? {
                ...current,
                items: current.items.map((item) => (item.id === updated.id ? updated : item)),
              }
            : current,
        );
        this.selected.set(updated);
        this.message.set('Ponowienie zapisane i zarejestrowane w audycie.');
      },
      error: (error: unknown) => {
        this.retryingId.set(null);
        this.problem.set(error instanceof ApiProblemError ? error : null);
        this.message.set('Nie udało się ponowić wiadomości.');
      },
    });
  }

  paginationState(): PaginationState {
    const value = this.page();
    return {
      page: value?.page ?? 0,
      size: value?.size ?? this.pageSize,
      totalElements: value?.totalElements ?? 0,
      totalPages: value?.totalPages ?? 0,
    };
  }

  isForbidden(problem: ApiProblemError | null): boolean {
    return problem?.problem.status === 403 || problem?.problem.code === 'FORBIDDEN';
  }

  isRetryable(problem: ApiProblemError | null): boolean {
    const status = problem?.problem.status ?? 0;
    return status === 0 || status >= 500 || problem?.problem.code === 'NETWORK_ERROR';
  }
}
