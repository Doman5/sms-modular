# Dodatek Planning

## Cel, zakres i analiza SMS2

Planować pracę pracowników w projektach dla dnia, obsłużyć draft oraz publikację.
Źródła: `ProjectPlanController`, planistyczna część `ProjectController`,
`ProjectPlanOverviewService`, `ProjectPlanDraft`, `ProjectAssignment`, migracja
`0025` i Angular `pages/plan` oraz plan w szczegółach projektu.

Moduł nie zarządza projektem, pracownikiem ani transportem SMS. Komunikat projektu
po publikacji jest przypadkiem użycia Projects.

## Model i reguły

- `Plan(id, tenantId, workDate, status, version, publishedAt/by)`.
- `PlanEntry(id, tenantId, planId, projectId, employeeId, note?, position)`.
- Draft jest bieżącą wersją planu ze optimistic locking; publikacja tworzy
  niezmienny snapshot/version do raportowania.
- Project i Employee muszą istnieć, być aktywni i należeć do tenanta; walidacja
  odbywa się przez `ProjectService` i `EmployeeService` z jawnym `tenantId`.
- Ten sam pracownik nie może mieć sprzecznych przydziałów dnia; dokładna reguła
  wieloprojektowości ma być zapisana jako walidator, nie constraint SMS2.
- Aktywacja i każde użycie wymagają jednocześnie `PLANNING` i `PROJECTS`.

## API, permissions i zdarzenia

- `/api/v1/plans/calendar`, `/api/v1/plans/{workDate}` oraz komendy save-draft,
  publish i reopen/correct z jawną wersją.
- Widok w kontekście projektu korzysta z tego samego API, nie z tabel Projects.
- Permissions `PLAN_READ`, `PLAN_EDIT`, `PLAN_PUBLISH`; capability `PLANNING`.
- Zdarzenia `PlanDraftChanged`, `PlanPublished`, `PlanCorrected`.
- Błędy `PLAN_VERSION_CONFLICT`, `PLAN_ASSIGNMENT_CONFLICT`,
  `PROJECT_NOT_ACTIVE`, `PLANNING_REQUIRES_PROJECTS`.

## Dane i frontend

- `plans`, `plan_entries`, opcjonalna tabela wersji snapshot; indeks tenant/date
  i klucze obce. Reguły dotyczące danych Projects i Employee sprawdzają ich
  serwisy.
- Angular `/plan`, `/plan/:date`: kalendarz, edycja draftu, konflikt, publikacja,
  unsaved changes i odświeżenie po 409.
- Wyłączenie Planning blokuje edycję i ukrywa trasy, nie usuwa planów.

## Etapy

1. Encje planu/draft/version oraz walidacja przez `ProjectService` i `EmployeeService`.
2. Query kalendarza/dnia oraz conflict validation.
3. Save/publish/correct z audytem i optimistic locking.
4. Angular calendar/editor i guards dwóch capabilities.
5. Zdarzenia do Reporting i wywołanie projektu dla opcjonalnej wiadomości.
6. Import draftów i planów SMS2.

## Migracja i testy

Rozdzielić `project_assignments` używane jako plan dnia od długoterminowego
assignment Projects; importować `project_plan_drafts` jako draft tylko jeśli nie
został opublikowany. Testować concurrent save, publish, obcy/nieaktywny projekt,
duplikat pracownika, zależność capability, timezone i zachowanie wersji.

## Zależności i ukończenie

Wymaga Projects i Employee oraz Platform Core. Odblokowuje planistyczne read
modele. Gotowe, gdy wyłączenie Projects przy aktywnym Planning jest niemożliwe,
a Planning nie importuje repozytorium Projects.
