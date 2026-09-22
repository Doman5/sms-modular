import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TenantContextService } from '../tenancy/tenant-context.service';
import { TenantStatus } from '../tenancy/tenancy.models';
import { SESSION_STATE_PROVIDER } from '../../core/auth/session-state.provider';
import { hasTenantPermission } from '../tenancy/tenant-permissions';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  readonly tenantContext = inject(TenantContextService);
  private readonly sessionProvider = inject(SESSION_STATE_PROVIDER);
  readonly canReadAudit = hasTenantPermission(this.sessionProvider, 'AUDIT_READ');

  constructor() {
    this.tenantContext.load();
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
}
