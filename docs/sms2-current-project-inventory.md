# SMS2 — inwentaryzacja obecnego projektu i wnioski dla SMS Modular

> Dokument roboczy do projektowania następnego rozwiązania SaaS, multi-tenant i modularnego.
> Stan analizowany: 21 września 2026 r.

## 1. Cel dokumentu

Ten dokument opisuje, co obecnie znajduje się w projekcie `sms2`, jakie są główne zależności między modułami oraz które elementy warto zachować, rozdzielić albo zaprojektować na nowo w `sms-modular`.

Opis faktów pochodzi z kodu, konfiguracji, migracji, testów i dokumentacji `sms2`. Rekomendacje architektoniczne są propozycjami do dalszej dyskusji, a nie gotową specyfikacją implementacyjną.

## 2. Obecny projekt w skrócie

`sms2` jest monolityczną aplikacją webową do obsługi pracowników, czasu pracy, nieobecności, SMS-ów, projektów i naliczania wynagrodzeń.

Składa się z dwóch aplikacji:

- backendu Spring Boot,
- frontendu Angular.

Backend posiada wspólną bazę PostgreSQL, migracje Liquibase, uwierzytelnianie JWT, integrację z SMS-Gate oraz opcjonalną integrację z modelem AI do interpretowania SMS-ów.

Frontend jest aplikacją Angular SPA z biblioteką PrimeNG. Komunikuje się z backendem przez REST i obsługuje logowanie, uprawnienia, listy, formularze, raporty i eksporty.

Aktualny projekt nie ma pojęcia tenanta, członkostwa użytkownika w tenantach ani mechanizmu płatnych pakietów funkcjonalnych. Wszystkie dane są traktowane jako należące do jednej organizacji.

## 3. Rozmiar i organizacja kodu

Na analizowanym commicie `1fa7e27` (`feat: retry only failed SMS recipients`) znajdowało się w przybliżeniu:

- 256 plików Java w backendzie,
- 23 pliki testów backendowych,
- 18 encji JPA,
- 28 plików zmian Liquibase,
- 74 pliki TypeScript w frontendzie,
- 17 komponentów Angular.

Backend jest organizowany głównie według funkcji biznesowych, a wewnątrz modułów występują pakiety `api`, `application` i `domain`.

Główne pakiety backendu:

| Pakiet | Przybliżona liczba plików | Zakres |
| --- | ---: | --- |
| `absence` | 18 | nieobecności, typy, akceptacja, konflikty |
| `attendance` | 20 | dni pracy, przedziały czasu, korekty |
| `audit` | 10 | log audytowy |
| `auth` | 7 | logowanie, bieżący użytkownik, zmiana hasła |
| `common` | 24 | wspólne błędy, daty, paginacja, eksporty i narzędzia |
| `dashboard` | 15 | agregaty do widoku głównego |
| `employee` | 14 | pracownicy, telefony, stawki, urlopy |
| `payroll` | 33 | ustawienia, wyliczenia, raporty, zamykanie miesięcy |
| `project` | 53 | projekty, przypisania, planowanie, SMS-y projektowe |
| `security` | 4 | JWT, konfiguracja Spring Security, użytkownik |
| `sms` | 39 | SMS-y przychodzące, parsowanie, review, outbox |
| `teamsummary` | 6 | podsumowania zespołu i eksporty |
| `user` | 12 | konta użytkowników, role, statusy, operacje administracyjne |

## 4. Backend — technologie i konfiguracja

### Technologie

- Java 21.
- Spring Boot 4.0.6.
- Spring MVC.
- Spring Data JPA i Hibernate.
- Liquibase.
- Spring Security oraz JWT.
- PostgreSQL 17 Alpine w środowisku lokalnym.
- H2 w testach, w trybie zgodności z PostgreSQL.
- Springdoc OpenAPI 3.0.3.
- Apache POI do XLSX.
- PDFBox do PDF.
- Spring AI/OpenAI do awaryjnego rozpoznawania SMS-ów.
- Lombok.

