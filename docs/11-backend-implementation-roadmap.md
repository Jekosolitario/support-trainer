# Backend Implementation Roadmap — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce l’ordine corretto di implementazione del backend di Support Trainer.

Lo scopo è:
- evitare caos durante lo sviluppo
- costruire il progetto per fasi progressive
- ridurre il rischio di errori strutturali
- arrivare a un backend funzionante passo dopo passo

---

## 2. Principio guida
Il backend non va sviluppato per moduli isolati tutti insieme, ma per strati progressivi.

Ordine corretto:
1. base progetto
2. persistenza dati
3. sicurezza e autenticazione
4. verifica account e registrazione con collegamenti
5. primi moduli business consultabili
6. moduli operativi avanzati
7. rifinitura, test e preparazione al deploy

---

## 3. Strategia consigliata
Per ogni fase bisogna lavorare così:

1. definire entity e repository del blocco
2. creare service con business logic minima necessaria
3. creare controller e DTO
4. testare tutto con Postman
5. solo dopo passare al blocco successivo

### Regola importante
Non iniziare il frontend vero finché il blocco backend non è:
- coerente
- testato
- minimamente stabile

---

## 4. Ordine corretto di sviluppo

## FASE 0 — Setup iniziale progetto
### Obiettivo
Preparare il progetto Spring Boot in modo pulito.

### Da fare
- creazione progetto Spring Boot
- configurazione package base
- configurazione `application.properties`
- collegamento MySQL
- configurazione JPA/Hibernate
- gestione environment base
- creazione struttura package iniziale:
  - `common`
  - `security`
  - `auth`
  - `profile`
  - `professional`
  - `client`
  - `invite`
  - `link`

### Definition of Done
- progetto avviabile
- connessione DB funzionante
- struttura package pronta
- build pulita

---

## FASE 1 — Fondazioni tecniche
### Obiettivo
Costruire la base riutilizzabile del backend.

### Da fare
- `BaseEntity` con `id`, `createdAt`, `updatedAt`
- enum principali
- eccezioni custom base
- global exception handler
- response/error model coerente
- repository comuni se necessari
- configurazione iniziale coerente per API REST

### Definition of Done
- base tecnica pronta
- gestione errori già presente
- convenzioni riutilizzabili in tutto il progetto

---

## FASE 2 — Modello utenti
### Obiettivo
Implementare la gerarchia utenti e la persistenza base.

### Da fare
- `User`
- `ProfessionalProfile`
- `ClientProfile`
- strategia JPA `JOINED`
- repository utenti
- vincoli base su email
- test persistenza entity

### Definition of Done
- utenti salvabili correttamente
- gerarchia utenti stabile
- query base funzionanti

---

## FASE 3 — Sicurezza e autenticazione
### Obiettivo
Far funzionare accesso, authority e protezione endpoint.

### Da fare
- Spring Security config
- `PasswordEncoder`
- JWT access token
- refresh token
- filtro JWT
- `UserDetailsService`
- login
- endpoint pubblici e protetti
- authority:
  - `PROFESSIONAL`
  - `CLIENT`

### Nota importante
Nel progetto attuale:
- access token e refresh token vengono generati
- il login è funzionante
- il refresh token è presente nel modello
- il flusso completo di refresh con endpoint dedicato non è ancora stato implementato

### Definition of Done
- login funzionante
- token generati correttamente
- endpoint protetti correttamente
- accesso base funzionante con Postman

---

## FASE 4 — Verifica email professionista
### Obiettivo
Chiudere bene il ciclo di attivazione del professionista.

### Da fare
- `EmailVerificationToken`
- endpoint verify email
- generazione token alla registrazione professionista
- blocco professionista non verificato
- attivazione account dopo verifica

### Definition of Done
- professionista registrabile ma non operativo finché non verifica email
- `emailVerified = true` dopo verifica
- `accountStatus = ACTIVE` dopo verifica
- flusso auth realistico e coerente

---

## FASE 5 — Inviti, registrazione cliente e collegamenti
### Obiettivo
Realizzare il primo flusso business completo.

### Da fare
- generazione `InviteCode`
- validazione codice invito
- registrazione cliente con codice
- creazione `ProfessionalClientLink`
- regole:
  - max 3 professionisti per cliente
  - no self-link
  - no duplicati attivi

### Definition of Done
- professionista verificato genera codice
- cliente si registra con codice valido
- codice viene marcato come usato
- collegamento creato correttamente

### Nota
Questa è la prima milestone business completa del progetto.

---

