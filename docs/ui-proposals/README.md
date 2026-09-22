# SMS Modular — propozycje interfejsu użytkownika

Makiety przedstawiają jeden spójny kierunek wizualny aplikacji Angular/PrimeNG
dla desktopu i urządzeń mobilnych. Są materiałem projektowym, a nie specyfikacją
piksel w piksel. Katalog obejmuje 20 głównych obszarów i stanów produktu.

## Kompletny katalog ekranów

Pierwsze cztery obszary mają osobne pliki dla obu breakpointów. Pozostałe
plansze zestawiają desktop i mobile obok siebie, aby ułatwić implementację oraz
porównanie priorytetów informacji.

| # | Obszar / ekran | Desktop | Mobile | Najważniejszy zakres |
| --- | --- | --- | --- | --- |
| 1 | Uwierzytelnianie | [desktop + mobile](screen-catalog/05-auth-desktop-mobile.png) | ta sama plansza | logowanie i wymuszona zmiana hasła |
| 2 | Pulpit | [desktop](01-dashboard.png) | [mobile](mobile/01-dashboard-mobile.png) | KPI, alerty i szybkie akcje |
| 3 | Lista pracowników | [desktop + mobile](screen-catalog/06-employees-list-desktop-mobile.png) | ta sama plansza | wyszukiwanie, filtry, dodawanie i edycja |
| 4 | Karta pracownika | [desktop](02-employee-detail.png) | [mobile](mobile/02-employee-detail-mobile.png) | profil, czas, nieobecności, projekty i narzędzia |
| 5 | Rejestracja czasu | [desktop + mobile](screen-catalog/07-time-tracking-desktop-mobile.png) | ta sama plansza | miesiąc, wpisy dzienne i szybkie dodawanie |
| 6 | Nieobecności | [desktop + mobile](screen-catalog/08-absences-desktop-mobile.png) | ta sama plansza | lista, kalendarz i formularz zdarzenia |
| 7 | Urlopy | [desktop + mobile](screen-catalog/09-leave-management-desktop-mobile.png) | ta sama plansza | wnioski, limity i akceptacja |
| 8 | Weryfikacja SMS | [desktop](03-sms-review.png) | [mobile](mobile/03-sms-review-mobile.png) | kolejka, szczegóły i decyzja |
| 9 | Lista projektów | [desktop + mobile](screen-catalog/10-projects-list-desktop-mobile.png) | ta sama plansza | filtrowanie, statusy i utworzenie projektu |
| 10 | Szczegóły projektu | [desktop + mobile](screen-catalog/11-project-detail-desktop-mobile.png) | ta sama plansza | zespół, postęp, czas i wiadomości |
| 11 | Planowanie | [desktop](04-planning.png) | [mobile](mobile/04-planning-mobile.png) | tygodniowa macierz oraz mobilny plan dnia |
| 12 | Rozliczenia | [desktop + mobile](screen-catalog/12-payroll-desktop-mobile.png) | ta sama plansza | podsumowanie miesiąca, zamknięcie i eksport |
| 13 | Ustawienia rozliczeń | [desktop + mobile](screen-catalog/13-payroll-settings-desktop-mobile.png) | ta sama plansza | stawki, reguły i okresy rozliczeniowe |
| 14 | Narzędzia | [desktop + mobile](screen-catalog/14-tools-desktop-mobile.png) | ta sama plansza | katalog narzędzi, przypisania i historia |
| 15 | Raporty | [desktop + mobile](screen-catalog/15-reports-desktop-mobile.png) | ta sama plansza | katalog, parametry i generowanie raportu |
| 16 | Użytkownicy i role | [desktop + mobile](screen-catalog/16-users-desktop-mobile.png) | ta sama plansza | zaproszenia, role, uprawnienia i blokada |
| 17 | Audyt | [desktop + mobile](screen-catalog/17-audit-desktop-mobile.png) | ta sama plansza | filtrowanie zdarzeń i podgląd zmian |
| 18 | Ustawienia organizacji | [desktop + mobile](screen-catalog/18-tenant-settings-desktop-mobile.png) | ta sama plansza | dane firmy, lokalizacja, branding i integracje |
| 19 | Pakiet i moduły | [desktop + mobile](screen-catalog/19-subscription-modules-desktop-mobile.png) | ta sama plansza | aktywne możliwości, limity i zależności modułów |
| 20 | Operator platformy | [desktop + mobile](screen-catalog/20-platform-tenants-desktop-mobile.png) | ta sama plansza | lista tenantów, statusy i zarządzanie pakietem |

