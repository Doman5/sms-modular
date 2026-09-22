import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export const TENANT_SLUG_PATTERN = /^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$/;
export const LOCALE_PATTERN = /^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$/;

export const timezoneValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const value = String(control.value ?? '').trim();
  if (!value) {
    return null;
  }

  try {
    new Intl.DateTimeFormat('en-US', { timeZone: value }).format();
    return null;
  } catch {
    return { timezone: true };
  }
};

export const localeValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '').trim();
  if (!value || !LOCALE_PATTERN.test(value)) {
    return value ? { locale: true } : null;
  }

  try {
    Intl.getCanonicalLocales(value);
    return null;
  } catch {
    return { locale: true };
  }
};
