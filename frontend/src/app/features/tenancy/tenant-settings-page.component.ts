import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiProblemError, ProblemFieldError } from '../../core/http/problem-details.error';
import { SESSION_STATE_PROVIDER } from '../../core/auth/session-state.provider';
import { ForbiddenStateComponent } from '../../shared/ui/forbidden-state/forbidden-state.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state/loading-state.component';
import { RetryableErrorStateComponent } from '../../shared/ui/retryable-error-state/retryable-error-state.component';
import { ValidationSummaryComponent } from '../../shared/ui/validation-summary/validation-summary.component';
import { TenantContextService } from './tenant-context.service';
import { hasTenantPermission } from './tenant-permissions';
import { TenancyApiService } from './tenancy.api';
import { TenantSettingsFormModel } from './tenancy.models';
import { localeValidator, timezoneValidator } from './tenant-validators';

@Component({
  selector: 'app-tenant-settings-page',
  standalone: true,
  imports: [
    ForbiddenStateComponent,
    LowerCasePipe,
    LoadingStateComponent,
    ReactiveFormsModule,
    RetryableErrorStateComponent,
    RouterLink,
    ValidationSummaryComponent,
  ],
  templateUrl: './tenant-settings-page.component.html',
  styleUrl: './tenant-settings-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TenantSettingsPageComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(TenancyApiService);
  private readonly sessionProvider = inject(SESSION_STATE_PROVIDER);
  readonly tenantContext = inject(TenantContextService);

  readonly settingsForm = this.formBuilder.group({
    name: ['', [Validators.required, Validators.maxLength(255), Validators.pattern(/\S/)]],
    timezone: ['', [Validators.required, timezoneValidator]],
    locale: ['', [Validators.required, localeValidator]],
  });

  readonly saveBusy = signal(false);
  readonly saveProblem = signal<ApiProblemError | null>(null);
  readonly saveMessage = signal('');
  readonly saved = signal(false);
  readonly serverFieldErrors = computed<readonly ProblemFieldError[]>(
    () => this.saveProblem()?.problem.errors ?? [],
  );
  readonly canEdit = hasTenantPermission(this.sessionProvider, 'TENANT_SETTINGS_EDIT');

  private hydratedTenantId: string | null = null;

  constructor() {
    effect(() => {
      const tenant = this.tenantContext.tenant();
      if (!tenant) {
        return;
      }

      if (tenant.id === this.hydratedTenantId && this.settingsForm.dirty) {
        return;
      }

      this.settingsForm.reset({
        name: tenant.name,
        timezone: tenant.timezone,
        locale: tenant.locale,
      });
      this.hydratedTenantId = tenant.id;
    });

    this.tenantContext.load();
  }

  save(): void {
    if (!this.canEdit || this.saveBusy()) {
      return;
    }

    this.settingsForm.markAllAsTouched();
    if (this.settingsForm.invalid) {
      this.saveMessage.set('Popraw zaznaczone pola przed zapisaniem.');
      this.saved.set(false);
      return;
    }

    const value: TenantSettingsFormModel = this.settingsForm.getRawValue();
    this.saveBusy.set(true);
    this.saveProblem.set(null);
    this.saveMessage.set('');
    this.saved.set(false);

    this.api
      .updateTenant({
        name: value.name.trim(),
        timezone: value.timezone.trim(),
        locale: value.locale.trim(),
      })
      .subscribe({
        next: (tenant) => {
          this.tenantContext.accept(tenant);
          this.settingsForm.markAsPristine();
          this.saveBusy.set(false);
          this.saved.set(true);
          this.saveMessage.set('Zmiany zostały zapisane.');
        },
        error: (error: unknown) => {
          this.saveBusy.set(false);
          this.saved.set(false);
          if (error instanceof ApiProblemError) {
            this.saveProblem.set(error);
            this.saveMessage.set(error.problem.message);
          } else {
            this.saveMessage.set('Nie udało się zapisać zmian. Spróbuj ponownie.');
          }
        },
      });
  }

  retryTenantLoad(): void {
    this.tenantContext.retry();
  }

  retrySave(): void {
    this.save();
  }

  isForbidden(problem: ApiProblemError | null): boolean {
    return problem?.problem.status === 403 || problem?.problem.code === 'FORBIDDEN';
  }

  isRetryable(problem: ApiProblemError | null): boolean {
    const status = problem?.problem.status ?? 0;
    return status === 0 || status >= 500 || problem?.problem.code === 'NETWORK_ERROR';
  }

  fieldError(field: keyof TenantSettingsFormModel): string | null {
    const control = this.settingsForm.controls[field];
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
      return 'Wartość nie może być pusta.';
    }
    if (control.hasError('timezone')) {
      return 'Podaj poprawną strefę czasową IANA, np. Europe/Warsaw.';
    }
    if (control.hasError('locale')) {
      return 'Podaj poprawny locale, np. pl-PL lub en-US.';
    }
    return 'Wartość jest niepoprawna.';
  }
}
