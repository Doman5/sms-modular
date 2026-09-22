# Moduł SMS Inbound

## Cel, zakres i analiza SMS2

Bezpiecznie przyjmować SMS pracownika, rozpoznać pracownika, uruchomić parsery,
przekazać komendę do Time/Absence albo skierować wiadomość do review. Źródła:
`../sms2/src/main/java/com/domanski/sms/sms`, migracje `0004`, `0015`, `0016`,
`0022` oraz Angular `pages/sms` i `core/api/sms-*`.

Usunąć bezpośrednie relacje JPA do Employee/Absence/Attendance. AI ma osobny port.
Moduł bazowy nie wysyła potwierdzeń ani odpowiedzi.

## Model i przepływ

- `SmsMessage`: tenant, provider/source, external ID, sender/recipient, sim/route,
  content, receivedAt, employeeId?, processing/review status, interpretation
  summary, source, confidence, errors, version i timestamps.
- `TenantSmsRoute`: routing dostawcy → tenant, zarządzany platformowo.
- Idempotencja `(tenant_id, provider, external_message_id)`.
- Webhook: verify signature/timestamp/replay → resolve route → persist message +
  outbox → return 2xx → asynchronous processing.
- Worker: resolve employee by normalized phone → rules → optional AI → command
  Time/Absence → completed albo review/error.

## API, permissions i kontrakty

- Publiczny `/api/integrations/v1/sms/{provider}/inbound`; nie przyjmuje zaufanego
  `tenantId`.
- `/api/v1/sms`: list/detail/reparse/resolve; permissions `SMS_READ`,
  `SMS_REVIEW`, capability `SMS_INBOUND`.
- Manual resolve wywołuje publiczną komendę właściciela i jest idempotentne.
- Konsumuje `EmployeeDirectoryPort`, `RegisterWorkEntryCommand`,
  `RegisterAbsenceEventCommand`, `InterpretSmsPort`, Usage i outbox.
- Kody: `SMS_ROUTE_UNKNOWN`, `SMS_SIGNATURE_INVALID`, `SMS_EMPLOYEE_UNKNOWN`,
  `SMS_REVIEW_REQUIRED`, `SMS_ALREADY_RESOLVED`.

## Dane, bezpieczeństwo i obserwowalność

- `sms_messages` z RLS oraz route mapping jako konfiguracja platformy.
- Treść może zawierać dane osobowe: szyfrowanie at rest na poziomie platformy,
  retencja, brak w logach i ograniczony permission do szczegółów.
- Metryki: accepted/duplicate/review/completed/error, lag, parser outcome, unknown
  route/employee. Alertować wzrost error i kolejki.
- Usage jest naliczane idempotentnie po trwałym przyjęciu; przekroczenie nie
  powoduje utraty webhooka.

## Frontend

- `/sms` lista z filtrami statusu/reason/date; `/sms/:id` szczegóły,
  interpretacja, historia prób, reparse i manual resolve.
- Nie pokazywać danych innego pracownika przy zmianie route; maskować telefon na
  liście zgodnie z permission.

## Etapy

1. Model wiadomości, routing, migracje, RLS i retencja.
2. Webhook security, idempotencja i transakcyjny outbox.
3. Worker, employee resolution i parsery regułowe przeniesione po testach golden.
4. Adaptery komend Time/Absence oraz stanowa maszyna przetwarzania.
5. Review/reparse/resolve API i Angular.
6. Podłączenie AI, usage, metryk, alertów i runbooka.

## Migracja i testy

Importować historyczne SMS jako zakończone/review bez enqueue; zachować external
ID i source, a relacje zastąpić ID/referencją. Golden tests parserów wykorzystają
zanonimizowane przypadki SMS2. Testować podpis, replay, duplikat, dwa tenanty z
tym samym telefonem, unknown employee, crash/retry, concurrent resolve, limit AI
i gwarancję braku outbound.

## Zależności i ukończenie

Wymaga Employee, Time, Absence, Integration Runtime, Usage i Audit. AI jest
opcjonalnym technicznym krokiem mimo bazowego capability. Gotowe po pełnym
przepływie webhook → komenda/review bez bezpośredniego zapisu obcej tabeli.

