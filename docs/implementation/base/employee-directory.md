# Moduł Employee Directory

## Cel, zakres i analiza SMS2

Moduł jest właścicielem podstawowej kartoteki pracownika. Źródła:
`../sms2/src/main/java/com/domanski/sms/employee`, `EmployeeController`, migracja
`0002-employee.yaml` i Angular `pages/employees` oraz `core/api/employee-*`.

Z obecnej encji zachować dane osobowe, kontaktowe, stanowisko, status i datę
zatrudnienia. Przenieść `hourlyRate` i wszystkie override stawek do Payroll, a
`annualVacationDays` do Leave Management. Miesięczne podsumowania i dni pracy
nie należą do API repozytorium Employee.

## Model i reguły

- `Employee(id, tenantId, firstName, lastName, normalizedPhone, phoneDisplay,
  email?, position, status, employmentDate, createdAt, updatedAt, version)`.
- Status co najmniej `ACTIVE`, `INACTIVE`; dezaktywacja nie usuwa historii.
- Telefon jest normalizowany do E.164 i unikalny w tenantcie. Ten sam numer może
  istnieć w dwóch tenantach, bo routing SMS ustala tenant wcześniej.
- Email pracownika, jeśli obecny, nie jest kontem użytkownika i nie musi być
  globalnie unikalny.
- Publiczne `EmployeeId`, `EmployeeSummary` i `EmployeeDirectoryPort`; bez encji.

## API, permissions i zdarzenia

- `/api/v1/employees`: lista z search/status/page, create.
- `/api/v1/employees/{id}`: get, update; osobne activate/deactivate.
- Permissions: `EMPLOYEE_READ`, `EMPLOYEE_CREATE`, `EMPLOYEE_EDIT`,
  `EMPLOYEE_STATUS_CHANGE`; capability `EMPLOYEE_DIRECTORY`.
- Błędy: `EMPLOYEE_NOT_FOUND`, `EMPLOYEE_PHONE_CONFLICT`,
  `EMPLOYEE_LIMIT_EXCEEDED`, `EMPLOYEE_VERSION_CONFLICT`.
- Zdarzenia: `EmployeeCreated`, `EmployeeUpdated`, `EmployeeStatusChanged` z
  wersją, tenantem i minimalną projekcją.

## Dane i zależności

- Tabela `employees`; unique `(tenant_id, normalized_phone)`, indeks listy po
  `(tenant_id, status, last_name, first_name)`, RLS i złożone FK dla konsumentów.
- Tworzenie/aktywacja korzysta z `UsageMeter(ACTIVE_EMPLOYEES)`; każda zmiana z
  Audit. Moduł nie importuje Time, Leave, Payroll ani Projects.

## Frontend

- Lazy routes `/employees`, `/employees/new`, `/employees/:id`.
- Lista, filtry, paginacja, formularz create/edit i status. Szczegóły mogą
  komponować zakładki innych modułów przez ich API, ale Employee nie scala DTO.
- Pola dodatków są widoczne tylko w ekranach dodatków, nie w formularzu bazowym.

## Etapy

1. Model, normalizacja telefonu, Liquibase, RLS i repozytorium prywatne.
2. Create/update/status z limitami, optimistic locking i audytem.
3. Query/list/detail, OpenAPI i publiczna projekcja portu.
4. Zdarzenia outbox i integracja read modeli.
5. Angular lista/formularz/szczegóły oraz capability/permission guards.
6. Adapter importu SMS2 rozdzielający pola do właścicielskich modułów.

## Migracja i testy

Import zachowuje UUID, normalizuje telefony i raportuje kolizje przed zapisem.
Stawki oraz dni urlopowe trafiają do plików importowych Payroll/Leave, a nie do
`employees`. Testować walidację, unikalność per tenant, limit aktywnych osób,
optimistic lock, deactivate z historią, port projekcji i ekran formularza.

## Zależności i ukończenie

Wymaga Tenancy, Identity, Entitlements, Usage i Audit. Odblokowuje wszystkie
moduły workforce i dodatki. Gotowe, gdy żaden konsument nie potrzebuje encji
Employee ani bezpośredniego zapytania do jej tabeli.

