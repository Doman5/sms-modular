# Tenancy — operacje cyklu życia

## Suspend

Zmiana statusu odbywa się przez `TenantService` i zapisuje `updatedAt`. Identity
odrzuca nowe komendy biznesowe zawieszonego tenanta. Odczyt administracyjny oraz
eksport pozostają dostępne według uprawnień.

## Activate

Dozwolone jest wznowienie tylko tenanta w statusie `SUSPENDED`. Aktywacja
przywraca możliwość wykonywania komend, ale nie zmienia użytkowników, pakietów
ani ich danych.

## Close

Close jest trwały. Przed zamknięciem operator uzgadnia eksport i retencję.
Serwis zmienia status oraz `closedAt`, nie usuwa danych modułów. Fizyczne
usuwanie wymaga odrębnej procedury zatwierdzonej przez politykę retencji.

Każda operacja jest autoryzowana przez Identity i zapisywana w Audit po
wdrożeniu tego modułu.
