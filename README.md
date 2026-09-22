# SMS Modular

Fundament modułowej platformy SaaS. Repozytorium zawiera backend Spring Boot,
PostgreSQL z migracjami Liquibase oraz responsywny shell Angulara. Fundament i
pierwszy pion Tenancy są wdrażane zgodnie z roadmapą w
`docs/implementation/README.md`; pozostałe obszary będą dodawane etapami.

## Wymagania

- JDK 21 lub nowsze,
- Node.js 20.19.0,
- npm,
- opcjonalnie Docker Desktop z Docker Compose.

Do przełączenia wersji Node można użyć nvm:

```bash
nvm use
```

## Uruchomienie lokalne

Backend:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

Backend działa pod `http://localhost:8080`.

Frontend:

```bash
cd frontend
npm ci
npm start
```

Frontend działa pod `http://localhost:4200`. Proxy Angulara przekazuje `/api`, `/v3/api-docs` i `/swagger-ui` do backendu.

## Podstawowe adresy

- API systemowe: `http://localhost:8080/api/system/info`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`

Przykładowa odpowiedź API systemowego:

```json
{
  "applicationName": "SMS Modular",
  "environment": "local",
  "version": "dev"
}
```

## Docker Compose

```bash
docker compose up --build
```

Po uruchomieniu frontend jest dostępny pod `http://localhost:4200`, backend pod
`http://localhost:8080`, a PostgreSQL pod `localhost:5432`. Backend czeka na
healthcheck bazy i przy starcie wykonuje migracje Liquibase.

## Konfiguracja backendu

| Zmienna | Domyślna wartość | Zastosowanie |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | port HTTP backendu |
| `APP_ENVIRONMENT` | `local` | nazwa środowiska zwracana przez `/api/system/info` |
| `ALLOWED_ORIGINS` | `http://localhost:4200` | lista originów CORS rozdzielona przecinkami |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/sms_modular` | adres PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | `sms_modular_runtime` | użytkownik runtime bez `BYPASSRLS` |
| `SPRING_DATASOURCE_PASSWORD` | `sms_modular_runtime_local` | lokalne hasło runtime; na innych środowiskach wymagany secret manager |
| `SPRING_LIQUIBASE_USER` | `sms_modular_owner` | oddzielona rola właścicielska migracji |
| `SPRING_LIQUIBASE_PASSWORD` | `sms_modular_owner_local` | lokalne hasło roli migracji; nie używać produkcyjnie |
| `JWT_ISSUER_URI` | brak | issuer JWT; bez niego chronione API działa fail-closed |

## Tenancy i role PostgreSQL

Lokalny Compose tworzy właściciela migracji `sms_modular_owner` oraz rolę
runtime `sms_modular_runtime` z `NOSUPERUSER NOBYPASSRLS`. Migracje działają
rolą właścicielską, a aplikacja używa wyłącznie roli runtime. Skrypt inicjalny
wykonuje się tylko dla świeżego wolumenu PostgreSQL; przy zmianie starego
wolumenu należy wykonać kontrolowany reset środowiska developerskiego i ponownie
uruchomić Compose. Produkcja musi dostarczyć role oraz hasła z secret managera.

Platformowe cykl życia tenanta:

- `POST /api/platform/v1/tenants/{tenantId}/suspend` zatrzymuje komendy biznesowe,
  ale zachowuje dane i administracyjny odczyt;
- `POST /api/platform/v1/tenants/{tenantId}/activate` wznawia tenant zawieszony;
- `POST /api/platform/v1/tenants/{tenantId}/close` jest nieodwracalne, ustawia
  `closedAt` i nie usuwa danych.

Eksport przed zamknięciem oraz retencja należą do późniejszego procesu Audit /
Reporting. Bootstrap administratora platformy jest jawnie odroczony do modułu
Identity & Access; Tenancy nie tworzy lokalnego konta administratora.

## Testy i build

Backend:

```bash
./mvnw test
```

Testy integracyjne używają PostgreSQL przez Testcontainers. Gdy Docker nie jest
dostępny, są pomijane; pełna bramka CI wymaga działającego Dockera.

Frontend:

```bash
cd frontend
npm test -- --watch=false
npm run build
```
