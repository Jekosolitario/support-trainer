# Sprint 05 — Bookings

## 1. Obiettivo dello sprint
Questo sprint ha lo scopo di introdurre il modulo prenotazioni, costruito sopra il modulo Availability già completato.

Alla fine dello sprint il backend deve permettere di:

- creare una richiesta di prenotazione su uno slot valido
- leggere le prenotazioni del cliente autenticato
- leggere le prenotazioni del professionista autenticato
- leggere il dettaglio di una prenotazione esistente
- confermare o rifiutare una richiesta lato professionista
- cancellare una richiesta secondo regole coerenti col dominio
- aggiornare correttamente lo stato degli slot coinvolti

---

## 2. Perché questo sprint è importante
Questo sprint è la naturale prosecuzione del modulo Availability.

Fino a questo punto il sistema sa:
- autenticare utenti e applicare autorizzazioni di base
- verificare i professionisti
- creare collegamenti professionista-cliente
- esporre area profilo e lettura relazioni
- gestire le disponibilità del professionista

Con questo sprint il sistema inizia finalmente a gestire:
- la prenotazione vera e propria di uno slot
- il primo ciclo di vita completo cliente → richiesta → decisione del professionista
- una milestone business concreta e direttamente utilizzabile

---

## 3. Risultato atteso
Al termine di questo sprint devono essere testabili questi flussi:

### Lato cliente autenticato
- creare una richiesta di prenotazione su uno slot consentito
- leggere le proprie richieste di prenotazione
- leggere il dettaglio di una propria prenotazione
- cancellare una propria richiesta nei casi consentiti

### Lato professionista autenticato
- leggere le richieste ricevute
- leggere il dettaglio di una richiesta che riguarda un proprio slot
- confermare una richiesta valida
- rifiutare una richiesta valida

### Lato business
- impedire prenotazioni su slot non prenotabili
- impedire prenotazioni fuori dalle relazioni consentite
- impedire conflitti o transizioni di stato incoerenti
- mantenere allineato lo stato della prenotazione con lo stato dello slot

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
- modulo Availability completato e funzionante
- endpoint Availability già implementati:
  - `POST /api/v1/availability`
  - `GET /api/v1/availability/my`
  - `GET /api/v1/professionals/{professionalId}/availability`
  - `PATCH /api/v1/availability/{slotId}`
  - `PATCH /api/v1/availability/{slotId}/block`
  - `PATCH /api/v1/availability/{slotId}/unblock`

Nella documentazione già esistente del progetto, il blocco successivo previsto è Bookings.

---

## 5. Fuori scope di questo sprint
In questo sprint non si implementano ancora:

- workout plans
- nutrition plans
- feedback
- measurements
- notifiche email/push
- calendario frontend avanzato
- pagamenti
- videochiamate o meeting esterni
- dashboard statistiche

---

## 6. Moduli coinvolti
In questo sprint si lavora soprattutto su:

- `booking`
- `availability`
- `professional`
- `client`
- `link`

Con supporto di:
- `security`
- `common`
- `auth`

E con dipendenza dalla parte già esistente di:
- `AvailabilitySlot`
- `ProfessionalProfile`
- `ClientProfile`
- `ProfessionalClientLink`

---

## 7. Obiettivo funzionale della parte
Questa parte del progetto serve a trasformare una disponibilità teorica in una richiesta concreta di appuntamento.

### In pratica deve permettere di:
- far selezionare a un cliente uno slot disponibile
- verificare che cliente e professionista abbiano una relazione valida
- far arrivare la richiesta al professionista corretto
- permettere al professionista di decidere se confermare o rifiutare
- mantenere coerenza tecnica tra prenotazione e slot

### Riassunto semplice
Lo Sprint 05 introduce il primo flusso operativo completo in cui due attori distinti collaborano sullo stesso oggetto business: il cliente richiede, il professionista decide, il sistema sincronizza stati e autorizzazioni.

---

## 8. Regole business principali dello sprint

### 8.1 Relazione cliente-professionista
Una richiesta di prenotazione deve essere consentita solo se il cliente è collegato in modo valido al professionista proprietario dello slot.

### 8.2 Prenotabilità dello slot
Uno slot deve essere prenotabile solo se è coerente con la finalità del modulo bookings.

In particolare lo sprint dovrà fissare in modo esplicito almeno:
- quali stati slot sono prenotabili
- quali slot non possono più ricevere richieste
- come si comporta il sistema quando una richiesta viene confermata, rifiutata o cancellata

### 8.3 Ownership e visibilità
Il cliente autenticato può vedere solo le proprie prenotazioni.

Il professionista autenticato può vedere solo le prenotazioni che riguardano slot di sua proprietà.

### 8.4 Ciclo di vita della prenotazione
La richiesta di prenotazione deve avere uno stato coerente con il dominio.

La documentazione esistente ipotizza almeno questi stati:
- `PENDING`
- `CONFIRMED`
- `REJECTED`
- `CANCELLED`

Il contratto finale degli stati va confermato nello sprint in base allo stato reale del codice creato.

### 8.5 Coerenza tra prenotazione e slot
Quando una richiesta cambia stato, anche lo slot collegato deve essere aggiornato secondo regole esplicite e coerenti.

Questa è una regola centrale dello sprint.

