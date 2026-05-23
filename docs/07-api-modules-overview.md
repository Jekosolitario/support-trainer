# API Modules Overview — Support Trainer

## 1. Obiettivo del documento

Questo documento descrive l’organizzazione funzionale delle API backend di Support Trainer, distinguendo chiaramente tra:

- moduli già implementati nel codice reale;
- componenti di dominio presenti ma non esposti come API dedicate;
- moduli pianificati ma non ancora implementati.

La mappa dettagliata degli endpoint reali è mantenuta nel documento:

- `08-endpoint-map.md`

---

## 2. Principio di organizzazione

Le API del progetto sono organizzate per moduli funzionali, non per pagine frontend.

Questo approccio consente di:

- separare responsabilità e regole business;
- mantenere controller e service ordinati;
- favorire l’evoluzione progressiva del backend;
- collegare il futuro frontend a contratti API chiari.

---

## 3. Moduli API attualmente implementati

Nel backend reale risultano implementati i seguenti moduli API:

- **Auth**
- **Profile / Account (`Me`)**
- **Clients**
- **Professionals**
- **Invites**
- **Availability**
- **Bookings**

Sono inoltre presenti e utilizzati:

- security JWT;
- gestione centralizzata degli errori;
- relazione professionista-cliente tramite `ProfessionalClientLink`.

---

## 4. Modulo Auth — Implementato

### Responsabilità attuali

Il modulo **Auth** gestisce:

- registrazione professionista;
- registrazione cliente tramite codice invito;
- validazione preventiva del codice invito per registrazione cliente;
- verifica email professionista;
- login.

### Endpoint implementati

- `POST /api/v1/auth/register/professional`
- `POST /api/v1/auth/register/client`
- `POST /api/v1/auth/register/client/validate-invite`
- `GET /api/v1/auth/verify-email`
- `POST /api/v1/auth/login`

### Cosa non gestisce ancora

Non risultano ancora implementati:

- refresh token;
- logout applicativo;
- forgot password;
- reset password.

---

## 5. Modulo Profile / Account (`Me`) — Implementato

### Responsabilità attuali

Il modulo **Me** gestisce le operazioni dell’utente autenticato sul proprio profilo e account:

- lettura profilo personale;
- lettura dati account;
- aggiornamento dati del profilo;
- aggiornamento stato operativo.

### Endpoint implementati

- `GET /api/v1/me/profile`
- `GET /api/v1/me/account`
- `PATCH /api/v1/me/profile`
- `PATCH /api/v1/me/profile/operational-status`

### Funzionalità non ancora implementate

Non risultano ancora implementati:

- upload immagine profilo;
- cambio password da area autenticata.

---

## 6. Modulo Clients — Implementato

### Responsabilità attuali

Il modulo **Clients** consente al professionista autenticato di leggere i clienti collegati.

### Endpoint implementati

- `GET /api/v1/clients/my`
- `GET /api/v1/clients/{clientId}`

### Regole principali

- accesso riservato al professionista;
- lettura consentita solo per clienti collegati;
- controllo della relazione attiva nel service layer.

---

## 7. Modulo Professionals — Implementato

### Responsabilità attuali

Il modulo **Professionals** consente al cliente autenticato di leggere i professionisti collegati e le disponibilità consultabili.

### Endpoint implementati

- `GET /api/v1/professionals/my`
- `GET /api/v1/professionals/{professionalId}`
- `GET /api/v1/professionals/{professionalId}/availability`

### Regole principali

- accesso riservato al cliente;
- lettura dettaglio consentita solo verso professionisti collegati;
- lettura availability consentita solo verso professionisti collegati;
- vengono esposti solo slot disponibili, non scaduti e privi di richieste booking `PENDING` attive.

### Nota architetturale

La lettura pubblicata tramite:

- `GET /api/v1/professionals/{professionalId}/availability`

appartiene funzionalmente all’area Availability, anche se il path API è esposto nell’area Professionals.

---

## 8. Modulo Invites — Implementato

### Responsabilità attuali

Il modulo **Invites** gestisce:

