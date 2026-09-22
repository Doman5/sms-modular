import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
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
import { AuditApiService } from './audit.api';
import { AuditLog, AuditLogPage, AuditLogQuery } from './audit.models';

type AuditPageStatus = 'loading' | 'ready' | 'empty' | 'forbidden' | 'error';

@Component({
  selector: 'app-audit-page',
  standalone: true,
  imports: [
    DatePipe,
    EmptyStateComponent,
    ForbiddenStateComponent,
    LoadingStateComponent,
    PaginationComponent,
    ReactiveFormsModule,
    RetryableErrorStateComponent,
    RouterLink,
  ],
  templateUrl: './audit-page.component.html',
  styleUrl: './audit-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditPageComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(AuditApiService);
  private readonly sessionProvider = inject(SESSION_STATE_PROVIDER);
  private readonly pageSize = 25;

  readonly filterForm = this.formBuilder.group({
    from: [''],
    to: [''],
    actorType: [''],
    actorId: [''],
    module: [''],
    action: [''],
    subjectType: [''],
    subjectId: [''],
  });
  readonly status = signal<AuditPageStatus>('loading');
  readonly page = signal<AuditLogPage | null>(null);
  readonly selected = signal<AuditLog | null>(null);
  readonly problem = signal<ApiProblemError | null>(null);
  readonly busy = computed(() => this.status() === 'loading');
  readonly canRead = this.hasPermission('AUDIT_READ');
  private query: AuditLogQuery = {};

  constructor() {
    if (!this.canRead) {
      this.status.set('forbidden');
      return;
    }
    this.load({ page: 0, size: this.pageSize });
  }

  submitFilters(): void {
    if (!this.canRead || this.busy()) {
      return;
    }
    const values = this.filterForm.getRawValue();
    this.query = {
      from: this.toInstant(values.from),
      to: this.toInstant(values.to),
      actorType: this.optional(values.actorType),
      actorId: this.optional(values.actorId),
      module: this.optional(values.module),
      action: this.optional(values.action),
      subjectType: this.optional(values.subjectType),
      subjectId: this.optional(values.subjectId),
    };
    this.load({ page: 0, size: this.pageSize });
  }

  clearFilters(): void {
    if (this.busy()) {
      return;
    }
    this.filterForm.reset();
    this.query = {};
    this.load({ page: 0, size: this.pageSize });
  }

  retry(): void {
    const current = this.page();
    this.load({ page: current?.page ?? 0, size: current?.size ?? this.pageSize });
  }

  select(entry: AuditLog): void {
    this.selected.set(this.selected()?.id === entry.id ? null : entry);
  }

  changePage(request: PageRequest): void {
    this.load(request);
  }

  metadataKeys(entry: AuditLog | null): readonly string[] {
    return entry ? Object.keys(entry.metadata) : [];
  }

  isForbidden(problem: ApiProblemError | null): boolean {
    return problem?.problem.status === 403 || problem?.problem.code === 'FORBIDDEN';
  }

  isRetryable(problem: ApiProblemError | null): boolean {
    const status = problem?.problem.status ?? 0;
    return status === 0 || status >= 500 || problem?.problem.code === 'NETWORK_ERROR';
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

  private load(request: PageRequest): void {
    if (!this.canRead) {
      this.status.set('forbidden');
      return;
    }
    this.status.set('loading');
    this.problem.set(null);
    this.api.list(request, this.query).subscribe({
      next: (page) => {
        this.page.set(page);
        this.selected.set(null);
        this.status.set(page.items.length === 0 ? 'empty' : 'ready');
      },
      error: (error: unknown) => {
        const problem = error instanceof ApiProblemError ? error : null;
        this.problem.set(problem);
        this.status.set(this.isForbidden(problem) ? 'forbidden' : 'error');
      },
    });
  }

  private hasPermission(permission: string): boolean {
    const snapshot = this.sessionProvider.getSnapshot();
    return snapshot.status === 'unknown'
      ? true
      : snapshot.status === 'authenticated' && snapshot.permissions.includes(permission);
  }

  private optional(value: string): string | undefined {
    const trimmed = value.trim();
    return trimmed || undefined;
  }

  private toInstant(value: string): string | undefined {
    if (!value) {
      return undefined;
    }
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
  }
}
