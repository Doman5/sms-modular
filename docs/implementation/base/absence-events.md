# Moduł Absence Events

## Cel, zakres i analiza SMS2

Przechowywać operacyjny fakt nieobecności niezależnie od płatnego workflow
urlopowego. Źródła: `../sms2/src/main/java/com/domanski/sms/absence`, migracje
`0007`, `0018`, `0019` i Angular `pages/absences`.

Ze starego modułu zachować okres, kategorię, źródło, komentarz i konflikty.
`VacationAllowanceService`, roczne pule i proces wniosku należą do Leave
Management. Parser SMS należy do SMS Inbound. Ogólne `approvalStatus` nie jest
mechanizmem urlopowym; zdarzenie może mieć status operacyjnego review.

## Model i reguły

- `AbsenceEvent(id, tenantId, employeeId, category, dateFrom, dateTo, source,
  sourceReference?, processingStatus, comment?, createdAt, updatedAt, version)`.
- `dateFrom <= dateTo`; pracownik istnieje i jest w tym samym tenantcie.
- Source reference nie ma FK do SMS/Leave, ale ma tenant-scoped idempotency.
- Nakładanie zdarzeń tego samego pracownika jest odrzucane lub kierowane do
  jawnego konfliktu; reguła nie usuwa automatycznie czasu pracy.
- Zaakceptowany urlop wywołuje tę samą komendę co inne źródła.

## Kontrakty, API i zdarzenia

- `RegisterAbsenceEventCommand` i `AbsenceConflictPort`.
- `/api/v1/absences`: list/filter/create; `/api/v1/absences/{id}` update/delete;
  brak endpointu urlopowej akceptacji w module bazowym.
- Permissions `ABSENCE_READ`, `ABSENCE_EDIT`; capability `ABSENCE_EVENTS`.
- Zdarzenia `AbsenceEventRegistered/Changed/Removed`.
- Błędy: `ABSENCE_RANGE_INVALID`, `ABSENCE_OVERLAP`, `WORK_TIME_CONFLICT`,
  `PERIOD_CLOSED`.

## Dane i frontend

- `absence_events` z RLS, indeksem tenant/employee/date range i unique source.
- Nie tworzyć FK do wiadomości ani wniosku urlopowego.
- Angular: lista/kalendarz nieobecności, filtrowanie, ręczny zapis i konflikt.
  Elementy puli/akceptacji pojawiają się dopiero z capability Leave.

## Etapy

1. Model, kategorie bazowe, constraints, RLS.
2. Komenda rejestracji, overlap i integracja z Employee.
3. Konflikt z Time Tracking przez port, audyt i zamknięty okres.
4. API query/commands i Angular.
5. Kontrakty dla SMS i Leave, zdarzenia do Reporting.

## Migracja i testy

Każdy stary rekord tworzy fakt `AbsenceEvent`. Dane właściwe dla urlopu są
równolegle mapowane do Leave; `source_sms_message_id` staje się source reference.
Porównać zakresy i kategorie. Testować overlap, granice dat, duplikat źródła,
konflikt czasu, closed period, cross-tenant i zachowanie po wyłączeniu Leave.

## Zależności i ukończenie

Wymaga Employee Directory i Audit; integruje Time przez port. Odblokowuje SMS,
Leave, Payroll i Reporting. Gotowe, gdy zapis faktu nieobecności nie wymaga
aktywnego dodatku Urlopy.