### 8.6 Idempotenza e transizioni valide
Conferma, rifiuto e cancellazione devono essere consentite solo quando la transizione di stato è valida.

Il sistema non deve permettere:
- doppia conferma
- conferma dopo cancellazione
- rifiuto dopo conferma, se non previsto
- cancellazione incoerente con lo stato corrente

---

## 9. Endpoint obiettivo dello sprint
Gli endpoint pianificati coerenti con questo sprint sono:

- **POST** `/api/v1/bookings`
- **GET** `/api/v1/bookings/client`
- **GET** `/api/v1/bookings/professional`
- **GET** `/api/v1/bookings/{bookingRequestId}`
- **PATCH** `/api/v1/bookings/{bookingRequestId}/confirm`
- **PATCH** `/api/v1/bookings/{bookingRequestId}/reject`
- **PATCH** `/api/v1/bookings/{bookingRequestId}/cancel`

### Query param ipotizzati da confermare nello sprint
- `status=PENDING|CONFIRMED|REJECTED|CANCELLED`
- `active=true|false`
- `from=...`
- `to=...`

Nota: il contratto definitivo di payload, filtri e naming va confermato quando inizieranno le chat operative dei blocchi.

---

## 10. Entità e componenti attesi
In coerenza con la roadmap documentale già esistente, questo sprint porterà con alta probabilità all’introduzione di:

### Dominio
- `BookingRequest`
- `BookingRequestItem`
- enum di stato prenotazione

### Persistence
- repository dedicati per bookings

### API layer
- request DTO per creazione richiesta
- response DTO per lista/dettaglio prenotazioni
- controller dedicato
- service dedicato

Nota: i nomi finali di DTO, service methods e package applicativi vanno fissati usando come fonte di verità i file reali che nasceranno durante lo sprint.

---

## 11. Validazioni minime attese
Durante questo sprint vanno coperte almeno queste validazioni:

- cliente autenticato valido
- professionista proprietario dello slot valido
- collegamento cliente-professionista attivo e coerente
- slot esistente
- slot prenotabile
- transizione di stato valida
- visibilità coerente tra cliente e professionista
- prenotazione esistente nei casi di dettaglio/confirm/reject/cancel

---

## 12. Error handling atteso
Lo sprint deve mantenere lo stile già presente nel progetto:

- eccezioni applicative coerenti
- codici errore leggibili
- messaggi chiari lato API
- comportamenti consistenti tra security layer e service layer

I casi minimi da coprire saranno almeno:
- slot non trovato
- prenotazione non trovata
- accesso negato
- richiesta non valida
- stato non valido per l’operazione richiesta
- relazione cliente-professionista non valida

---

## 13. Output finale dello sprint
Al termine dello sprint il progetto dovrà avere:

- modulo bookings strutturato e coerente
- primo workflow completo cliente ↔ professionista sulle prenotazioni
- stati slot sincronizzati con le decisioni sulla prenotazione
- endpoint bookings testati con Postman
- build e test automatici ancora verdi
- documentazione aggiornata allo stato reale

---

## 14. Stato finale Sprint 05

Lo Sprint 05 è stato completato con l’implementazione del modulo Bookings.

Endpoint implementati:

- `POST /api/v1/bookings`
- `GET /api/v1/bookings/client`
- `GET /api/v1/bookings/professional`
- `GET /api/v1/bookings/{bookingRequestId}`
- `PATCH /api/v1/bookings/{bookingRequestId}/confirm`
- `PATCH /api/v1/bookings/{bookingRequestId}/reject`
- `PATCH /api/v1/bookings/{bookingRequestId}/cancel`

### Transizioni confermate

| Azione | Attore autorizzato | Stato iniziale | Stato finale | Stato slot |
|---|---|---|---|---|
| confirm | professionista proprietario | `PENDING` | `CONFIRMED` | `AVAILABLE → BOOKED` |
| reject | professionista proprietario | `PENDING` | `REJECTED` | resta `AVAILABLE` |
| cancel | cliente proprietario | `PENDING` | `CANCELLED` | resta `AVAILABLE` |
| cancel | cliente proprietario | `CONFIRMED` | `CANCELLED` | `BOOKED → AVAILABLE` |
| cancel | professionista proprietario | `CONFIRMED` | `CANCELLED` | `BOOKED → AVAILABLE` |

### Regole di coerenza confermate

Il sistema blocca:

- doppia conferma
- doppio rifiuto
- doppia cancellazione
- conferma di booking non `PENDING`
- rifiuto di booking non `PENDING`
- cancellazione di booking già `REJECTED`
- accesso a booking non appartenenti all’utente autenticato
- conferma/rifiuto da parte di cliente
- conferma/rifiuto da parte di professionista non proprietario

### Stato finale

Il ciclo di vita della prenotazione è ora completo:

`PENDING → CONFIRMED / REJECTED / CANCELLED`

e la coerenza tra `BookingRequest` e `AvailabilitySlot` è stata verificata tramite test manuali Postman.

---

## 15. Nota di metodo
Questo sprint va affrontato in più blocchi separati.

Ordine corretto consigliato:
1. dominio + persistenza
2. creazione richiesta + letture principali
3. dettaglio + decisioni del professionista
4. cancellazione + rifinitura sicurezza/coerenza + aggiornamento documentazione

Questo ordine riduce il rischio di caos e mantiene il progetto coerente con la roadmap già esistente.
