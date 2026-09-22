# Moduł Tenancy

## Cel, zakres i źródła SMS2

Moduł jest właścicielem firmy, jej statusu, locale, strefy czasowej oraz
`TenantContext`. SMS2 nie ma odpowiednika; wszystkie obecne tabele są globalne.
Źródłem ryzyk są migracje w `../sms2/src/main/resources/db/changelog/changes`,
gdzie telefon, email i kod projektu mają globalne ograniczenia.

Poza zakresem: użytkownicy, pakiety, billing, konfiguracja domenowa i oddziały.

## Model i reguły

- `Tenant(id, slug, name, status, timezone, locale, createdAt, updatedAt,
  closedAt)`; statusy `ACTIVE`, `SUSPENDED`, `CLOSED`.
- `slug` jest globalnie unikalny i niezmienny po onboardingu.
- `SUSPENDED` blokuje komendy biznesowe, zachowując administracyjny odczyt.
- `CLOSED` wymaga wcześniejszego eksportu i nie usuwa danych synchronicznie.
- `TenantId` i `TenantContext` są publicznym kontraktem; encja pozostaje prywatna.

## API i uprawnienia

- Platforma: CRUD kontrolowany w `/api/platform/v1/tenants`, suspend i close jako
  osobne komendy; wymagane permissions `PLATFORM_TENANT_*`.
- Tenant: `GET /api/v1/tenant` i późniejsze `PATCH` tylko dla
  `TENANT_SETTINGS_EDIT`.
- Zwykłe API nie przyjmuje `tenantId`; jest ono pobierane z principalu.

## Dane, RLS i konfiguracja

- `tenants` jest tabelą platformową bez RLS. Każda przyszła tabela klienta ma
  `tenant_id NOT NULL REFERENCES tenants(id)`.
- Transakcja ustawia `SET LOCAL app.tenant_id`; polityka porównuje kolumnę z
  `current_setting('app.tenant_id', true)`.
- Rola aplikacji nie ma `BYPASSRLS`; migracje używają osobnej roli.
- Filtr HTTP po uwierzytelnieniu tworzy kontekst, a interceptor transakcyjny
  ustawia kontekst DB. Czyszczenie następuje zawsze w `finally`.
- Metryki: brak kontekstu, odrzucone żądania zawieszonego tenanta i błędy RLS.

## Frontend

W Fali 1A shell korzysta tymczasowo z `GET /api/v1/tenant` i nie ma
przełącznika firmy. Docelowy kontekst sesji z `/api/v1/me/context` zostanie
dostarczony przez moduł Identity & Access w Fali 1C. Widok ustawień pokazuje
nazwę, timezone, locale i status, a operacje platformowe powstają w oddzielnej
przestrzeni routingu.

## Etapy

1. Model i migracja `tenants`, bootstrap pierwszego administratora platformy.
2. `TenantContext` dla HTTP, transakcji i testów.
3. Bazowa polityka RLS oraz helper do stosowania jej w migracjach modułów.
4. Platformowe API cyklu życia i audytowalne zdarzenia `Tenant*`.
5. Kontekst sesji i ustawienia tenanta w Angularze.
6. Runbook suspend/close oraz przygotowanie eksportu.

Bootstrap administratora platformy jest celowo odroczony do `identity-access`;
Tenancy nie tworzy atrapowego konta ani lokalnego JWT. Eksport, audyt i retencja
po `CLOSED` są odroczone do właściwych fal. Operacyjny opis przejść znajduje się
w [runbooku cyklu życia](tenancy-runbook.md).

## Migracja i testy

Migracja SMS2 tworzy jednego tenanta przed importem innych danych. Testy muszą
potwierdzić brak kontekstu, obcy tenant, pooling połączeń, worker context,
statusy `SUSPENDED/CLOSED`, globalną unikalność slug oraz zachowanie strefy czasu.

## Zależności i ukończenie

Wymaga Fundamentu. Odblokowuje wszystkie moduły. Gotowe, gdy RLS blokuje odczyt
i zapis między dwoma tenantami także przy użyciu bezpośredniego repozytorium.
