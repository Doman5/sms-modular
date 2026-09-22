# Dodatek Payroll

## Cel, zakres i analiza SMS2

Obliczać wynagrodzenie, zarządzać stawkami i ustawieniami, generować niezmienne
raporty oraz zamykać okresy. Źródła: cały
`../sms2/src/main/java/com/domanski/sms/payroll`, migracje `0011`–`0014`, `0020`,
`0021`, pola stawek `Employee` oraz Angular reports/settings.

Payroll nie jest właścicielem pracownika, czasu ani nieobecności. Dane wejściowe
pobiera jako wersjonowany snapshot przez porty.

## Model i reguły

- `PayrollSettings` tenant-scoped i wersjonowane: normy, overtime/weekend/holiday
  mode oraz wartości domyślne.
- `EmployeeCompensation` przejmuje hourly rate i per-employee overrides.
- `PayrollReport` ma okres, settings version, status i aggregate totals;
  `PayrollReportRow` jest niezmiennym snapshotem wejść i wyników pracownika.
- `PayrollPeriodClosure` blokuje korekty danych wpływających na miesiąc poprzez
  publiczny `PeriodClosurePort`, bez dostępu Time/Absence do tabel Payroll.
- Ponowne otwarcie wymaga `PAYROLL_REOPEN`, powodu i audytu.
- Algorytm jest deterministyczny, operuje na `BigDecimal` i ma jawne zasady
  zaokrąglenia oraz kolejność mnożników.

## API, permissions i zdarzenia

- `/api/v1/payroll/settings`, `/preview`, `/reports`, `/periods/{period}/close`
  i reopen; eksport CSV/XLSX/PDF przy konkretnym report ID.
- Permissions `PAYROLL_READ`, `PAYROLL_CONFIGURE`, `PAYROLL_GENERATE`,
  `PAYROLL_CLOSE`, `PAYROLL_REOPEN`; capability `PAYROLL`.
- Kontrakty: konsumowane `EmployeePayrollSnapshot`, `WorkTimeSnapshot` i
  opcjonalny `LeavePayrollSnapshot`; udostępniany `PeriodClosurePort`.
- Zdarzenia `PayrollReportGenerated`, `PayrollPeriodClosed/Reopened`.

## Dane, frontend i dezaktywacja

- Tabele settings/version, compensation, reports, rows, closures; wszystkie z
  tenantem i RLS. Unique closure per tenant/period.
- Angular reports/settings: preview, generate, history, snapshot details,
  export, close/reopen i komunikat brakującego dodatku.
- Wyłączenie blokuje generowanie i zmiany, ale raporty i closures pozostają do
  audytowalnego odczytu administracyjnego/eksportu.

## Etapy

1. Settings/version i Employee Compensation z migracją stawek.
2. Porty snapshotów i deterministic calculation engine.
3. Preview oraz golden tests na przypadkach SMS2.
4. Report snapshot/generate/history i eksporty.
5. Closure port, close/reopen i integracja blokad Time/Absence/SMS reprocess.
6. Angular settings/reports i reporting events.

## Migracja i testy

Importować settings, stawki i overrides, reports/rows oraz closures zachowując
historyczne wartości snapshotów. Porównać raporty wiersz po wierszu i sumy;
nie przeliczać zamkniętej historii nowym algorytmem. Testować rounding, overtime,
weekend/holiday, brak danych, concurrent generate/close, zamknięty okres, eksport,
opcjonalny Leave i cross-tenant.

## Zależności i ukończenie

Wymaga Employee, Time Tracking, Entitlements i Audit; Leave jest opcjonalnym
źródłem przez port. Gotowe, gdy raport można odtworzyć ze snapshotu bez odczytu
bieżących tabel modułów źródłowych.