- generazione di codici invito da parte del professionista;
- elenco dei codici invito generati.

### Endpoint implementati

- `POST /api/v1/invites`
- `GET /api/v1/invites`

### Regole principali

- accesso riservato al professionista;
- il professionista deve rispettare le condizioni applicative richieste;
- il codice viene utilizzato dal flusso di registrazione cliente.

### Estensioni future possibili

Non risultano ancora implementati endpoint dedicati per:

- dettaglio singolo invito;
- disattivazione manuale di un invito non usato.

---

## 9. Relazione professionista-cliente (`ProfessionalClientLink`) — Presente nel dominio, senza API dedicate

### Stato reale

La relazione tra professionista e cliente è implementata tramite:

- `ProfessionalClientLink`.

La relazione viene utilizzata per:

- collegare cliente e professionista dopo una registrazione cliente valida;
- autorizzare la lettura clienti/professionisti;
- autorizzare la lettura availability;
- autorizzare la creazione booking.

### Precisazione importante

Nel backend attuale non esiste un modulo API autonomo `Links` con controller dedicato.

Non risultano ancora implementati endpoint come:

- elenco link;
- dettaglio link;
- disattivazione link.

Queste API potranno essere valutate in futuro solo se richieste dal flusso applicativo reale.

---

## 10. Modulo Availability — Implementato

### Responsabilità attuali

Il modulo **Availability** gestisce gli slot di disponibilità dei professionisti `PERSONAL_TRAINER`.

### Endpoint implementati

- `POST /api/v1/availability`
- `GET /api/v1/availability/my`
- `PATCH /api/v1/availability/{slotId}`
- `PATCH /api/v1/availability/{slotId}/block`
- `PATCH /api/v1/availability/{slotId}/unblock`

La lettura lato cliente è esposta tramite:

- `GET /api/v1/professionals/{professionalId}/availability`

### Regole principali implementate

- solo il professionista autorizzato può creare e gestire i propri slot;
- il professionista deve avere account attivo, email verificata e profilo attivo;
- gli slot devono avere intervallo temporale valido;
- gli slot creati o aggiornati devono iniziare nel futuro;
- non sono consentiti slot sovrapposti;
- gli slot possono essere bloccati e sbloccati secondo stato;
- il cliente può leggere availability solo di professionisti collegati;
- gli slot disponibili ma scaduti non vengono mostrati al cliente;
- gli slot con una richiesta booking `PENDING` attiva non vengono mostrati al cliente come disponibilità prenotabili;
- uno slot con booking `PENDING` attivo non può essere modificato o bloccato manualmente dal professionista.

### Stati slot gestiti

- `AVAILABLE`
- `BLOCKED`
- `BOOKED`

---

## 11. Modulo Bookings — Implementato

### Responsabilità attuali

Il modulo **Bookings** gestisce il ciclo di prenotazione tra cliente collegato e professionista.

### Endpoint implementati

- `POST /api/v1/bookings`
- `GET /api/v1/bookings/client`
- `GET /api/v1/bookings/professional`
- `GET /api/v1/bookings/{bookingRequestId}`
- `PATCH /api/v1/bookings/{bookingRequestId}/confirm`
- `PATCH /api/v1/bookings/{bookingRequestId}/reject`
- `PATCH /api/v1/bookings/{bookingRequestId}/cancel`

### Contratto attuale

Nel backend attuale una richiesta booking viene creata a partire da:

- un singolo `availabilitySlotId`.

Il modello con `BookingRequestItem` resta estendibile a scenari multi-slot futuri, ma l’API attuale opera su un solo slot per richiesta.

### Regole principali implementate

- il cliente può prenotare solo slot di professionisti collegati;
- lo slot deve essere attivo, disponibile e non scaduto;
- non può esistere una richiesta `PENDING` duplicata sullo stesso slot;
- la nota è facoltativa, normalizzata e limitata a `1000` caratteri;
- il dettaglio booking è visibile solo agli utenti coinvolti;
- un booking pending con slot ormai scaduto non può essere confermato.
- una richiesta `PENDING` riserva logicamente lo slot;
- finché una richiesta è `PENDING`, lo slot non viene più esposto come disponibilità prenotabile agli altri clienti;
- finché una richiesta è `PENDING`, lo slot non può essere modificato o bloccato manualmente dal professionista;
- i flussi critici di creazione e transizione booking sono protetti da lock pessimisti per evitare inconsistenze concorrenti.

