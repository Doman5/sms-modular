# AGENTS.md

## Cel projektu

SMS Modular jest modularnym monolitem SaaS dla wielu tenantów. Jedna firma jest
jednym tenantem. Produkt ma obowiązkowy pakiet bazowy i opcjonalne dodatki.
Foundation, Tenancy, Identity & Access, Audit, Entitlements, pierwszy zakres
Usage, Employee Directory, Time Tracking i bazowe Absence Events są zaimplementowane.
Dokumenty i inwentarz SMS2 opisują cel oraz kolejność dalszego wdrażania.

## Źródła prawdy

1. `docs/implementation/README.md` określa kolejność i bramki modułów.
2. `docs/implementation/<obszar>/<modul>.md` określa zachowanie danego modułu.
3. `docs/sms-modular-architecture-outline.md` określa wspólne decyzje.
4. `docs/ui-proposals/README.md` opisuje kierunek UI desktop/mobile.
5. `docs/sms2-current-project-inventory.md` jest źródłem zachowań legacy, nie
   wzorcem kodu ani architektury.

## Stos technologiczny

- backend: Java 21, Spring Boot 4, Maven Wrapper;
- frontend: Angular 21, standalone components, SCSS, PrimeNG/Aura;
- baza: PostgreSQL i Liquibase;
- testy bazy: Testcontainers PostgreSQL;
- pakiet Java: `com.domanski.smsmodular`.

## Obowiązująca kolejność

1. Foundation.
2. Tenancy — model, tabela i serwis; publiczne operacje tenantów wymagają Identity.
3. Identity & Access — logowanie, principal i bezpieczne API Tenancy.
4. Audit.
5. Entitlements, następnie Usage.
6. Employee Directory.
7. Time Tracking i Absence Events.
8. Integration Runtime i SMS Inbound.
9. AI Interpretation.
10. Szczegółowe nieobecności, Projects i Tool Assignment.
11. Planning i Payroll.
12. Reporting/read models.
13. Migracja SMS2 i cutover.

Nie twórz kodu ani pustych migracji dla późniejszych modułów. Pierwszy provider
lub outbox powstaje dopiero razem z jego rzeczywistym konsumentem.

## Architektura aplikacji

Każdy moduł grupuje pliki według prostych warstw:

- `controller` — HTTP i mapowanie DTO;
- `service` — przypadki użycia, walidacja biznesowa i transakcje;
- `repository` — zapytania Spring Data/JDBC, używane wyłącznie przez właściciela modułu;
- `entity` — jedna klasa JPA dla jednej tabeli;
- `dto` — wejście i wyjście HTTP;
- `api` — wyłącznie typy/metody potrzebne innemu modułowi.

Reguły:

- Nie twórz równoległego modelu domenowego i encji JPA. Encja jest modelem
  persystencji, a reguły i przejścia stanu obsługuje serwis.
- Nie twórz pary interfejs/adapter dla repozytorium używanego przez jeden moduł.
- Kontrakty między modułami ogranicz do identyfikatorów i prostych DTO. Moduły
  nie importują cudzych encji ani repozytoriów.
- Interfejsy wprowadzaj dla rzeczywistych zewnętrznych providerów albo gdy
  istnieje więcej niż jedna implementacja.
- Kontroler nie korzysta bezpośrednio z repozytorium. Encje nie są odpowiedzią
  HTTP.
- Nie dodawaj komentarzy ani Javadoc do klas źródłowych.
- `common` zawiera wyłącznie stabilne elementy techniczne, nigdy reguły domenowe.

## Multi-tenancy

- Wspólny schemat PostgreSQL; nie używamy Row-Level Security ani ThreadLocal
  `TenantContext`.
- `tenantId` pochodzi ze zweryfikowanego principalu. Controller przekazuje go
  jawnie jako `UUID` do każdej metody tenantowej serwisu.
- Worker pobiera tenant ID z własnego rekordu i przekazuje go jawnie.
- Tenant API nie przyjmuje tenant ID od klienta w body, query ani nagłówku.
  Platform API może mieć tenant ID w ścieżce i wymaga platformowego permission.
- Każda tabela z danymi klienta ma `tenant_id NOT NULL` i FK do `tenants`;
  `audit_entries` dopuszcza `tenant_id NULL` wyłącznie dla zdarzeń globalnych platformy.
- Każde zapytanie tenantowe filtruje po `tenant_id`; nie udostępniaj metod
  `findById` ani listowania bez tenant ID dla danych klienta.
- Unikalność biznesowa jest złożona z `tenant_id`. Relacje tenantowe mają
  złożone ograniczenia bazodanowe zawierające tenant ID.
- Test izolacji sprawdza tenant A, tenant B, obcy UUID i brak tenant principalu.

## API i uprawnienia

- tenant API: `/api/v1/**`;
- platform API: `/api/platform/v1/**`;
- integracje: `/api/integrations/v1/**`;
- rozdzielaj tenant, permission, capability i limit usage;
- backend weryfikuje dostęp przy każdym chronionym przypadku użycia;
- błędy API używają RFC 9457 z `code`, `message`, `correlationId` i opcjonalnymi
  błędami pól;
- sekrety pochodzą ze środowiska lub secret managera.

## Baza i czas

- każda zmiana schematu ma changelog Liquibase; master zawiera tylko istniejące
  moduły;
- changelogi zawierają tabele, indeksy, ograniczenia i uzasadnione dane startowe;
- nazwy SQL są `snake_case`; UUID są identyfikatorami rekordów;
- zdarzenia zapisują `Instant` w UTC, a daty biznesowe `LocalDate`;
- wstrzykuj `Clock` do obliczeń zależnych od czasu.

## Frontend

- funkcje biznesowe są pod `frontend/src/app/features/<module>`;
- używaj standalone components i lazy routes;
- modele transportowe nie są bezpośrednio modelami formularzy;
- widok obsługuje loading, empty, validation, forbidden i retryable error;
- desktop powstaje pierwszy, ale API i UI muszą umożliwić późniejszy mobile;
- najważniejsze akcje są dostępne w maksymalnie dwóch kliknięciach lub dotknięciach.

## Testy i ukończenie

Każdy moduł ma testy adekwatne do zachowania: serwisów, API, PostgreSQL,
uprawnień, tenant isolation i UI. Testów izolacji nie zastępuj H2. Aktualizuj
OpenAPI i dokumentację, gdy zmienia się kontrakt. Podsumowanie zmian wymienia
pliki, rzeczywiście wykonane kontrole i pozostałe ryzyka.
