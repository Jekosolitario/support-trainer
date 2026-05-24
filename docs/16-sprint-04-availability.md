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
- `startDateTime` nel futuro in fase di creazione o aggiornamento

Non è consentito creare o aggiornare slot che iniziano nel passato.

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

La lettura delle disponibilità di un professionista espone al cliente solo gli slot consultabili come disponibilità.

Regole implementate:

- il cliente deve essere autenticato;
- il cliente deve avere profilo attivo;
- il professionista deve esistere ed essere attivo;
- deve esistere un collegamento attivo tra cliente e professionista;
- vengono esposti solo gli slot disponibili secondo la logica del modulo.

---

## 9. Endpoint obiettivo dello sprint
Gli endpoint pianificati coerenti con questo sprint sono:

- **POST** `/api/v1/availability`
- **GET** `/api/v1/availability/my`
- **GET** `/api/v1/professionals/{professionalId}/availability`
- **PATCH** `/api/v1/availability/{slotId}`
- **PATCH** `/api/v1/availability/{slotId}/block`
- **PATCH** `/api/v1/availability/{slotId}/unblock`

### Query param non implementati nello Sprint 04

In questo sprint non sono stati introdotti filtri tramite query param.

Restano eventualmente valutabili in uno sprint futuro:

- `status=AVAILABLE|BOOKED|BLOCKED`
- `from=...`
- `to=...`
- `active=true|false`

La versione attuale mantiene il modulo semplice e coerente con il flusso base.

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

## 11. Validazioni implementate

Durante lo sprint e la successiva fase di stabilizzazione risultano implementate queste validazioni:

- professionista autenticato valido;
- account attivo;
- email verificata per il professionista;
- profilo professionista attivo;
- specializzazione corretta (`PERSONAL_TRAINER`);
- intervallo temporale valido;
- data iniziale dello slot nel futuro;
- assenza di sovrapposizioni per lo stesso professionista;
- slot esistente nei casi di update/block/unblock;
- ownership corretta dello slot in update/block/unblock;
- update consentito solo su slot `AVAILABLE`;
- block consentito solo su slot `AVAILABLE`;
- unblock consentito solo su slot `BLOCKED`;
- lettura cliente consentita solo verso professionisti collegati.

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

## 15. Stato finale dello sprint

Lo Sprint 04 risulta completato con il modulo `availability` funzionante e successivamente consolidato durante l’audit tecnico del backend.

### Funzionalità implementate

- creazione slot disponibilità;
- lettura degli slot del professionista autenticato;
- lettura degli slot disponibili di un professionista da parte del cliente collegato;
- aggiornamento parziale di uno slot;
- blocco manuale di uno slot;
- sblocco manuale di uno slot.

### Endpoint finali implementati

- `POST /api/v1/availability`
- `GET /api/v1/availability/my`
- `GET /api/v1/professionals/{professionalId}/availability`
- `PATCH /api/v1/availability/{slotId}`
- `PATCH /api/v1/availability/{slotId}/block`
- `PATCH /api/v1/availability/{slotId}/unblock`

### Regole implementate e verificate

- solo professionisti autenticati possono creare e gestire slot availability;
- solo professionisti `PERSONAL_TRAINER` possono creare e gestire slot availability;
- un professionista `NUTRITIONIST` non può creare slot availability;
- il professionista deve avere account attivo, email verificata e profilo attivo;
- il professionista può modificare solo i propri slot;
- il cliente può leggere availability solo di professionisti a lui collegati;
- gli slot non possono avere intervalli temporali non validi;
- gli slot creati o aggiornati devono iniziare nel futuro;
- gli slot non possono sovrapporsi ad altri slot attivi dello stesso professionista;
- l’update è consentito solo su slot `AVAILABLE`;
- `AVAILABLE -> BLOCKED` è consentito;
- `BLOCKED -> AVAILABLE` è consentito;
- slot inesistenti o non accessibili producono errore applicativo coerente;
- body vuoto in update produce `400 BAD_REQUEST`.
- la creazione e l’aggiornamento di slot availability dello stesso professionista sono protetti da lock pessimista sul profilo professionale;
- il controllo di sovrapposizione resta coerente anche in presenza di richieste concorrenti;
- uno slot con una richiesta booking `PENDING` non può essere modificato;
- uno slot con una richiesta booking `PENDING` non può essere bloccato;
- prima di modificare o bloccare uno slot con richiesta pendente, il professionista deve gestire la richiesta tramite il flusso booking previsto.
- uno slot con richiesta booking `PENDING` attiva non viene esposto al cliente come disponibilità prenotabile;
- uno slot già coinvolto in almeno una richiesta booking non può essere ripianificato modificandone data o ora;
- la regola vale anche se la richiesta precedente è stata rifiutata o cancellata;
- per proporre un nuovo intervallo temporale dopo uno storico booking, il professionista deve creare un nuovo slot.