### Główne zależności z `pom.xml`

Obecny backend korzysta z:

- `spring-boot-starter-data-jpa`,
- `spring-boot-starter-liquibase`,
- `spring-boot-starter-security`,
- `spring-boot-starter-oauth2-resource-server`,
- `spring-boot-starter-validation`,
- `spring-boot-starter-webmvc`,
- Springdoc,
- Apache POI,
- PDFBox,
- Spring AI OpenAI,
- PostgreSQL,
- H2,
- Lombok.

Warto w nowym projekcie nie przenosić tych zależności automatycznie. Każdy moduł powinien dostarczać tylko potrzebne zależności, szczególnie jeśli planowany jest modularny produkt z funkcjami aktywowanymi pakietami.

## 5. Backend — moduły biznesowe

### Autoryzacja i użytkownicy

`auth`, `security` i `user` realizują:

- logowanie przez email i hasło,
- token JWT,
- pobranie bieżącego użytkownika,
- zmianę hasła,
- role `ADMIN`, `MANAGER`, `ACCOUNTANT`,
- tworzenie i edycję kont,
- reset hasła,
- zmianę statusu użytkownika.

Autoryzacja jest stateless. Hasła są haszowane przez BCrypt. Dostęp do części operacji jest dodatkowo ograniczany przez `@PreAuthorize`.

### Pracownicy

Moduł `employee` obsługuje:

- listę i szczegóły pracowników,
- tworzenie i edycję,
- aktywność/status,
- numer telefonu,
- stawki,
- liczbę dni urlopowych,
- podsumowanie miesięczne,
- eksporty XLSX/PDF.

### Ewidencja czasu pracy

Moduł `attendance` obsługuje:

- dni pracy,
- przedziały czasu pracy,
- ręczne korekty,
- konflikty,
- wyliczanie godzin i brakujących godzin,
- powiązania z nieobecnościami i listą płac.

### Nieobecności

Moduł `absence` obsługuje:

- urlopy,
- zwolnienia chorobowe,
- nieobecności na żądanie,
- źródło nieobecności z SMS-a,
- status akceptacji,
- konflikty z czasem pracy,
- akceptację i usuwanie,
- powiązania z przypisaniami do projektów,
- wpisy audytowe.

### SMS przychodzące

Moduł `sms` obsługuje:

- przyjęcie SMS-a z zewnętrznego źródła,
- zapis treści i metadanych,
- identyfikację pracownika po numerze,
- parsery regułowe,
- kolejkę przetwarzania,
- ponawianie i leasing zadań,
- rozpoznawanie prośby o nieobecność,
- rozpoznawanie wpisu czasu pracy,
- review i ręczne rozstrzygnięcie,
- ponowne parsowanie.

### Projekty i planowanie

Moduł `project` obsługuje:

- projekty i ich status,
- archiwizowanie oraz przywracanie,
- przypisywanie pracowników do projektów,
- planowanie pracy na dni,
- wersję roboczą planu,
- SMS-y do pracowników przypisanych do projektu,
- historię wysyłek,
- odbiorców i statusy dostarczenia,
- raporty projektowe.

### Payroll

Moduł `payroll` obsługuje:

- ustawienia rozliczeń,
- stawki i nadpisania stawek,
- wyliczenia miesięczne,
- raporty i wiersze raportów,
- eksport CSV/XLSX/PDF,
- snapshoty,
- zamykanie miesięcy,
- listę SMS-ów możliwych do ponownego przetworzenia.

Zamknięcie miesiąca pełni funkcję blokady zmian w danych, które wpływają na rozliczenie.

### Dashboard, raporty zespołu i audyt

`dashboard` agreguje dane z wielu obszarów. `teamsummary` przygotowuje widoki i eksporty zespołowe. `audit` zapisuje operacje wykonywane przez użytkowników i część procesów automatycznych.

