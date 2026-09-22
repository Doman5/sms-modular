import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ApiProblemError, ProblemFieldError } from '../../core/http/problem-details.error';
import { PageRequest } from '../../core/api/api.models';
import { SESSION_STATE_PROVIDER } from '../../core/auth/session-state.provider';
import { EmptyStateComponent } from '../../shared/ui/empty-state/empty-state.component';
import { ForbiddenStateComponent } from '../../shared/ui/forbidden-state/forbidden-state.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state/loading-state.component';
import { OperationConfirmationComponent } from '../../shared/ui/operation-confirmation/operation-confirmation.component';
import {
  PaginationComponent,
  PaginationState,
} from '../../shared/ui/pagination/pagination.component';
import { RetryableErrorStateComponent } from '../../shared/ui/retryable-error-state/retryable-error-state.component';
import { ValidationSummaryComponent } from '../../shared/ui/validation-summary/validation-summary.component';
import { hasTenantPermission } from './tenant-permissions';
import { TenancyApiService } from './tenancy.api';
import { Tenant, TenantPage, TenantStatus } from './tenancy.models';
import { localeValidator, TENANT_SLUG_PATTERN, timezoneValidator } from './tenant-validators';

type ListStatus = 'loading' | 'ready' | 'error' | 'forbidden';
type DetailStatus = 'idle' | 'loading' | 'ready' | 'error';
type LifecycleAction = 'suspend' | 'activate' | 'close';

