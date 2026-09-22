# Reporting i read modele

## Cel, zakres i analiza SMS2

Budować dashboard, podsumowania zespołu i raporty przekrojowe jako warstwę
odczytu, bez zmiany danych domenowych. Źródła:
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
- Na początku `ReportingService` składa DTO z metod odczytu modułów źródłowych.
  Nie tworzyć osobnego event busa, inboxa ani projekcji trwałych bez wykazanego
  problemu wydajnościowego.
- Wszystkie metody przekazują jawne `tenantId` i stosują filtrowanie danych
  klienta na poziomie repozytorium.

## Spójność, przebudowa i błędy

- Raporty na żywo odzwierciedlają dane zwrócone przez serwisy modułów.
- Jeśli obciążenie uzasadni agregację, dodać konkretną tabelę read modelu i
  aktualizować ją przez istniejący mechanizm Integration Runtime.

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
- Dla agregatów dodać tylko indeksy potrzebne konkretnym raportom.
- Metryki: query latency, niekompletne odpowiedzi i różnica liczności podczas
  kontroli.

## Etapy

1. Query DTO potrzebne z Employee, Time, Absence i SMS.
2. `ReportingService` oraz query API dla dashboardu.
3. Angular dashboard ze stanem loading/error i sekcjami dodatków.
4. Team summary i eksporty.
5. Dodawanie raportów Leave, Projects, Payroll i Tools.
6. Dopiero przy potwierdzonej potrzebie wydajnościowej dodać trwały read model.

## Migracja i testy

Nie importować agregatów SMS2 jako źródła prawdy. Porównać KPI, minuty i liczności
z wynikami starego Dashboard/TeamSummary. Sprawdzić dwa tenanty, brak dodatku,
częściowy błąd i eksporty.

## Zależności i ukończenie

Wymaga publicznych metod odczytu Employee, Time, Absence i SMS. Dodatki można
podpinać później. Gotowe, gdy dashboard prezentuje dane modułów i nie zapisuje
ich w imieniu właścicieli.