Są to moduły przekrojowe. W architekturze modularnej powinny korzystać z kontraktów lub read modelu, a nie z bezpośredniego dostępu do tabel innych modułów.

## 6. Zależności między obecnymi modułami

Najważniejsze sprzężenia biznesowe wyglądają obecnie następująco:

```text
SMS przychodzący
  ├─> pracownik
  ├─> ewidencja czasu pracy
  ├─> nieobecność
  └─> AI / review

Nieobecność
  ├─> ewidencja czasu pracy
  ├─> przypisania projektowe
  ├─> audyt
  └─> SMS

Projekt
  ├─> pracownicy
  ├─> planowanie
  ├─> SMS-Gate
  └─> raporty

Payroll
  ├─> pracownicy
  ├─> ewidencja czasu pracy
  ├─> SMS
  └─> audyt

Dashboard
  └─> agreguje wiele domen
```

Wniosek dla nowego rozwiązania: te zależności należy zamienić na jawne kontrakty modułów, komendy, zdarzenia domenowe albo dedykowane read modele. Szczególnie ważne jest odseparowanie podstawowych modułów od opcjonalnego modułu SMS i payroll.

## 7. Publiczne API obecnego projektu

Najważniejsze grupy endpointów:

| Prefix | Odpowiedzialność |
| --- | --- |
| `/api/auth` | logowanie, bieżący użytkownik, zmiana hasła |
| `/api/users` | administracja użytkownikami |
| `/api/employees` | pracownicy i ich czas pracy |
| `/api/absences` | nieobecności |
| `/api/sms` | SMS-y przychodzące, review, reprocess |
| `/api/projects` | projekty, planowanie, SMS-y projektowe |
| `/api/plans` | kalendarz i plan dnia |
| `/api/reports/payroll` | raporty i zamknięcia payroll |
| `/api/settings/payroll` | ustawienia payroll |
| `/api/dashboard/overview` | dashboard |
| `/api/team-summary` | podsumowania i eksporty zespołu |
| `/api/audit-logs` | log audytowy |
| `/api/projects/sms-gate/webhook` | webhook dostawcy SMS |
| `/api/system/info` | informacje techniczne o aplikacji |

Pełne operacje obejmują między innymi CRUD, akceptację, archiwizowanie, retry, eksporty i podgląd historii. W nowym projekcie publiczne API powinno być wersjonowane oraz jawnie powiązane z modułami i uprawnieniami.

## 8. Model danych i migracje

### Obecne encje/tabele

- `users` — konta użytkowników.
- `employees` — pracownicy.
- `sms_messages` — SMS-y przychodzące i ich wynik przetwarzania.
- `sms_processing_outbox` — kolejka przetwarzania SMS.
- `work_days` — dni pracy.
- `work_intervals` — przedziały czasu pracy.
- `absences` — nieobecności.
- `audit_logs` — wpisy audytowe.
- `payroll_reports` — raporty payroll.
- `payroll_report_rows` — wiersze raportów.
- `payroll_month_closures` — zamknięcia miesięcy.
- `payroll_settings` — ustawienia payroll.
- `projects` — projekty.
- `project_assignments` — przypisania pracowników do projektów.
- `project_plan_drafts` — wersje robocze planów.
- `project_sms_dispatches` — wysyłki SMS projektu.
- `project_sms_recipients` — odbiorcy wysyłki.
- `project_sms_webhook_events` — zdarzenia webhooka dostawcy.

### Historia zmian Liquibase

Zmiany `0001`–`0028` dodają kolejno między innymi schemat aplikacji, pracowników, użytkowników, SMS-y, ewidencję czasu, nieobecności, audyt, payroll, AI review, projekty, planowanie, webhooki oraz mechanizm zmiany hasła.

Migracje są liniowe i współdzielą jedną bazę. Nie ma jeszcze podziału migracji według modułów ani mechanizmu migracji per tenant.

### Istotne reguły danych

