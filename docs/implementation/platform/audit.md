# Moduł Audit

## Cel, zakres i analiza SMS2

Zapewnić niezmienny, tenant-scoped ślad operacji użytkownika, systemu i
administratora platformy. Zachować użyteczne idee z
`../sms2/src/main/java/com/domanski/sms/audit`, lecz zastąpić zamknięte enumy
`AuditAction`/`AuditEntityType` stabilnymi kodami modułowymi i dodać tenant,
correlation ID oraz wynik operacji. Migracje źródłowe: `0008`–`0010`.

Audit nie jest logiem technicznym ani magazynem pełnych payloadów.

## Model i kontrakt

- `AuditEntry(id, tenantId?, actorType, actorId?, module, action, subjectType,
  subjectId?, outcome, occurredAt, correlationId, metadata)`.
- `tenantId` jest wymagany dla operacji klienta i pusty tylko dla jawnej operacji
  platformowej; metadata ma allow-listę i nie zawiera sekretów ani treści SMS.
- Publiczne `ActorRef` oraz `AuditPort.record(AuditCommand)`; błąd audytu dla
  krytycznej komendy wycofuje transakcję albo zapisuje się w tym samym outboxie.
- Wpisów nie aktualizuje się i nie usuwa przez API.

## API, frontend i bezpieczeństwo

- `GET /api/v1/audit-logs` z filtrami czasu, aktora, modułu, akcji i subject;
  permission `AUDIT_READ`.
- Platformowy odpowiednik wymaga `PLATFORM_AUDIT_READ` i jawnego tenant filter.
- Angular: tabela z paginacją serwerową i szczegóły bez surowego JSON sekretów.
- Retencja jest zadaniem platformowym i zapisuje osobny wpis o wykonaniu.

## Etapy

1. Schemat, append-only repository i RLS.
2. `ActorRef`, integracja z correlation context oraz `AuditPort`.
3. Rejestracja zmian tenanta i identity jako pierwszych konsumentów.
4. Query API, filtry, indeksy `(tenant_id, occurred_at)` i subject.
5. Widok Angular oraz eksport administracyjny, jeśli zostanie wymagany prawnie.

## Migracja, testy i zależności

Stare wpisy otrzymują tenant importu; nierozpoznany aktor staje się
`LEGACY_SYSTEM`, bez wymyślania użytkownika. Testować append-only, maskowanie,
platform/tenant scope, sortowanie, zapis w rollbackowanej transakcji i retencję.

Wymaga Fundamentu i Tenancy. Udostępnia kontrakt wszystkim modułom. Ukończone,
gdy każda komenda zmieniająca stan może zapisać spójny wpis bez importu encji Audit.

Runbook RLS, append-only, migracji i odtworzenia znajduje się w
[audit-runbook.md](audit-runbook.md).
