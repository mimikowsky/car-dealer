# INTENT: dokumenty pojazdów i gotowość do wydania, lista klientów

Źródło: transkrypcja spotkania z salonem Auto Mazur (`BRIEF.md`) oraz kontrakt API z briefu.
Kontrakt API (ścieżki, nazwy typów, statusów i `reason`, kody 409) jest stały i ma pierwszeństwo przed
założeniami poniżej.

## Cel

Salon chce zastąpić papierową checklistę sprawdzaniem w systemie, czy auto ma komplet poprawnych
dokumentów przed wydaniem klientowi. Problem do rozwiązania: auto wydane (lub prawie wydane) bez
wymaganego dokumentu, np. bez COC, bo ktoś to przeoczył.

Użytkownicy: Anna (administracja sprzedaży) i Tomek (doradca handlowy). Obie osoby dodają i weryfikują
dokumenty.

## Wymagania

### A. Dokumenty pojazdu

- **A1.** Pojazd ma listę dokumentów. Typy (`type`): `SALES_AGREEMENT` (umowa sprzedaży),
  `INVOICE` (faktura), `COC` (świadectwo zgodności), `OC_POLICY` (polisa OC),
  `REGISTRATION_POA` (pełnomocnictwo do rejestracji).
- **A2.** Dokument ma: `type`, `number`, `issueDate`, opcjonalnie `expiryDate`, oraz `status`.
- **A3.** Nowy dokument ma zawsze status `PENDING` (czeka na weryfikację). Klient nie podaje statusu
  przy dodawaniu.
- **A4.** Statusy (`status`): `PENDING`, `VERIFIED`, `REJECTED`. Weryfikacja kończy się
  zweryfikowaniem albo odrzuceniem (np. zły VIN na fakturze).
- **A5.** `issueDate` nie może być z przyszłości (błąd walidacji, 400).
- **A6.** Na pojazd może być tylko jeden **aktywny** dokument danego typu. Aktywny = nieodrzucony
  (`PENDING` lub `VERIFIED`). Odrzucone zostają w historii i nie blokują dodania nowego.
  Dodanie dokumentu typu, który ma już aktywny dokument, zwraca **409**.
- **A7.** Odrzucony dokument zostaje odrzucony. Poprawka od klienta to nowy dokument, a nie zmiana
  statusu starego (decyzja Anny, obowiązuje do czasu ewentualnej innej decyzji dyrektora).
  Zmiana `REJECTED` → cokolwiek zwraca **409**.
- **A8.** Niedozwolona zmiana statusu zwraca **409**. Dozwolone przejścia: patrz założenie Z3.
- **A9.** API (stałe):
  - `GET /api/vehicles/{vehicleId}/documents`: lista dokumentów pojazdu (także odrzuconych).
  - `POST /api/vehicles/{vehicleId}/documents`: dodanie dokumentu.
  - `PUT /api/vehicles/{vehicleId}/documents/{documentId}/status`: zmiana statusu, body
    `{"status": "VERIFIED"}`.

### B. Rejestracja przez salon

- **B1.** Przy pojeździe można zaznaczyć, że salon rejestruje auto za klienta (zaznacza Tomek po
  ustaleniu z klientem).
- **B2.** Domyślnie klient rejestruje sam (flaga wyłączona).
- **B3.** `REGISTRATION_POA` jest wymagane do wydania tylko wtedy, gdy flaga jest włączona.

### C. Gotowość do wydania

- **C1.** `GET /api/vehicles/{vehicleId}/handover-readiness` zwraca `ready`,
  `missingDocumentTypes` i `problems` (format z briefu).
- **C2.** Wymagane typy: `SALES_AGREEMENT`, `INVOICE`, `COC`, `OC_POLICY`, a dodatkowo
  `REGISTRATION_POA`, gdy salon rejestruje auto (B3).
- **C3.** `missingDocumentTypes`: wymagane typy, dla których nie ma aktywnego dokumentu. Odrzucony
  dokument liczy się jak brak dokumentu.
