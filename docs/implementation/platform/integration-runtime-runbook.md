# Integration Runtime — leasing i odtwarzanie

Outbox i inbox są częścią Integration Runtime. Żaden endpoint klienta nie
zwraca surowego payloadu outboxa. Panel operatora wymaga platform permission i
jawnego tenant filtra.

Worker pobiera rekord zadania, sprawdza jego tenant ID i przekazuje UUID jawnie
do serwisu konsumenta. Nie używa ThreadLocal ani kontekstu współdzielonego
między zadaniami.

## Leasing i awarie

Status `PROCESSING` ma `lease_owner` i `lease_until`. Leasing używa
`FOR UPDATE SKIP LOCKED`. Zapis wyniku sprawdza aktualnego właściciela lease.
Retry ma wykładniczy backoff, ograniczony jitter i maksymalną liczbę prób.
Po jej przekroczeniu rekord przechodzi do `DEAD_LETTER` z bezpiecznym kodem błędu.

## Diagnostyka

- `RETRY_WAIT`: sprawdź `available_at`, `attempt` i metryki retry.
- `DEAD_LETTER`: sprawdź topic, wersję, correlation ID i error code; payload
  pozostaje niedostępny w API.
- Odzyskiwanie lease: sprawdź timeout handlera, GC i wielkość partii.
- Pusty wynik: sprawdź tenant filter, permission i status rekordu.

## Provider SMS

Provider jest uruchamiany dopiero po dostarczeniu sekretu przez środowisko lub
secret manager. Adapter sprawdza podpis HMAC, timestamp, replay protection i
routing zapisany dla dispatchu. Payload webhooka nie ustala tenant ID.

## Migracja

Przed cutover opróżnić aktywne kolejki SMS2. Historię importować jako zakończone
rekordy bez aktywnego lease. Migracja bazy jest wykonywana rolą migracyjną po
backupie i zatrzymaniu workera.
