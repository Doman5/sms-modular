# Migracja SMS2 i cutover

## Cel i zasady

Przenieść jednego istniejącego klienta SMS2 do jednego tenanta SMS Modular bez
utraty danych, podwójnego przetworzenia SMS ani przeliczenia zamkniętej historii.
Migracja następuje dopiero po ukończeniu właścicielskich modułów i ich adapterów.

Zasady:

- SMS2 pozostaje źródłem prawdy do momentu rozpoczęcia finalnego okna cutover;
- każda tabela jest importowana przez moduł właścicielski;
- zachowywać UUID, gdy nie powoduje konfliktu, oraz utrzymywać mapę legacy ID;
- import jest powtarzalny, idempotentny i generuje raport odrzuceń;
- historycznych SMS i dispatches nie enqueue się ponownie;
- zamkniętych raportów Payroll nie przelicza się nową logiką;
- po każdej fali porównuje się liczności i wartości kontrolne.

## Inwentaryzacja przed migracją

1. Zablokować wersję/commit SMS2 używany do analizy.
2. Zebrać liczności, min/max timestamps, duplikaty i osierocone FK.
3. Zidentyfikować kolizje po nowych regułach: telefon E.164, email użytkownika,
   kod projektu, source IDs i okresy overlap.
4. Spisać aktywne workery, kolejki, lease, retry i webhooki w locie.
5. Uzgodnić tenant timezone/locale oraz dodatki potrzebne do zachowania funkcji.
6. Zanonimizować kopię do wielokrotnych prób migracyjnych.

## Kolejność importu

1. Tenant oraz plan bazowy i wymagane dodatki.
2. Użytkownicy, role i permissions; zachowanie BCrypt, wymuszenie zmiany hasła
   tylko zgodnie z istniejącym stanem lub polityką cutover.
3. Employee Directory bez pól Payroll i Leave.
4. Leave allowances/types potrzebne do mapowania urlopów.
5. SMS history jako rekordy niekolejkowane.
6. Time Tracking: work days, potem intervals i source references.
7. Absence Events, a następnie historyczne Leave Requests/decisions.
8. Projects i assignments; osobno Planning drafts/published plans.
9. Project message dispatches, recipients i webhook history bez retry.
10. Payroll settings/compensation, reports/rows i closures.
11. Audit jako legacy entries z aktorem `LEGACY_SYSTEM`, jeśli nie da się
    wiarygodnie ustalić użytkownika.
12. Tool Assignment pomija import jako moduł greenfield.
13. Usage bieżącego okresu wyliczone ze źródeł; wcześniejsze okresy opcjonalnie
    jako historia raportowa, nie aktywny licznik.
14. Pełny rebuild read modeli z nowych domen.

## Mapowanie odpowiedzialności

| SMS2 | SMS Modular | Kontrola |
| --- | --- | --- |
| `users` | Identity Access | liczba/status/rola/hash obecny |
| `employees` pola bazowe | Employee Directory | liczba, status, telefon po normalizacji |
| stawki/override pracownika | Payroll Compensation | wartości `BigDecimal` |
| annual vacation days | Leave Allowance | saldo per pracownik/rok |
| `work_days`, `work_intervals` | Time Tracking | liczby i suma minut per miesiąc |
| `absences` | Absence Event + opcjonalnie Leave Request | zakres, kategoria, status, source |
| `sms_messages`, processing outbox | SMS Inbound/history | statusy bez aktywnego joba |
| `projects`, assignment | Projects | liczby, statusy, kody i okresy |
| plan drafts/day assignments | Planning | wersja/dzień/status publikacji |
| dispatch/recipients/webhooks | Projects + Integration Runtime history | status per recipient i provider IDs |
| payroll settings/reports/rows/closures | Payroll | wartości wierszy, sumy, zamknięcia |
| audit logs | Audit legacy | czas, akcja, subject, rozpoznany aktor |

## Próby i walidacja

- Minimum dwie pełne próby na zanonimizowanej kopii o rozmiarze produkcyjnym.
- Raport per moduł: read/insert/update/skip/error, czas i checksum zestawu.
- Kontrole finansowe: raport Payroll wiersz po wierszu, total i closure.
- Kontrole operacyjne: suma minut, zakresy nieobecności, statusy SMS, odbiorcy
  dispatch oraz projekty/plany per dzień.
- Test logowania reprezentatywnych ról i test odmowy między dwoma tenantami.
- Test webhooka po zmianie routingu i potwierdzenie, że stary endpoint nie
  tworzy danych po cutover.
- Sign-off właściciela danych po raporcie ostatniej próby.

## Finalny cutover

1. Ogłosić okno i zatrzymać nowe komendy w SMS2.
2. Wyłączyć i opróżnić inbound/outbound workery; zapisać nierozwiązane rekordy.
3. Wykonać backup i snapshot bazy SMS2.
4. Uruchomić delta import od timestampu/checkpointu ostatniej próby.
5. Wykonać automatyczne kontrole; przy rozbieżności nie przełączać ruchu.
6. Skonfigurować nowy routing SMS i sekrety, uruchomić aplikację w trybie
   ograniczonym i wykonać smoke tests.
7. Odblokować użytkowników, następnie inbound, a na końcu outbound Projects.
8. Monitorować błędy, lag, review, usage i raporty przez uzgodniony okres.
9. Utrzymać SMS2 read-only do końca okresu akceptacji i retencji.

## Rollback

- Przed przyjęciem pierwszego nowego SMS: można cofnąć routing i odblokować SMS2.
- Po zapisach w obu systemach nie wykonywać automatycznego merge. Zatrzymać ruch,
  wyeksportować dziennik nowych komend z SMS Modular i uruchomić zatwierdzony
  adapter reverse/replay dla wspieranego zakresu.
- Rollback nie usuwa bazy SMS Modular; oznacza import jako nieaktywny, zachowuje
  log i przywraca snapshot dopiero w odizolowanym środowisku.

## Kryteria ukończenia

- wszystkie kontrole per moduł są zgodne albo mają zaakceptowany raport różnic;
- zero aktywnych legacy lease/jobów zostało uruchomionych w nowym systemie;
- raporty zamknięte są identyczne wartościowo;
- tenant, permissions, capabilities, usage i routing są poprawne;
- smoke tests UI/API/SMS przechodzą, alerty są aktywne i istnieje właściciel
  decyzji rollback;
- SMS2 działa read-only, a data jego wyłączenia wynika z polityki retencji.

## Zależności

Wymaga ukończenia wszystkich migrowanych modułów, read modeli, runbooków i
monitoringu. Nie odblokowuje nowych funkcji; kończy przejście produkcyjne.