- **C4.** `problems` z `reason`:
  - `NOT_VERIFIED`: aktywny dokument wymaganego typu ma status `PENDING`.
  - `EXPIRED`: aktywny dokument wymaganego typu ma `expiryDate` wcześniejszą niż data wydania.
    Dokument ważny do dnia wydania włącznie jest w porządku (`expiryDate == plannedHandoverDate`
    → OK).
  - `NO_HANDOVER_DATE`: pojazd nie ma ustalonej daty wydania (`plannedHandoverDate` puste),
    `documentType: null`.
- **C5.** `ready == true` wtedy i tylko wtedy, gdy `missingDocumentTypes` i `problems` są puste.
  Bez daty wydania auto nigdy nie jest gotowe.
- **C6.** Na ekranie pojazdu Tomek od razu widzi: „gotowe do wydania” albo czego brakuje / co jest
  nie tak.

### D. Klienci

- **D1.** Prosta lista klientów, CRUD pod `/api/customers` według konwencji modułu pojazdów
  (lista, szczegóły, dodanie, edycja, usunięcie; backend i frontend).
- **D2.** Pola: imię, nazwisko, e-mail, telefon, NIP (dla firm, opcjonalny).
- **D3.** E-mail jest unikalny. Duplikat przy dodaniu lub edycji zwraca **409** (jak VIN w pojazdach).
- **D4.** Bez powiązań klientów z pojazdami na tym etapie.

## Założenia (do weryfikacji)

- **Z1. Data wydania.** „Dzień wydania” to istniejące pole pojazdu `plannedHandoverDate`. Ważność
  dokumentów sprawdzamy względem tej daty, nie względem dzisiaj.
- **Z2. Brak daty wydania a `EXPIRED`.** Gdy pojazd nie ma `plannedHandoverDate`, zwracamy problem
  `NO_HANDOVER_DATE` i nie sprawdzamy `EXPIRED` (nie ma względem czego). `NOT_VERIFIED` i braki
  nadal raportujemy, żeby było widać pełny obraz.
- **Z3. Przejścia statusów.** Dozwolone tylko `PENDING → VERIFIED` i `PENDING → REJECTED`.
  Wszystko inne zwraca 409, w tym: `REJECTED → *` (A7), `VERIFIED → PENDING`,
  `VERIFIED → REJECTED` oraz ustawienie tego samego statusu (np. `PENDING → PENDING`).
  Status w body, który nie jest jedną z trzech wartości, to 400.
- **Z4. Data ważności opcjonalna dla każdego typu.** Kontrakt mówi „opcjonalnie `expiryDate`”, więc
  nie wymuszamy jej dla polisy ani pełnomocnictwa. Dokument bez `expiryDate` traktujemy jako
  bezterminowy. Nie blokujemy też podania daty ważności dla umowy czy faktury.
- **Z5. Spójność dat.** `expiryDate`, jeśli podana, nie może być wcześniejsza niż `issueDate`
  (400). Data ważności może być w przeszłości (dokument się wtedy zapisze, a gotowość pokaże `EXPIRED`).
- **Z6. Dokumenty niewymagane.** Dokument typu, który dla danego auta nie jest wymagany
  (`REGISTRATION_POA`, gdy klient rejestruje sam), nie wpływa na gotowość: nie generuje
  problemów `NOT_VERIFIED` ani `EXPIRED`.
- **Z7. Jeden dokument, kilka problemów.** Aktywny dokument może mieć jednocześnie `NOT_VERIFIED`
  i `EXPIRED`; zwracamy oba wpisy.
- **Z8. Kolejność.** `missingDocumentTypes` i `problems` w kolejności typów z kontraktu
  (`SALES_AGREEMENT`, `INVOICE`, `COC`, `OC_POLICY`, `REGISTRATION_POA`); `NO_HANDOVER_DATE`
  na początku listy problemów.
- **Z9. 404.** Nieistniejący pojazd → 404 na wszystkich endpointach dokumentów i gotowości.
  Dokument, który nie istnieje albo należy do innego pojazdu → 404.
