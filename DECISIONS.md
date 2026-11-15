# DECISIONS

Gdzie AI się pomyliło, co zostało zrobione ręcznie i dlaczego.

## Dokumenty pojazdu: przejścia statusów (ręcznie)

- **Co wygenerował skill:** `feature-slice` stworzył endpoint `PUT .../documents/{id}/status`,
  `DocumentStatusRequest`, test kontrolera na 409 i w `DocumentService.changeStatus` zostawił
  `TODO` — status był ustawiany bez żadnej walidacji. To celowe: reguły biznesowe spoza wzorca
  modułu pojazdów piszemy sami (BRIEF, pkt 5).
- **Co zrobione ręcznie:** w `DocumentService` stała `ALLOWED_TRANSITIONS` (`EnumMap`) z jedynym
  dozwolonym przejściem `PENDING → VERIFIED | REJECTED` (INTENT A7, A8, Z3). Każde inne przejście,
  w tym ustawienie tego samego statusu, rzuca `ConflictException` (409) z komunikatem
  `Document with id N cannot change status from X to Y`. Najpierw 404 (pojazd/dokument), potem 409.
- **Dlaczego mapa w serwisie, a nie metoda na enumie:** CLAUDE.md trzyma reguły biznesowe w serwisach;
  otwarte pytania do klienta (1 i 2) mogą dodać `REJECTED → VERIFIED` lub `VERIFIED → REJECTED` —
  wtedy zmienia się jedna linijka.
- **Testy:** `DocumentServiceTest` sprawdza wszystkie 9 kombinacji (sparametryzowane: 2 dozwolone,
  7 → 409 bez zapisu), plus nazwane testy przypadków z INTENT (odrzucony → zweryfikowany,
  zweryfikowany → odrzucony, ten sam status).
- **Frontend bez zmian:** przyciski Verify/Reject są pokazywane tylko dla `PENDING`, a ewentualne 409
  (np. dwie otwarte karty) wyświetla istniejąca obsługa ProblemDetail — pokryte w
  `document-section.spec.ts`.
- **Świadomie pominięte:** blokada optymistyczna (`@Version`) przy równoczesnej zmianie statusu.
