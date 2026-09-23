# Moduł Time Tracking

## Zakres wdrożony w etapie 7

Moduł przechowuje ręczne wpisy czasu pracy. `WorkDay` ma pracownika, datę,
status `ACTIVE/CANCELLED`, sumę minut i wersję. `WorkInterval` przechowuje
granice przedziału jako minuty od początku dnia i historię korekt. W danej dacie może istnieć tylko jeden
aktywny `WorkDay` pracownika; anulowany wpis pozostaje w bazie, lecz można
utworzyć nowy aktywny wpis na tę datę.

Przedziały są dzienne, dodatnie i nie mogą się nakładać. Sąsiadujące granice
są dozwolone. Zmiana przez północ wymaga wpisów dla dwóch dni; `00:00` jako
koniec przedziału oznacza granicę następnego dnia, bez utraty minuty. Korekta
zastępuje cały zestaw aktywnych przedziałów, zachowując poprzednie jako
anulowane, i wymaga aktualnej wersji dnia. Suma wynika z aktywnych
przedziałów.

Zapis blokuje tenantowy rekord pracownika i odrzuca dzień oznaczony jako
nieobecny. Dane mają jawny `tenant_id`, tenantowe FK i zapytania filtrowane
po tenantcie. Każda zmiana trafia do audytu.

## API i UI

- `GET /api/v1/work-days?from&to&employeeId&page` — aktywne wpisy.
- `GET /api/v1/work-days/summary?month&employeeId` — liczba dni i suma minut.
- `GET /api/v1/work-days/{id}` — szczegóły, również anulowanego wpisu.
- `POST /api/v1/employees/{employeeId}/work-days` — ręczne utworzenie.
- `PUT /api/v1/employees/{employeeId}/work-days/{id}` — korekta przedziałów.
- `POST /api/v1/employees/{employeeId}/work-days/{id}/cancel` — anulowanie.

Odczyt wymaga `TIME_READ`, zmiany `TIME_EDIT`, a cały moduł capability
`TIME_TRACKING`. Interfejs `/time` ma miesięczną listę, podsumowanie,
formularz i wersję mobilną. Karta pracownika otwiera przefiltrowany widok.
Wybór pracownika korzysta z ograniczonego DTO pod `/api/v1/employees/options`,
dostępnego także dla `TIME_READ` bez `EMPLOYEE_READ`.

## Integracja SMS i dalsze etapy

Etap 8 dodał `source=SMS` i `sms_message_id`. Serwis może atomowo zapisać
jednoznaczny przedział otrzymany z SMS, również przez północ jako dwa wpisy.
Ten sam SMS nie tworzy drugiego wpisu, a konflikt z istniejącym czasem lub
nieobecnością kieruje wiadomość do review. Nie wyliczamy brakujących wpisów
bez grafiku. Import SMS2 i integracja Payroll pozostają dalszym zakresem.
Moduł nie importuje repozytorium nieobecności; używa publicznego odczytu
konfliktu.
