# AGENTS.md

## Cel projektu

SMS Modular jest modularnym monolitem SaaS dla wielu tenantów. Jedna firma jest
jednym tenantem. Produkt składa się z obowiązkowego pakietu bazowego oraz
opcjonalnych dodatków aktywowanych per tenant.

Priorytetem nie jest szybkie odtworzenie struktury starego SMS2, lecz bezpieczny
podział odpowiedzialności, izolacja danych oraz możliwość późniejszego
wydzielania modułów bez kopiowania ich tabel i logiki.

## Źródła prawdy

Przed implementacją przeczytaj dokumenty właściwe dla zadania:

1. `docs/implementation/README.md` — kolejność prac, zależności i globalna
   definicja ukończenia.
2. `docs/implementation/<obszar>/<modul>.md` — szczegółowy plan danego modułu.
3. `docs/sms-modular-architecture-outline.md` — docelowe granice, multi-tenancy,
   pakiety i kontrakty.
4. `docs/ui-proposals/README.md` — katalog ekranów i zasady UX.
5. `docs/sms2-current-project-inventory.md` — źródło istniejących zachowań i
   danych migracyjnych, ale nie wzorzec docelowej architektury.

Gdy dokumenty są sprzeczne, szczegółowy plan modułu ma pierwszeństwo w jego
zakresie, a decyzje architektoniczne z `sms-modular-architecture-outline.md`
mają pierwszeństwo przed rozwiązaniami odziedziczonymi z SMS2. Nie rozwiązuj
ważnej sprzeczności ukrytą decyzją w kodzie — opisz ją i zaktualizuj dokumentację.

## Stan technologiczny

- backend: Java 21, Spring Boot 4, Maven Wrapper;
- frontend: Angular 21, standalone components, SCSS, PrimeNG/Aura;
- baza docelowa: PostgreSQL i Liquibase;
- testy integracyjne bazy: Testcontainers PostgreSQL;
- główny pakiet Java: `com.domanski.smsmodular`;
- publiczne API tenanta: `/api/v1/**`;
- API operatora platformy: `/api/platform/v1/**`;
- webhooki i integracje: `/api/integrations/v1/**`.

Repozytorium zaczyna jako szkielet. Nie zakładaj, że element opisany w planie
jest już zaimplementowany. Najpierw sprawdź kod, migracje i testy.

## Obowiązująca kolejność implementacji

Nie omijaj bramki wcześniejszej fali, jeżeli nowy moduł korzysta z jej
kontraktów:

1. Foundation.
2. Tenancy.
3. Audit i Integration Runtime.
4. Identity & Access.
5. Entitlements, następnie Usage.
6. Employee Directory.
7. Time Tracking i Absence Events.
8. SMS Inbound, następnie AI Interpretation.
9. Leave Management, Projects i Tool Assignment.
10. Planning i Payroll.
11. Reporting/read models.
12. Migracja SMS2 i cutover.

Elementy jednej fali można realizować równolegle wyłącznie po spełnieniu bramki
poprzedniej fali. Jeżeli żądanie dotyczy późniejszej fali, sprawdź jej
zależności. Nie twórz lokalnych atrap trwałych kontraktów tylko po to, aby
ominąć brakujący moduł.

## Sposób pracy

- Przed zmianą ustal właściciela danych, wymagane kontrakty i falę roadmapy.
- Preferuj mały pionowy fragment zakończony testami zamiast szerokiego,
  niedokończonego szkieletu.
- Zachowuj istniejące zmiany użytkownika i nie wykonuj niezwiązanych refaktorów.
- Nie dodawaj abstrakcji bez bieżącego konsumenta ani ogólnego `common` dla
  wygody.
- Zmiana zachowania wymaga testu na poziomie, na którym można je wiarygodnie
  udowodnić.
- Długotrwałe decyzje aktualizuj w odpowiednim dokumencie w `docs/`.
- Nie kopiuj kodu z sąsiedniego SMS2 bez sprawdzenia jego semantyki,
  bezpieczeństwa i właściciela w nowym podziale.

## Granice modułów

