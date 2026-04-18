# Sprint 04 — Availability

## 1. Obiettivo dello sprint
Questo sprint ha lo scopo di introdurre il primo modulo operativo reale successivo a profilo, relazioni e lettura dati: la gestione delle disponibilità del professionista.

Alla fine dello sprint il backend deve permettere di:

- creare slot di disponibilità validi
- leggere gli slot del professionista autenticato
- leggere gli slot disponibili di un professionista
- aggiornare uno slot esistente
- bloccare o sbloccare uno slot quando necessario
- applicare le regole base di coerenza temporale e di autorizzazione

---

## 2. Perché questo sprint è importante
Questo sprint è importante perché apre la prima area davvero operativa del progetto.

Fino a questo punto il sistema sa:
- registrare e autenticare utenti
- verificare i professionisti
- generare e validare inviti
- registrare clienti
- creare collegamenti professionista-cliente
- leggere e aggiornare il profilo base
- navigare le relazioni già esistenti

Con questo sprint il sistema inizia finalmente a gestire:
- la disponibilità prenotabile del professionista
- una base concreta per le future richieste di prenotazione
- le prime regole business temporali reali

---

## 3. Risultato atteso
Al termine di questo sprint devono essere testabili questi flussi:

### Lato professionista autenticato
- creare una nuova disponibilità
- leggere le proprie disponibilità
- aggiornare una propria disponibilità
- bloccare una propria disponibilità
- sbloccare una propria disponibilità

### Lato utente/cliente
- leggere le disponibilità disponibili di un professionista

### Lato business
- impedire slot con intervallo non valido
- impedire sovrapposizioni per lo stesso professionista
- limitare il modulo ai soli professionisti con specializzazione `PERSONAL_TRAINER`

---

## 4. Stato di partenza reale
All’inizio di questo sprint il progetto dispone già di:

- autenticazione JWT funzionante
- verifica email professionista
- registrazione cliente con invite code
- collegamento attivo professionista-cliente
- area `/api/v1/me` funzionante
- area `/api/v1/clients` read funzionante
- area `/api/v1/professionals` read funzionante
- area `/api/v1/invites` funzionante

Non risultano invece ancora implementati nel codice attuale:

- entity `AvailabilitySlot`
- repository availability
- service availability
- controller availability
- DTO request/response availability
- logiche booking

---

## 5. Fuori scope di questo sprint
In questo sprint non si implementano ancora:

- bookings
- `BookingRequest`
- `BookingRequestItem`
- conferma/rifiuto richieste
- workout plans
- nutrition plans
- feedback
- measurements
- notifiche
- dashboard avanzate
- link management come modulo API dedicato

---

## 6. Moduli coinvolti
In questo sprint si lavora soprattutto su:

- `availability`
- `professional`

Con supporto di:
- `security`
- `common`
- `auth`

E con dipendenza dalla parte già esistente di:
- `ProfessionalProfile`
- `ProfessionalClientLink`
- `ProfessionalSpecialization`

---

## 7. Obiettivo funzionale della parte
Questa parte del progetto serve a costruire l’agenda di base del professionista, separando in modo chiaro:

- la definizione della disponibilità
- la futura prenotazione degli slot

### In pratica deve permettere di:
- far dichiarare al personal trainer quando è disponibile
- esporre slot leggibili in modo coerente
- applicare regole minime ma reali sugli intervalli temporali
- preparare il terreno per il modulo bookings senza mescolare responsabilità

### Riassunto semplice
Lo Sprint 04 non introduce ancora le prenotazioni, ma costruisce il livello necessario per dire quando il professionista è disponibile e quali slot sono utilizzabili.

---

## 8. Regole business principali dello sprint

### 8.1 Ambito del modulo
Le disponibilità sono previste solo per professionisti con specializzazione:
- `PERSONAL_TRAINER`

### 8.2 Ownership dello slot
Ogni slot appartiene a:
- un solo professionista

Il professionista autenticato può gestire:
- solo i propri slot

### 8.3 Regola temporale
Ogni slot deve avere:
- `startDateTime < endDateTime`

