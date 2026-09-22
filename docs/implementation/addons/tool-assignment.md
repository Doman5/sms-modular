# Dodatek Tool Assignment

## Cel i zakres

Greenfield: ewidencja narzędzi, ich statusu, wydania pracownikowi, zwrotu,
bieżącego posiadacza i pełnej historii. SMS2 nie ma odpowiednika.

Poza pierwszą wersją: serwis, przeglądy, szkody, dokumenty, koszty, magazyny,
rezerwacje, lokalizacje i przypisanie do projektu.

## Model i reguły

- `Tool(id, tenantId, inventoryNumber, name, description?, status, createdAt,
  updatedAt, version)`; inventory number unikalny per tenant.
- Status `AVAILABLE`, `ASSIGNED`, `INACTIVE`; status wynika ze stanu i komend,
  nie może przeczyć aktywnemu wydaniu.
- `ToolAssignment(id, tenantId, toolId, employeeId, issuedAt, issuedBy,
  returnedAt?, returnedBy?, note?)`.
- Jedno aktywne wydanie na narzędzie; wydanie tylko aktywnemu pracownikowi i
  dostępnemu narzędziu. Dezaktywacja narzędzia wymaga wcześniejszego zwrotu.
- Dezaktywacja pracownika nie zamyka automatycznie wydania; powstaje zadanie/lista
  do rozliczenia, aby nie utracić odpowiedzialności materialnej.

## API, permissions i zdarzenia

- `/api/v1/tools`: list/detail/create/update/status.
- `/api/v1/tools/{id}/assignments`: history oraz komendy issue/return.
- Widok pracownika używa query Tool Assignment, nie relacji w Employee.
- Permissions `TOOL_READ`, `TOOL_EDIT`, `TOOL_ISSUE`, `TOOL_RETURN`;
  capability `TOOL_ASSIGNMENT`.
- Zdarzenia `ToolCreated/StatusChanged`, `ToolIssued`, `ToolReturned`.
- Błędy `TOOL_NOT_AVAILABLE`, `TOOL_ALREADY_ASSIGNED`,
  `TOOL_ACTIVE_ASSIGNMENT`, `EMPLOYEE_NOT_ACTIVE`.

## Dane, frontend i obserwowalność

- `tools`, `tool_assignments`; partial unique index na aktywne wydanie, RLS i
  indeks tenant/employee/returnedAt.
- Angular `/tools`, `/tools/:id`: katalog, formularz, issue/return, historia;
  zakładka pracownika pokazuje aktywne i historyczne wydania.
- Metryki: aktywne wydania, przeterminowanie tylko po przyszłym dodaniu due date,
  konflikty i próby operacji na nieaktywnych danych.

## Etapy

1. Tool catalog, constraints, CRUD/status i Angular list/detail.
2. Issue/return z Employee port, audytem i optimistic locking.
3. Historia narzędzia i pracownika oraz zdarzenia read modelu.
4. Entitlement/permission guards, eksport listy i runbook rozliczenia pracownika.

## Testy i zależności

Brak migracji SMS2. Testować równoległe wydanie jednego narzędzia, zwrot dwa razy,
obcego/nieaktywnego pracownika, deactivate z aktywnym wydaniem, unikalność numeru
per tenant, wyłączenie capability i pełną historię.

Wymaga Employee, Entitlements i Audit. Gotowe, gdy bieżący posiadacz jest
jednoznacznie wyliczalny z aktywnego wydania, a Employee nie zna encji Tool.