@Component({
  selector: 'app-platform-tenants-page',
  standalone: true,
  imports: [
    EmptyStateComponent,
    ForbiddenStateComponent,
    LoadingStateComponent,
    OperationConfirmationComponent,
    PaginationComponent,
    ReactiveFormsModule,
    RetryableErrorStateComponent,
    ValidationSummaryComponent,
  ],
  templateUrl: './platform-tenants-page.component.html',
  styleUrl: './platform-tenants-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlatformTenantsPageComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(TenancyApiService);
  private readonly sessionProvider = inject(SESSION_STATE_PROVIDER);

  readonly tenants = signal<readonly Tenant[]>([]);
  readonly page = signal<PaginationState>({
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
  });
  readonly listStatus = signal<ListStatus>('loading');
  readonly listProblem = signal<ApiProblemError | null>(null);

  readonly selectedTenant = signal<Tenant | null>(null);
  readonly detailStatus = signal<DetailStatus>('idle');
  readonly detailProblem = signal<ApiProblemError | null>(null);

  readonly createOpen = signal(false);
  readonly createBusy = signal(false);
  readonly createProblem = signal<ApiProblemError | null>(null);
  readonly createMessage = signal('');

  readonly editBusy = signal(false);
  readonly editProblem = signal<ApiProblemError | null>(null);
  readonly editMessage = signal('');

  readonly lifecycleBusy = signal(false);
  readonly lifecycleAction = signal<LifecycleAction | null>(null);
  readonly lifecycleProblem = signal<ApiProblemError | null>(null);
  readonly lifecycleMessage = signal('');
  readonly closeConfirmationOpen = signal(false);

  readonly createFieldErrors = () => this.createProblem()?.problem.errors ?? [];
  readonly editFieldErrors = () => this.editProblem()?.problem.errors ?? [];

  readonly createForm = this.formBuilder.group({
    slug: [
      '',
      [Validators.required, Validators.maxLength(64), Validators.pattern(TENANT_SLUG_PATTERN)],
    ],
    name: ['', [Validators.required, Validators.maxLength(255), Validators.pattern(/\S/)]],
    timezone: ['', [Validators.required, timezoneValidator]],
    locale: ['', [Validators.required, localeValidator]],
  });

  readonly editForm = this.formBuilder.group({
    name: ['', [Validators.required, Validators.maxLength(255), Validators.pattern(/\S/)]],
    timezone: ['', [Validators.required, timezoneValidator]],
    locale: ['', [Validators.required, localeValidator]],
  });

  readonly canRead = hasTenantPermission(this.sessionProvider, 'PLATFORM_TENANT_READ');
  readonly canCreate = hasTenantPermission(this.sessionProvider, 'PLATFORM_TENANT_CREATE');
  readonly canUpdate = hasTenantPermission(this.sessionProvider, 'PLATFORM_TENANT_UPDATE');
  readonly canSuspend = hasTenantPermission(this.sessionProvider, 'PLATFORM_TENANT_SUSPEND');
  readonly canActivate = hasTenantPermission(this.sessionProvider, 'PLATFORM_TENANT_ACTIVATE');
  readonly canClose = hasTenantPermission(this.sessionProvider, 'PLATFORM_TENANT_CLOSE');

  constructor() {
    this.loadPage(0);
  }

  loadPage(page: number): void {
    if (!this.canRead) {
      this.listStatus.set('forbidden');
      return;
    }

    const request: PageRequest = { page, size: this.page().size };
    this.listStatus.set('loading');
    this.listProblem.set(null);
    this.api.listPlatformTenants(request).subscribe({
      next: (result) => this.setPage(result),
      error: (error: unknown) => {
        this.listStatus.set(this.isForbiddenError(error) ? 'forbidden' : 'error');
        this.listProblem.set(error instanceof ApiProblemError ? error : null);
      },
    });
  }

  retryList(): void {
    this.loadPage(this.page().page);
  }

  selectTenant(tenant: Tenant): void {
    if (!this.canRead) {
      return;
    }

    this.selectedTenant.set(tenant);
    this.editProblem.set(null);
    this.editMessage.set('');
    this.detailStatus.set('loading');
    this.detailProblem.set(null);
    this.api.getPlatformTenant(tenant.id).subscribe({
      next: (detail) => {
        this.setSelectedTenant(detail);
        this.detailStatus.set('ready');
      },
      error: (error: unknown) => {
        this.detailStatus.set('error');
        this.detailProblem.set(error instanceof ApiProblemError ? error : null);
      },
    });
  }

  openCreate(): void {
    if (!this.canCreate) {
      return;
    }

    this.createForm.reset({ slug: '', name: '', timezone: 'Europe/Warsaw', locale: 'pl-PL' });
    this.createProblem.set(null);
    this.createMessage.set('');
    this.createOpen.set(true);
  }

  closeCreate(): void {
    if (!this.createBusy()) {
      this.createOpen.set(false);
    }
  }

  createTenant(): void {
    if (!this.canCreate || this.createBusy()) {
      return;
    }

    this.createForm.markAllAsTouched();
    if (this.createForm.invalid) {
      this.createMessage.set('Popraw zaznaczone pola przed zapisaniem.');
      return;
    }

    const value = this.createForm.getRawValue();
    this.createBusy.set(true);
    this.createProblem.set(null);
    this.createMessage.set('');
    this.api
      .createPlatformTenant({
        slug: value.slug.trim(),
        name: value.name.trim(),
        timezone: value.timezone.trim(),
        locale: value.locale.trim(),
      })
      .subscribe({
        next: (tenant) => {
          this.createBusy.set(false);
          this.createOpen.set(false);
          this.createMessage.set('Tenant został utworzony.');
          this.setSelectedTenant(tenant);
          this.detailStatus.set('ready');
          this.loadPage(this.page().page);
        },
        error: (error: unknown) => {
          this.createBusy.set(false);
          if (error instanceof ApiProblemError) {
            this.createProblem.set(error);
            this.createMessage.set(error.problem.message);
          } else {
            this.createMessage.set('Nie udało się utworzyć tenanta. Spróbuj ponownie.');
          }
        },
      });
  }

  updateTenant(): void {
    const tenant = this.selectedTenant();
    if (!tenant || !this.canUpdate || this.editBusy()) {
      return;
    }

    this.editForm.markAllAsTouched();
    if (this.editForm.invalid) {
      this.editMessage.set('Popraw zaznaczone pola przed zapisaniem.');
      return;
    }

    const value = this.editForm.getRawValue();
    this.editBusy.set(true);
    this.editProblem.set(null);
    this.editMessage.set('');
    this.api
      .updatePlatformTenant(tenant.id, {
        name: value.name.trim(),
        timezone: value.timezone.trim(),
        locale: value.locale.trim(),
      })
      .subscribe({
        next: (updated) => {
          this.editBusy.set(false);
          this.editForm.markAsPristine();
          this.setSelectedTenant(updated);
          this.replaceTenant(updated);
          this.editMessage.set('Zmiany zostały zapisane.');
        },
        error: (error: unknown) => {
          this.editBusy.set(false);
          if (error instanceof ApiProblemError) {
            this.editProblem.set(error);
            this.editMessage.set(error.problem.message);
          } else {
            this.editMessage.set('Nie udało się zapisać zmian. Spróbuj ponownie.');
          }
        },
      });
  }

  startLifecycle(action: LifecycleAction): void {
    const tenant = this.selectedTenant();
    if (!tenant || !this.canLifecycle(action) || this.lifecycleBusy()) {
      return;
    }

    if (action === 'close') {
      this.lifecycleAction.set(action);
      this.closeConfirmationOpen.set(true);
      return;
    }

    this.executeLifecycle(action);
  }

  cancelClose(): void {
    if (!this.lifecycleBusy()) {
      this.closeConfirmationOpen.set(false);
      this.lifecycleAction.set(null);
    }
  }

  confirmClose(): void {
    if (this.closeConfirmationOpen() && !this.lifecycleBusy()) {
      this.closeConfirmationOpen.set(false);
      this.executeLifecycle('close');
    }
  }

  retryLifecycle(): void {
    const action = this.lifecycleAction();
    if (action && !this.lifecycleBusy()) {
      this.executeLifecycle(action);
    }
  }

  canLifecycle(action: LifecycleAction): boolean {
    return action === 'suspend'
      ? this.canSuspend
      : action === 'activate'
        ? this.canActivate
        : this.canClose;
  }

  lifecycleLabel(action: LifecycleAction): string {
    return action === 'suspend'
      ? 'Zawieś tenanta'
      : action === 'activate'
        ? 'Aktywuj tenanta'
        : 'Zamknij tenanta';
  }

  lifecycleIcon(action: LifecycleAction): string {
    return action === 'suspend' ? 'Ⅱ' : action === 'activate' ? '▶' : '×';
  }

  statusLabel(status: TenantStatus): string {
    return status === 'ACTIVE' ? 'Aktywny' : status === 'SUSPENDED' ? 'Zawieszony' : 'Zamknięty';
  }

  statusIcon(status: TenantStatus): string {
    return status === 'ACTIVE' ? '●' : status === 'SUSPENDED' ? '!' : '×';
  }

  statusClass(status: TenantStatus): string {
    return status.toLowerCase();
  }

  formatDate(value: string): string {
    const parsed = new Date(value);
    return Number.isNaN(parsed.valueOf())
      ? value
      : new Intl.DateTimeFormat('pl-PL', { dateStyle: 'medium' }).format(parsed);
  }

  controlError(control: AbstractControl): string | null {
    if (!control.touched || !control.errors) {
      return null;
    }
    if (control.hasError('required')) {
      return 'To pole jest wymagane.';
    }
    if (control.hasError('maxlength')) {
      return 'Wartość jest za długa.';
    }
    if (control.hasError('pattern')) {
      return 'Wartość ma niepoprawny format.';
    }
    if (control.hasError('timezone')) {
      return 'Podaj poprawną strefę czasową IANA.';
    }
    if (control.hasError('locale')) {
      return 'Podaj poprawny locale, np. pl-PL.';
    }
    return 'Wartość jest niepoprawna.';
  }

  isForbiddenError(error: unknown): boolean {
    return (
      error instanceof ApiProblemError &&
      (error.problem.status === 403 || error.problem.code === 'FORBIDDEN')
    );
  }

  isRetryable(problem: ApiProblemError | null): boolean {
    const status = problem?.problem.status ?? 0;
    return status === 0 || status >= 500 || problem?.problem.code === 'NETWORK_ERROR';
  }

  private executeLifecycle(action: LifecycleAction): void {
    const tenant = this.selectedTenant();
    if (!tenant || !this.canLifecycle(action) || this.lifecycleBusy()) {
      return;
    }

    this.lifecycleAction.set(action);
    this.lifecycleBusy.set(true);
    this.lifecycleProblem.set(null);
    this.lifecycleMessage.set('');
    const request =
      action === 'suspend'
        ? this.api.suspendPlatformTenant(tenant.id)
        : action === 'activate'
          ? this.api.activatePlatformTenant(tenant.id)
          : this.api.closePlatformTenant(tenant.id);

    request.subscribe({
      next: (updated) => {
        this.lifecycleBusy.set(false);
        this.setSelectedTenant(updated);
        this.replaceTenant(updated);
        this.lifecycleMessage.set(
          `Tenant: ${this.lifecycleLabel(action).toLowerCase()} — zakończono.`,
        );
        this.closeConfirmationOpen.set(false);
      },
      error: (error: unknown) => {
        this.lifecycleBusy.set(false);
        if (error instanceof ApiProblemError) {
          this.lifecycleProblem.set(error);
          this.lifecycleMessage.set(error.problem.message);
        } else {
          this.lifecycleMessage.set('Nie udało się wykonać operacji. Spróbuj ponownie.');
        }
      },
    });
  }

  private setPage(result: TenantPage): void {
    this.tenants.set(result.items);
    this.page.set({
      page: result.page,
      size: result.size,
      totalElements: result.totalElements,
      totalPages: result.totalPages,
    });
    this.listStatus.set('ready');
    this.listProblem.set(null);
  }

  private setSelectedTenant(tenant: Tenant): void {
    this.selectedTenant.set(tenant);
    this.editForm.reset({ name: tenant.name, timezone: tenant.timezone, locale: tenant.locale });
  }

  private replaceTenant(updated: Tenant): void {
    this.tenants.update((items) =>
      items.map((tenant) => (tenant.id === updated.id ? updated : tenant)),
    );
  }
}
