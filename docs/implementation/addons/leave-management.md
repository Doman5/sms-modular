# Dodatek Leave Management

## Cel, zakres i analiza SMS2

Obsłużyć uprawnienia urlopowe, wnioski, akceptację i salda, oddzielając je od
bazowego faktu nieobecności. Źródła:
`../sms2/src/main/java/com/domanski/sms/absence`, zwłaszcza
`VacationAllowanceService`, `AbsenceApprovalStatus`, migracje `0017` i `0019`
oraz urlopowe części `pages/absences` i Employee.

Nie przejmować chorobowych ani zwykłych zdarzeń nieobecności. Nie przechowywać
rocznej puli w encji Employee.

## Model i reguły

- `LeaveType`: tenant-scoped typ, jednostka dni, aktywność i reguła salda.
- `LeaveAllowance`: employee, rok, typ, granted, carried, adjusted i version.
- `LeaveRequest`: employee, typ, zakres, liczba dni, status, reason, requester,
  approver, decisionAt, linkedAbsenceEventId i timestamps.
- Statusy: `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`; decyzje są
  jawne, nie wynikają z samego zapisu rekordu.
- Dni liczyć w timezone tenanta według zdefiniowanego kalendarza roboczego;
  saldo zmienia się transakcyjnie przy approve/cancel.
- Approve wywołuje `RegisterAbsenceEventCommand`; retry nie tworzy duplikatu.

## API, permissions i zdarzenia

- `/api/v1/leave/types`, `/allowances`, `/requests`; komendy submit, approve,
  reject i cancel jako osobne endpointy.
- Permissions `LEAVE_READ`, `LEAVE_REQUEST`, `LEAVE_APPROVE`,
  `LEAVE_ALLOWANCE_EDIT`; capability `LEAVE_MANAGEMENT`.
- Błędy: `LEAVE_BALANCE_EXCEEDED`, `LEAVE_OVERLAP`, `LEAVE_INVALID_TRANSITION`,
  `LEAVE_ABSENCE_CONFLICT`.
- Zdarzenia `LeaveRequested/Approved/Rejected/Cancelled/AllowanceAdjusted`.

## Dane, frontend i dezaktywacja

- Tabele types, allowances, requests z tenantem, RLS, optimistic locking i
  unikalnością allowance per employee/year/type.
- Angular: lista i kalendarz, formularz wniosku, kolejka akceptacji, saldo oraz
  ustawienia typów; route guard capability + permissions.
- Po wyłączeniu dodatku blokować komendy i ukrywać UI, zachować requests i
  allowances. Utworzone Absence Events pozostają aktywne i czytelne.

## Etapy

1. Leave types i allowance ledger/model z kalkulatorem dni.
2. Request lifecycle i walidacje salda/overlap.
3. Approve/cancel z idempotentną integracją Absence Events i audytem.
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

Wymaga Employee, Absence Events, Entitlements, Audit i Integration Runtime.
Opcjonalnie dostarcza Payroll dane o płatnych urlopach. Gotowe, gdy bazowa
nieobecność działa nadal po wyłączeniu dodatku.

