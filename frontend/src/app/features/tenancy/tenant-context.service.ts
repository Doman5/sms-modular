import { Injectable, computed, inject, signal } from '@angular/core';
import { ApiProblemError } from '../../core/http/problem-details.error';
import { Tenant } from './tenancy.models';
import { TenancyApiService } from './tenancy.api';

export type TenantContextStatus = 'idle' | 'loading' | 'ready' | 'error';

@Injectable({ providedIn: 'root' })
export class TenantContextService {
  private readonly api = inject(TenancyApiService);
  private readonly state = signal<{
    status: TenantContextStatus;
    tenant: Tenant | null;
    error: ApiProblemError | null;
  }>({ status: 'idle', tenant: null, error: null });

  readonly status = computed(() => this.state().status);
  readonly tenant = computed(() => this.state().tenant);
  readonly error = computed(() => this.state().error);

  load(force = false): void {
    const current = this.state();
    if (!force && (current.status === 'loading' || current.status === 'ready')) {
      return;
    }

    this.state.set({ status: 'loading', tenant: current.tenant, error: null });
    this.api.getTenant().subscribe({
      next: (tenant) => this.state.set({ status: 'ready', tenant, error: null }),
      error: (error: unknown) => {
        const problem = error instanceof ApiProblemError ? error : null;
        this.state.set({ status: 'error', tenant: current.tenant, error: problem });
      },
    });
  }

  retry(): void {
    this.load(true);
  }

  accept(tenant: Tenant): void {
    this.state.set({ status: 'ready', tenant, error: null });
  }
}
