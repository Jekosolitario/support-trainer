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

## 8. Regole business implementate

### 8.1 Relazione cliente-professionista

Una richiesta di prenotazione può essere creata solo se:

- il cliente autenticato esiste ed è attivo;
- il professionista proprietario dello slot esiste ed è attivo;
- esiste un collegamento attivo tra cliente e professionista.

Un cliente non collegato non può creare booking su slot di quel professionista.

### 8.2 Prenotabilità dello slot

Nel backend attuale una richiesta booking viene creata a partire da un singolo `availabilitySlotId`.

Uno slot può ricevere una nuova richiesta solo se:

- esiste;
- appartiene al professionista collegato al cliente;
- è attivo;
- è in stato `AVAILABLE`;
- non esiste già una richiesta `PENDING` attiva sullo stesso slot.

### 8.3 Nota della richiesta

La richiesta booking può contenere una `note` facoltativa.

Regole implementate durante la stabilizzazione:

- la nota non può superare `1000` caratteri;
- gli spazi iniziali e finali vengono rimossi;
- una nota vuota dopo la normalizzazione viene trattata come assente.

### 8.4 Ownership e visibilità

Il cliente autenticato può:

- leggere solo le proprie richieste;
- leggere il dettaglio solo dei booking in cui è coinvolto;
- cancellare solo booking propri negli stati consentiti.

Il professionista autenticato può:

- leggere solo le richieste ricevute;
- leggere il dettaglio solo dei booking relativi ai propri slot;
- confermare o rifiutare solo booking relativi ai propri slot;
- cancellare solo booking confermati in cui è coinvolto.

### 8.5 Stati della prenotazione

Gli stati implementati sono:

- `PENDING`
- `CONFIRMED`
- `REJECTED`
- `CANCELLED`

### 8.6 Transizioni consentite

| Azione | Attore autorizzato | Stato iniziale | Stato finale | Effetto sullo slot |
|---|---|---|---|---|
| confirm | professionista proprietario | `PENDING` | `CONFIRMED` | `AVAILABLE -> BOOKED` |
| reject | professionista proprietario | `PENDING` | `REJECTED` | resta `AVAILABLE` |
| cancel | cliente proprietario | `PENDING` | `CANCELLED` | resta `AVAILABLE` |
| cancel | cliente proprietario | `CONFIRMED` | `CANCELLED` | `BOOKED -> AVAILABLE` |
| cancel | professionista proprietario | `CONFIRMED` | `CANCELLED` | `BOOKED -> AVAILABLE` |

### 8.7 Transizioni bloccate

Il sistema non consente:

- doppia conferma;
- doppio rifiuto;
- doppia cancellazione;
- conferma di booking non `PENDING`;
- rifiuto di booking non `PENDING`;
- cancellazione di booking `REJECTED`;
- cancellazione professionista di booking `PENDING`, che deve essere gestita tramite rifiuto;
- operazioni da parte di utenti non coinvolti nella prenotazione.

---

## 9. Endpoint implementati

Gli endpoint reali del modulo Bookings sono:

- **POST** `/api/v1/bookings`
- **GET** `/api/v1/bookings/client`
- **GET** `/api/v1/bookings/professional`
- **GET** `/api/v1/bookings/{bookingRequestId}`
- **PATCH** `/api/v1/bookings/{bookingRequestId}/confirm`
- **PATCH** `/api/v1/bookings/{bookingRequestId}/reject`
- **PATCH** `/api/v1/bookings/{bookingRequestId}/cancel`

### Regole di autorizzazione esplicite

- `POST /api/v1/bookings` → solo `CLIENT`
- `GET /api/v1/bookings/client` → solo `CLIENT`
- `GET /api/v1/bookings/professional` → solo `PROFESSIONAL`
- `PATCH /api/v1/bookings/{bookingRequestId}/confirm` → solo `PROFESSIONAL`
- `PATCH /api/v1/bookings/{bookingRequestId}/reject` → solo `PROFESSIONAL`
- `GET /api/v1/bookings/{bookingRequestId}` → utente autenticato, con controllo ownership nel service
- `PATCH /api/v1/bookings/{bookingRequestId}/cancel` → utente autenticato, con controllo ownership e transizione nel service

