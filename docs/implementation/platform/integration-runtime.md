# Moduł Integration Runtime

## Cel, zakres i analiza SMS2

Dostarczyć wspólny runtime dla outboxa, jobów, retry, leasingu, webhooków i
transportu SMS. Uogólnić mechanizmy z `SmsProcessingJob`/`SmsProcessingWorker`
oraz `ProjectSmsOutboxWorker` w `../sms2`, bez przenoszenia ich logiki domenowej.

Moduł nie interpretuje SMS, nie wybiera odbiorców projektu i nie posiada danych
biznesowych właścicielskich modułów.

## Model i kontrakty

- `OutboxMessage`: tenant, topic, aggregate, payload version, payload, status,
  attempt, availableAt, leaseOwner/Until, correlation i idempotency key.
- `InboxReceipt`: konsument, event ID i tenant; gwarantuje idempotentną konsumpcję.
- `JobContext` odtwarza tenant i correlation context na czas jednego zadania.
- Retry: exponential backoff z jitter, maksymalna liczba prób, `DEAD_LETTER` i
  ręczne ponowienie z audytem.
- `SmsDispatchPort` przyjmuje wiadomość i odbiorcę z modułu Projects; adapter
  przechowuje status techniczny oraz weryfikuje webhook dostarczenia.

## Konfiguracja i bezpieczeństwo

- Sekrety SMS-Gate wyłącznie z secret managera/środowiska.
- Webhook: podpis, timestamp, replay window, idempotency i routing do zapisanego
  dispatchu; payload nie może narzucić tenanta.
- Konfiguracja: enable, batch size, lease, retry i timeout; walidacja startowa
  nie pozwala włączyć workera bez kompletnej integracji.
- Metryki: lag, retry, dead letters, lease recovery, provider latency i statusy.

## API i frontend

Brak ogólnego API outboxa dla klienta. Platform admin otrzymuje odczyt dead
letters i audytowane retry. UI domenowe pokazuje status przez projekcję właściciela,
nie przez surowy rekord infrastruktury.

## Etapy

1. Outbox/inbox, transakcyjna publikacja i RLS.
2. Scheduler, bezpieczny leasing i odzyskanie wygasłego lease.
3. Rejestr handlerów, wersjonowanie payloadu i dead-letter.
4. Adapter SMS provider, podpisane webhooki i `SmsDispatchPort`.
5. Panel operacyjny, metryki, alerty i runbook retry.

## Migracja, testy i zależności

Nie importować aktywnych lease z SMS2. Przed cutover stare kolejki należy
opróżnić; wiadomości historyczne importować jako zakończone projekcje. Testować
crash po zapisie, równoległe workery, wygaśnięcie lease, duplikat, kolejność,
obcy tenant, błędy 429/5xx i niepoprawny webhook.

Wymaga Fundamentu i Tenancy; używa Audit. Odblokowuje SMS Inbound, AI,
reporting i wysyłkę projektową.

