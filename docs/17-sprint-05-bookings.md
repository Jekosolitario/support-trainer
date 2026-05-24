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
- appartiene a un professionista con specializzazione `PERSONAL_TRAINER`;
- è attivo;
- è in stato `AVAILABLE`;
- ha `startDateTime` nel futuro;
- non esiste già una richiesta `PENDING` attiva sullo stesso slot.

Uno slot appartenente a un professionista `NUTRITIONIST` non è prenotabile tramite il modulo Bookings, anche qualora fosse già presente nel database per dati storici o anomali.

Uno slot rimasto `AVAILABLE` ma ormai scaduto non è prenotabile.

### 8.2.1 Protezione da richieste concorrenti

La creazione di una richiesta booking protegge lo slot selezionato tramite lock pessimista in scrittura.

Durante la transazione di creazione:

- lo slot viene caricato con `PESSIMISTIC_WRITE`;
- viene verificato che sia ancora prenotabile;
- viene verificata l’assenza di una richiesta `PENDING` attiva sullo stesso slot;
- solo dopo viene salvata la nuova richiesta booking.

Questa protezione impedisce che due richieste simultanee sullo stesso slot possano entrambe essere create come `PENDING`.

### 8.2.2 Riserva logica dello slot durante una richiesta pending

Una richiesta booking in stato `PENDING` non marca ancora lo slot come `BOOKED`, perché il professionista non ha ancora confermato la prenotazione.

Tuttavia, durante lo stato `PENDING`, lo slot è considerato logicamente impegnato.

Finché esiste una richiesta `PENDING` attiva sullo slot:

- non può essere creata una seconda richiesta `PENDING`;
- il professionista non può modificarne data e ora;
- il professionista non può bloccarlo manualmente;
- lo slot non viene più esposto ai clienti come disponibilità prenotabile.

Questa regola evita che altri clienti visualizzino o tentino di prenotare uno slot già interessato da una richiesta in attesa.

Per modificare o bloccare lo slot, il professionista deve prima gestire la richiesta pendente, ad esempio rifiutandola.

### 8.2.3 Integrità storica dello slot dopo una richiesta booking

Quando uno slot viene collegato a una richiesta booking, il relativo intervallo temporale diventa parte dello storico della prenotazione.

Anche se la richiesta viene successivamente:

- rifiutata;
- cancellata;

lo slot non può più essere ripianificato modificandone data o ora.

Lo slot può eventualmente ricevere nuove richieste sullo stesso intervallo temporale, se rispetta ancora tutte le regole di prenotabilità previste.

Per proporre una nuova disponibilità in un giorno o orario diverso, il professionista deve creare un nuovo slot availability.

Questa regola impedisce che lo storico di una richiesta già creata mostri date diverse da quelle originariamente selezionate dal cliente.

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
- cancellare solo booking propri negli stati consentiti;
- visualizzare come disponibilità soltanto slot realmente prenotabili e senza booking `PENDING` attivo.

Il professionista autenticato può:

- leggere solo le richieste ricevute;
- leggere il dettaglio solo dei booking relativi ai propri slot;
- confermare o rifiutare solo booking relativi ai propri slot;
- cancellare solo booking confermati in cui è coinvolto.

### 8.5 Stati della prenotazione

Gli stati implementati sono:

- `PENDING`;
- `CONFIRMED`;
- `REJECTED`;
- `CANCELLED`.

### 8.6 Transizioni consentite

| Azione | Attore autorizzato | Stato iniziale | Stato finale | Effetto sullo slot |
|---|---|---|---|---|
| confirm | professionista proprietario | `PENDING` | `CONFIRMED` | `AVAILABLE -> BOOKED` |
| reject | professionista proprietario | `PENDING` | `REJECTED` | resta `AVAILABLE` |
| cancel | cliente proprietario | `PENDING` | `CANCELLED` | resta `AVAILABLE` |
| cancel | cliente proprietario | `CONFIRMED` | `CANCELLED` | `BOOKED -> AVAILABLE` |
| cancel | professionista proprietario | `CONFIRMED` | `CANCELLED` | `BOOKED -> AVAILABLE` |

### 8.6.1 Protezione delle transizioni concorrenti

Le operazioni che modificano lo stato di una richiesta booking proteggono la richiesta tramite lock pessimista in scrittura.

Sono protette in questo modo le operazioni di:

- conferma;
- rifiuto;
- cancellazione.

Questa scelta impedisce che due operazioni simultanee possano partire dallo stesso stato iniziale e produrre transizioni incoerenti sulla stessa richiesta.

### 8.6.2 Protezione dello slot durante la conferma

Durante la conferma di una richiesta booking, il sistema protegge anche lo slot collegato tramite lock pessimista in scrittura.

Il flusso di conferma avviene nel seguente ordine:

1. viene bloccata la richiesta booking;
2. viene bloccato lo slot collegato;
3. viene verificato che lo slot sia ancora:
   - attivo;
   - appartenente a un `PERSONAL_TRAINER`;
   - in stato `AVAILABLE`;
   - non scaduto;
4. solo dopo il booking passa a `CONFIRMED` e lo slot passa a `BOOKED`.

Questa protezione impedisce che due conferme concorrenti possano utilizzare lo stesso slot come disponibile.

### 8.7 Operazioni bloccate

Il sistema non consente:

