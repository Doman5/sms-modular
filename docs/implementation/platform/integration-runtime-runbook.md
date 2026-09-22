# Integration Runtime — runbook leasingu i odtwarzania

## Zakres i bezpieczeństwo

integration-runtime jest właścicielem outbox_messages i inbox_receipts; statusy
SmsDispatchPort pozostają kontraktem transportowym do czasu wyboru providera.
Żaden endpoint tenanta nie zwraca
surowego outboxa ani payloadu. Panel operatora wymaga tenantId oraz
PLATFORM_INTEGRATION_READ/PLATFORM_INTEGRATION_RETRY.

Rola sms_modular_runtime ma NOBYPASSRLS. Nie ma globalnej polityki opartej na
ustawialnym GUC. Worker pobiera wyłącznie aktywne identyfikatory przez publiczny
kontrakt Tenancy, a każdą transakcję leasingu i handlera wykonuje dla jednego
tenanta z set_config('app.tenant_id', value, true). TenantContext i MDC są
czyszczone w finally po każdym zadaniu.

## Leasing i awarie

Leasing używa PostgreSQL FOR UPDATE SKIP LOCKED. Status PROCESSING zawsze
ma lease_owner i lease_until; po wygaśnięciu kolejny worker może przejąć
rekord. Aktualizacja wyniku sprawdza właściciela lease, więc spóźniony worker
nie nadpisze wyniku nowego właściciela.

Handler i InboxReceipt są w jednej transakcji konsumenta. Błąd handlera
wycofuje receipt, ale nie cofa transakcji, która opublikowała outbox. Retry ma
wykładniczy backoff, ograniczony jitter i maksymalną liczbę prób. Po jej
przekroczeniu rekord otrzymuje DEAD_LETTER i bezpieczny kod błędu bez payloadu.

## Diagnostyka

- RETRY_WAIT: sprawdź available_at, attempt i metrykę retry; nie uruchamiaj ręcznego SQL na puli aplikacyjnej.
- DEAD_LETTER: przejrzyj tylko topic, wersję, correlation ID i errorCode; payload pozostaje niedostępny w API. Retry wykonuj wyłącznie w panelu z audytem.
- wzrost lease recovery: sprawdź timeout handlera, GC i wielkość partii; nie zwiększaj lease bez obserwacji czasu p95.
- brak wiadomości przy poprawnym uprawnieniu oznacza najczęściej brak jawnego tenant filtera albo brak lokalnego app.tenant_id.

## SMS provider i webhook

Konkretny provider nie jest jeszcze wybrany. SmsDispatchPort i statusy są
stabilnym kontraktem, ale adapter transportowy jest
celowo odroczony. app.integration-runtime.sms.enabled=true odrzuca start
aplikacji, dopóki adapter nie dostarczy sekretu z env/secret managera,
timestampu, podpisu HMAC, replay protection i routingu z zapisanego dispatchu.
Payload webhooka nie może wyznaczać tenanta.

## Migracja i rollback

Przed cutover opróżnić aktywne kolejki SMS2; historyczne rekordy importować jako
projekcje zakończone, bez aktywnego lease. Changelog Integration Runtime zawiera
wyłącznie tabele, constrainty, indeksy i jawne RLS; uprawnienia ról są
konfigurowane poza migracją. Rollback
wykonuje właściciel migracji po backupie i zatrzymaniu workera; rola runtime nie
może wykonywać rollbacku ani omijać RLS.