- Identyfikatory są typu UUID.
- Daty/czasy są przechowywane z informacją o strefie czasowej.
- Statusy są przechowywane jako wartości tekstowe podobne do enumów.
- Email użytkownika jest globalnie unikalny.
- Numer telefonu pracownika jest globalnie unikalny.
- Kod projektu jest globalnie unikalny.
- `project_assignments` ma unikalność pracownik + dzień pracy.
- SMS przychodzący jest idempotentny po `external_source` + `external_message_id`.
- Zamknięty miesiąc blokuje odpowiednie zmiany.

W modelu multi-tenant większość powyższych ograniczeń powinna być rozważona ponownie. Typowy docelowy klucz unikalności będzie wyglądał jak `(tenant_id, wartość)`, a nie jak unikalność globalna.

## 9. SMS — obecne przepływy

### SMS przychodzący

1. Dostawca wywołuje `/api/sms/inbound`.
2. System zapisuje wiadomość i rekord w `sms_processing_outbox`.
3. `SmsProcessingWorker` pobiera zadania według opóźnienia, batch size, leasingu i limitu prób.
4. Najpierw uruchamiane są parsery regułowe.
5. Jeśli wynik nie jest wystarczająco jednoznaczny, może zostać użyty model AI.
6. System tworzy wpis czasu pracy albo nieobecność, albo kieruje wiadomość do review/error.

Konfiguracja obejmuje między innymi: włączenie workera, interwał, rozmiar partii, czas leasingu i maksymalną liczbę prób.

### SMS wychodzący dla projektu

1. Plan projektu tworzy wysyłkę i rekordy odbiorców.
2. `ProjectSmsOutboxWorker` przetwarza odbiorców niezależnie.
3. `SmsGateClient` wysyła wiadomości przez REST do SMS-Gate z Basic Auth.
4. Błędy 429 i 5xx są ponawiane według opóźnień.
5. Webhook dostawcy aktualizuje status dostarczenia.
6. Idempotencja webhooka opiera się na identyfikatorze zdarzenia dostawcy.

W obecnym kodzie mechanizm SMS jest mocno związany z pracownikiem, obecnością, projektem i konfiguracją całej aplikacji. W SaaS powinien stać się opcjonalnym modułem z własną konfiguracją tenanta, limitami, kredytami i billingiem.

## 10. Integracje zewnętrzne i uruchamianie

### PostgreSQL

Lokalny Compose uruchamia PostgreSQL 17 Alpine z bazą `worktime_sms`, użytkownikiem `worktime` i hasłem `worktime`. Testy używają H2.

### SMS-Gate

Integracja używa `RestClient`, Basic Auth, timeoutów, ponowień i webhooka z podpisem `X-Signature` oraz znacznikiem `X-Timestamp`.

### AI

Spring AI/OpenAI może być używany do strukturalnego rozpoznawania niejednoznacznych SMS-ów. Wymagany jest próg confidence oraz strefa biznesowa `Europe/Warsaw`.

### Eksporty

- CSV,
- XLSX przez Apache POI,
- PDF przez PDFBox.

### Railway

Dokumentacja projektu opisuje wdrożenie jako trzy usługi: backend, frontend i PostgreSQL. Backend działa przez prywatny URL, TLS kończy się na Railway, a aplikacja używa profilu `railway`, portu `${PORT:8080}` i wyłączonego lokalnego SSL.

W repozytorium nie znaleziono konfiguracji CI/CD. Health check backendu korzysta z `/api/system/info`, a frontendu z `/health`.

## 11. Frontend Angular

### Technologie

- Angular 21.2.x.
- Standalone components.
- Strict TypeScript.
- SCSS.
- PrimeNG, PrimeIcons i motyw Aura.
- Node 20.19.0 deklarowany w `.nvmrc`.

### Widoki i obszary

Frontend ma między innymi:

