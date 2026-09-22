# Audit — operacje i odtwarzanie

Moduł Audit jest właścicielem tabeli `audit_entries`. Inne moduły wywołują
`AuditService` z tenant UUID i prostym DTO. Wpisy nie mają publicznej ścieżki
aktualizacji ani usunięcia.

Tenant API filtruje wpisy po tenant ID ze zweryfikowanego principalu. Platformowe
API wymaga platform permission oraz jawnego `tenantId` lub `scope=global`. Wpis globalnej
operacji platformowej może mieć pusty tenant ID wyłącznie wtedy, gdy operacja
nie dotyczy konkretnej firmy.

Metadata używa allow-listy i nie zawiera treści SMS, payloadów, promptów,
tokenów, haseł, numerów telefonów ani adresów email.

Weryfikacja po wdrożeniu: zalogować się jako OWNER i odczytać własne wpisy,
sprawdzić odmowę odczytu dla roli bez `AUDIT_READ`, porównać tenant A i B,
odczytać zakres globalny kontem platformy oraz potwierdzić, że wpis zmiany
powstaje razem z operacją. Dla istniejących ról OWNER sprawdzić pojedynczy
rekord `AUDIT_READ` w `role_permissions`.

## Odtworzenie

1. Zatrzymać deploy korzystający z nowej tabeli.
2. Wykonać kopię `audit_entries` zgodnie z polityką backupu.
3. Przywrócić bazę z kontrolowanego snapshotu sprzed migracji, jeśli wymagany
   jest pełny powrót; changelog nie zawiera automatycznego usuwania danych.
4. Zweryfikować tabelę i indeksy w PostgreSQL.
5. Zweryfikować filtrowanie tenantów i niezmienność wpisów przed ruchem.

Rollback wykonuje właściciel migracji, nigdy request aplikacyjny.
