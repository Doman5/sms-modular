# Moduł Identity & Access

## Cel, zakres i analiza SMS2

Obsłużyć logowanie, użytkowników tenanta, zmianę/reset hasła, role, permissions
i kontekst bieżącej sesji. Przeanalizować `auth`, `security`, `user`, migracje
`0003` i `0027` oraz Angular `core/auth`, login, change-password i panel users.
Zachować BCrypt i stateless JWT; usunąć globalny model roli jako jedyny mechanizm.

Poza zakresem: użytkownicy wielu tenantów, SSO i samoobsługowa rejestracja.

## Model i reguły

- `UserAccount` ma `tenant_id`, globalnie unikalny znormalizowany email, status,
  password hash, `mustChangePassword` i session version.
- Role są tenant-scoped; rola mapuje permissions, a kod sprawdza permission.
- Domyślne role migrują z `ADMIN`, `MANAGER`, `ACCOUNTANT`; ich nazwy nie są
  używane w `@PreAuthorize` logiki domenowej.
- JWT zawiera user ID, tenant ID, session version, issued/expiry; nie zawiera
  długowiecznego snapshotu dodatków.
- Platform admin ma oddzielny typ principalu i issuer/audience lub jawny scope.

## API i frontend

- `/api/v1/auth/login`, `/api/v1/auth/change-password`.
- `/api/v1/me/context`: użytkownik, tenant, permissions, capabilities i usage
  sklejone przez porty, bez udostępniania encji.
- `/api/v1/users`: lista, create, patch, reset-password, status; permissions
  `USER_READ`, `USER_CREATE`, `USER_EDIT`, `USER_RESET_PASSWORD`.
- Angular: sesja, interceptor, guard, login, wymuszona zmiana hasła, panel kont;
  token i dane sesji czyścić po 401 lub zmianie session version.

## Bezpieczeństwo i obserwowalność

- Generyczny błąd logowania, rate limit i audyt sukcesu/porażki bez hasła.
- Nie można zdezaktywować ostatniego aktywnego administratora tenanta ani
  własnego konta bez przekazania administracji.
- Reset generuje jednorazowy sekret pokazywany tylko raz albo token aktywacyjny;
  jego jawna wartość nie trafia do bazy ani logów.

## Etapy

1. Użytkownicy, role, permissions, migracje i RLS.
2. JWT, authentication converter, password policy i session invalidation.
3. Login/change-password i bootstrap właściciela tenanta.
4. Administracja użytkownikami oraz audyt.
5. `/me/context` po podłączeniu Entitlements/Usage przez porty.
6. Angular auth, guards, panel i testy uprawnień.

## Migracja, testy i zależności

Import przypisuje wszystkich użytkowników SMS2 do utworzonego tenanta, zachowuje
hash BCrypt i mapuje role na permissions. Testować izolację list, brute force,
JWT obcego tenanta, wygaśnięcie, reset, ostatniego admina i wymuszoną zmianę.

Wymaga Tenancy, Audit i Integration Runtime. Odblokowuje chronione API oraz
Entitlements. Gotowe, gdy backend nie opiera decyzji domenowej na samej nazwie roli.

