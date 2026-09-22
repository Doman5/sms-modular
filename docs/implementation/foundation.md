# Foundation

## Cel

Przygotować minimalny runtime, konwencje API, PostgreSQL, migracje i kontrolę
granic pakietów, bez tworzenia tabel ani atrap przyszłych modułów.

## Zakres

- Spring Boot 4, Java 21, Maven Wrapper, PostgreSQL i Liquibase.
- Angular 21, routing, wspólna konfiguracja motywu i obsługa HTTP dodawana przy
  pierwszym module, który jej potrzebuje.
- RFC 9457 Problem Details z kodem, komunikatem i correlation ID.
- Walidacja HTTP, paginacja, UTC oraz wstrzykiwany `Clock`.
- Domyślnie chronione endpointy pozostają niedostępne przed wdrożeniem Identity.
- Pakiety modułów warstwowe: controller, service, repository, entity, dto.

## Etapy

1. Backend i Angular uruchamiają się jako minimalny szkielet.
2. PostgreSQL, Liquibase master oraz role migracji/runtime są skonfigurowane.
3. RFC 9457, correlation ID, walidacja, paginacja, UTC, OpenAPI i bezpieczna
   domyślna konfiguracja HTTP są gotowe.
4. Dodać i uruchomić testy ArchUnit oraz Testcontainers przed zamknięciem etapu.
5. Utrzymywać build i uruchomienie Compose opisane w README.

## Kryteria ukończenia

- Aplikacja startuje z nową bazą PostgreSQL i Liquibase.
- Drugi start nie zmienia istniejącego schematu.
- Błędy walidacji i wyjątków aplikacji mają stabilny format Problem Details.
- Moduły mogą zostać implementowane bez współdzielonego kodu biznesowego.
- Angular uruchamia root route bez placeholderów dla przyszłych funkcji.

Foundation nie dodaje RLS, `TenantContext` ani modeli domen biznesowych. Master
Liquibase pozostaje punktem wejścia; zmiany schematu pojawią się wraz z pierwszym
modułem posiadającym tabele. Szczegółowe zasady znajdują się w roadmapie.
