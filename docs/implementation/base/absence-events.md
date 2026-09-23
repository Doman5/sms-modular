# Moduł Absence Events — Braki obecności

## Zakres wdrożony w etapie 7

Pakiet bazowy oznacza wyłącznie fakt, że pracownik był nieobecny danego dnia.
Każda data ma osobny `AbsenceDay` z pracownikiem, źródłem `MANUAL` lub `SMS`, opcjonalną
notatką, statusem `ACTIVE/CANCELLED` i wersją. Nie ma kategorii, akceptacji,
sald ani naliczania dni urlopowych. Zakres w formularzu zapisuje wszystkie
dni kalendarzowe atomowo; konflikt w jednym dniu odrzuca całość. Maksymalny
zakres to 366 dni.

Data rekordu jest niezmienna. Można poprawić notatkę lub anulować dzień z
kontrolą wersji. Anulowany rekord pozostaje w historii, nie jest pokazywany
na bieżącej liście i nie blokuje ponownego oznaczenia tej daty.

Zapis blokuje tenantowy rekord pracownika i odrzuca dzień z aktywnym czasem
pracy. Własna tabela ma `tenant_id`, tenantowy FK do pracownika, unikalność
aktywnego dnia i indeksy do kalendarza. Każda zmiana trafia do audytu.

## API i UI

- `GET /api/v1/absence-days?from&to&employeeId&page` — aktywne dni.
- `GET /api/v1/absence-days/calendar?month&employeeId` — liczby dni do kalendarza.
- `GET /api/v1/absence-days/{id}` — szczegóły, również anulowanego dnia.
- `POST /api/v1/absence-days` — atomowe oznaczenie zakresu dat.
- `PUT /api/v1/absence-days/{id}` — zmiana notatki.
- `POST /api/v1/absence-days/{id}/cancel` — anulowanie.

Odczyt wymaga `ABSENCE_READ`, zmiany `ABSENCE_EDIT`, a moduł capability
`ABSENCE_EVENTS`. Widok `/absence-days` pokazuje kalendarz i listę; karta
pracownika otwiera przefiltrowany widok. Nazwa w UI to „Braki obecności”.
Wybór pracownika korzysta z ograniczonego DTO pod `/api/v1/employees/options`,
dostępnego także dla `ABSENCE_READ` bez `EMPLOYEE_READ`.

## Zależność od przyszłego dodatku

Planowany dodatek `DETAILED_ABSENCES` jest właścicielem typów `VACATION`,
`SICK_LEAVE`, `ON_DEMAND_LEAVE`, `OTHER`, ich przypisania do dni, zliczania
oraz właściwych workflow i sald. Bazowy dzień działa niezależnie od dodatku
i zachowuje identyfikator potrzebny do przyszłego powiązania. Etap 8 dodał
`sms_message_id` i atomowy zapis pojedynczego, ogólnego dnia z jednoznacznego
SMS. Wiadomości wskazujące kategorię urlopu wymagają ręcznej weryfikacji;
import SMS2 pozostaje dalszym etapem.
