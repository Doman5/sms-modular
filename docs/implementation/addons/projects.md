# Dodatek Projects

## Cel, zakres i analiza SMS2

Zarządzać projektami, ich cyklem życia, przypisaniami pracowników, raportem
projektowym oraz projektową wysyłką SMS. Źródła:
`../sms2/src/main/java/com/domanski/sms/project`, `ProjectController`, migracje
`0023`, `0024`, `0026`, `0028` oraz Angular `pages/projects`.

Plan dnia i draft planu należą do Planning. Transport SMS, retry i webhook statusu
należą do Integration Runtime; Projects posiada treść biznesową, odbiorców i
historię dispatchu widoczną dla użytkownika.

## Model i reguły

- `Project(id, tenantId, code, name, description?, startDate?, endDate?, status,
  createdAt, updatedAt, version)`; code unikalny per tenant.
- `ProjectMembership/Assignment` określa projekt, pracownika i okres przypisania;
  nie używać obecnej unikalności pracownik+dzień jako modelu projektu.
- Statusy `ACTIVE`, `FINISHED`, `ARCHIVED`; przejścia jawne i audytowane.
- `ProjectMessageDraft/Dispatch` przechowuje projektowy przypadek użycia, listę
  employee IDs i status biznesowy; transport ma referencję runtime.
- Brak aktywnego pracownika/telefonu daje walidowany wynik odbiorcy, nie awarię
  całej wysyłki.

## API, permissions i zdarzenia

- `/api/v1/projects`: list/detail/create/update oraz finish/archive/restore.
- `/api/v1/projects/{id}/assignments` i `/messages`; raport projektu bez danych
  należących do Planning.
- Permissions `PROJECT_READ`, `PROJECT_EDIT`, `PROJECT_ASSIGN`,
  `PROJECT_MESSAGE_SEND`; capability `PROJECTS`.
- Zdarzenia `ProjectCreated/StatusChanged`, `ProjectAssignmentChanged`,
  `ProjectMessageRequested/RecipientStatusChanged`.
- Konsumuje Employee projection, `SmsDispatchPort`, Usage i Audit.

## Dane, frontend i entitlement

- `projects`, `project_assignments`, `project_message_dispatches/recipients` z
  tenantem, RLS i złożonymi FK; provider webhook nie wybiera tenanta z payloadu.
- Angular `/projects`, `/projects/:id`: dane, przypisania, raport i historia
  wiadomości. Zakładka planowania jest dostarczana tylko przez Planning.
- Wyłączenie dodatku zatrzymuje nowe dispatches i komendy; zapisane projekty,
  statusy dostarczenia i historia pozostają.

## Etapy

1. Project lifecycle, RLS, API i lista/szczegóły Angular.
2. Assignment z walidacją Employee i okresów.
3. Zdarzenia i podstawowy raport właścicielski.
4. Draft/dispatch biznesowy, Usage i `SmsDispatchPort`.
5. Webhook statusów przez Integration Runtime, retry tylko failed recipients.
6. Import SMS2 i zgodność raportów.

## Migracja i testy

Importować `projects`, przypisania oraz historię dispatch/recipients zachowując
provider IDs i statusy; plan drafts przekazać do Planning. Testować code per
tenant, przejścia statusów, assignment obcego pracownika, częściowy błąd SMS,
retry failed-only, duplikat webhooka, limit i wyłączenie capability.

## Zależności i ukończenie

Wymaga Employee, Entitlements, Usage, Audit i Integration Runtime. Odblokowuje
Planning i read modele. Gotowe, gdy moduł nie posiada encji planu dnia ani kodu
klienta konkretnego dostawcy SMS.

