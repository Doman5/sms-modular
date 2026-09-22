# Identity & Access

## Stan implementacji

Etap 3 ma implementację backendu i pierwszą wersję panelu desktopowego z
responsywnym układem. Testy integracyjne na PostgreSQL obejmują migrację,
bootstrap, wymuszoną zmianę hasła, unieważnienie sesji, uprawnienia, izolację
tenantów, blokadę po pięciu błędnych próbach oraz ochronę ostatniego admina.
Frontend ma testy przepływu tokenu i kontraktów API.

Bootstrap wymaga `AUTH_JWT_SECRET_BASE64` oraz, przy pustej tabeli kont
platformowych, `BOOTSTRAP_PLATFORM_EMAIL` i `BOOTSTRAP_PLATFORM_PASSWORD`.
Konto startowe jest tworzone tylko raz. JWT ma ważność 8 godzin, bez refresh
tokena; logout usuwa token z `sessionStorage`. Po zmianie hasła użytkownik
loguje się ponownie. `capabilities` i `usage` w kontekście są puste do etapów
Entitlements i Usage.

Aktualne ścieżki API: `/api/v1/auth/**`, `/api/v1/me/context`,
`/api/v1/users/**`, `/api/v1/roles/**`, `/api/v1/tenant/settings`,
`/api/platform/v1/auth/**`, `/api/platform/v1/me/context` i
`/api/platform/v1/tenants/**`.

## Cel i zakres

Zapewnić logowanie, konta użytkowników, role, permissions, reset hasła i
identyfikację bieżącego użytkownika. Zachować BCrypt i bezstanowe JWT.

Pierwsza wersja nie obejmuje SSO, rejestracji publicznej ani użytkownika
przypisanego do wielu tenantów.

## Model i reguły

- `UserAccount` ma tenant ID, globalnie unikalny znormalizowany email, status,
  hash hasła, flagę wymuszonej zmiany hasła i wersję sesji.
- Role należą do tenanta i mapują się na granularne permissions. Kod autoryzuje
  permission, nie nazwę roli.
- Platform administrator jest osobnym typem principalu i nie należy do tenanta.
- JWT v1 jest HS256, podpisywany sekretem środowiskowym, z user ID, tenant ID,
  session version, `iat` i `exp`. Nie zawiera capabilities ani limitów.
- Każde odczyt/zapis konta tenantowego filtruje po jawnie podanym tenant ID.

## API i frontend

- `/api/v1/auth/login`, `/api/v1/auth/change-password`.
- `/api/v1/me/context` zwraca użytkownika, tenant, permissions, capabilities i
  usage. Integracje z modułami korzystają z jawnych metod serwisów i DTO.
- `/api/v1/users` obsługuje listę, create, patch, reset hasła i status.
- `/api/platform/v1/tenants/**` udostępnia platformowe operacje Tenancy.
- Angular zapewnia login, wymuszoną zmianę hasła, guardy oraz panel kont.

## Bezpieczeństwo

- Generyczny błąd logowania, ograniczanie prób i audyt bez hasła.
- Nie można zdezaktywować ostatniego aktywnego administratora tenanta.
- Wartość jednorazowego resetu jest pokazywana raz i nie jest logowana.
- Tenant API nie przyjmuje tenant ID od klienta. Kontroler odczytuje go z
  principalu i przekazuje jako `UUID` do serwisu.

## Etapy

1. Dodać użytkowników, role, permissions i ograniczenia tenantowe.
2. Dodać bootstrap platform admina i podpisywanie JWT z sekretem środowiskowym.
3. Dodać login, logout po stronie klienta i wymuszoną zmianę hasła.
4. Dodać użytkowników oraz bezpieczne API Tenantów z permissions.
5. Dodać `/me/context`; dołączać Entitlements i Usage po ich implementacji.
6. Dodać Angular auth i administrację użytkownikami.

## Testy i zależności

Testować BCrypt, generyczne błędy, wygaśnięcie i unieważnienie JWT, reset,
ostatniego administratora, brak/obce tenant ID oraz permissions. Wymaga
Tenancy; odblokowuje chronione API i Entitlements.