## FASE 6 — Modulo profilo/account
### Obiettivo
Permettere agli utenti di leggere e aggiornare i propri dati base.

### Da fare
- `GET /api/v1/me/profile`
- `GET /api/v1/me/account`
- `PATCH /api/v1/me/profile`
- `PATCH /api/v1/me/profile/operational-status`

### Definition of Done
- utente autenticato legge il proprio profilo
- utente autenticato legge i propri dati account
- utente autenticato aggiorna i campi consentiti del profilo
- utente autenticato aggiorna il proprio stato operativo

---

## FASE 7 — Modulo clienti e professionisti in lettura
### Obiettivo
Consentire navigazione e lettura delle relazioni create.

### Da fare
- elenco clienti del professionista autenticato
- dettaglio cliente collegato
- elenco professionisti del cliente autenticato
- dettaglio professionista collegato
- controlli su relazione attiva
- controlli di autorizzazione coerenti tra security e service layer

### Definition of Done
- lato lettura relazioni pronto
- professionista e cliente vedono solo i propri collegamenti
- i dettagli sono accessibili solo se la relazione è valida

---

## FASE 8 — Availability
### Obiettivo
Permettere al professionista di definire le proprie disponibilità.

### Da fare
- `AvailabilitySlot`
- creazione slot
- update slot
- blocco/sblocco slot
- query per intervallo date
- validazioni:
  - solo professionista autorizzato
  - no sovrapposizioni
  - intervalli validi

### Definition of Done
- professionista crea disponibilità valide
- lettura slot disponibile
- regole principali rispettate

### Stato attuale

Completata e verificata.

Endpoint implementati:

- `POST /api/v1/availability`
- `GET /api/v1/availability/my`
- `GET /api/v1/professionals/{professionalId}/availability`
- `PATCH /api/v1/availability/{slotId}`
- `PATCH /api/v1/availability/{slotId}/block`
- `PATCH /api/v1/availability/{slotId}/unblock`

Sono presenti controlli su:

- professionista autenticato
- account attivo
- email verificata per professionista
- profilo attivo
- intervalli temporali validi
- slot nel futuro
- assenza di sovrapposizioni
- accesso cliente solo se collegato al professionista

---

## FASE 9 — Bookings
### Obiettivo
Implementare il flusso cliente → richiesta → conferma/rifiuto.

### Da fare
- `BookingRequest`
- `BookingRequestItem`
- creazione richiesta
- elenco richieste cliente
- elenco richieste professionista
- conferma richiesta
- rifiuto richiesta
- update stato slot

### Definition of Done
- cliente invia richiesta
- professionista la vede
- professionista conferma/rifiuta
- slot aggiornati correttamente

### Stato attuale

Completata e verificata.

Endpoint implementati:

- `POST /api/v1/bookings`
- `GET /api/v1/bookings/client`
- `GET /api/v1/bookings/professional`
- `GET /api/v1/bookings/{bookingRequestId}`
- `PATCH /api/v1/bookings/{bookingRequestId}/confirm`
- `PATCH /api/v1/bookings/{bookingRequestId}/reject`
- `PATCH /api/v1/bookings/{bookingRequestId}/cancel`

Transizioni gestite:

- `PENDING -> CONFIRMED`
- `PENDING -> REJECTED`
- `PENDING -> CANCELLED`
- `CONFIRMED -> CANCELLED`

Effetti sugli slot:

- conferma booking: slot `AVAILABLE -> BOOKED`
- cancellazione booking confermato: slot `BOOKED -> AVAILABLE`
- rifiuto booking pending: slot resta `AVAILABLE`

Nota: nel codice attuale la creazione booking avviene su un singolo `availabilitySlotId`.
Il modello con `BookingRequestItem` resta estendibile per scenari multi-slot futuri.

### Nota
Questa è la seconda grande milestone del progetto.

---

## FASE 10 — Stabilizzazione backend Availability / Bookings

### Obiettivo

Consolidare il primo workflow operativo completo del progetto prima dell’introduzione di nuovi moduli business o dell’integrazione frontend reale.

### Stato attuale

Completata, in attesa della validazione conclusiva sugli allegati finali aggiornati.

### Attività completate

