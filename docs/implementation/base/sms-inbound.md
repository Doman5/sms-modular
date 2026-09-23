# SMS Inbound — etap 8

## Zakres wdrożony

Moduł odbiera zdarzenia `sms:received` z jednego urządzenia SMS-Gate. Webhook
`POST /api/integrations/v1/sms/sms-gate/inbound` weryfikuje HMAC-SHA256 z surowej
treści i nagłówka czasu (okno ±5 minut), następnie ustala tenant z aktywnej
trasy po numerze odbiorcy lub awaryjnie po `deviceId + simNumber`. Payload nie
może wybrać tenant ID. Niejednoznaczny lub sprzeczny routing jest odrzucany.

Przyjęcie zapisuje zaszyfrowane dane SMS, deduplikację zdarzenia oraz zadanie
outbox w jednej transakcji. Ponowne dostarczenie identycznego zdarzenia zwraca
istniejącą wiadomość; ten sam identyfikator z inną treścią daje konflikt.
Worker rozpoznaje pracownika po numerze telefonu wyłącznie w ustalonym
tenancie. Regułowy parser może automatycznie utworzyć tylko jednoznaczny
przedział pracy albo jeden dzień ogólnej nieobecności. Praca przez północ jest
atomowo dzielona na dwa dni. Niejednoznaczny tekst, typ urlopu, brak pracownika
i konflikt z istniejącą ewidencją trafiają do `REVIEW_REQUIRED`.

Nie ma wysyłania SMS, interpretacji AI, importu historii ani naliczania limitu
SMS. Dodatek szczegółowych nieobecności nie jest wywoływany: SMS zapisuje
jedynie bazowe, nietypowane oznaczenie dnia.

## API i uprawnienia

- `GET /api/v1/sms` — zakres dat i filtry statusu, pracownika, review; `SMS_READ`.
- `GET /api/v1/sms/{id}` — pełna treść dostępna wyłącznie z `SMS_READ`.
- `POST /api/v1/sms/{id}/resolve` — ręczne przypisanie pracy/nieobecności albo
  odrzucenie; `SMS_REVIEW`, kontrola wersji i aktywności pracownika.
- `POST /api/v1/sms/{id}/reparse` — ponowne uruchomienie parsera; `SMS_REVIEW`.
- `GET/POST /api/platform/v1/sms/routes` i
  `POST /api/platform/v1/sms/routes/{id}/deactivate` — zarządzanie trasami przez
  uprawnienia platformowe.

Operacje tenantowe wymagają capability `SMS_INBOUND`. Trasy są unikalne dla
aktywnego numeru odbiorcy lub pary urządzenie/SIM. UI `/sms` oferuje listę,
filtry, panel szczegółów i ręczne rozstrzygnięcie; `/platform/sms/routes`
pozwala wyszukać tenant i skonfigurować trasę. Oba widoki działają w układzie
desktopowym i mobilnym.

## Dane i operacje

`sms_messages` przechowuje tenant ID, identyfikatory providera, stan, wersję,
powód review i zaszyfrowane AES-GCM treść oraz numer nadawcy. Klucz danych
jest podawany przez `SMS_DATA_KEY_BASE64`; nie wolno go zmieniać bez
procedury re-encryption. Po 90 dniach zadanie retencyjne usuwa treść i nadawcę,
pozostawiając metadane i ślad audytowy. Dane SMS nie są logowane.

Konfiguracja: `SMS_INBOUND_ENABLED`, `SMS_WORKER_ENABLED`, `SMS_GATE_DEVICE_ID`,
`SMS_GATE_WEBHOOK_SIGNING_KEY`, `SMS_DATA_KEY_BASE64`. Bez pełnej konfiguracji webhook
nie przyjmuje wiadomości. Worker i webhook powinny być włączone dopiero po
utworzeniu trasy i nadaniu klientowi capability. Procedura obsługi awarii jest
w [runbooku Integration Runtime](../platform/integration-runtime-runbook.md).

## Weryfikacja i dalszy etap

Testy PostgreSQL obejmują migrację, podpis, duplikat, routing, izolację tenantów,
automatyczny zapis i review konfliktu. Parser ma testy jednostkowe. UI i API
mają testy frontendu. Następny etap może dodać AI jako opcjonalną pomoc w
interpretacji wiadomości kierowanych do review; wynik nie powinien omijać
kontroli konfliktów ani tenant ID.