Każdy moduł ma pakiety `api`, `application`, `domain` i `infrastructure`:

- `api` zawiera kontrolery i jawnie opublikowane DTO/kontrakty;
- `application` orkiestruje przypadki użycia i granice transakcji;
- `domain` zawiera reguły, stan oraz błędy domenowe;
- `infrastructure` zawiera JPA, dostawców zewnętrznych i adaptery techniczne.

Reguły obowiązkowe:

- jedna dana biznesowa ma dokładnie jednego właściciela modułowego;
- moduł nie importuje encji, repozytorium ani implementacji infrastrukturalnej
  innego modułu;
- komunikacja synchroniczna przechodzi przez mały publiczny kontrakt operujący
  na identyfikatorach i projekcjach, nie na encjach JPA;
- zmiany przekrojowe są propagowane zdarzeniami/outboxem do read modeli;
- kontrolery są cienkie i nie korzystają bezpośrednio z repozytoriów;
- repozytoria pozostają prywatne dla modułu;
- porty aplikacyjne są obowiązkowe na granicach modułów i dla rzeczywistych
  providerów zewnętrznych (`AuditPort`, `OutboxPort`, `SmsDispatchPort` oraz
  podobne kontrakty publiczne). Wewnętrzne repozytorium/infrastrukturalna
  fasada używana przez jeden moduł może być konkretną klasą Spring Data/JDBC;
  nie twórz portu i jedynego adaptera wyłącznie dla delegacji;
- modele API, formularzy i persystencji nie są jednym wspólnym typem;
- `common` może zawierać tylko stabilne elementy techniczne, np. zegar,
  correlation ID, paginację i bazowe typy błędów — nigdy reguły biznesowe.

Dodaj lub utrzymuj testy ArchUnit, które wykrywają naruszenie tych granic.

## Multi-tenancy i baza danych

Izolacja tenanta jest wymaganiem bezpieczeństwa, nie filtrem interfejsu.

- Każda tabela zawierająca dane klienta ma `tenant_id NOT NULL` oraz FK do
  `tenants`.
- Unikalność biznesowa jest tenant-scoped, np.
  `(tenant_id, normalized_phone)` albo `(tenant_id, project_code)`.
- Relacje między danymi klienta zabezpieczaj również na poziomie bazy tak, aby
  nie dało się utworzyć relacji między tenantami.
- UUID nie jest zabezpieczeniem i nie zastępuje warunku po `tenant_id`.
- Każda tabela tenantowa otrzymuje politykę PostgreSQL RLS i odpowiednie
  tenant-scoped indeksy.
- Zwykła rola aplikacyjna nie może mieć `BYPASSRLS`; migracje i operacje
  platformowe korzystają z oddzielnej, jawnie kontrolowanej ścieżki.
- W transakcji ustawiaj wyłącznie lokalny kontekst bazy, np.
  `SET LOCAL app.tenant_id`. Nie pozostawiaj go na połączeniu z puli.
- W API tenanta identyfikator pochodzi tylko ze zweryfikowanego principalu.
  Nie przyjmuj `tenantId` z nagłówka, query param, ścieżki ani body jako źródła
  kontekstu.
- Endpoint z `tenantId` w ścieżce należy do API platformowego i wymaga
  platformowego principala.
- Job, event i rekord outboxa przechowują tenant ID. Worker odtwarza kontekst i
  zawsze czyści go w `finally`.
- Testy RLS i constraintów uruchamiaj na PostgreSQL. H2 nie jest akceptowalnym
  substytutem.

Każdy nowy moduł tenantowy musi zawierać test przynajmniej dwóch tenantów:
dozwolony odczyt/zapis własnych danych, odmowę dla obcych UUID, brak kontekstu
oraz brak wycieku kontekstu przez pulę połączeń lub worker.

## Uwierzytelnianie, uprawnienia i pakiety

Rozróżniaj cztery niezależne decyzje:

1. tenant — do czyich danych wykonywana jest operacja;
2. permission — czy użytkownik może wykonać operację;
3. entitlement/capability — czy tenant ma zakupioną funkcję;
4. usage limit — czy zasób może zostać zużyty w danym okresie.

