import { ChangeDetectionStrategy, Component } from '@angular/core';
import { inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { SESSION_STATE_PROVIDER } from '../../core/auth/session-state.provider';
import { hasTenantPermission } from './tenant-permissions';

@Component({
  selector: 'app-platform-shell',
  standalone: true,
  imports: [RouterLink, RouterOutlet],
  templateUrl: './platform-shell.component.html',
  styleUrl: './platform-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlatformShellComponent {
  private readonly sessionProvider = inject(SESSION_STATE_PROVIDER);
  readonly canReadIntegration = hasTenantPermission(
    this.sessionProvider,
    'PLATFORM_INTEGRATION_READ',
  );
}
