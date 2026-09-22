import { PageResponse } from '../../core/api/api.models';

export interface DeadLetterResponseDto {
  readonly id: string;
  readonly tenantId: string;
  readonly topic: string;
  readonly aggregateType: string;
  readonly aggregateId: string;
  readonly payloadVersion: number;
  readonly status: string;
  readonly attempt: number;
  readonly availableAt: string;
  readonly correlationId: string;
  readonly idempotencyKey: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly errorCode: string | null;
}

export type DeadLetter = DeadLetterResponseDto;
export type DeadLetterPage = PageResponse<DeadLetter>;

export function mapDeadLetter(dto: DeadLetterResponseDto): DeadLetter {
  return { ...dto };
}