Frontend może ukryć niedostępną funkcję, ale backend ponownie sprawdza wszystkie
wymagane warunki. Nie opieraj autoryzacji domenowej na nazwie roli. Kod sprawdza
granularne permission. Capabilities i limity nie trafiają do długowiecznego JWT;
frontend pobiera je z `/api/v1/me/context`.

Pięć capabilities bazowych jest obowiązkowych dla aktywnego planu:
`EMPLOYEE_DIRECTORY`, `SMS_INBOUND`, `AI_INTERPRETATION`, `TIME_TRACKING` i
`ABSENCE_EVENTS`. Dodatki to `LEAVE_MANAGEMENT`, `PAYROLL`, `PROJECTS`,
`PLANNING` i `TOOL_ASSIGNMENT`. `PLANNING` wymaga aktywnego `PROJECTS`.

## API, błędy i dane

- Używaj wersjonowanych endpointów zgodnie z przestrzeniami API projektu.
- Waliduj wejście na granicy, a reguły biznesowe również w domenie.
- Błędy HTTP zwracaj jako RFC 9457 Problem Details z co najmniej `code`,
  `message` i `correlationId`; błędy pól są opcjonalną kolekcją.
- Kody problemów są stabilnym kontraktem. Frontend nie rozpoznaje przyczyny po
  tekście komunikatu.
- Nie zwracaj stack trace, nazw tabel ani szczegółów infrastruktury.
- Listy używają wspólnego `PageResponse<T>`.
- Identyfikatory są UUID. Zdarzenia używają `Instant` i UTC; daty biznesowe
  używają `LocalDate` interpretowanego w strefie tenanta.
- Wstrzykuj `Clock`; nie używaj bezpośredniego `now()` w logice domenowej.
- Zmiana schematu wymaga changelogu Liquibase. Migracja ma rollback albo
  udokumentowaną, przetestowaną procedurę odtworzenia.

## Zdarzenia i integracje

- Zapis stanu właściciela i rekordu outbox wykonuj w jednej transakcji.
- Zdarzenie zawiera tenant ID, correlation ID, wersję kontraktu oraz klucz
  idempotencji.
- Konsument zdarzenia i zewnętrzny callback muszą być idempotentne.
- Awaria konsumenta nie może cofać zakończonej transakcji właściciela.
- Retry ma limit, backoff, obserwowalny stan i drogę do obsługi dead letter.
- Webhook najpierw weryfikuje podpis, timestamp i replay protection.
- Tenant webhooka jest wyznaczany z zaufanego routingu dostawcy, nigdy z
  dowolnego pola payloadu.
- Adapter dostawcy nie może zawierać reguł domenowych.

SMS Inbound zapisuje i routuje wiadomość, ale nie zapisuje tabel czasu pracy ani
nieobecności. Wywołuje publiczne komendy właścicielskich modułów. Pakiet bazowy
nie inicjuje SMS-ów wychodzących.

## Backend Java

- Preferuj konstruktorowe dependency injection i niemutowalne wartości.
- Encje JPA nie opuszczają modułu i nie są zwracane przez kontrolery.
- Transakcje deklaruj na poziomie przypadku użycia, nie kontrolera.
- Mapowanie ma być jawne; nie wprowadzaj biblioteki mapującej tylko dla jednego
  prostego DTO.
- Wyjątki transportowe mapuj centralnie w `@RestControllerAdvice`.
- Logi są strukturalne i zawierają bezpieczne `tenantId`, `correlationId`, moduł
  i operację. Maskuj telefon, email, treść SMS, tokeny, prompty i sekrety.
- Sekrety pochodzą ze zmiennych środowiskowych lub secret managera. Nie zapisuj
  ich w repozytorium ani ustawieniach tenanta.

## Frontend Angular

- Funkcje biznesowe umieszczaj pod `frontend/src/app/features/<module>`.
- Kod przekrojowy ogranicz do `core/http`, `core/auth`, `core/entitlements` oraz
  rzeczywiście współdzielonych komponentów prezentacyjnych.
