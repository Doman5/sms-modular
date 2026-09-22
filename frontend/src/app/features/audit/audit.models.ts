import { PageResponse } from '../../core/api/api.models';

export interface AuditLogResponseDto {
  readonly id: string;
  readonly tenantId: string | null;
  readonly actorType: string;
  readonly actorId: string | null;
  readonly module: string;
  readonly action: string;
  readonly subjectType: string;
  readonly subjectId: string | null;
  readonly outcome: string;
  readonly occurredAt: string;
  readonly correlationId: string;
  readonly metadata: Readonly<Record<string, string>>;
}

export interface AuditLog {
  readonly id: string;
  readonly tenantId: string | null;
  readonly actorType: string;
  readonly actorId: string | null;
  readonly module: string;
  readonly action: string;
  readonly subjectType: string;
  readonly subjectId: string | null;
  readonly outcome: string;
  readonly occurredAt: string;
  readonly correlationId: string;
  readonly metadata: Readonly<Record<string, string>>;
}

export interface AuditLogQuery {
  readonly from?: string;
  readonly to?: string;
  readonly actorType?: string;
  readonly actorId?: string;
  readonly module?: string;
  readonly action?: string;
  readonly subjectType?: string;
  readonly subjectId?: string;
}

export type AuditLogPage = PageResponse<AuditLog>;

export function mapAuditLogResponse(dto: AuditLogResponseDto): AuditLog {
  return {
    ...dto,
    metadata: { ...dto.metadata },
  };
}
