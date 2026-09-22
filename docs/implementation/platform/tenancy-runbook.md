# Runbook cyklu życia tenanta

Tenancy udostępnia operacje platformowe wymagające granularnych uprawnień
`PLATFORM_TENANT_SUSPEND`, `PLATFORM_TENANT_ACTIVATE` i
`PLATFORM_TENANT_CLOSE`. Administrator platformy jest osobnym principalem;
bootstrap jego konta i logowanie realizuje dopiero Identity & Access.

## Suspend

1. Zweryfikuj tenant ID i powód operacji w systemie operatorskim.
2. Wywołaj `POST /api/platform/v1/tenants/{tenantId}/suspend`.
3. Potwierdź odpowiedź `status=SUSPENDED` oraz zachowanie danych.
4. Sprawdź, że komendy tenantowe otrzymują problem `TENANT_SUSPENDED`, a
   administracyjny `GET /api/v1/tenant` nadal działa dla zweryfikowanego
   principala tenanta.

Suspend nie usuwa danych i może zostać odwrócony przez `activate`.

## Activate

`POST /api/platform/v1/tenants/{tenantId}/activate` działa wyłącznie dla
`SUSPENDED`. Próba aktywacji `CLOSED` jest odrzucana; nie ma ścieżki ponownego
otwarcia.

## Close

1. Poza tym modułem uzgodnij i wykonaj wymagany eksport danych oraz decyzję o
   retencji. Tenancy nie udaje ukończenia eksportu ani audytu.
2. Wywołaj `POST /api/platform/v1/tenants/{tenantId}/close` z uprawnieniem
   `PLATFORM_TENANT_CLOSE`.
3. Zapisz odpowiedź zawierającą `status=CLOSED` i niepuste `closedAt`.
4. Zablokuj dalsze komendy domenowe i monitoruj odrzucone operacje.

Close nie jest `DELETE`, nie kasuje rekordów i jest nieodwracalne. Fizyczne
usuwanie, eksport/audyt i retencja zostają w odpowiednich późniejszych falach.
