# Integration Runtime

## Cel i zakres

Etap 8 wdrożył trwały inbox/outbox dla przychodzących wiadomości SMS. Runtime
nie jest uniwersalnym brokerem ani modułem wysyłania SMS. Udostępnia zapis
zdarzenia, leasing zadania, retry i stan `DEAD_LETTER` pierwszemu konsumentowi.

## Model i warstwy

- Jedna encja JPA `OutboxMessage` i jedna `InboxReceipt`.
- Każdy rekord z danymi klienta ma tenant ID; worker jawnie przekazuje UUID do
  wywoływanego serwisu.
- `IntegrationRuntimeService.enqueue` zapisuje inbox i outbox w transakcji
  serwisu przyjmującego webhook.
- Dostęp do pojedynczego zadania wymaga tenant ID; globalne pobranie partii
  przez worker przekazuje następnie jawny tenant ID do przetwarzania.
- Retry ma limit, backoff, idempotencję i stan dead letter.

## Konfiguracja i bezpieczeństwo

- Sekrety SMS-Gate są z zewnętrznego secret managera/środowiska.
- Podpis, timestamp i replay window webhooka waliduje SMS Inbound.
- Tenant pochodzi z zapisanej trasy urządzenia/odbiorcy, nie z payloadu.
- Nie ma jeszcze panelu operatora ani dedykowanych metryk runtime.

## Etapy

1. Dodać tabele outbox/inbox i indeksy dla używanych zapytań.
2. Dodać transakcyjną publikację, idempotentny odbiór i worker tenant-scoped.
3. Dodać lease, retry i dead letter, gdy wymaga tego SMS Inbound.
4. Wdrożyć podpisany webhook przychodzący SMS-Gate.
5. Panel operacyjny i dedykowane metryki pozostają dalszym zakresem.

## Testy i zależności

Testować dwa tenanty, obcy UUID, duplikat eventu, równoległy leasing, crash,
retry, timeout handlera i podpis webhooka. Wymaga Tenancy, Identity i Audit.
Obecnym konsumentem jest wyłącznie SMS Inbound.