- Używaj standalone components i lazy routes.
- Jeden interceptor dodaje token i correlation ID, a wspólna obsługa mapuje
  Problem Details na jawne stany UI.
- Modele odpowiedzi API nie są bezpośrednio modelami formularzy.
- Nawigację, route guards i komunikaty limitów buduj na podstawie
  `/api/v1/me/context`. Aplikacja tenantowa nie ma przełącznika firmy.
- Każdy widok obsługuje loading, empty, validation, forbidden i retryable error.
- Każda akcja zapisująca blokuje podwójne wysłanie i pokazuje wynik operacji.
- Stosuj kierunek wizualny z `docs/ui-proposals`. Główna funkcja obszaru ma być
  osiągalna w maksymalnie dwóch kliknięciach lub dotknięciach.
- Desktop używa bocznej nawigacji; mobile dolnej nawigacji oraz `Więcej`.
- Tabel nie skaluj mechanicznie na telefon — używaj list, kart albo widoku dnia.
- Minimalny obszar dotyku to 44×44 px. Zachowaj obsługę klawiatury, focus,
  semantyczne etykiety i czytelność statusu bez polegania wyłącznie na kolorze.

Mobile jest drugim etapem wdrożenia, ale kontrakty API, komponenty i układ
desktopu nie mogą uniemożliwiać późniejszej implementacji wariantu mobilnego.

## Testy

Dobierz najmniejszy zestaw testów, który wiarygodnie dowodzi zmiany, ale nie
pomijaj testów bezpieczeństwa i izolacji.

Backend według potrzeby:

- testy jednostkowe reguł domenowych;
- testy kontrolera i kontraktu Problem Details;
- testy integracyjne z PostgreSQL/Testcontainers dla repozytoriów, migracji,
  transakcji i RLS;
- testy dwóch tenantów dla każdego tenant-scoped przypadku użycia;
- testy idempotencji, retry i propagacji kontekstu dla workerów/integracji;
- testy ArchUnit granic modułowych.

Frontend według potrzeby:

- testy komponentów, serwisów, guardów i interceptorów;
- testy stanów loading/empty/error/forbidden;
- testy permissions i capabilities dla routingu oraz widoczności akcji;
- build produkcyjny po zmianie routingu, zależności albo konfiguracji.

Podstawowe komendy:

```bash
./mvnw test

cd frontend
npm test -- --watch=false
npm run build

docker compose up --build
```

Nie raportuj testu jako wykonanego, jeżeli nie został uruchomiony. Jeżeli testu
nie można uruchomić, podaj konkretną przyczynę i pozostałe ryzyko.

## Migracja z SMS2

- SMS2 jest źródłem reguł i danych do uzgodnienia, nie celem kopiowania pakietów.
- Import danych odbywa się przez adapter modułu będącego właścicielem danych.
- Zachowuj UUID, gdy nie powoduje konfliktu, i utrzymuj jawne mapowanie legacy ID.
- Normalizuj dane przed zapisem i raportuj kolizje zamiast cicho je poprawiać.
- Migrację prowadź w kolejności zależności modułów.
- Cutover wymaga próbnej migracji, uzgodnienia liczności, finansów, SMS-ów i
  read modeli oraz udokumentowanego rollbacku.

## Definicja ukończenia

Zmiana jest ukończona dopiero, gdy:

- zachowanie odpowiada planowi modułu i nie narusza kolejności zależności;
- właściciel danych i publiczne kontrakty są jednoznaczne;
- izolacja tenanta jest wymuszona w aplikacji i bazie;
- permission, entitlement i limit są sprawdzane tam, gdzie są wymagane;
- migracje oraz testy adekwatne do zmiany przechodzą;
- frontend obsługuje podstawowe stany i nie traktuje ukrycia elementu jako
  zabezpieczenia;
- OpenAPI, konfiguracja i dokumentacja zostały zaktualizowane, jeśli zmienił się
  ich kontrakt;
- nie dodano sekretów, danych osobowych ani treści SMS do logów lub fixture;
- podsumowanie wymienia zmienione pliki, uruchomione testy i znane ryzyka.
