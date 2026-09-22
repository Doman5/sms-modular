export type ApiProblemCode =
  | 'BAD_REQUEST'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'VALIDATION_ERROR'
  | 'DOMAIN_ERROR'
  | 'MALFORMED_REQUEST'
  | 'RATE_LIMITED'
  | 'INTERNAL_ERROR'
  | 'NETWORK_ERROR'
  | (string & {});

export interface ApiFieldError {
  readonly field: string;
  readonly message: string;
}

export interface LegacyApiFieldError {
  readonly field: string;
  readonly messages: readonly string[];
}

export interface ProblemDetailsApiModel {
  readonly type?: string;
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly code?: ApiProblemCode;
  readonly message?: string;
  readonly correlationId?: string;

  readonly fieldErrors?: readonly ApiFieldError[];

  readonly errors?:
    | readonly (ApiFieldError | LegacyApiFieldError)[]
    | Readonly<Record<string, readonly string[]>>;
}

export interface PageResponse<T> {
  readonly items: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface PageRequest {
  readonly page: number;
  readonly size: number;
}

export interface CurrentUserApiModel {
  readonly id: string;
  readonly displayName: string;
  readonly email?: string;
}

export interface SessionContextApiModel {
  readonly user: CurrentUserApiModel;
  readonly tenantId: string;
  readonly permissions: readonly string[];
  readonly capabilities: readonly string[];
}
