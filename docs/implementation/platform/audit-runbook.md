# Audit — runbook i procedura odtworzenia

## Własność i kontrakty

Moduł audit jest właścicielem tabeli audit_entries. Pozostałe moduły zapisują
wpisy wyłącznie przez com.domanski.smsmodular.audit.api.contract.AuditPort.
ActorRef, AuditCommand i AuditPort nie przenoszą encji JPA między modułami.

Wpis dla operacji tenantowej ma tenant_id i jest widoczny po ustawieniu
transakcyjnego app.tenant_id. Platformowa zmiana konkretnego tenanta również
zachowuje jego tenant_id, ale aktorem jest PLATFORM; dzięki temu endpoint
platformowy zawsze wymaga jawnego filtra tenanta. Tylko jawnie oznaczona,
globalna operacja platformowa może użyć tenant_id = NULL.

## Izolacja i operacje platformowe

Tabela wymusza FORCE ROW LEVEL SECURITY. Polityka odczytu sprawdza wyłącznie
tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid, więc
ustawienie app.audit_platform = true nie daje globalnego odczytu. Ten GUC jest
używany tylko przez kontrolowaną transakcję inserta globalnego wpisu
platformowego, a zapytania platformowe ustawiają konkretny app.tenant_id.

Każda ścieżka runtime musi używać set_config(..., true) w transakcji. Nie
ustawiać app.tenant_id przez zwykłe SET, nie nadawać roli runtime BYPASSRLS i
nie wykonywać odczytu platformowego bez filtra tenantId.

## Append-only i retencja

audit_entries odbiera PUBLIC uprawnienia UPDATE i DELETE, a RLS tworzy wyłącznie
polityki SELECT i INSERT. Retencja
nie może usuwać wpisów bez osobnej, zatwierdzonej ścieżki właściciela bazy i
audytowania jej wykonania. W pierwszej wersji nie ma endpointu modyfikującego
ani usuwającego wpisów.

Metadata jest ograniczone allow-listą (source, reason, status, previousStatus,
newStatus, changedFields, operation), długością i filtrami treści. Nie
przechowuje się treści SMS, payloadów webhooka, promptów, tokenów, haseł,
numerów telefonów ani e-maili.

## Migracja i rollback

Changelog db/changelog/audit/db.changelog-audit.yaml tworzy tabelę, indeksy,
proste RLS i constraint typu JSON metadata. Ma rollback usuwający te obiekty
w odwrotnej kolejności.

Procedura:

1. Wstrzymać deploy korzystający z nowych wpisów i sprawdzić brak aktywnej migracji Liquibase.
2. Wykonać kopię audit_entries zgodnie z polityką retencji/backupów.
3. Uruchomić rollback Liquibase dla ostatniego changesetu Audit wyłącznie na kontrolowanej ścieżce właściciela migracji.
4. Zweryfikować brak tabeli, indeksów i polityki w katalogu PostgreSQL.
5. Odtworzyć changelog z backupu lub ponownie zastosować migrację; po odtworzeniu uruchomić test RLS i append-only przed przywróceniem ruchu.

Rollback nie jest operacją biznesową i nie może być wykonywany przez rolę
sms_modular_runtime. W razie nieudanego rollbacku pozostawić moduł w trybie
tylko do odczytu i eskalować do operatora bazy — nie przyznawać aplikacji
uprawnień modyfikacji ani kasowania.

## Diagnostyka

- 403 na /api/v1/audit-logs oznacza brak AUDIT_READ; backend jest źródłem prawdy, a frontend tylko odzwierciedla permission.
- 403 na /api/platform/v1/audit-logs wymaga PLATFORM_AUDIT_READ oraz parametru tenantId.
- Pusty wynik przy poprawnym permission zwykle oznacza inny tenant context lub filtr czasu; nie należy próbować ustawiać globalnego GUC w sesji puli.
- Błąd uprawnień przy zmianie jest oczekiwany: wpisy są niezmienne.
