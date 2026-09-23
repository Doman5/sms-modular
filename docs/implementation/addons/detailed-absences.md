# Dodatek Szczegółowe nieobecności

## Cel, zakres i analiza SMS2

Obsłużyć typy i zliczanie nieobecności oraz właściwe dla wybranych typów
wnioski, akceptację i salda, oddzielając je od bazowego dnia nieobecności. Źródła:
`../sms2/src/main/java/com/domanski/sms/absence`, zwłaszcza
`VacationAllowanceService`, `AbsenceApprovalStatus`, migracje `0017` i `0019`
oraz urlopowe części `pages/absences` i Employee.

Nie duplikować bazowego dnia. Nie przechowywać rocznej puli w encji Employee.

## Model i reguły

- `AbsenceType`: tenant-scoped typ (`VACATION`, `SICK_LEAVE`,
  `ON_DEMAND_LEAVE`, `OTHER`), aktywność i reguła zliczania.
- `AbsenceClassification`: tenantowy link do bazowego `AbsenceDay`, typ i
  metadane klasyfikacji; jeden aktywny typ na dzień.
- `LeaveAllowance`: employee, rok, typ, granted, carried, adjusted i version.
- `LeaveRequest`: employee, typ, zakres, liczba dni, status, reason, requester,
  approver, decisionAt, linkedAbsenceEventId i timestamps.
- Statusy: `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`; decyzje są
  jawne, nie wynikają z samego zapisu rekordu.
- Dni liczyć w timezone tenanta według zdefiniowanego kalendarza roboczego;
  saldo zmienia się transakcyjnie przy approve/cancel.
- Approve przypisuje typ do bazowych dni przez publiczny serwis Absence Events;
  retry nie tworzy duplikatu. Dla typów bez workflow klasyfikacja nie wymaga
  akceptacji ani salda.

## API, permissions i zdarzenia

- `/api/v1/detailed-absences/types`, `/classifications`, `/allowances`,
  `/requests`; komendy submit, approve,
  reject i cancel jako osobne endpointy.
- Permissions `LEAVE_READ`, `LEAVE_REQUEST`, `LEAVE_APPROVE`,
  `LEAVE_ALLOWANCE_EDIT`; capability `DETAILED_ABSENCES`.
- Błędy: `LEAVE_BALANCE_EXCEEDED`, `LEAVE_OVERLAP`, `LEAVE_INVALID_TRANSITION`,
  `LEAVE_ABSENCE_CONFLICT`.
- Zdarzenia `LeaveRequested/Approved/Rejected/Cancelled/AllowanceAdjusted`.

## Dane, frontend i dezaktywacja

- Tabele types, classifications, allowances, requests z `tenant_id`, optimistic locking i
  unikalnością allowance per employee/year/type.
- Angular: lista i kalendarz, formularz wniosku, kolejka akceptacji, saldo oraz
  ustawienia typów; route guard capability + permissions.
- Po wyłączeniu dodatku blokować komendy i ukrywać UI, zachować klasyfikacje,
  requests i allowances. Bazowe dni pozostają aktywne i czytelne.

## Etapy

1. Typy nieobecności, klasyfikacje i kalkulator liczby dni.
2. Request lifecycle i walidacje salda/overlap.
3. Approve/cancel z idempotentną integracją bazowych dni i audytem.
4. API query/commands oraz Angular użytkownika i akceptanta.
5. Zdarzenia, reporting i administracja puli.
6. Adapter migracyjny rocznych limitów i historycznych urlopów.

## Migracja i testy

`annual_vacation_days` tworzy startowe allowance dla właściwego roku; rekordy
urlopowe tworzą request historyczny i link do zaimportowanego Absence Event.
Nie fabrykować decyzji/aktora, jeśli SMS2 ich nie przechowuje — użyć aktora
`LEGACY_SYSTEM`. Testować lata przestępne, timezone, saldo, concurrency approve,
retry, overlap, wyłączenie capability i izolację.

## Zależności i ukończenie

Wymaga Employee, Absence Events, Entitlements i Audit.
Opcjonalnie dostarcza Payroll dane o płatnych urlopach. Gotowe, gdy bazowa
nieobecność działa nadal po wyłączeniu dodatku.
