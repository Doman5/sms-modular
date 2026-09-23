# Moduł Entitlements

## Stan wdrożenia

Moduł jest wdrożony dla jednego planu `BASE` w wersji 1. Nowy tenant otrzymuje
subskrypcję w transakcji tworzenia firmy. Migracja zakłada ją również dla
istniejących tenantów. Plan jest niemodyfikowalną wersją z limitem
`ACTIVE_USERS = UNLIMITED`.

Katalog zawiera bazowe `EMPLOYEE_DIRECTORY`, `SMS_INBOUND`,
`AI_INTERPRETATION`, `TIME_TRACKING`, `ABSENCE_EVENTS` oraz dodatki
`DETAILED_ABSENCES`, `PAYROLL`, `PROJECTS`, `PLANNING`, `TOOL_ASSIGNMENT`.
Wszystkie mają obecnie status `PLANNED`: są widoczne, ale nie dają capability
i nie można ich aktywować, dopóki ich funkcje nie zostaną wdrożone.

## Reguły

- Efektywny snapshot uwzględnia status firmy, subskrypcji, katalogu oraz
  przedziały `startsAt`/`endsAt`. Brak capability zwraca
  `403 CAPABILITY_NOT_ENABLED`.
- Operator platformy może ustawić dodatek na okres bez końca lub z datą końca.
  `PLANNING` wymaga `PROJECTS` aktywnego przez cały ten okres. Wyłączenie
  Projects z aktywnym Planning jest blokowane, bez cichej kaskady.
- Operator może ustawić limit aktywnych użytkowników jako `FINITE`,
  `UNLIMITED` albo `INHERIT`. Override może wygasnąć; obniżenie limitu nie
  usuwa istniejących kont, ale blokuje nowe i ponowną aktywację ponad limit.
- Komendy są audytowane. Stan po zmianie jest wyliczany z bazy bez cache.

## API i UI

- Tenant: `GET /api/v1/subscription` z `SUBSCRIPTION_READ`.
- Platforma: `GET /api/platform/v1/tenants/{tenantId}/subscription` z
  `PLATFORM_SUBSCRIPTION_READ`.
- Platforma: `PUT .../addons/{key}` i `PUT .../limits/ACTIVE_USERS` z
  `PLATFORM_SUBSCRIPTION_MANAGE`.
- Odpowiedź zawiera moduły, capabilities, zużycie aktywnych użytkowników,
  efektywny limit, ustawiony override oraz najbliższą granicę ważności.
- `/me/context` udostępnia efektywne capabilities i usage. Angular ma
  osobne widoki tenantowy i platformowy, responsywne według propozycji UI 19.

## Poza zakresem i następne kroki

Nie ma checkoutu, cen, faktur, zmiany planu ani automatycznego włączania
zaplanowanych modułów. Kolejne moduły należy oznaczać `AVAILABLE` dopiero po
wdrożeniu ich backendu, uprawnień, danych i UI. Wtedy ich przypadki użycia
muszą wymagać odpowiedniej capability po stronie serwera. Bezpieczeństwo nie
opiera się na ukryciu przycisku w frontendzie.

Moduł zależy od Tenancy, Identity i Audit. Testy PostgreSQL obejmują izolację,
plan startowy, uprawnienia, zależności dodatków, limit oraz równoległe tworzenie
użytkowników.