### Filtri non ancora implementati

Nel backend attuale non risultano implementati filtri tramite query parameter per:

- stato booking;
- intervallo temporale;
- flag `active`.

Eventuali filtri dovranno essere introdotti in uno sprint futuro, solo se necessari al frontend.

---

## 10. Entità e componenti implementati

Il modulo Bookings è composto da:

### Dominio

- `BookingRequest`
- `BookingRequestItem`
- `BookingRequestStatus`

### Persistence

- `BookingRequestRepository`
- `BookingRequestItemRepository`

### API layer

- `CreateBookingRequest`
- `BookingRequestResponse`
- `BookingRequestItemResponse`
- `BookingController`
- `BookingService`

### Relazione con Availability

Ogni richiesta creata tramite API contiene attualmente un solo `BookingRequestItem`, collegato allo slot indicato da `availabilitySlotId`.

Il modello dati resta predisposto per una futura evoluzione multi-slot, ma tale comportamento non è parte dell’API attuale.

---

## 11. Validazioni implementate

Nel modulo Bookings risultano implementate le seguenti validazioni:

- cliente autenticato valido e attivo in fase di creazione richiesta;
- professionista proprietario dello slot valido;
- collegamento attivo cliente-professionista;
- slot esistente;
- slot attivo e disponibile;
- assenza di altra richiesta `PENDING` attiva sullo stesso slot;
- nota facoltativa, normalizzata e limitata a `1000` caratteri;
- booking esistente nei flussi di dettaglio, conferma, rifiuto e cancellazione;
- ownership corretta della richiesta;
- ruolo coerente con l’operazione richiesta;
- transizione di stato consentita;
- sincronizzazione coerente tra stato booking e stato slot.

---

## 12. Error handling implementato

Il modulo mantiene lo stile applicativo già adottato nel backend:

- eccezioni applicative centralizzate;
- codici errore leggibili;
- messaggi chiari lato API;
- distinzione tra problemi di autenticazione, autorizzazione e regole business.

I casi gestiti comprendono:

- slot non trovato;
- booking non trovato;
- cliente non autorizzato;
- professionista non autorizzato;
- relazione cliente-professionista assente;
- slot non disponibile;
- richiesta pending già presente sullo slot;
- transizione booking non consentita;
- accesso al dettaglio da utente non coinvolto;
- cancellazione professionista di una richiesta ancora `PENDING`.

---

## 13. Output finale dello sprint

Lo Sprint 05 ha introdotto il primo workflow completo di prenotazione del progetto:

cliente collegato
-> selezione slot disponibile
-> richiesta booking PENDING
-> conferma / rifiuto / cancellazione
-> aggiornamento coerente dello slot

---

## Migliorie future importanti

### Motivo del rifiuto di una richiesta di prenotazione

In una versione successiva del modulo Bookings, il rifiuto di una richiesta di prenotazione dovrà permettere al professionista di indicare un messaggio esplicativo per il cliente.

Obiettivo della feature:

- permettere al professionista di spiegare perché una richiesta viene rifiutata
- rendere più chiara la comunicazione verso il cliente
- conservare il motivo del rifiuto nello storico della prenotazione
- mostrare il messaggio al cliente nel dettaglio della richiesta

Possibile evoluzione tecnica:

- aggiungere un campo dedicato su `BookingRequest`, ad esempio `rejectionReason`
- introdurre una request DTO per `PATCH /api/v1/bookings/{bookingRequestId}/reject`
- validare il messaggio lato backend
- includere il motivo nella response della booking
- mostrare il motivo lato frontend nella pagina cliente

Questa miglioria non è inclusa nello Sprint 05 attuale, ma va considerata una feature importante per migliorare la qualità del flusso di prenotazione.