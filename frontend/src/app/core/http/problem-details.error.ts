import { ProblemDetailsApiModel } from '../api/api.models';

export interface NormalizedProblemDetails {
  readonly type?: string;
  readonly title?: string;
  readonly status: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly code: string;
  readonly message: string;
  readonly correlationId?: string;
  readonly errors: readonly ProblemFieldError[];
}

export interface ProblemFieldError {
  readonly field: string;
  readonly messages: readonly string[];
}

export class ApiProblemError extends Error {
  override readonly name = 'ApiProblemError';
  readonly kind = 'api-problem' as const;

  constructor(
    readonly problem: NormalizedProblemDetails,
    readonly originalError?: unknown,
  ) {
    super(problem.message);
  }
}

export function isProblemDetailsPayload(value: unknown): value is ProblemDetailsApiModel {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    return false;
  }

  const payload = value as Record<string, unknown>;
  return (
    typeof payload['code'] === 'string' ||
    typeof payload['message'] === 'string' ||
    typeof payload['detail'] === 'string' ||
    typeof payload['title'] === 'string' ||
    typeof payload['status'] === 'number' ||
    Array.isArray(payload['fieldErrors'])
  );
}

export function normalizeFieldErrors(
  errors: ProblemDetailsApiModel['errors'],
  fieldErrors: ProblemDetailsApiModel['fieldErrors'] = undefined,
): readonly ProblemFieldError[] {
  if (fieldErrors) {
    return fieldErrors
      .filter(
        (error): error is { field: string; message: string } =>
          typeof error === 'object' &&
          error !== null &&
          typeof error.field === 'string' &&
          typeof error.message === 'string',
      )
      .map((error) => ({ field: error.field, messages: [error.message] }));
  }

  if (!errors) {
    return [];
  }

  if (Array.isArray(errors)) {
    return errors
      .filter(
        (error): error is { field: string; messages: readonly string[] } =>
          typeof error === 'object' &&
          error !== null &&
          typeof error.field === 'string' &&
          Array.isArray(error.messages),
      )
      .map((error) => ({
        field: error.field,
        messages: error.messages.filter(
          (message): message is string => typeof message === 'string',
        ),
      }));
  }

  const objectErrors = errors as Readonly<Record<string, readonly unknown[]>>;
  return Object.entries(objectErrors).map(([field, messages]) => ({
    field,
    messages: messages.filter((message: unknown): message is string => typeof message === 'string'),
  }));
}