- logowanie,
- wymuszenie zmiany hasła,
- dashboard,
- szybkie akcje,
- listę pracowników,
- szczegóły i tworzenie pracownika,
- szczegóły projektu,
- planowanie,
- szczegóły SMS,
- nieobecności,
- raporty,
- ustawienia,
- panel użytkownika,
- podsumowanie zespołu.

### Warstwa komunikacji

W `core/api` znajdują się serwisy dla absencji, audytu, dashboardu, pracowników, payroll, ustawień payroll, projektów, SMS-ów, podsumowania zespołu i użytkowników. Autoryzacja jest wydzielona do osobnego serwisu.

Sesja przechowuje token i bieżącego użytkownika w `localStorage` pod kluczami:

- `worktime_sms.access_token`,
- `worktime_sms.current_user`.

Interceptor dodaje Bearer token, pomija logowanie i przekierowuje po 401. Drugi interceptor ponawia tylko bezpieczne żądania `GET`, `HEAD` i `OPTIONS` dla wybranych statusów chwilowych.

### Stan i shell aplikacji

Istnieją usługi stanu dla miesiąca, historii nawigacji, motywu, eksportów i paginacji. Główny shell zawiera nawigację, menu, wybór miesiąca i akcje eksportu.

W nowej aplikacji należy dodać wyraźną warstwę kontekstu tenanta, katalog modułów i feature flags. Samo ukrycie pozycji menu nie może być traktowane jako mechanizm autoryzacji.

## 12. Security i brakujący multi-tenancy

### Obecny model bezpieczeństwa

- stateless Spring Security,
- JWT HS256 generowany i dekodowany lokalnie,
- role `ADMIN`, `MANAGER`, `ACCOUNTANT`,
- BCrypt,
- `@EnableMethodSecurity`,
- `@PreAuthorize` przy wybranych operacjach,
- publiczne endpointy systemowe, Swagger, inbound SMS, webhook i OPTIONS,
- CORS konfigurowany przez `ALLOWED_ORIGINS`.

JWT zawiera między innymi email, ID użytkownika, pełną nazwę, rolę i informację o konieczności zmiany hasła. Nie zawiera tenant ID, membership ID ani listy uprawnień do modułów.

### Czego brakuje dla SaaS multi-tenant

Do zaprojektowania są co najmniej:

- `Tenant`/organizacja jako właściciel danych biznesowych,
- członkostwo użytkownika w tenantach,
- rola użytkownika w konkretnym tenancie,
- wybór aktywnego tenanta,
- izolacja danych na poziomie aplikacji i bazy,
- strategia dla użytkownika należącego do wielu tenantów,
- tenant-scoped konfiguracja i sekrety,
- tenant-scoped limity i usage,
- katalog modułów oraz entitlements,
- rozliczenia pakietów,
- audyt z tenantem i aktorem,
- bezpieczne webhooki przypisane do tenanta,
- polityka usunięcia/eksportu danych tenanta.

Najważniejsza zasada: tenant context musi być ustalany przed wykonaniem logiki domenowej i propagowany do repozytoriów, zadań asynchronicznych, eventów, logów i integracji. Nie powinien być dopisywany tylko w kontrolerach.

## 13. Rozbieżności i ryzyka konfiguracji w `sms2`

Podczas analizy zauważono kilka rzeczy do uporządkowania przed kopiowaniem konfiguracji:

1. Lokalny `application.yaml` używa HTTPS na porcie `8443` i nieśledzonego `local-ssl.p12`, podczas gdy README opisuje HTTP na porcie `8080`.
2. Proxy Angulara kieruje ruch do HTTPS `8443`, mimo że README mówi o `8080`.
3. `.nvmrc` wskazuje Node `20.19.0`, a frontendowy Dockerfile używa obrazu `node:22-alpine`.
4. README opisuje AI jako domyślnie wyłączone i wskazuje model `gpt-5-nano`, a aktualny YAML ma tryb `AUTO` i model `gpt-5.6-luna`.
5. Lokalny plik `.p12` jest nieśledzony i nie ma ogólnej reguły ignorowania certyfikatów.
6. Lokalnie włączone są workery SMS, choć adres SMS-Gate jest pusty. To wymaga ostrożnego uruchamiania i testów.
7. W repozytorium nie znaleziono CI/CD ani osobnego mechanizmu automatycznej weryfikacji konfiguracji środowisk.

