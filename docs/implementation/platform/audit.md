# Audit

## Stan implementacji

Etap 4 obejmuje tabelę `audit_entries`, jedną encję JPA, zapis append-only
przez serwis, allow-listę metadanych, audyt zmian Tenancy i Identity oraz
logowania znanych kont. Zapis zachodzi w transakcji operacji źródłowej;
odmowa logowania znanego konta jest utrwalana wraz z licznikiem prób.
Nieznane adresy e-mail nie generują wpisu. Blokada konta nie generuje kolejnych
wpisów przy każdej próbie. Globalne zdarzenia platformy mają `tenant_id NULL`.

Odczyt firmy: `GET /api/v1/audit-logs` z `AUDIT_READ`. Odczyt platformy:
`GET /api/platform/v1/audit-logs` z `PLATFORM_AUDIT_READ` oraz dokładnie jednym
z `tenantId` lub `scope=global`. Filtry: `from`, `to`, `actorId`, `module`,
`action`, `result`, `targetId` i paginacja. Brak dat oznacza ostatnie 30 dni.
Sortowanie jest malejące po czasie i ID. UI ma listę, filtry i szczegóły na
desktopie i mobile. Uprawnienie `AUDIT_READ` jest nadawane nowym rolom OWNER,
a migracja uzupełnia istniejące role OWNER.

Nie ma publicznych operacji aktualizacji/usuwania wpisów. Retencja i eksport
nie są częścią tego etapu.

## Cel i zakres

Przechowywać bezpieczny, append-only ślad operacji użytkownika, systemu i
platformy. Wpis audytu nie jest logiem technicznym ani magazynem payloadów.

## Model i warstwy

- Jedna encja JPA `AuditEntry` reprezentuje tabelę audytu.
- Pola: tenant ID, aktor, moduł, akcja, typ i ID obiektu, wynik, czas,
  correlation ID oraz allow-list metadata.
- Tenant operacji jest jawnie przekazywany do `AuditService`.
- Pozostałe moduły wywołują publiczną metodę `AuditService` z prostym DTO.
- API nie udostępnia aktualizacji ani usuwania wpisów.

## API i bezpieczeństwo

- `GET /api/v1/audit-logs` wymaga `AUDIT_READ` i filtruje po tenant ID z sesji.
- Platformowy endpoint wymaga `PLATFORM_AUDIT_READ` i jawnego zakresu tenant/global.
- Metadane nie zawierają treści SMS, payloadów, promptów, sekretów, haseł ani PII.
- Platformowe operacje dotyczące konkretnego tenanta zachowują tenant ID wpisu.

## Etapy

1. Dodać jedną encję, repository i migrację tabeli/indeksów/ograniczeń.
2. Dodać `AuditService` z allow-listą metadanych i correlation ID.
3. Dodać zapis zmian Tenancy i Identity w tej samej transakcji.
4. Dodać filtrowane API, paginację, permission i Angular.
5. Dodać kontrolowany proces retencji, jeśli będzie wymagany.

## Testy i zależności

Testować append-only API, allow-listę, filtr tenant/platform, dwa tenanty,
sortowanie oraz wspólny rollback wpisu i operacji źródłowej. Wymaga Tenancy i
Identity. Odblokowuje audytowane komendy kolejnych modułów.
