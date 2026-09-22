import { PageResponse } from '../../core/api/api.models';

export const TENANT_STATUS_VALUES = ['ACTIVE', 'SUSPENDED', 'CLOSED'] as const;
export type TenantStatus = (typeof TENANT_STATUS_VALUES)[number];

export interface TenantResponseDto {
  readonly id: string;
  readonly slug: string;
  readonly name: string;
  readonly status: TenantStatus;
  readonly timezone: string;
  readonly locale: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly closedAt: string | null;
}

export interface Tenant {
  readonly id: string;
  readonly slug: string;
  readonly name: string;
  readonly status: TenantStatus;
  readonly timezone: string;
  readonly locale: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly closedAt: string | null;
}

export interface TenantSettingsPatchDto {
  readonly name?: string;
  readonly timezone?: string;
  readonly locale?: string;
}

export interface CreateTenantRequestDto {
  readonly slug: string;
  readonly name: string;
  readonly timezone: string;
  readonly locale: string;
}

export interface TenantSettingsFormModel {
  name: string;
  timezone: string;
  locale: string;
}

export interface CreateTenantFormModel extends TenantSettingsFormModel {
  slug: string;
}

export type TenantPage = PageResponse<Tenant>;

export function mapTenantResponse(dto: TenantResponseDto): Tenant {
  return {
    id: dto.id,
    slug: dto.slug,
    name: dto.name,
    status: dto.status,
    timezone: dto.timezone,
    locale: dto.locale,
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt,
    closedAt: dto.closedAt,
  };
}
