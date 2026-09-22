import { InjectionToken } from '@angular/core';

export const CORRELATION_ID_HEADER = 'X-Correlation-Id';

export interface CorrelationIdProvider {
  generate(): string;
  isValid(value: string): boolean;
}

const SAFE_CORRELATION_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/;

export class DefaultCorrelationIdProvider implements CorrelationIdProvider {
  generate(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }

    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
      const random = Math.floor(Math.random() * 16);
      const value = character === 'x' ? random : (random & 0x3) | 0x8;
      return value.toString(16);
    });
  }

  isValid(value: string): boolean {
    return SAFE_CORRELATION_ID_PATTERN.test(value);
  }
}

export const CORRELATION_ID_PROVIDER = new InjectionToken<CorrelationIdProvider>(
  'CORRELATION_ID_PROVIDER',
);