### Stati booking gestiti

- `PENDING`
- `CONFIRMED`
- `REJECTED`
- `CANCELLED`

### Transizioni principali

| Azione | Attore | Stato booking | Effetto sullo slot |
|---|---|---|---|
| confirm | professionista coinvolto | `PENDING -> CONFIRMED` | `AVAILABLE -> BOOKED` |
| reject | professionista coinvolto | `PENDING -> REJECTED` | resta `AVAILABLE` |
| cancel | cliente coinvolto | `PENDING -> CANCELLED` | resta `AVAILABLE` |
| cancel | cliente coinvolto | `CONFIRMED -> CANCELLED` | `BOOKED -> AVAILABLE` |
| cancel | professionista coinvolto | `CONFIRMED -> CANCELLED` | `BOOKED -> AVAILABLE` |

---

## 12. Moduli pianificati ma non ancora implementati

I seguenti moduli non risultano presenti nel backend reale:

- **Workout Plans**
- **Nutrition Plans**
- **Feedback**
- **Measurements**

### 12.1 Workout Plans

Modulo futuro dedicato alle schede di allenamento create dai professionisti `PERSONAL_TRAINER`.

### 12.2 Nutrition Plans

Modulo futuro dedicato ai piani alimentari creati dai professionisti `NUTRITIONIST`.

### 12.3 Feedback

Modulo futuro dedicato ai feedback del cliente su workout e nutrition.

### 12.4 Measurements

Modulo futuro dedicato allo storico delle misurazioni fisiche del cliente.

---

## 13. Funzionalità tecniche future

Oltre ai moduli business non ancora implementati, restano da valutare o sviluppare:

- refresh token persistenti;
- logout;
- forgot password;
- reset password;
- upload immagine profilo;
- cambio password autenticato;
- API dedicate per gestione link;
- integrazione frontend reale;
- preparazione deploy.

---

## 14. Moduli esclusi dal perimetro attuale

Non fanno parte del backend attualmente implementato:

- admin avanzato;
- notifiche;
- promemoria;
- pagamenti;
- chat real time;
- statistiche avanzate.

Queste aree potranno essere considerate solo in fasi successive.

---

## 15. Ruolo dei controller

I controller implementati espongono gli endpoint HTTP e delegano la logica ai service.

### Controller presenti nel backend reale

- `AuthController`
- `MeController`
- `ClientController`
- `ProfessionalController`
- `InviteController`
- `AvailabilityController`
- `BookingController`

### Regola architetturale

I controller devono:

- ricevere request DTO;
- delegare ai service;
- restituire response DTO;
- evitare business logic complessa.

---

## 16. Ruolo dei service

I service rappresentano il punto centrale della business logic.

Nel backend attuale gestiscono:

- validazioni applicative;
- autorizzazione logica;
- ownership delle risorse;
- relazione cliente-professionista;
- coerenza degli stati;
- coordinamento tra repository;
- sincronizzazione Availability / Bookings.

---

## 17. Ruolo dei repository

I repository gestiscono:

- accesso ai dati;
- ricerca per identificativi;
- query su stati e relazioni;
- query temporali per availability;
- controlli di esistenza utili alla business logic.

La business logic resta nei service e non nei repository.

---

## 18. Stato complessivo

La base API attualmente implementata copre:

registrazione e autenticazione
-> profilo/account
-> collegamento professionista-cliente
-> lettura relazioni
-> disponibilità professionista
-> richiesta e gestione prenotazione

Il workflow Availability / Bookings è consolidato anche nei casi critici:

- slot scaduti non esposti né prenotabili;
- slot con booking `PENDING` non esposti come disponibili;
- slot con booking `PENDING` non modificabili o bloccabili manualmente;
- operazioni concorrenti protette nei punti sensibili del flusso.