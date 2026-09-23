# Integration Runtime — leasing i odtwarzanie

Outbox i inbox są częścią Integration Runtime. Żaden endpoint klienta nie
zwraca surowego payloadu outboxa. Panel operatora nie jest jeszcze wdrożony.

Worker pobiera rekord zadania, sprawdza jego tenant ID i przekazuje UUID jawnie
do serwisu konsumenta. Nie używa ThreadLocal ani kontekstu współdzielonego
między zadaniami.

## Leasing i awarie

Status `PROCESSING` ma `lease_owner` i `lease_until`. Leasing używa
`FOR UPDATE SKIP LOCKED`. Zapis wyniku sprawdza aktualnego właściciela lease.
Retry ma wykładniczy backoff, ograniczony jitter i maksymalną liczbę prób.
Po jej przekroczeniu rekord przechodzi do `DEAD_LETTER` z bezpiecznym kodem błędu.

## Diagnostyka

- `RETRY_WAIT`: sprawdź `available_at`, `attempt` i `error_code` w
  `outbox_messages`.
- `DEAD_LETTER`: sprawdź tenant ID, SMS ID i `error_code`; wiadomość ma stan
  `ERROR` i można ją ponowić z ekranu review po usunięciu przyczyny.
- Odzyskiwanie lease: sprawdź timeout handlera, GC i wielkość partii.
- Pusty wynik: sprawdź tenant filter, permission i status rekordu.

## Provider SMS

Inbound SMS-Gate wymaga `SMS_INBOUND_ENABLED=true`, identyfikatora urządzenia,
sekretu podpisu i 32-bajtowego klucza danych zakodowanego Base64. Webhook
sprawdza podpis HMAC oraz timestamp ±5 minut. Idempotencję zapewnia unikalny
identyfikator eventu. Payload webhooka nie ustala tenant ID. Sekrety podawać
przez środowisko lub secret manager, nigdy przez repozytorium.

## Migracja

Przed cutover opróżnić aktywne kolejki SMS2. Historię importować jako zakończone
rekordy bez aktywnego lease. Migracja bazy jest wykonywana rolą migracyjną po
backupie i zatrzymaniu workera.