- correzione dei controlli di accesso Availability;
- validazione slot availability futuri;
- esclusione degli slot scaduti dalla lettura cliente;
- esclusione degli slot con booking `PENDING` dalle disponibilità mostrate al cliente;
- blocco booking e conferma booking su slot scaduti;
- limite e normalizzazione della nota booking;
- regole Booking rese esplicite in `SecurityConfig`;
- controllo specializzazione `PERSONAL_TRAINER` per Availability e Bookings;
- blocco booking e conferma booking su slot appartenenti a `NUTRITIONIST`;
- lock pessimisti per proteggere la creazione Booking sullo stesso slot;
- lock pessimisti per proteggere le transizioni concorrenti della richiesta Booking;
- lock pessimisti sugli slot durante la conferma Booking;
- lock pessimisti sul professionista per proteggere Availability da overlap concorrenti;
- riserva logica dello slot quando esiste un booking `PENDING`;
- blocco update e block dello slot con richiesta booking `PENDING`;
- protezione dell’integrità storica Booking tramite blocco della ripianificazione temporale di slot già utilizzati in una richiesta;
- ampliamento test automatici principali per Availability e Bookings;
- riallineamento della documentazione tecnica allo stato reale del backend.

### Regole consolidate più importanti

- gli slot Availability sono gestiti solo da professionisti `PERSONAL_TRAINER`;
- il cliente vede solo slot futuri, disponibili e realmente prenotabili;
- uno slot con booking `PENDING` non viene più mostrato come disponibile;
- uno slot con booking `PENDING` non può essere modificato o bloccato manualmente;
- uno slot già coinvolto in un booking non può cambiare data o ora;
- per proporre un nuovo intervallo temporale dopo uno storico Booking deve essere creato un nuovo slot.

### Definition of Done

- build backend completata correttamente;
- test automatici completati correttamente;
- documentazione allineata al codice finale;
- audit conclusivo senza incongruenze bloccanti residue.

---

## FASE 11 — Workout module
### Obiettivo
Implementare l’area schede di allenamento.

### Da fare
- `WorkoutPlan`
- `WorkoutWeek`
- `WorkoutDay`
- `WorkoutExercise`
- creazione scheda
- lettura scheda cliente
- lettura schede professionista
- nuova versione scheda
- storico schede
- regola: una sola scheda attiva per coppia professionista-cliente, se previsto dal dominio

### Definition of Done
- professionista crea scheda completa
- cliente la visualizza
- nuova versione archivia la precedente

---

## FASE 12 — Nutrition module
### Obiettivo
Implementare l’area piani alimentari.

### Da fare
- `NutritionPlan`
- `NutritionWeek`
- `NutritionDay`
- `NutritionEntry`
- creazione piano
- lettura piano cliente
- lettura piani professionista
- nuova versione piano
- storico
- regola: un solo piano attivo per coppia professionista-cliente, se previsto dal dominio

### Definition of Done
- professionista crea piano
- cliente lo visualizza
- storico e versionamento base funzionanti

---

## FASE 13 — Feedback
### Obiettivo
Permettere al cliente di segnalare problemi o richieste su contenuti assegnati.

### Da fare
- feedback su schede o piani
- invio feedback
- lettura feedback lato professionista
- storico feedback inviati lato cliente

### Definition of Done
- cliente invia feedback solo su contenuti propri
- professionista riceve feedback corretti

---

## FASE 14 — Measurements
### Obiettivo
Implementare lo storico misurazioni cliente.

### Da fare
- `ClientMeasurement`
- inserimento misurazione
- elenco storico
- dettaglio misurazione
- controlli autorizzazione:
  - cliente stesso
  - professionista collegato

### Definition of Done
- storico misurazioni funzionante
- permessi corretti
- base pronta per grafici futuri

---

## FASE 15 — Password reset
### Obiettivo
Aggiungere il recupero password in modo coerente con il sistema auth.

### Da fare
- `PasswordResetToken`
- endpoint forgot password
- endpoint reset password
- token monouso con scadenza
- invalidazione token dopo utilizzo

### Definition of Done
- utente può richiedere reset password
- utente può impostare nuova password con token valido
- token di reset sicuro e monouso

---

## FASE 16 — Hardening finale e preparazione integrazione/deploy
### Obiettivo
Ripulire il backend prima dell’integrazione forte col frontend e del deploy.

### Da fare
- rifinitura DTO
- rifinitura mapper
- standardizzazione response
- controllo naming endpoint
- test dei casi di errore
- test autorizzazioni
- log puliti
- seed dati demo, se utile

### Definition of Done
- backend coerente
- errori gestiti bene
- struttura leggibile
- pronto per integrazione frontend seria

---

---

## 5. Ordine reale consigliato delle milestone
Per ragionare in blocchi sostenibili:

