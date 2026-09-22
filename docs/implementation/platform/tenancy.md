# Tenancy

## Cel i zakres

Zarządzać organizacją, jej statusem, strefą czasową i locale. Wspólny schemat
zawiera wiele tenantów. `Tenant` jest jedną encją JPA; przejścia statusu i
walidację wykonuje `TenantService`.

Poza zakresem: konta, logowanie, plany, dodatki, billing i ustawienia domenowe.

## Model i reguły

- `Tenant`: UUID, globalnie unikalny i niezmienny slug, nazwa, status,
  timezone, locale, `createdAt`, `updatedAt`, `closedAt`.
- Statusy: `ACTIVE`, `SUSPENDED`, `CLOSED`.
- Suspend blokuje operacje biznesowe, ale zachowuje odczyt administracyjny.
- Close jest nieodwracalne i nie usuwa rekordów synchronicznie.
- Serwis waliduje slug, nazwę, IANA timezone, locale oraz dozwolone przejścia.

## Persystencja i tenant isolation

- Jedna tabela `tenants`, bez RLS.
- Encja i repozytorium są używane wyłącznie przez Tenancy; inne moduły
  korzystają z `TenantService` i prostych DTO.
- Każda tabela przyszłych danych klienta ma `tenant_id NOT NULL` i FK do
  `tenants`.
- Tenantowe zapytania muszą zawierać tenant UUID. Repozytorium udostępnia
  wyłącznie sygnatury tenant-scoped dla danych klienta.
- Kontroler tenantowy otrzymuje tenant UUID ze zweryfikowanego principalu.
  Publiczne endpointy zostaną wystawione po Identity.

## API i frontend

Po ukończeniu Identity:

- platforma: tworzenie, lista, szczegóły, suspend, activate i close pod
  `/api/platform/v1/tenants`;
- tenant: odczyt i edycja własnych ustawień pod `/api/v1/tenant`;
- `tenantId` platformowego tenant API pochodzi z path, a tenant API nie przyjmuje
  go od klienta;
- Angular: lista tenantów platformy oraz ustawienia organizacji.

## Etapy

1. Dodać tabelę, unikalność slug, ograniczenia pól i indeks statusu.
2. Dodać jedną encję JPA i tenantowe repozytorium.
3. Dodać serwis create/read/update/suspend/activate/close z transakcjami.
4. Po Identity dodać chronione platformowe i tenantowe kontrolery.
5. Dodać widoki Angular i audyt po wdrożeniu Audit.

## Testy i zależności

Sprawdzić slug, niepoprawny timezone/locale, aktualizację, zamkniętego tenanta,
dozwolone przejścia i daty. Testy PostgreSQL mają potwierdzić ograniczenia oraz
że zapytanie serwisu dla tenant A nie zwraca danych tenant B. Wymaga Foundation.
Odblokowuje Identity oraz wszystkie moduły tenantowe.
