# Moduł Usage

## Cel i zakres

Idempotentnie mierzyć użycie limitowanych zasobów i udostępniać decyzję, czy
operacja mieści się w efektywnym limicie. Moduł jest greenfield. Nie nalicza cen
i nie jest ledgerem finansowym.

Metryki pierwszej wersji: aktywni użytkownicy, aktywni pracownicy, SMS inbound,
wywołania/budżet AI oraz projektowe SMS outbound.

## Model i kontrakty

- `UsageMetric` jest stabilnym enumem/katalogiem z jednostką i typem okresu.
- `UsageCounter(tenant, metric, periodStart, periodEnd, consumed, version)` ma
  unikalność tenant/metric/period.
- `UsageReceipt(operationId, tenant, metric, amount)` zapobiega podwójnemu
  naliczeniu retry.
- `UsageMeter.check`, `consume` i `snapshot`; consume jest atomowe i zwraca
  limit, zużycie, pozostałą wartość oraz wynik.
- Limit pochodzi z Entitlements; brak zdefiniowanego limitu oznacza politykę
  zapisaną jawnie w planie, nie domysł modułu.

## Zachowanie przekroczeń

- SMS inbound zostaje trwale przyjęty i oznaczony do administracyjnej obsługi;
  webhook nie traci danych i nie wpada w retry loop.
- Brak budżetu AI powoduje parser regułowy + review.
- Tworzenie użytkownika/pracownika ponad limit jest odrzucane przed zapisem.
- Project outbound nie jest wysyłany ponad limit i zachowuje draft/status błędu.

## API i frontend

- Dane usage są częścią `/api/v1/me/context` i `GET /api/v1/subscription`.
- Platforma ma tenant-scoped podgląd i audytowaną korektę, bez ręcznej edycji
  liczników SQL.
- Angular pokazuje zużycie, próg i kod `LIMIT_EXCEEDED`; nie oblicza limitu sam.

## Etapy

1. Katalog metryk, okresy, counters, receipts i RLS.
2. Atomowy meter z ochroną przed wyścigiem i retry.
3. Integracja z Entitlements i kontekstem sesji.
4. Integracje kolejno: users, employees, inbound SMS, AI, outbound SMS.
5. Agregacja, metryki, alerty i administracyjna korekta.

## Testy, migracja i zależności

Startowe liczniki migracji wyliczyć z bieżącego okresu SMS2, nie z całej historii.
Testować równoległe consume, duplikat operation ID, granicę okresu w timezone,
zmianę limitu, przekroczenia specyficzne dla zasobu i izolację tenanta.

Wymaga Entitlements, Integration Runtime i Audit. Odblokowuje pełne wdrożenie
Employee, SMS, AI i Projects.