### Milestone 1
- setup
- fondazioni tecniche
- utenti
- auth
- verifica email
- inviti
- registrazione cliente
- link professionista-cliente

### Milestone 2
- profilo
- clienti/professionisti read

### Milestone 3
- availability
- bookings
- stabilizzazione del workflow Availability / Bookings
- tutela concorrenza, visibilità slot e storico prenotazioni

### Milestone 4
- workout
- nutrition
- feedback
- measurements

### Milestone 5
- password reset
- pulizia
- test
- integrazione completa
- preparazione deploy

---

## 6. Stato attuale coerente del progetto

In base all’avanzamento reale fin qui raggiunto, risultano completate:

- FASE 0 — Setup iniziale progetto
- FASE 1 — Fondazioni tecniche
- FASE 2 — Modello utenti
- FASE 3 — Sicurezza e autenticazione
- FASE 4 — Verifica email professionista
- FASE 5 — Inviti, registrazione cliente e collegamenti
- FASE 6 — Modulo profilo/account
- FASE 7 — Modulo clienti e professionisti in lettura
- FASE 8 — Availability
- FASE 9 — Bookings
- FASE 10 — Stabilizzazione backend Availability / Bookings

Risultano inoltre aggiunti test automatici di copertura per i flussi principali di Availability e Bookings.

Dopo il completamento di Availability e Bookings è stata eseguita una fase di consolidamento backend e documentale.

### Stabilizzazione completata

Durante la stabilizzazione sono stati completati:

- correzione dei controlli di accesso Availability;
- validazione slot availability futuri;
- esclusione degli slot scaduti dalla lettura cliente;
- blocco booking e conferma booking su slot scaduti;
- limite e normalizzazione della nota booking;
- regole Booking rese esplicite in `SecurityConfig`;
- controllo specializzazione `PERSONAL_TRAINER` per Availability e Bookings;
- blocco booking e conferma booking su slot appartenenti a `NUTRITIONIST`;
- test automatici principali per Availability e Bookings;
- lock pessimisti per proteggere creazione e transizioni Booking concorrenti;
- lock pessimisti per proteggere Availability da overlap concorrenti;
- riserva logica dello slot quando esiste un booking `PENDING`;
- blocco update/block dello slot con richiesta booking pendente;
- esclusione degli slot con booking `PENDING` dalla lettura disponibilità lato cliente;
- tutela dello storico Booking tramite immutabilità temporale degli slot già coinvolti in richieste;
- obbligo di creare un nuovo slot per proporre un intervallo diverso dopo uno storico Booking;
- riallineamento della documentazione tecnica allo stato reale del backend.

Il backend può ora essere considerato consolidato fino al workflow operativo Availability/Bookings, salvo eventuali problemi che emergano dalla validazione finale dei file aggiornati.

Dopo questa fase, le due strade più sensate saranno:

1. iniziare l’integrazione frontend reale sugli endpoint già pronti
2. iniziare il modulo Workout

---

## 7. Cosa NON fare
Per non perderti, evita questi errori:

- fare backend e frontend insieme da subito
- scrivere tutte le entity e poi lasciare i service vuoti
- creare molti endpoint senza testarli
- passare a un nuovo modulo quando quello prima è rotto
- fare security troppo tardi
- saltare Postman e testare solo dal frontend

---

## 8. Metodo di lavoro consigliato per ogni modulo
Per ogni modulo usa sempre questo mini-flusso:

1. entity
2. repository
3. service
4. DTO
5. mapper o costruzione response
6. controller
7. test Postman
8. rifinitura eccezioni e validazioni

---

## 9. Criterio per capire se puoi passare allo step successivo
Puoi passare oltre solo se il blocco corrente è:

- compilato senza errori
- testato con Postman
- con validazioni minime presenti
- con errori gestiti in modo chiaro
- comprensibile anche se lo riapri dopo giorni

---

## 10. Decisione pratica finale
L’ordine consigliato definitivo aggiornato è:

1. setup — completato
2. fondazioni tecniche — completato
3. utenti — completato
4. security e auth — completato
5. verifica email uniforme per professionista e cliente — completato
6. inviti + registrazione cliente + link — completato
7. profilo/account — completato
8. relazioni clienti/professionisti read — completato
9. availability — completato
10. bookings — completato
11. stabilizzazione backend Availability / Bookings, test e documentazione — completata, in attesa di validazione conclusiva aggiornata
12. frontend integration oppure workout module — da decidere dopo validazione conclusiva
13. nutrition module
14. feedback
15. measurements
16. password reset
17. hardening finale e preparazione deploy
