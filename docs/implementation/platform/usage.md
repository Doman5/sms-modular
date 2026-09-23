# Moduł Usage

## Stan wdrożenia

Pierwsza metryka to `ACTIVE_USERS`. Zużycie jest liczone z kont o statusie
`ACTIVE` danego tenanta; nie ma oddzielnego licznika ani synchronizacji. Wynik
zawiera `used`, efektywny tryb i wartość limitu oraz `remaining` (zero także
po obniżeniu limitu poniżej bieżącego zużycia). Jest dostępny w
`/api/v1/me/context` i w widokach subskrypcji.

Tworzenie użytkownika i przejście `DISABLED -> ACTIVE` sprawdzają limit przed
zapisem. Operacje kont są serializowane blokadą tenanta, dlatego dwa równoległe
żądania nie przekroczą dostępnego miejsca. Wyłączenie konta zwalnia miejsce;
obniżenie limitu nie usuwa istniejących kont. Przekroczenie zwraca
`403 LIMIT_EXCEEDED`.

## Dalsze metryki

Liczniki okresowe, potwierdzenia idempotencji i korekty administracyjne nie
powstały teraz. Będą projektowane przy konkretnych konsumentach:

1. aktywni pracownicy — Employee Directory;
2. inbound SMS — Integration Runtime i SMS Inbound;
3. wywołania/budżet AI — AI Interpretation;
4. outbound SMS projektów — Projects/SMS Outbound.

Przy metrykach zdarzeniowych trzeba zapewnić atomowe naliczanie,
idempotentność retry, okres rozliczeniowy i odrębną politykę po przekroczeniu.
Nie należy używać licznika aktywnych użytkowników jako ogólnego wzorca dla
każdej metryki: jego źródłem prawdy są konta, a nie zdarzenia.

Usage zależy od Entitlements i Identity. Testy PostgreSQL sprawdzają limit,
zwalnianie miejsca, re-aktywację oraz wyścig równoległych tworzeń.