- doppia conferma;
- doppio rifiuto;
- doppia cancellazione;
- conferma di booking non `PENDING`;
- rifiuto di booking non `PENDING`;
- cancellazione di booking `REJECTED`;
- cancellazione professionista di booking `PENDING`, che deve essere gestita tramite rifiuto;
- creazione booking su slot ormai scaduto;
- conferma di un booking `PENDING` quando lo slot collegato è ormai scaduto;
- creazione booking su slot appartenente a un professionista `NUTRITIONIST`;
- conferma booking su slot appartenente a un professionista `NUTRITIONIST`;
- seconda richiesta `PENDING` attiva sullo stesso slot;
- esposizione lato cliente di uno slot con richiesta `PENDING` attiva;
- modifica di uno slot con richiesta booking `PENDING` attiva;
- blocco manuale di uno slot con richiesta booking `PENDING` attiva;
- operazioni da parte di utenti non coinvolti nella prenotazione;
- ripianificazione tramite modifica data/ora di uno slot già coinvolto in una richiesta booking, anche se la richiesta è stata rifiutata o cancellata.

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
- professionista proprietario dello slot valido, attivo e con specializzazione `PERSONAL_TRAINER`;
- collegamento attivo cliente-professionista;
- slot esistente, attivo, disponibile e futuro;
- assenza di altra richiesta `PENDING` attiva sullo stesso slot;
- nota facoltativa, normalizzata e limitata a `1000` caratteri;
- booking esistente nei flussi di dettaglio, conferma, rifiuto e cancellazione;
- ownership e ruolo coerenti con l’operazione richiesta;
- transizione di stato consentita;
- conferma consentita solo se lo slot collegato è ancora disponibile, futuro e riferito a un `PERSONAL_TRAINER`;
- sincronizzazione coerente tra stato booking e stato slot;
- protezione pessimistica dello slot durante la creazione booking;
- protezione pessimistica della richiesta durante conferma, rifiuto e cancellazione;
- protezione pessimistica dello slot durante la conferma booking;
- blocco della modifica o del blocco manuale di uno slot con richiesta booking `PENDING`;
- esclusione dalla lettura cliente degli slot con richiesta `PENDING` attiva;
- blocco della ripianificazione di uno slot già coinvolto in almeno una richiesta booking.

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
- cliente o professionista non autorizzato;
- relazione cliente-professionista assente;
- slot non disponibile o scaduto;
- slot appartenente a un nutrizionista non prenotabile o non confermabile;
- richiesta `PENDING` già presente sullo slot;
- transizione booking non consentita;
- conferma booking pending con slot ormai scaduto;
- accesso al dettaglio da utente non coinvolto;
- cancellazione professionista di una richiesta ancora `PENDING`.

---

## 13. Output finale dello sprint

Lo Sprint 05 ha introdotto il primo workflow completo di prenotazione del progetto:

```text
cliente collegato
-> selezione slot disponibile
-> richiesta booking PENDING
-> conferma / rifiuto / cancellazione
-> aggiornamento coerente dello slot
```

---

## 14. Stato finale consolidato dopo audit

Lo Sprint 05 risulta completato e successivamente rafforzato durante l’audit tecnico del backend.

### Funzionalità implementate

- creazione richiesta booking single-slot;
- lista richieste lato cliente e lato professionista;
- dettaglio richiesta accessibile solo agli utenti coinvolti;
- conferma, rifiuto e cancellazione richiesta secondo ruolo e stato;
- sincronizzazione tra stato booking e stato availability slot;
- normalizzazione e limite della nota;
- blocco booking e conferma su slot scaduti o appartenenti a nutrizionisti;
- protezione da richieste e conferme concorrenti sullo stesso slot;
- protezione da transizioni concorrenti sulla stessa richiesta;
- riserva logica dello slot durante una richiesta booking `PENDING`;
- blocco modifica/blocco manuale dello slot finché esiste una richiesta pendente;
- esclusione degli slot con booking `PENDING` dalle disponibilità visibili al cliente;
- protezione dell’integrità storica tramite blocco della ripianificazione di slot già utilizzati in booking.

### Test automatici aggiunti durante la stabilizzazione

Sono presenti test automatici per verificare:

- creazione booking da cliente collegato su slot disponibile;
- blocco creazione booking da cliente non collegato;
- conferma booking e passaggio slot a `BOOKED`;
- rifiuto booking pending con slot ancora `AVAILABLE`;
- cancellazione booking pending lato cliente;
- cancellazione booking confirmed lato cliente con slot nuovamente `AVAILABLE`;
- blocco cancellazione booking pending lato professionista;
- blocco lettura dettaglio booking da utente non coinvolto;
- blocco creazione booking su slot scaduto;
- blocco conferma booking pending con slot ormai scaduto;
- blocco creazione e conferma booking su slot appartenente a un nutrizionista;
- blocco creazione di una seconda richiesta `PENDING` sullo stesso slot;
- blocco modifica o blocco manuale di uno slot con booking `PENDING`;
- esclusione dalla lettura cliente di uno slot con booking `PENDING` attivo;
- blocco della ripianificazione di uno slot già coinvolto in un booking rifiutato.

Le protezioni da concorrenza tramite lock sono implementate; non risultano coperte da un test parallelo multi-transazione dedicato.

---

## 15. Evoluzione successiva

Il modulo Bookings è ora coerente con:

- ruoli applicativi;
- relazione cliente-professionista;
- specializzazione del professionista;
- stato e validità temporale degli slot;
- transizioni di stato della prenotazione;
- visibilità delle disponibilità lato cliente;
- integrità storica dell’intervallo temporale richiesto.

La futura miglioria prioritaria del modulo resta la possibilità per il professionista di inserire un motivo testuale in fase di rifiuto della richiesta.

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