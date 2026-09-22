# Moduł AI Interpretation

## Cel, zakres i analiza SMS2

Interpretować wyłącznie SMS-y, których parser regułowy nie rozstrzygnął, i
zwracać ustrukturyzowaną propozycję. Źródła:
`../sms2/src/main/java/com/domanski/sms/sms/application/ai`,
`SmsAutomaticParsingService`, `SmsInterpretationStateService` oraz ustawienia
`app.sms.ai`/Spring AI w `application.yaml`.

Moduł nie zapisuje czasu ani nieobecności, nie decyduje o auto-apply i nie
przechowuje klucza dostawcy per tenant w pierwszej wersji.

## Kontrakt i reguły

- `InterpretSmsCommand(tenantId, messageId, redactedContent, timezone,
  allowedIntentTypes, correlationId)`.
- `InterpretationResult(intentType, structuredPayload, confidence, model,
  promptVersion, usage, warnings)` albo kontrolowany unavailable/invalid result.
- Schema output jest wersjonowana i walidowana; tekst modelu nigdy nie jest
  wykonywany jako polecenie ani SQL.
- Progi auto-apply są konfiguracją platformy/modułu, ale finalną decyzję podejmuje
  SMS Inbound po walidacji domenowej.
- Po braku limitu, timeout, 429, 5xx lub invalid schema zwrócić fallback do review.

## Dane, prywatność i użycie

- Przechowywać wynik, model, prompt version, token/usage, latency i outcome; pełny
  prompt/response tylko jeśli polityka prywatności jawnie na to pozwala.
- Redukować dane do minimum, maskować zbędne PII i stosować platformowy sekret.
- `UsageMeter(AI_*)` rezerwuje/nalicza użycie idempotentnie po message ID + attempt.
- Metryki: latency, provider errors, schema invalid, confidence distribution,
  auto-apply/review i koszt jednostkowy.

## API i frontend

Brak publicznego endpointu dowolnego promptu. `AiInterpretationService` udostępnia
wewnętrzną metodę przyjmującą DTO. Interfejs dostawcy AI jest używany wyłącznie
do integracji z zewnętrzną usługą. Administrator SMS widzi w szczegółach źródło, confidence, model i
powód review; nie widzi sekretów ani chain-of-thought.

## Etapy

1. Kontrakt, wersjonowany schema i fake provider do testów.
2. Adapter Spring AI z timeoutem, retry kontrolowanym przez runtime i redakcją.
3. Usage, wynik audytowalny i metryki kosztu.
4. Integracja po parserach regułowych oraz walidacja komendy docelowej.
5. Panel szczegółów/review i narzędzia porównania prompt version.

## Migracja i testy

Historyczne pola `interpretation_source`, confidence i `ai_model` mapować do
wyniku bez odtwarzania promptu. Testy: contract/schema, prompt injection,
timeout/429/invalid JSON, limit, timezone, niski confidence, deterministic fake,
brak PII w logach oraz brak bezpośredniego dostępu do domen docelowych.

## Zależności i ukończenie

Wymaga SMS Inbound, Usage i Integration Runtime. Udostępnia metodę
`AiInterpretationService.interpret(...)`. Gotowe, gdy awaria dostawcy zawsze prowadzi do review i nie
blokuje trwałego przyjęcia wiadomości.