Formularze w panelach bocznych, bottom sheetach i modalach są stanami ekranu
nadrzędnego, a nie dodatkowymi poziomami nawigacji. Dzięki temu rozpoczęcie
każdej kluczowej operacji mieści się w limicie dwóch kliknięć lub dotknięć.

## Zasada maksymalnie dwóch kliknięć

- Główne obszary są stale widoczne w bocznej nawigacji; bez podmenu.
- Globalne szybkie akcje są na pulpicie nad pierwszym załamaniem ekranu.
- Akcje kontekstowe są obok tytułu rekordu lub zaznaczonego elementu.
- Szczegóły i edycja korzystają z zakładek albo panelu bocznego, nie z łańcucha
  osobnych ekranów.
- Po zakończeniu operacji masowej system przechodzi do następnego elementu, np.
  kolejnego SMS-a do weryfikacji.

| Cel | Maksymalna ścieżka |
| --- | --- |
| utworzenie pracownika | `Dodaj pracownika` |
| dodanie czasu | `Dodaj czas pracy` → zapis formularza |
| zgłoszenie nieobecności | `Zgłoś nieobecność` → zapis formularza |
| rozstrzygnięcie SMS | `Przejrzyj SMS` → decyzja |
| przejście do danych pracownika | `Pracownicy` → wybór wiersza |
| edycja dnia pracy pracownika | wybór dnia w kalendarzu → zapis panelu |
| utworzenie przydziału | `Planowanie` → plus w komórce lub `Dodaj przydział` |
| publikacja planu | `Planowanie` → `Opublikuj plan` |

## Wspólny system wizualny

- jasne powierzchnie, granatowa nawigacja i turkusowa akcja główna;
- bursztynowy wyłącznie dla elementów wymagających uwagi, czerwony dla błędu lub
  operacji destrukcyjnej;
- czytelna typografia bez tekstu mniejszego niż praktyczne minimum aplikacji;
- jedna dominująca akcja główna na kontekst;
- tabele i kalendarze zamiast nadmiernej liczby kart;
- status pokazywany jednocześnie kolorem, ikoną i tekstem;
- brak hamburgera na desktopie, wielopoziomowych menu, pełnoekranowych kreatorów,
  glassmorphismu i dekoracyjnych gradientów.

## Uwagi implementacyjne

- Pozycje dodatków (`Projekty`, `Planowanie`, `Narzędzia`, `Raporty`) są
  renderowane na podstawie capability z `/api/v1/me/context`.
- Widoczność przycisku zależy również od permission użytkownika; backend zawsze
  ponownie weryfikuje dostęp.
- Nawigacja powinna umożliwiać zwinięcie etykiet dopiero na mniejszym desktopie,
  zachowując tooltipy i pełną obsługę klawiatury.
- Panele boczne nie mogą zasłaniać informacji potrzebnych do podjęcia decyzji.
- Wszystkie akcje mają stan loading, blokadę podwójnego wysłania i czytelny
  Problem Details po błędzie.

## Etap drugi — implementacja mobilna

- Zachować te same endpointy, DTO, permissions i capabilities co na desktopie;
  mobile jest inną prezentacją, nie osobnym produktem ani API.
- Przy szerokości mobilnej zastąpić boczną nawigację stałym paskiem:
  `Pulpit`, `Pracownicy`, `SMS`, `Plan`, `Więcej`.
- `Więcej` otwiera jeden bottom sheet z: Czas pracy, Nieobecności, Projekty,
  Raporty, Narzędzia i Ustawienia. Każdy obszar pozostaje osiągalny w dwóch
  dotknięciach.
- Tabele desktopowe zamieniać na listy kart/wierszy; nie ukrywać kluczowych kolumn
  bez udostępnienia ich w szczegółach elementu.
- Formularze i szczegóły otwierać jako pełny ekran lub bottom sheet zależnie od
  ilości danych. Akcja główna pozostaje przyklejona nad dolną nawigacją.
- Planowanie na mobile działa w widoku jednego dnia z poziomym paskiem dat;
  tygodniowa macierz pozostaje wariantem desktopowym/tabletowym.
- Weryfikacja SMS pokazuje jedną wiadomość i przejście poprzednia/następna;
  rozstrzygnięcie automatycznie ładuje kolejną pozycję.
- Minimalny obszar dotyku to 44×44 px, pasek dolny respektuje safe area, a pola
  formularzy nie mogą być zasłaniane przez klawiaturę ekranową.
- Testy responsywne objąć szerokościami telefonu, orientacją poziomą, powiększeniem
  tekstu, klawiaturą oraz obsługą czytnika ekranu.