### 8.4 Regola di non sovrapposizione
Per lo stesso professionista non devono esistere slot attivi sovrapposti nello stesso intervallo temporale.

### 8.5 Stato dello slot
Uno slot può trovarsi in uno dei seguenti stati:
- `AVAILABLE`
- `BOOKED`
- `BLOCKED`

### 8.6 Separazione da bookings
Il modulo availability non deve ancora:
- creare richieste di prenotazione
- confermare prenotazioni
- rifiutare prenotazioni
- gestire storico richieste

### 8.7 Lettura disponibilità di un professionista
La lettura delle disponibilità di un professionista deve esporre solo gli slot coerenti con la finalità del modulo, quindi in prima battuta quelli effettivamente consultabili come disponibilità.

Il dettaglio finale dei filtri e delle regole di visibilità andrà fissato durante l’implementazione dello sprint.

---

## 9. Endpoint obiettivo dello sprint
Gli endpoint pianificati coerenti con questo sprint sono:

- **POST** `/api/v1/availability`
- **GET** `/api/v1/availability/my`
- **GET** `/api/v1/professionals/{professionalId}/availability`
- **PATCH** `/api/v1/availability/{slotId}`
- **PATCH** `/api/v1/availability/{slotId}/block`
- **PATCH** `/api/v1/availability/{slotId}/unblock`

### Query param ipotizzati da confermare nello sprint
- `status=AVAILABLE|BOOKED|BLOCKED`
- `from=...`
- `to=...`
- `active=true|false`

Nota: il contratto definitivo dei filtri va confermato prima di fissarlo come API finale.

---

## 10. Entità e componenti attesi
In coerenza con la documentazione attuale, questo sprint porterà con alta probabilità all’introduzione di:

### Dominio
- `AvailabilitySlot`
- `AvailabilitySlotStatus`

### Persistence
- repository dedicato per availability

### API layer
- request DTO per creazione slot
- request DTO per aggiornamento slot
- response DTO per lettura slot
- controller dedicato
- service dedicato

Nota: i nomi finali dei DTO e delle classi applicative vanno confermati quando si dividerà lo sprint in blocchi operativi.

---

## 11. Validazioni minime attese
Durante questo sprint vanno coperte almeno queste validazioni:

- professionista autenticato valido
- ruolo corretto
- specializzazione corretta (`PERSONAL_TRAINER`)
- intervallo temporale valido
- assenza di sovrapposizioni per lo stesso professionista
- slot esistente nei casi di update/block/unblock
- ownership corretta dello slot in update/block/unblock

---

## 12. Eccezioni applicative attese
Durante questo sprint devono essere gestiti in modo chiaro almeno questi casi:

- professionista non trovato
- utente autenticato non valido
- ruolo non autorizzato
- specializzazione non autorizzata per il modulo availability
- slot non trovato
- slot non appartenente al professionista autenticato
- intervallo temporale non valido
- sovrapposizione con slot già esistente
- richiesta non valida

---

## 13. Cose da NON fare in questo sprint
Per non complicare inutilmente questa fase, evitare di:

- iniziare bookings nello stesso sprint operativo
- creare subito flussi multi-slot
- mescolare la disponibilità con logiche di conferma/rifiuto richieste
- introdurre dashboard o reportistica
- introdurre logiche admin
- anticipare moduli workout, nutrition, feedback o measurements
- definire filtri API più complessi del necessario prima di chiudere il flusso base

---

## 14. Output finale atteso dello sprint
Alla fine di questo sprint il progetto deve avere:

- un primo modulo `availability` reale e testabile
- regole base corrette sugli slot temporali
- controllo ownership del professionista sugli slot
- lettura delle disponibilità coerente con il dominio
- una base pronta per aprire il futuro Sprint 05 dedicato ai bookings

---

## 15. Prossimo step dopo questo sprint
Una volta completato questo sprint, il passo successivo naturale sarà introdurre il modulo bookings, usando availability come base già affidabile per il flusso cliente → richiesta → risposta del professionista.