Warto zachować rozdzielenie konfiguracji lokalnej, testowej i produkcyjnej oraz wprowadzić walidację startową, która jasno zgłasza brak wymaganych sekretów zamiast uruchamiać częściowo skonfigurowane workery.

## 14. Co powinno zostać bazą w `sms-modular`

Za rozsądną bazę techniczną można uznać:

- Spring Boot z minimalnym modułem webowym,
- Angular standalone z prostym shellem,
- wspólne konwencje konfiguracji i health endpoint,
- walidację requestów,
- OpenAPI,
- Docker Compose bez wymuszania konkretnej domeny,
- testy startu aplikacji i podstawowego endpointu,
- rozdzielenie konfiguracji według środowisk,
- spójny sposób budowania backendu i frontendu.

Nie należy kopiować do rdzenia nowego projektu:

- encji pracownika,
- encji SMS,
- encji payroll,
- starego modelu ról jako jedynego modelu autoryzacji,
- bezpośrednich zależności dashboardu do wszystkich tabel,
- globalnych ograniczeń unikalności bez `tenant_id`,
- integracji SMS-Gate jako wymaganej funkcji startowej,
- AI jako zależności uruchomieniowej całej aplikacji.

## 15. Proponowane granice modułów w rozwiązaniu docelowym

Poniższy podział jest propozycją do weryfikacji:

### Platform core

Funkcje wspólne dla każdego SaaS:

- tenanty,
- użytkownicy i członkostwa,
- role i uprawnienia,
- moduły i entitlements,
- plany/pakiety,
- usage i limity,
- billing hooks,
- audyt,
- konfiguracja,
- powiadomienia,
- wspólne błędy i identyfikacja requestu.

### Workforce core

Funkcje domenowe niezależne od kanału SMS:

- pracownicy,
- kalendarze,
- ewidencja czasu,
- nieobecności,
- projekty i przypisania.

### Communication module

Opcjonalny moduł kanałów komunikacji:

- SMS inbound/outbound,
- provider adapters,
- kolejki i retry,
- limity wiadomości,
- statusy dostarczenia,
- webhooki,
- szablony wiadomości.

### Payroll module

Opcjonalny moduł rozliczeń:

- ustawienia,
- reguły naliczania,
- raporty,
- zamknięcia okresów,
- eksporty.

### AI module

Opcjonalny moduł inteligentnego rozpoznawania:

- provider abstraction,
- budżety i limity,
- confidence i review,
- redakcja danych wrażliwych,
- logowanie kosztu i użycia.

### Reporting module

Raportowanie powinno mieć jasno opisane read modele. Raport nie powinien wymuszać bezpośredniego łączenia tabel wszystkich modułów w transakcji domenowej.

## 16. Najważniejsze decyzje projektowe do podjęcia

Przed przenoszeniem mechanizmów z `sms2` trzeba ustalić:

1. Czy tenant oznacza firmę, klienta, oddział czy inną jednostkę rozliczeniową?
2. Czy jeden użytkownik może należeć do wielu tenantów?
3. Czy każdy tenant ma własną bazę/schema, czy wszyscy współdzielą tabele z `tenant_id`?
4. Czy plan produktu aktywuje moduły, limity, czy oba mechanizmy jednocześnie?
5. Czy funkcje mogą być aktywowane czasowo, trialowo lub przez administratora?
6. Jak rozliczane są SMS-y i użycie AI?
7. Czy pracownik jest globalny, czy zawsze należy do konkretnego tenanta?
8. Czy telefon może być współdzielony między tenantami?
9. Jak wygląda migracja istniejącego klienta z `sms2`?
10. Czy istniejące role będą mapowane na permissions, czy zostaną tylko jako domyślne role systemowe?
11. Czy integracje zewnętrzne są konfigurowane per tenant?
12. Czy dane raportowe będą liczone na żądanie, czy przez osobne read modele?