- **Z10. Skan dokumentu.** W transkrypcji jest „wgranie skanu”, ale kontrakt API nie ma pliku.
  Przechowujemy tylko metadane dokumentu (typ, numer, daty, status), bez plików.
- **Z11. Flaga rejestracji.** Nowe pole pojazdu `registeredByDealer` (boolean, domyślnie `false`,
  migracja ustawia `false` dla istniejących aut), edytowalne w formularzu pojazdu i zwracane w
  `VehicleResponse`. Brak pola w żądaniu = `false`.
- **Z12. „Dzisiaj”** dla walidacji `issueDate` to data serwera (strefa czasowa serwera); w kodzie przez
  wstrzykiwany `Clock`, żeby dało się to testować.
- **Z13. Klienci, walidacja.** Wymagane: imię, nazwisko, e-mail, telefon. NIP opcjonalny, jeśli podany:
  dokładnie 10 cyfr (bez sprawdzania sumy kontrolnej). E-mail walidowany formatem, unikalność
  bez rozróżniania wielkości liter (zapisujemy znormalizowany do małych liter). Telefon: dowolny
  tekst do 20 znaków (cyfry, spacje, `+`, `-`), bez wymuszania formatu polskiego.
- **Z14. Usuwanie i edycja dokumentów.** Kontrakt nie przewiduje edycji ani usuwania dokumentów, więc
  ich nie robimy. Pomyłkę we wpisie poprawia się odrzuceniem i dodaniem nowego dokumentu.
- **Z15. Usunięcie pojazdu z dokumentami.** Usunięcie pojazdu usuwa też jego dokumenty (kaskadowo).
- **Z16. Uprawnienia.** Brak logowania i ról, tak jak w obecnej aplikacji; każdy użytkownik może
  dodawać i weryfikować dokumenty.

## Pytania do klienta

1. **Poprawianie odrzuconych dokumentów (Anna vs Tomek).** Czy dyrektor potwierdził, że odrzucony
   dokument zostaje odrzucony, a poprawka to nowy wpis? Na razie robimy wersję Anny (A7). Jeśli
   decyzja się zmieni, trzeba dopuścić `REJECTED → VERIFIED`.
2. Czy zweryfikowany dokument można później odrzucić (np. ktoś zauważył błąd po weryfikacji)? Na razie
   nie (Z3).
3. Czy data ważności ma być obowiązkowa dla polisy OC (i pełnomocnictwa)? Czy COC ma datę ważności?
   (Z4)
4. Czy przy dokumencie ma być przechowywany skan (plik)? Jeśli tak: jakie formaty, rozmiar, gdzie
   trzymamy pliki? (Z10)
5. Czy trzeba zapisywać, kto i kiedy zweryfikował lub odrzucił dokument, oraz powód odrzucenia
   („przy kontroli widać, co było źle”)? Na razie zapisujemy tylko status.
6. Czy `plannedHandoverDate` to właściwa „data wydania”, czy potrzebna jest osobna, potwierdzona data
   wydania? (Z1)
7. Co, jeśli auto zostało już wydane — czy blokujemy zmiany dokumentów lub oznaczamy auto jako wydane?
8. Klienci: czy dla firmy potrzebna jest też nazwa firmy? Czy NIP ma być sprawdzany sumą kontrolną?
   Czy telefon jest obowiązkowy? (Z13)
9. Czy system ma blokować samą operację wydania auta, czy wystarczy informacja o gotowości?
10. Czy numer dokumentu ma być unikalny (np. numer faktury w obrębie salonu)?

## Poza zakresem

- Powiadomienia SMS do klienta o gotowości auta (osobna integracja z bramką SMS, kolejny etap).
- Powiązanie klientów z pojazdami (później, według Anny).
- Przechowywanie skanów / plików dokumentów (Z10).
- Edycja i usuwanie dokumentów (Z14).
- Historia zmian statusów, audyt (kto/kiedy), powód odrzucenia (pytanie 5).
- Logowanie, role i uprawnienia (Z16).
- Rejestracja wydania auta i blokowanie wydania w systemie (pytania 7, 9).
