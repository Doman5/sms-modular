# Reporting i read modele

## Cel, zakres i analiza SMS2

Budować dashboard, podsumowania zespołu i raporty przekrojowe bez bezpośredniego
łączenia tabel modułów w transakcji domenowej. Źródła:
`../sms2/src/main/java/com/domanski/sms/dashboard`, `teamsummary`, odpowiednie
kontrolery/DTO oraz Angular `pages/dashboard`, `pages/reports` i
`pages/team-summary`.

Eksport właścicielski pozostaje w module źródłowym. Reporting nie koryguje danych
domenowych i nie jest źródłem prawdy dla Payroll.

## Read modele i kontrakty

- `DashboardOverviewProjection`: KPI pracowników, czasu, nieobecności, SMS review
  i opcjonalnych dodatków.
- `TeamPeriodSummaryProjection`: pracownik, okres, minuty, nieobecności i status
  kompletności danych.
- Projekcje dodatków istnieją tylko, gdy capability jest aktywne; brak dodatku
  daje jawny brak sekcji, nie zera sugerujące dane biznesowe.
- Konsumenci używają wersjonowanych zdarzeń outbox. `InboxReceipt` gwarantuje
  idempotencję, a projection checkpoint pozwala ocenić świeżość.
- Każda projekcja ma `tenant_id`, `sourceVersion/updatedAt` i RLS.

## Spójność, przebudowa i błędy

- Model jest eventually consistent; API zwraca `dataAsOf`.
- Handler jest idempotentny i odporny na zdarzenie starsze niż aktualna wersja.
- Rebuild działa tenant po tenantcie, zapisuje postęp i przełącza wersję projekcji
  dopiero po sukcesie. Nie blokuje komend domenowych.
- Brak/nieobsługiwane zdarzenie trafia do dead-letter i alertu; nie jest cicho
  pomijane.

## API, permissions i frontend

- `GET /api/v1/dashboard/overview?period=...`.
- `GET /api/v1/team-summary?...` i eksporty, jeżeli dane nie należą do jednego
  modułu właścicielskiego.
- `REPORT_READ` plus permissions do danych wrażliwych; capability sekcji dodatku
  jest sprawdzane przy projekcji i odpowiedzi.
- Angular dashboard renderuje sekcje dynamicznie, pokazuje `dataAsOf`, loading,
  częściową niedostępność i link do kolejki SMS review.

## Dane i obserwowalność

- Osobne tabele projekcji według widoku, nie jedna uniwersalna tabela JSON.
- Indeksy tenant/period i tenant/employee/period; brak FK do tabel źródłowych,
  ponieważ projekcja jest odbudowywalna.
- Metryki: projection lag, handler failures, dead letters, rebuild progress,
  query latency i różnica liczności podczas kontroli.

## Etapy

1. Versioned event envelope, inbox i pierwszy minimalny dashboard projection.
2. Handlery Employee, Time, Absence i SMS review.
3. Query API, freshness metadata i Angular dashboard.
4. Team summary i eksporty.
5. Handlery dodatków Leave, Projects, Payroll i Tools.
6. Rebuild, panel operacyjny, alerty i porównanie z SMS2.

## Migracja i testy

Nie importować agregatów SMS2 jako źródła prawdy. Zbudować projekcje z
zaimportowanych domen, a następnie porównać KPI, minuty i liczności z wynikami
starego Dashboard/TeamSummary. Testować duplikaty i zmianę kolejności eventów,
rebuild, lag, dwa tenanty, brak dodatku, częściowy błąd i eksporty.

## Zależności i ukończenie

Wymaga Integration Runtime i stabilnych zdarzeń co najmniej Employee, Time,
Absence oraz SMS. Dodatki można podpinać później. Gotowe, gdy żaden query handler
Reporting nie importuje repozytorium właścicielskiego modułu.