## 17. Ryzyka przy przenoszeniu logiki z `sms2`

### Ryzyko izolacji danych

Największe ryzyko to przeniesienie repozytorium lub serwisu bez wymuszenia tenant context. Testy muszą sprawdzać, że użytkownik jednego tenanta nie zobaczy ani nie zmodyfikuje danych drugiego.

### Ryzyko ukrytych zależności

Obecny SMS korzysta z pracowników, obecności i nieobecności. Przeniesienie go jako samodzielnego modułu wymaga interfejsów do identyfikacji pracownika i wykonywania komend domenowych.

### Ryzyko rozrostu core

Jeżeli payroll, SMS, AI i raporty trafią do wspólnego core, późniejsze pakietowanie funkcji będzie trudne. Core powinien zawierać tylko platformę i niezbędne domeny bazowe.

### Ryzyko asynchroniczności

Workery i webhooki muszą zawsze przenosić tenant context, correlation ID oraz wersję kontraktu. Dotyczy to także retry, leasingu, dead-letter i ręcznego reprocessingu.

### Ryzyko konfiguracji i sekretów

Sekrety SMS-Gate, AI i billingowe nie powinny być globalnymi wartościami konfiguracji, jeśli klient może posiadać własnego dostawcę lub własne limity.

## 18. Zalecana kolejność dalszych prac

1. Ustalić model tenanta, członkostw i aktywnego kontekstu.
2. Ustalić model planów, modułów, permissions, limitów i usage.
3. Zdefiniować granice modułów oraz publiczne kontrakty między nimi.
4. Dodać do platform core izolację danych i testy cross-tenant.
5. Zbudować podstawowy moduł użytkowników i administracji tenanta.
6. Przenieść niezależne elementy workforce core.
7. Przenieść komunikację SMS jako opcjonalny moduł z provider abstraction.
8. Przenieść payroll jako oddzielny moduł.
9. Dodać raportowanie i read modele.
10. Dodać billing, usage i proces aktywacji pakietów.
11. Przygotować migrację danych klienta z `sms2`.
12. Dopiero potem przenosić funkcje pomocnicze, eksporty i optymalizacje.

## 19. Przydatne artefakty źródłowego projektu

Podczas dalszej analizy `sms2` warto wracać do:

- `pom.xml` — zależności backendu,
- `src/main/resources/application.yaml` — konfiguracja lokalna,
- `src/main/resources/application-railway.yaml` — konfiguracja wdrożenia,
- `src/main/resources/db/changelog/` — model i historia bazy,
- `src/main/java/.../security` — obecny model security,
- `src/main/java/.../sms` — przepływy inbound/outbound,
- `src/main/java/.../project` — planowanie i wysyłki,
- `src/main/java/.../payroll` — rozliczenia i zamknięcia,
- `frontend/src/app/core` — sesja, API, interceptory i stan,
- `frontend/proxy.conf.json` — lokalne połączenie Angular–backend,
- `Dockerfile`, `docker-compose.yml` i README — sposób uruchamiania.

## 20. Podsumowanie

`sms2` jest wartościowym źródłem reguł biznesowych i przepływów, ale nie powinien być kopiowany jako całość do produktu SaaS. Najbardziej rozsądny kierunek to zachowanie minimalnej bazy technicznej oraz stopniowe przenoszenie domen jako opcjonalnych modułów.

Najważniejszą zmianą względem obecnego projektu jest wprowadzenie tenant context, członkostw, entitlements i izolacji danych zanim zostaną przeniesione mechanizmy SMS, pracowników, projektów i payroll. Dzięki temu późniejsze pakiety funkcjonalne będą elementem modelu domenowego, a nie wyłącznie zestawem ukrytych pozycji menu.