### Protezione da operazioni concorrenti

Durante la creazione o l’aggiornamento di availability slot, il backend protegge il professionista proprietario tramite lock pessimista in scrittura.

Il lock viene applicato al `ProfessionalProfile` perché, in fase di creazione di un nuovo slot, potrebbe non esistere ancora una riga slot da bloccare.

Questa strategia garantisce che due operazioni simultanee dello stesso professionista non possano entrambe superare il controllo di sovrapposizione e salvare intervalli incompatibili.

Le operazioni di modifica e blocco di uno slot utilizzano inoltre il lock sullo slot interessato, mantenendo coerente l’interazione con il modulo Bookings.

### Integrazione con richieste booking pendenti

Uno slot in stato `AVAILABLE` può avere una richiesta booking in stato `PENDING`.

In questo caso, il professionista non può:

- modificarne data e ora;
- bloccarlo manualmente.

Questa regola impedisce che la disponibilità proposta al cliente venga alterata mentre la richiesta è ancora in attesa di risposta.

Per rendere nuovamente modificabile o bloccabile lo slot, il professionista deve prima gestire la richiesta pendente secondo il flusso previsto, ad esempio rifiutandola.

### Integrità storica degli slot già utilizzati in Booking

Uno slot può essere collegato a una richiesta booking anche quando la richiesta viene successivamente:

- rifiutata;
- cancellata.

In questi casi lo slot può restare disponibile per nuove richieste sullo stesso intervallo temporale, purché rispetti tutte le regole applicative previste.

Tuttavia, lo slot non può più essere ripianificato modificandone data o ora.

Questa regola protegge lo storico delle richieste già create: una richiesta passata deve continuare a riferirsi all’intervallo temporale originariamente selezionato dal cliente.

Per offrire una disponibilità in un nuovo giorno o orario, il professionista deve creare un nuovo slot availability.

### Stati slot gestiti

- `AVAILABLE`
- `BLOCKED`
- `BOOKED`

Lo stato `BOOKED`, inizialmente previsto per il modulo successivo, è ora utilizzato operativamente dal modulo Bookings completato nello Sprint 05.

Inoltre, uno slot con richiesta booking `PENDING` attiva non viene più restituito nella lettura availability lato cliente.

La lista delle disponibilità consultabili espone quindi solo slot realmente prenotabili:

- attivi;
- in stato `AVAILABLE`;
- futuri;
- senza richieste booking `PENDING` attive collegate.

### Test automatici aggiunti durante la stabilizzazione

Sono presenti test automatici per verificare:

- creazione slot valido da parte di un personal trainer;
- blocco della creazione slot da parte di un nutrizionista;
- blocco della creazione di slot sovrapposti;
- lettura availability da parte del cliente collegato;
- blocco della lettura da parte del cliente non collegato;
- esclusione degli slot `AVAILABLE` ormai scaduti dalla lettura cliente;
- blocco e sblocco di uno slot;
- impossibilità di aggiornare uno slot non disponibile.
- impossibilità di modificare uno slot con booking `PENDING`;
- impossibilità di bloccare uno slot con booking `PENDING`.
- esclusione dalla lettura cliente di uno slot con booking `PENDING` attivo.
- impossibilità di ripianificare uno slot già coinvolto in un booking rifiutato, a tutela dello storico della richiesta.

---

## 16. Evoluzione successiva

Dopo il completamento dello Sprint 04 è stato introdotto lo Sprint 05 dedicato al modulo Bookings.

Il modulo Availability costituisce ora la base operativa del flusso:

cliente collegato -> selezione slot disponibile -> richiesta booking -> conferma/rifiuto/cancellazione
