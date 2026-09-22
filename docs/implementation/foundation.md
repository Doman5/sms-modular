# Fundament techniczny

## Cel i zakres

Przygotować wspólne zasady, zależności i testy, zanim powstanie pierwsza tabela
tenant-scoped. Fundament nie zawiera domeny biznesowej ani tabel zastępczych dla
przyszłych modułów.

## Stan obecny i źródła

`sms-modular` ma Spring Boot 4.0.6, Java 21, podstawowy Spring MVC/OpenAPI oraz
pusty Angular 21. SMS2 dostarcza wzorce walidacji, eksportów i obsługi błędów w
`../sms2/src/main/java/com/domanski/sms/common`, ale jego współdzielone klasy
biznesowe nie mogą zostać skopiowane do nowego `common`.

## Wymagania

- Dodać JPA, PostgreSQL, Liquibase, Spring Security resource server, testy
  Testcontainers PostgreSQL i testy architektury ArchUnit.
- Pakiety modułu: `api`, `application`, `domain`, `infrastructure`; tylko typy w
  jawnym pakiecie kontraktów mogą być używane przez inny moduł.
- Wprowadzić RFC 9457 Problem Details z `code`, `message`, `correlationId` i
  opcjonalnymi błędami pól. Nie zwracać stack trace ani nazw tabel.
- Ujednolicić UUID, UTC w bazie, `Instant` dla zdarzeń i `LocalDate` dla dat
  biznesowych interpretowanych w strefie tenanta.
- Liquibase ma master changelog i osobny katalog na moduł. Kolejność migracji
  wynika z zależności, nie z przypadkowej nazwy klasy.
- Testy integracyjne używają PostgreSQL; H2 nie jest źródłem prawdy dla RLS,
  constraintów i składni SQL.
- Correlation ID jest przyjmowany tylko w bezpiecznym formacie albo generowany,
  zwracany w odpowiedzi i propagowany do jobów oraz eventów.
- Logi są strukturalne; telefon, email, treść SMS, token, prompt i sekrety są
  maskowane.

## Interfejsy i konwencje

- `ApiProblemCode` — stabilne kody klienta, niezależne od tekstu komunikatu.
- `PageResponse<T>` — `items`, `page`, `size`, `totalElements`, `totalPages`.
- `CorrelationContext` — bieżący identyfikator żądania/operacji.
- `Clock` wstrzykiwany do logiki; brak bezpośredniego `now()` w domenie.
- Endpointy klienta: `/api/v1/**`; endpointy platformowe:
  `/api/platform/v1/**`; webhooki: `/api/integrations/v1/**`.
- Nazwy tabel i kolumn `snake_case`; klucze obce i indeksy nazwane jawnie.

## Frontend

- Utworzyć shell, lazy routes, `core/http`, `core/auth`, `core/entitlements` i
  katalog `features/<module>`.
- Jeden interceptor dodaje token i correlation ID; drugi mapuje Problem Details.
- API models są jawne i nie są współdzielone jako modele formularzy.
- Wspólne komponenty obejmują stan ładowania, pusty wynik, błąd z retry,
  potwierdzenie operacji oraz kontrolowaną paginację.
- Route guards sprawdzają sesję, permission i capability, ale nie zastępują
  autoryzacji backendu.

## Etapy implementacji

1. Zależności Maven/npm i lokalny PostgreSQL w Compose.
2. Liquibase oraz test startu na pustej bazie i po ponownym uruchomieniu.
3. Problem Details, correlation ID, zegar i konwencje paginacji.
4. Struktura modułów oraz test ArchUnit zakazujący dostępu do obcych `domain` i
   `infrastructure`.
5. Testcontainers, wspólna baza testowa i fabryki danych bez globalnych fixture.
6. Angular shell, klient HTTP i wspólne stany UI.
7. Aktualizacja OpenAPI, README i komend CI.

## Testy i kryteria ukończenia

- aplikacja uruchamia się na świeżym PostgreSQL i po wykonanych migracjach;
- rollback kontrolnej migracji działa na kopii bazy;
- Problem Details zachowuje kontrakt dla walidacji, konfliktu, 401, 403 i 500;
- ArchUnit wykrywa celowo dodaną niedozwoloną zależność w teście kontrolnym;
- Angular build i testy wspólnych interceptorów przechodzą;
- żaden element fundamentu nie importuje domeny przyszłego modułu.

## Zależności

Brak zależności modułowych. Fundament odblokowuje wszystkie dalsze plany.

