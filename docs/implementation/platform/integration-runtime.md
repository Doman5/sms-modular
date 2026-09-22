# Integration Runtime

## Cel i zakres

Dodać trwałe tło dla realnych zdarzeń i integracji: outbox/inbox, retry, leasing,
webhooki, dead letters i SMS transport. Implementować dopiero przed pierwszym
konsumentem zdarzeń, razem z nim.

## Model i warstwy

- Jedna encja JPA `OutboxMessage` i jedna `InboxReceipt`.
- Każdy rekord z danymi klienta ma tenant ID; worker jawnie przekazuje UUID do
  wywoływanego serwisu.
- `OutboxService` zapisuje zdarzenie w transakcji serwisu właściciela.
- Repository jest package-private i każda metoda wymaga tenant ID.
- `SmsDispatchProvider` może mieć interfejs, bo komunikuje się z zewnętrznym
  dostawcą. Wewnętrzna kolejka nie dostaje portu i adaptera delegującego.
- Retry ma limit, backoff, idempotencję i stan dead letter.

## Konfiguracja i bezpieczeństwo

- Sekrety SMS-Gate są z zewnętrznego secret managera/środowiska.
- Podpis, timestamp i replay window webhooka są walidowane przed obsługą.
- Tenant pochodzi z zapisanego routingu dispatchu, nie z payloadu webhooka.
- API operatora wymaga permission oraz jawnego tenant filtra.
- Metryki obejmują lag, retry, dead letters, odzyskanie lease i czas providera.

## Etapy

1. Dodać tabele outbox/inbox i indeksy dla używanych zapytań.
2. Dodać transakcyjną publikację, idempotentny odbiór i worker tenant-scoped.
3. Dodać lease, retry i dead letter, gdy wymaga tego SMS Inbound.
4. Dodać rzeczywisty provider SMS i podpisany webhook.
5. Dodać panel operacyjny po wdrożeniu Audit i Identity.

## Testy i zależności

Testować dwa tenanty, obcy UUID, duplikat eventu, równoległy leasing, crash,
retry, timeout handlera, podpis webhooka i retry provider errors. Wymaga
Tenancy, Identity i Audit. Odblokowuje SMS Inbound oraz wysyłki Projects.
