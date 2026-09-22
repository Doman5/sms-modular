# Moduł Entitlements

## Cel i zakres

Zarządzać wersjonowanym planem bazowym, dodatkami i efektywnymi capabilities.
Moduł jest greenfield; SMS2 nie ma planów ani feature flags. Nie realizuje
płatności, faktur ani checkoutu.

## Model i reguły

- `ModuleCatalog(key, type, status)`; typ `BASE` albo `ADD_ON`.
- `Plan` i niezmienna `PlanVersion`; `PlanModule` wiąże capability i limit.
- `TenantSubscription` ma tenant, wersję planu, status i okres obowiązywania.
- `TenantAddon` ma capability, źródło aktywacji, status oraz `startsAt/endsAt`.
- `TenantLimitOverride` jest czasowy i audytowany.
- Bazowe: `EMPLOYEE_DIRECTORY`, `SMS_INBOUND`, `AI_INTERPRETATION`,
  `TIME_TRACKING`, `ABSENCE_EVENTS`; dodatki zgodne z architekturą.
- `PLANNING` wymaga `PROJECTS`; dezaktywacja Projects jest blokowana, jeśli
  Planning pozostaje aktywne. Brak cichej kaskady.
- Dezaktywacja blokuje nowe operacje, ale nie usuwa danych.

## Kontrakty i API

- `EntitlementChecker.require(CapabilityKey)` i odczyt
  `EntitlementSnapshot(capabilities, limits, validUntil)`.
- Tenant: `GET /api/v1/subscription`.
- Platforma: odczyt i audytowane komendy zmiany planu/dodatku w
  `/api/platform/v1/tenants/{tenantId}/subscription`.
- Brak capability zwraca `403 CAPABILITY_NOT_ENABLED`, odróżniony od permission.

## Frontend, cache i obserwowalność

- Shell korzysta ze snapshotu `/me/context`; menu i route guards używają
  capability keys, nie flag rozsianych po komponentach.
- Cache ma krótki TTL i unieważnienie po komendzie. Backend sprawdza snapshot
  niezależnie od stanu frontendu.
- Metryki: odmowy per capability, aktywacje, wygasanie i błędy zależności.

## Etapy

1. Katalog, plan/version, subskrypcja i dodatki z seedem bazowych keys.
2. Resolver snapshotu i walidacja statusów/czasu/zależności.
3. `require`, integracja z metodami domenowymi i kody Problem Details.
4. Platformowe API komend, audyt i cache invalidation.
5. `/subscription`, integracja `/me/context` i Angular guards.

## Testy, migracja i zależności

Dla importowanego tenanta utworzyć bazę i dodatki odpowiadające przenoszonym
danym. Testować granice czasu, zmianę plan version, cache, suspend, brak
permission mimo dodatku, Planning bez Projects oraz zachowanie danych po wyłączeniu.

Wymaga Tenancy, Identity i Audit. Udostępnia kontrakt wszystkim modułom oraz Usage.

