# DECISIONS

## 1. Test skilla na encji Customer.

- **Błąd AI w code review:** Po wygenerowaniu klientów skillem zleciłem przegląd kodu względem `CLAUDE.md` i `INTENT.md`. Okazało się, że domyślne szablony skilla nadpisały `INTENT.md` — agent wymyślił ostrzejszy regex na telefon i rozrzucił normalizację e-maila między mapper a serwis.
- **Poprawka:** Zaktualizowałem `SKILL.md`, narzucając hierarchię `kontrakt API > INTENT.md > opis > domyślne wartości skilla` oraz normalizację danych wyłącznie w `*Request*`.

## 2. Dokumenty pojazdu (`Document`) — granica skilla i reguły domenowe

- **Podział pracy:** Skill `feature-slice` wygenerował wyłącznie szkielet zgodny z modułem pojazdów (`V6`/`V7`, CRUD, `ClockConfig`, interfejs TS nazwany `VehicleDocument` zamiast `Document`, by uniknąć kolizji z DOM API) i celowo zostawił `// TODO` przy zmianie statusów.
- **Przejścia statusów:** Zgodnie z decyzją Anny (odrzucony dokument zostaje w historii jako odrzucony) dodałem w `DocumentService` mapę `ALLOWED_TRANSITIONS` (`EnumMap`) pozwalającą wyłącznie na `PENDING → VERIFIED | REJECTED` (pozostałe zwracają `409`, po wcześniejszym `404` dla braku pojazdu/dokumentu).
- **Weryfikacja:** Każda zmiana sprawdzona przez `./test.sh` oraz ręczne przeklikanie UI i wywołania API.