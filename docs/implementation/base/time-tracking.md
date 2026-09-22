# Moduł Time Tracking

## Cel, zakres i analiza SMS2

Rejestrować dni i przedziały pracy, korekty, źródło oraz konflikty. Źródła:
`../sms2/src/main/java/com/domanski/sms/attendance`, migracje `0005` i `0006`
oraz endpointy work-days umieszczone obecnie w `EmployeeController`.

Parser tekstu SMS przechodzi do SMS Inbound; normy i kwoty wynagrodzeń do
Payroll. Time Tracking przyjmuje ustrukturyzowaną komendę.

## Model i reguły

- `WorkDay(id, tenantId, employeeId, workDate, status, source, totalMinutes,
  createdAt, updatedAt, version)`.
- `WorkInterval(id, tenantId, workDayId, startTime?, endTime?, durationMinutes,
  sourceReference?, createdAt, updatedAt)`.
- Unique `(tenant_id, employee_id, work_date)` oraz idempotency source reference.
- Źródło: manual, SMS lub import; wskazuje ID źródła, bez FK do tabeli SMS.
- Walidować dodatni czas, poprawność granic, nakładanie przedziałów, zamknięty
  okres Payroll i kolizję z Absence przez publiczny port.

## Kontrakty, API i zdarzenia

- `RegisterWorkEntryCommand` z tenant context, employee ID, datą, przedziałem,
  źródłem i idempotency key; wynik rozróżnia applied/duplicate/conflict/review.
- `WorkTimeSnapshotPort` udostępnia dane Payroll, nie encje.
- `/api/v1/employees/{employeeId}/work-days`, miesięczne summary oraz CRUD dnia;
  permissions `TIME_READ`, `TIME_EDIT`; capability `TIME_TRACKING`.
- Zdarzenia `WorkDayCreated/Corrected/Deleted` i `WorkEntryRejected`.
- Kody: `WORK_DAY_CONFLICT`, `WORK_INTERVAL_INVALID`, `PERIOD_CLOSED`.

## Dane, frontend i obserwowalność

- `work_days`, `work_intervals`, oba z tenantem, RLS i złożonymi FK.
- Indeksy employee/date i tenant/date dla raportów; sumy aktualizowane w tej samej
  transakcji lub liczone deterministycznie z przedziałów.
- Angular: zakładka czasu na pracowniku, miesięczne podsumowanie, ręczne dodanie,
  korekta/usunięcie, komunikaty konfliktu i zamkniętego okresu.
- Metryki: komendy SMS, duplikaty, konflikty, korekty manualne.

## Etapy

1. Model, constraints, RLS i obliczanie sum.
2. Komendy manualne z Employee port, audytem i optimistic locking.
3. Query API, summary i frontend.
4. `RegisterWorkEntryCommand` dla SMS oraz idempotencja źródła.
5. Snapshot Payroll i port sprawdzania zamknięcia okresu.
6. Zdarzenia i read modele.

## Migracja i testy

Importować work days przed intervals, zachować UUID i source SMS jako luźną
referencję. Porównać liczbę dni/przedziałów oraz sumy minut per pracownik/miesiąc.
Testować północ, nakładanie, duplikat SMS, równoległe korekty, obcego pracownika,
absence conflict, closed period oraz Angular validation.

## Zależności i ukończenie

Wymaga Employee Directory, Audit, Integration Runtime i capability bazowego.
Konsumuje opcjonalny port konfliktów Absence oraz closure Payroll bez twardej
zależności do ich tabel. Odblokowuje SMS, Payroll i Reporting.

