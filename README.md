# SMS Modular

SMS Modular jest modularnym monolitem SaaS dla wielu tenantów. Repozytorium ma
podstawową warstwę Foundation, Tenancy, Identity & Access, Audit, Entitlements
oraz limit aktywnych użytkowników. Dostęp do
endpointów biznesowych wymaga uwierzytelnienia JWT i właściwych uprawnień.

## Wymagania

- JDK 21 lub nowsze,
- Node.js 20.19.0,
- npm,
- Docker Desktop z Docker Compose.

Do przełączenia wersji Node można użyć nvm:

```bash
nvm use
```

## Uruchomienie lokalne

```bash
docker compose up -d postgres
export AUTH_JWT_SECRET_BASE64="$(openssl rand -base64 32)"
export BOOTSTRAP_PLATFORM_EMAIL="admin@example.com"
export BOOTSTRAP_PLATFORM_PASSWORD="wlasne-bezpieczne-haslo-minimum-12-znakow"
./mvnw spring-boot:run
```

Backend działa pod `http://localhost:8080` i wykonuje changelog Liquibase przy
starcie.

Frontend:

```bash
cd frontend
npm ci
npm start
```

Frontend działa pod `http://localhost:4200`.

## Docker Compose

```bash
export AUTH_JWT_SECRET_BASE64="$(openssl rand -base64 32)"
export BOOTSTRAP_PLATFORM_EMAIL="admin@example.com"
export BOOTSTRAP_PLATFORM_PASSWORD="wlasne-bezpieczne-haslo-minimum-12-znakow"
docker compose up --build
```

Po uruchomieniu frontend jest dostępny pod `http://localhost:4200`, backend pod
`http://localhost:8080`, a PostgreSQL pod `localhost:5432`.

## Konfiguracja backendu

| Zmienna | Domyślna wartość | Zastosowanie |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | port HTTP |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/sms_modular` | adres PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | `sms_modular_runtime` | użytkownik aplikacji |
| `SPRING_DATASOURCE_PASSWORD` | `sms_modular_runtime_local` | lokalne hasło aplikacji |
| `SPRING_LIQUIBASE_USER` | `sms_modular_owner` | użytkownik migracji |
| `SPRING_LIQUIBASE_PASSWORD` | `sms_modular_owner_local` | lokalne hasło migracji |
| `ALLOWED_ORIGINS` | `http://localhost:4200` | lista dozwolonych originów CORS oddzielona przecinkami |
| `AUTH_JWT_SECRET_BASE64` | brak | sekret HS256 zakodowany Base64, minimum 32 losowe bajty; zachować ten sam po restarcie |
| `BOOTSTRAP_PLATFORM_EMAIL` | brak | e-mail pierwszego administratora platformy |
| `BOOTSTRAP_PLATFORM_PASSWORD` | brak | hasło pierwszego administratora, wymagane tylko przy pustej tabeli platform accounts |

Lokalny Compose rozdziela rolę aplikacji od roli wykonującej migracje. Produkcja
musi dostarczyć role i sekrety przez zarządzaną konfigurację wdrożeniową.

Przy pierwszym starcie konto administratora platformy jest tworzone tylko raz.
Po logowaniu pod `/api/platform/v1/auth/login` trzeba zmienić hasło. Administrator
tworzy tenanta wraz z pierwszym administratorem firmy; jego hasło tymczasowe
jest zwracane jednorazowo w odpowiedzi. Token JWT wygasa po 8 godzinach,
a zmiana hasła i reset konta unieważniają wcześniejsze tokeny. Sekret JWT
powinien być stały między restartami i współdzielony przez instancje.

Dziennik audytu jest dostępny w panelu firmy pod `/audit` dla osób z
`AUDIT_READ` i w panelu platformy pod `/platform/audit` dla osób z
`PLATFORM_AUDIT_READ`. API odczytu to odpowiednio `GET /api/v1/audit-logs`
oraz `GET /api/platform/v1/audit-logs`; platforma musi podać `tenantId` albo
`scope=global`. Domyślnie zwracane są zdarzenia z ostatnich 30 dni.

Pakiet firmy i wykorzystanie aktywnych kont są dostępne pod `/subscription`
oraz `GET /api/v1/subscription`. Operator platformy zarządza limitem kont i
dodatkami z widoku tenanta. Moduły biznesowe w katalogu mają obecnie status
`PLANNED` i pozostają nieaktywne do czasu ich wdrożenia. Widoki logowania,
użytkowników, audytu, ustawień i pakietu mają układy desktop/mobile oparte na
[propozycjach UI](docs/ui-proposals/README.md).

## Architektura i kolejność prac

[Plan implementacji](docs/implementation/README.md) opisuje etapy od Foundation
do migracji danych SMS2. Każdy moduł będzie miał proste warstwy controller,
service, repository i entity. Tenant ID pochodzi ze zweryfikowanego principalu
i jest przekazywany jawnie do metod oraz zapytań. Aplikacja nie używa RLS.

Analiza funkcji legacy znajduje się w
[inwentarzu SMS2](docs/sms2-current-project-inventory.md), a makiety desktopowe
i mobilne w [katalogu UI](docs/ui-proposals/README.md).
