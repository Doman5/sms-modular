# SMS Modular — architektura multi-tenant

Szczegółowa kolejność implementacji znajduje się w
[indeksie planów](implementation/README.md). Zachowania obecnego SMS2 opisuje
[inwentarz projektu](sms2-current-project-inventory.md).

## Decyzje produktu

- Jedna firma jest jednym tenantem; użytkownik należy do jednego tenanta.
- Dane tenantów współdzielą bazę i schemat PostgreSQL.
- Tenant ma plan bazowy oraz może otrzymać dodatki. Pierwsza wersja nie ma
  operatora płatności.
- Pakiet bazowy obejmuje Employee Directory, SMS Inbound, AI Interpretation,
  Time Tracking i Absence Events.
- Pakiet bazowy nie wysyła automatycznych odpowiedzi ani kampanii SMS.
- SMS wychodzący jest funkcją Projects i używa wspólnej bramki platformy.

## Prosta architektura warstwowa

Każdy moduł jest pakietem biznesowym podzielonym na warstwy:

```text
<module>
├── controller
├── service
├── repository
├── entity
├── dto
└── api
```

Controller obsługuje HTTP. Service realizuje przypadek użycia, waliduje reguły i
otwiera transakcję. Repository wykonuje zapytania. Jedna encja JPA reprezentuje
wiersz tabeli. DTO chronią kontrakt HTTP. Pakiet `api` dodajemy tylko wtedy, gdy
inny moduł rzeczywiście potrzebuje stabilnego dostępu.

Nie utrzymujemy drugiego modelu domenowego obok encji, `TenantContext`, RLS,
wewnętrznych repozytoryjnych portów i adapterów dla pojedynczej implementacji.
Rzeczywiste zewnętrzne integracje mogą mieć interfejs adaptera.

Moduł jest właścicielem swoich tabel. Moduły komunikują się przez proste DTO,
identyfikatory i publiczne metody serwisów. Import obcej encji lub repozytorium
jest zabroniony. Odczyt przekrojowy odbywa się przez wyraźnie potrzebny read
model.

## Moduły

| Obszar | Moduły i zakres |
| --- | --- |
| Platforma | Tenancy, Identity & Access, Audit, Entitlements, Usage |
| Pakiet bazowy | Employee Directory, Time Tracking, Absence Events, SMS Inbound, AI Interpretation |
| Dodatki | Szczegółowe nieobecności, Projects, Tool Assignment, Planning, Payroll |
| Przekrojowe | Integration Runtime, Reporting/read models |

`PLANNING` wymaga `PROJECTS`. Stawki należą do Payroll, klasyfikacja i rozliczenie
nieobecności do dodatku Szczegółowe nieobecności, a transport SMS do Integration Runtime. Szczegółowy podział funkcji
znajduje się w poszczególnych planach modułów.

## Tenant isolation

Tenant ID jest pobierany ze zweryfikowanego principalu. Kontroler przekazuje go
jawnie jako `UUID` do serwisu, a serwis przekazuje go do każdego zapytania
tenantowego. Tenant ID nie jest pobierany z body, query ani nagłówka. Platformowe
API może wskazywać tenant w ścieżce i wymaga platformowego permission.

Każda tabela klienta ma `tenant_id NOT NULL` i FK do `tenants`. Unikalność
biznesowa i relacje tenantowe uwzględniają tenant ID. Każde repozytorium danych
klienta ma tenant ID w sygnaturach zapytań. Wspólna baza nie korzysta z RLS ani
globalnego kontekstu wątku. Ochronę potwierdzają testy PostgreSQL dla dwóch
tenantów, obcego UUID i braku principalu.

Procesy asynchroniczne odczytują tenant ID z własnego rekordu i przekazują go
jawnie do wywoływanego serwisu. Rekord zadania bez tenant ID nie jest wykonywany.

## Dostęp i pakiety

Backend rozdziela:

1. tenant — zakres danych;
2. permission — uprawnienie użytkownika;
3. capability — funkcję aktywną w planie tenanta;
4. usage limit — dostępny zasób w danym okresie.

JWT nie przechowuje długowiecznych capabilities ani limitów. Frontend pobiera
kontekst sesji po zalogowaniu, ale backend ponownie sprawdza dostęp przy każdej
operacji.

Obowiązkowe capabilities bazowe to `EMPLOYEE_DIRECTORY`, `SMS_INBOUND`,
`AI_INTERPRETATION`, `TIME_TRACKING` i `ABSENCE_EVENTS`. Dodatki to
`DETAILED_ABSENCES`, `PAYROLL`, `PROJECTS`, `PLANNING` i `TOOL_ASSIGNMENT`.

## Technologia i dane

- Backend: Java 21, Spring Boot 4.
- Frontend: Angular 21, standalone components, SCSS i PrimeNG/Aura.
- Dane: PostgreSQL, migracje Liquibase.
- API tenanta: `/api/v1/**`.
- API platformy: `/api/platform/v1/**`.
- Integracje: `/api/integrations/v1/**`.
- Błędy: RFC 9457 Problem Details z trwałym kodem i correlation ID.
- Daty zdarzeń: UTC/`Instant`; daty biznesowe: `LocalDate` w strefie tenanta.

Sekrety providerów są dostarczane przez środowisko lub secret manager. Konfiguracja
domenowa jest typowana i należy do modułu, który jej używa.

## Projekt UI

Makiety desktop/mobile znajdują się w `docs/ui-proposals`. Desktop jest pierwszą
wersją implementacji, a mobile drugą. Najważniejsze funkcje pozostają osiągalne
w maksymalnie dwóch kliknięciach. Mobile używa tych samych endpointów,
permissions i capabilities.
